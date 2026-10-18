<template>
  <section
    class="tips-panel glass-panel"
    :class="{ 'is-loading': loading, 'just-finished': justFinished }"
  >
    <div class="tips-head">
      <span class="tips-mark">✦</span>
      <h3>旅行建议</h3>
    </div>
    <p v-if="loading" class="tips-empty">正在生成个性化建议…</p>
    <p v-else-if="error" class="tips-empty tips-error">{{ error }}</p>
    <ul v-else-if="tips.length">
      <li v-for="tip in tips" :key="tip">{{ tip }}</li>
    </ul>
    <p v-else class="tips-empty">AI 暂无额外建议。</p>
  </section>
</template>

<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'

const props = defineProps<{
  tips: string[]
  loading?: boolean
  error?: string
}>()

const justFinished = ref(false)
let finishTimer: ReturnType<typeof setTimeout> | null = null

watch(
  () => props.tips,
  (next, previous) => {
    if ((previous?.length ?? 0) === 0 && (next?.length ?? 0) > 0) {
      justFinished.value = true
      if (finishTimer) clearTimeout(finishTimer)
      finishTimer = setTimeout(() => {
        justFinished.value = false
        finishTimer = null
      }, 1500)
    }
  },
)

onUnmounted(() => {
  if (finishTimer) clearTimeout(finishTimer)
})
</script>

<style scoped>
.tips-panel {
  margin-top: 16px;
  padding: 20px 22px;
  border-radius: 18px;
  background: var(--tp-glass);
}

.tips-panel.is-loading {
  animation: tips-breathing 2s ease-in-out infinite;
}

.tips-panel.just-finished {
  animation: tips-finished 1.5s ease-out 1;
}

@keyframes tips-breathing {
  0%,
  100% {
    box-shadow:
      0 0 0 1px rgba(77, 107, 254, 0.18),
      0 0 12px rgba(77, 107, 254, 0.08);
  }

  50% {
    box-shadow:
      0 0 0 1px rgba(77, 107, 254, 0.55),
      0 0 22px rgba(77, 107, 254, 0.35);
  }
}

@keyframes tips-finished {
  0% {
    box-shadow:
      0 0 0 1px rgba(77, 107, 254, 0.8),
      0 0 24px rgba(77, 107, 254, 0.45);
  }

  100% {
    box-shadow:
      0 0 0 1px rgba(77, 107, 254, 0.18),
      0 0 12px rgba(77, 107, 254, 0.08);
  }
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

.tips-error {
  color: #d14343;
}
</style>
