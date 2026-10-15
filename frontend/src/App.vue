<template>
  <div class="app-shell">
    <el-header v-if="showNav" class="app-nav">
      <span class="brand">AI 旅行规划平台</span>
      <nav class="nav-links">
        <router-link to="/content">内容资源</router-link>
        <router-link to="/planning">智能规划</router-link>
        <router-link to="/orders">我的订单</router-link>
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
  border-bottom: 1px solid #e4e7ed;
}

.brand {
  font-weight: 600;
}

.nav-links {
  display: flex;
  gap: 16px;
  flex: 1;
}

.nav-links a {
  color: #303133;
  text-decoration: none;
}

.nav-links a.router-link-active {
  color: #409eff;
  font-weight: 600;
}
</style>
