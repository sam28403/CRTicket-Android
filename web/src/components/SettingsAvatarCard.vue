<script setup>
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import api from '@/api.js'
import { useUserStore } from '@/stores/user.js'
const userStore = useUserStore()
const avatarFileInput = ref(null)
const avatarDialogVisible = ref(false)
const avatarSaving = ref(false)
const avatarSource = ref('')
const avatarImage = ref(null)
const avatarZoom = ref(1)
const avatarOffsetX = ref(0)
const avatarOffsetY = ref(0)
const avatarUrl = ref(JSON.parse(localStorage.getItem('user') || '{}').avatar || '')
const avatarPreviewCanvas = ref(null)

function drawAvatar(context, size, clipCircle = false) {
  const image = avatarImage.value
  if (!image) return
  context.clearRect(0, 0, size, size)
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, size, size)
  if (clipCircle) {
    context.save()
    context.beginPath()
    context.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2)
    context.clip()
  }

  // 初始缩放完整包含原图；用户继续放大时才进行裁切。
  const containScale = Math.min(size / image.naturalWidth, size / image.naturalHeight)
  const scale = containScale * avatarZoom.value
  const width = image.naturalWidth * scale
  const height = image.naturalHeight * scale
  const travelX = Math.abs(width - size) / 2
  const travelY = Math.abs(height - size) / 2
  const x = (size - width) / 2 + (avatarOffsetX.value / 100) * travelX
  const y = (size - height) / 2 + (avatarOffsetY.value / 100) * travelY
  context.drawImage(image, x, y, width, height)
  if (clipCircle) context.restore()
}

function refreshAvatarPreview() {
  const canvas = avatarPreviewCanvas.value
  if (!canvas) return
  drawAvatar(canvas.getContext('2d'), canvas.width, true)
}

watch([avatarZoom, avatarOffsetX, avatarOffsetY], refreshAvatarPreview)

async function handleAvatarFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) {
    ElMessage.error('请选择不超过 10MB 的 JPG、PNG 或 WebP 图片')
    return
  }

  const source = URL.createObjectURL(file)
  const image = new Image()
  image.onload = () => {
    if (avatarSource.value.startsWith('blob:')) URL.revokeObjectURL(avatarSource.value)
    avatarSource.value = source
    avatarImage.value = image
    avatarZoom.value = 1
    avatarOffsetX.value = 0
    avatarOffsetY.value = 0
    avatarDialogVisible.value = true
    nextTick(refreshAvatarPreview)
  }
  image.onerror = () => {
    URL.revokeObjectURL(source)
    ElMessage.error('无法读取这张图片')
  }
  image.src = source
}

function renderAvatar() {
  const image = avatarImage.value
  const size = 256
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const context = canvas.getContext('2d')
  drawAvatar(context, size)
  return canvas.toDataURL('image/jpeg', 0.86)
}

async function persistAvatar(avatar) {
  avatarSaving.value = true
  try {
    const response = await api.post('/user/update-avatar', { avatar })
    if (!response.data.success) throw new Error(response.data.message || '头像修改失败')
    userStore.setUser(response.data.user)
    avatarUrl.value = response.data.user.avatar || ''
    avatarDialogVisible.value = false
    ElMessage.success(response.data.message)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '头像修改失败')
  } finally {
    avatarSaving.value = false
  }
}

async function saveAvatar() {
  if (!avatarImage.value) return
  await persistAvatar(renderAvatar())
}

async function removeAvatar() {
  await persistAvatar(null)
}

</script>
<template>
            <el-card class="avatar-card" shadow="hover">
              <template #header>
                <span>头像</span>
              </template>
              <div class="avatar-area">
                <el-avatar :size="180" :src="avatarUrl" :icon="UserFilled" />
                <input
                  ref="avatarFileInput"
                  class="avatar-file-input"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  @change="handleAvatarFile"
                />
                <div class="avatar-actions">
                  <el-button size="large" type="primary" @click="avatarFileInput?.click()">更改头像</el-button>
                  <el-button
                    v-if="avatarUrl"
                    size="large"
                    :loading="avatarSaving"
                    @click="removeAvatar"
                  >清除头像</el-button>
                </div>
              </div>
            </el-card>
          <el-dialog v-model="avatarDialogVisible" title="裁切头像" width="420px" :close-on-click-modal="false">
            <div class="avatar-crop-wrap">
              <div class="avatar-crop-preview">
                <canvas ref="avatarPreviewCanvas" width="260" height="260" aria-label="头像裁切预览"></canvas>
              </div>
              <label>缩放 <el-slider v-model="avatarZoom" :min="1" :max="3" :step="0.01" /></label>
              <label>水平位置 <el-slider v-model="avatarOffsetX" :min="-100" :max="100" /></label>
              <label>垂直位置 <el-slider v-model="avatarOffsetY" :min="-100" :max="100" /></label>
            </div>
            <template #footer>
              <el-button @click="avatarDialogVisible = false">取消</el-button>
              <el-button type="primary" :loading="avatarSaving" @click="saveAvatar">保存头像</el-button>
            </template>
          </el-dialog>

</template>
<style scoped>
.avatar-area { display: grid; justify-items: center; gap: 20px; }
.avatar-file-input { display: none; }
.avatar-crop-preview { width: 260px; height: 260px; margin: 0 auto 20px; }
.avatar-crop-preview canvas { width: 100%; border-radius: 50%; }
.avatar-crop-wrap label { display: grid; grid-template-columns: 80px 1fr; align-items: center; }
</style>
