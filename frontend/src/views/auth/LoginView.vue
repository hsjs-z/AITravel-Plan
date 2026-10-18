<template>
  <el-container class="auth-page">
    <el-main class="auth-main">
      <el-card class="auth-card">
        <h2>登录</h2>
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
              autocomplete="current-password"
            />
          </el-form-item>
          <el-alert
            v-if="error"
            :title="error"
            type="error"
            :closable="false"
            class="form-error"
          />
          <el-button type="primary" native-type="submit" :loading="loading" class="submit-btn">
            登录
          </el-button>
          <p class="switch-link">
            还没有账号？<router-link to="/register">去注册</router-link>
          </p>
        </el-form>
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { login } from '../../api/auth'
import { saveSession } from '../../api/session'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()

const form = reactive({ email: '', password: '' })
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
    const result = await login(form.email, form.password)
    saveSession(result.token, result.userId)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/content'
    await router.replace(redirect)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
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
    linear-gradient(180deg, #dcf0ff, #fafbff);
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
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 10px 32px rgba(77, 107, 254, 0.08);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
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
