<template>
  <el-card shadow="hover">
    <template #header>用户名</template>
    <el-form label-position="top" @submit.prevent="save">
      <el-form-item label="显示名称">
        <el-input
          v-model="username"
          placeholder="输入用户名"
          autocomplete="nickname"
          maxlength="64"
          show-word-limit
          clearable
          :disabled="saving"
        />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="saving">
        保存用户名
      </el-button>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api.js'
import { useUserStore } from '@/stores/user.js'
import { normalizeUsername } from '@/utils/username.js'

const userStore = useUserStore()
const username = ref(JSON.parse(localStorage.getItem('user') || '{}').username || '默认用户')
const saving = ref(false)

async function save() {
  if (saving.value) return
  try {
    const normalized = normalizeUsername(username.value)
    saving.value = true
    const { data } = await api.post('/user/update-name', { username: normalized })
    if (!data.success) throw new Error(data.message || '用户名保存失败')
    userStore.setUser(data.user)
    username.value = data.user.username
    ElMessage.success(data.message)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '用户名保存失败')
  } finally {
    saving.value = false
  }
}
</script>
