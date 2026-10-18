<template>
  <div class="planning-wait">
    <div class="wait-dots" aria-hidden="true">
      <i />
      <i />
      <i />
    </div>
    <Transition name="wait-fade" mode="out-in">
      <p :key="current">{{ current }}</p>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps<{
  hasPreferences: boolean
}>()

const ALL_MESSAGES = [
  '正在检索目的地真实景区数据…',
  'AI 正在结合你的偏好挑选景点…',
  '正在编排每日行程与游览时段…',
  '正在生成行程，请稍候…',
]

const messages = computed(() =>
  props.hasPreferences
    ? ALL_MESSAGES
    : ALL_MESSAGES.filter((_, index) => index !== 1),
)
const index = ref(0)
const current = computed(() => messages.value[index.value] ?? messages.value[0])
let timer: ReturnType<typeof setInterval> | null = null

function start() {
  stop()
  timer = setInterval(() => {
    index.value = (index.value + 1) % messages.value.length
  }, 2000)
}

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(
  () => messages.value.length,
  () => {
    index.value = 0
  },
)

onMounted(start)
onUnmounted(stop)
</script>

<style scoped>
.planning-wait {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 60px 0;
  color: var(--tp-text-secondary);
}

.wait-dots {
  display: inline-flex;
  gap: 6px;
}

.wait-dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #4d6bfe;
  animation: wait-pulse 1.2s ease-in-out infinite;
}

.wait-dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.wait-dots i:nth-child(3) {
  animation-delay: 0.3s;
}

.planning-wait p {
  margin: 0;
  font-size: 13px;
}

.wait-fade-enter-active,
.wait-fade-leave-active {
  transition: opacity 0.3s ease;
}

.wait-fade-enter-from,
.wait-fade-leave-to {
  opacity: 0;
}

@keyframes wait-pulse {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: scale(0.85);
  }

  40% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
