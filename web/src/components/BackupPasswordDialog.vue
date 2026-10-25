<script setup>
import { ref, watch } from 'vue'

const props = defineProps({ mode: String, busy: Boolean, error: String })
const emit = defineEmits(['submit', 'cancel'])
const encrypt = ref(true)
const password = ref('')
const confirmation = ref('')
const validation = ref('')

watch(() => props.mode, () => {
  encrypt.value = true
  password.value = ''
  confirmation.value = ''
  validation.value = ''
})

function submit() {
  if (props.mode === 'export' && encrypt.value) {
    if ([...password.value].length < 8 || password.value.length > 1024) {
      validation.value = '备份密码须为 8 至 1024 个字符'
      return
    }
    if (password.value !== confirmation.value) {
      validation.value = '两次输入的密码不一致'
      return
    }
  }
  emit('submit', { password: password.value, encryption: encrypt.value ? 'aes-gcm' : 'none' })
  password.value = ''
  confirmation.value = ''
  validation.value = ''
}
</script>

<template>
  <el-dialog
    :model-value="!!mode"
    :title="mode === 'export' ? '导出备份' : '导入备份'"
    width="min(440px, 92vw)"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    @close="emit('cancel')"
  >
    <el-form label-position="top" :disabled="busy" @submit.prevent="submit">
      <el-form-item v-if="mode === 'export'" label="加密备份">
        <el-switch v-model="encrypt" />
      </el-form-item>
      <template v-if="mode === 'import' || encrypt">
        <el-form-item label="备份密码">
          <el-input
            v-model="password" type="password" show-password
            autocomplete="off" :maxlength="1024"
          />
        </el-form-item>
        <el-form-item v-if="mode === 'export'" label="再次输入密码">
          <el-input
            v-model="confirmation" type="password" show-password
            autocomplete="off" :maxlength="1024"
          />
        </el-form-item>
      </template>
      <el-alert v-if="validation || error" :title="validation || error" type="error" :closable="false" />
    </el-form>
    <template #footer>
      <el-button :disabled="busy" @click="emit('cancel')">取消</el-button>
      <el-button
        type="primary" :loading="busy"
        :disabled="(mode === 'import' || encrypt) && !password"
        @click="submit"
      >
        {{ mode === 'export' ? '开始导出' : '确定' }}
      </el-button>
    </template>
  </el-dialog>
</template>
