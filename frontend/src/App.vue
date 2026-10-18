<template>
  <div class="app-shell">
    <el-header v-if="showNav" class="app-nav" :class="{ 'is-scrolled': scrolled }">
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
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearSession } from './api/session'

const route = useRoute()
const router = useRouter()

const showNav = computed(() => route.name !== 'login' && route.name !== 'register')
const scrolled = ref(false)

function onScroll() {
  scrolled.value = window.scrollY > 12
}

function logout() {
  clearSession()
  router.replace('/login')
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => window.removeEventListener('scroll', onScroll))
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
  border-bottom: 1px solid transparent;
  background: transparent;
  transition: background-color 150ms ease-out, border-color 150ms ease-out;
}

.app-nav.is-scrolled {
  border-bottom-color: rgba(31, 39, 51, 0.06);
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.brand {
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #1f2733;
}

.nav-links {
  display: flex;
  gap: 16px;
  flex: 1;
}

.nav-links a {
  color: #1f2733;
  text-decoration: none;
  font-size: 14px;
  transition: color 150ms ease-out;
}

.nav-links a.router-link-active {
  color: var(--tp-brand);
  font-weight: 600;
}

.nav-links a:hover {
  color: var(--tp-brand-hover);
}
</style>
