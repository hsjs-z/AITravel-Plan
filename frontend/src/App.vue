<template>
  <div class="app-shell">
    <el-header v-if="showNav" class="app-nav">
      <span class="brand">AI 旅行规划平台</span>
      <nav class="nav-links">
        <router-link to="/content">内容资源</router-link>
        <router-link to="/planning">智能规划</router-link>
        <router-link to="/orders">我的订单</router-link>
        <router-link to="/notifications">通知中心</router-link>
      </nav>
      <el-button text @click="logout">退出</el-button>
    </el-header>
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearSession } from './api/session'

const route = useRoute()
const router = useRouter()

const showNav = computed(() => route.name !== 'login' && route.name !== 'register')

function logout() {
  clearSession()
  router.replace('/login')
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-nav {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(19, 22, 29, 0.55);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.brand {
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--tp-text);
}

.nav-links {
  display: flex;
  gap: 16px;
  flex: 1;
}

.nav-links a {
  color: var(--tp-text-secondary);
  text-decoration: none;
  font-size: 14px;
  transition: color 150ms ease-out;
}

.nav-links a.router-link-active {
  color: var(--tp-brand-hover);
  font-weight: 600;
}

.nav-links a:hover {
  color: var(--tp-text);
}
</style>
