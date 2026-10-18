<template>
  <section class="tips-panel glass-panel">
    <div class="tips-head">
      <span class="tips-mark">✦</span>
      <h3>旅行建议</h3>
    </div>
    <p v-if="loading" class="tips-empty">正在生成个性化建议…</p>
    <p v-else-if="error" class="tips-empty">{{ error }}</p>
    <ul v-else-if="tips.length">
      <li v-for="tip in tips" :key="tip">{{ tip }}</li>
    </ul>
    <p v-else class="tips-empty">AI 暂无额外建议。</p>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  tips: string[]
  loading?: boolean
  error?: string
}>()
</script>

<style scoped>
.tips-panel {
  margin-top: 16px;
  padding: 20px 22px;
  border-radius: 18px;
  background: var(--tp-glass);
}

.tips-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.tips-mark {
  color: #b26a00;
  font-size: 16px;
}

.tips-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

li {
  position: relative;
  padding-left: 15px;
  color: var(--tp-text-secondary);
  font-size: 12px;
  line-height: 1.8;
}

li::before {
  content: "·";
  position: absolute;
  left: 0;
  color: var(--tp-brand-hover);
}

.tips-empty {
  margin: 0;
  color: var(--tp-text-secondary);
  font-size: 12px;
}
</style>
