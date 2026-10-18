<template>
  <el-container class="auth-page">
    <el-main class="auth-main">
      <el-card class="auth-card">
        <h2>注册</h2>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="submit"
        >
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="you@example.com" autocomplete="email" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="密码"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="手机号（可选）" prop="phone">
            <el-input v-model="form.phone" placeholder="选填" />
          </el-form-item>
          <el-alert
            v-if="error"
            :title="error"
            type="error"
            :closable="false"
            class="form-error"
          />
          <el-button type="primary" native-type="submit" :loading="loading" class="submit-btn">
            注册并登录
          </el-button>
          <p class="switch-link">
            已有账号？<router-link to="/login">去登录</router-link>
          </p>
        </el-form>
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { register } from '../../api/auth'
import { saveSession } from '../../api/session'

const router = useRouter()
const formRef = ref<FormInstance>()

const form = reactive({ email: '', password: '', phone: '' })
const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const loading = ref(false)
const error = ref<string | null>(null)

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  error.value = null
  try {
    const result = await register({
      email: form.email,
      password: form.password,
      phone: form.phone.trim() || undefined,
    })
    saveSession(result.token, result.userId)
    await router.replace('/content')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  position: relative;
  overflow: hidden;
  min-height: 100vh;
  background:
    radial-gradient(circle at 16% 18%, rgba(255, 255, 255, 0.9), transparent 26%),
    radial-gradient(circle at 84% 12%, rgba(255, 255, 255, 0.68), transparent 24%),
    linear-gradient(180deg, #dcf0ff, #f6f9ff);
}

.auth-page::after {
  content: "";
  position: absolute;
  right: -15%;
  bottom: -120px;
  left: -15%;
  height: 240px;
  border-radius: 50% 50% 0 0;
  background: rgba(191, 217, 255, 0.35);
  pointer-events: none;
}

.auth-main {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 1;
}

.auth-card {
  width: 380px;
  background: #ffffff;
  border: 1px solid #e3eaf6;
  box-shadow: 0 8px 24px rgba(77, 107, 254, 0.08);
}

.form-error {
  margin-bottom: 16px;
}

.submit-btn {
  width: 100%;
}

.switch-link {
  margin: 16px 0 0;
  text-align: center;
  color: var(--tp-text-secondary);
  font-size: 14px;
}
</style>
