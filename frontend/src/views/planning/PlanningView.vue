<template>
  <main class="planning-shell">
    <section class="planning-wrap">
      <header class="planning-hero">
        <p class="eyebrow">AI TRAVEL STUDIO</p>
        <h1>把想去的地方，交给一场好计划。</h1>
        <p class="hero-sub">选择目的地与节奏，AI 会基于真实上架景区生成每日草稿。</p>
      </header>

      <el-card class="planning-card glass-card" :body-style="{ padding: '28px' }">
        <el-form label-position="top" @submit.prevent="submit">
          <div class="field-block">
            <div class="field-head">
              <label>出行天数</label>
              <span>{{ days }} 天</span>
            </div>
            <div class="pill-row">
              <button
                v-for="n in 10"
                :key="n"
                type="button"
                class="pill-option day-pill"
                :class="{ 'is-active': days === n }"
                @click="days = n"
              >
                {{ n }}
              </button>
            </div>
          </div>

          <div class="field-block">
            <div class="field-head">
              <label>目的地</label>
              <span>{{ destinationMode === 'hot' ? '热门地区' : '国内自由输入' }}</span>
            </div>
            <div class="mode-switch">
              <button
                type="button"
                class="mode-button"
                :class="{ 'is-active': destinationMode === 'hot' }"
                @click="destinationMode = 'hot'"
              >
                热门地区
              </button>
              <button
                type="button"
                class="mode-button"
                :class="{ 'is-active': destinationMode === 'free' }"
                @click="destinationMode = 'free'"
              >
                其他地区
              </button>
            </div>

            <div v-if="destinationMode === 'hot'" class="city-grid">
              <button
                v-for="city in hotDestinations"
                :key="city.name"
                type="button"
                class="pill-option city-pill"
                :class="{ 'is-active': selectedHot === city.name }"
                @click="selectedHot = city.name"
              >
                {{ city.label }}
              </button>
            </div>
            <div v-else class="free-input-block">
              <el-input
                v-model="freeDestination"
                size="large"
                placeholder="输入中国国内省份或城市，例如：大理"
                maxlength="40"
              />
              <p class="field-hint">仅支持中国国内目的地；可输入省份或城市。</p>
            </div>
          </div>

          <div class="field-block">
            <div class="field-head">
              <label>游览节奏</label>
              <span>决定每天的行程容量</span>
            </div>
            <div class="pace-grid">
              <button
                v-for="item in paceOptions"
                :key="item.value"
                type="button"
                class="pace-card"
                :class="{ 'is-active': pace === item.value }"
                @click="pace = item.value"
              >
                <span class="pace-name">{{ item.label }}</span>
                <span class="pace-desc">{{ item.description }}</span>
              </button>
            </div>
          </div>

          <div class="field-row">
            <div class="field-block compact">
              <div class="field-head">
                <label>出行人数</label>
                <span>默认 1 人</span>
              </div>
              <el-input-number v-model="partySize" :min="1" :max="20" size="large" />
            </div>
            <div class="field-block compact">
              <div class="field-head">
                <label>旅行偏好</label>
                <span>可选</span>
              </div>
              <el-input
                v-model="preferences"
                size="large"
                maxlength="2000"
                placeholder="风格 / 兴趣 / 预算 / 特殊要求"
              />
            </div>
          </div>

          <div v-if="error" class="error-line">{{ error }}</div>
          <div v-if="task && task.status !== 'SUCCEEDED'" class="task-line">
            <span>{{ statusLabel }}</span>
            <span v-if="task.status === 'FAILED'" class="task-failure">{{ task.failureReason }}</span>
            <el-button v-if="task.status === 'FAILED'" text type="primary" @click="retry">重试</el-button>
          </div>

          <el-button
            class="submit-button"
            type="primary"
            native-type="submit"
            size="large"
            :loading="submitting"
          >
            开始规划
          </el-button>
        </el-form>
      </el-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createPlanningTask, getPlanningTask, retryPlanningTask } from '../../api/planning'
import type { Pace, PlanningTask } from '../../api/types'
import { HOT_DESTINATIONS, isLikelyDomesticDestination } from './hotDestinations'

type DestinationMode = 'hot' | 'free'

const router = useRouter()
const hotDestinations = HOT_DESTINATIONS
const destinationMode = ref<DestinationMode>('hot')
const selectedHot = ref('')
const freeDestination = ref('')
const days = ref(5)
const partySize = ref(1)
const pace = ref<Pace>('BALANCED')
const preferences = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)
const task = ref<PlanningTask | null>(null)

const paceOptions: Array<{ value: Pace; label: string; description: string }> = [
  { value: 'RELAXED', label: '慵懒', description: '少走一点，留白多一点' },
  { value: 'BALANCED', label: '散步', description: '松弛与探索的平衡' },
  { value: 'VIBRANT', label: '精彩', description: '把一天装得充实一点' },
]

let pollTimer: ReturnType<typeof setInterval> | null = null

const statusLabel = computed(() => {
  switch (task.value?.status) {
    case 'PENDING':
      return '已受理，正在准备'
    case 'RUNNING':
      return '正在生成行程'
    case 'FAILED':
      return '规划失败'
    default:
      return ''
  }
})

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling(taskId: string) {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const next = await getPlanningTask(taskId)
      task.value = next
      if (next.status === 'FAILED') {
        stopPolling()
        return
      }
      if (next.status === 'SUCCEEDED') {
        stopPolling()
        if (next.itineraryId) {
          router.push({ name: 'planning-result', params: { taskId: next.id } })
        } else {
          error.value = '规划完成但没有生成草稿行程，请重试'
        }
      }
    } catch (err) {
      stopPolling()
      error.value = err instanceof Error ? err.message : '查询任务失败'
    }
  }, 2000)
}

async function submit() {
  error.value = null
  const destination =
    destinationMode.value === 'hot' ? selectedHot.value : freeDestination.value.trim()
  if (!destination) {
    error.value = destinationMode.value === 'hot' ? '请选择一个热门地区' : '请输入目的地'
    return
  }
  if (destinationMode.value === 'free' && !isLikelyDomesticDestination(destination)) {
    error.value = '仅支持中国国内目的地，请检查输入'
    return
  }

  submitting.value = true
  task.value = null
  stopPolling()
  try {
    const created = await createPlanningTask({
      destination,
      days: days.value,
      partySize: partySize.value,
      pace: pace.value,
      preferences: preferences.value.trim() || undefined,
    })
    task.value = {
      id: created.taskId,
      status: 'PENDING',
      itineraryId: null,
      failureReason: null,
      result: null,
      pace: pace.value,
    }
    startPolling(created.taskId)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '提交失败')
    error.value = err instanceof Error ? err.message : '提交失败'
  } finally {
    submitting.value = false
  }
}

async function retry() {
  if (!task.value) return
  submitting.value = true
  error.value = null
  try {
    const next = await retryPlanningTask(task.value.id)
    task.value = next
    startPolling(next.id)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '重试失败'
  } finally {
    submitting.value = false
  }
}

onUnmounted(stopPolling)
</script>

<style scoped>
.planning-shell {
  min-height: calc(100vh - 64px);
  display: flex;
  justify-content: center;
  padding: 56px 20px 72px;
}

.planning-wrap {
  width: 100%;
  max-width: 720px;
}

.planning-hero {
  margin-bottom: 22px;
}

.eyebrow {
  margin: 0 0 10px;
  color: var(--tp-brand-hover);
  font-size: 11px;
  letter-spacing: 0.18em;
}

.planning-hero h1 {
  margin: 0;
  font-size: clamp(28px, 4vw, 42px);
  font-weight: 650;
  letter-spacing: -0.03em;
  color: var(--tp-text);
}

.hero-sub {
  margin: 12px 0 0;
  color: var(--tp-text-secondary);
  line-height: 1.7;
  font-size: 14px;
}

.planning-card {
  border-radius: 24px;
}

.field-block {
  margin-bottom: 26px;
}

.field-block.compact {
  flex: 1;
  margin-bottom: 0;
}

.field-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.field-head label {
  color: var(--tp-text);
  font-size: 14px;
  font-weight: 600;
}

.field-head span {
  color: var(--tp-text-secondary);
  font-size: 12px;
}

.field-row {
  display: flex;
  gap: 18px;
  margin-bottom: 26px;
}

.pill-row,
.city-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.day-pill {
  width: 40px;
  min-height: 36px;
  padding: 0;
}

.city-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.city-pill {
  width: 100%;
  font-size: 13px;
}

.mode-switch {
  display: inline-flex;
  gap: 6px;
  padding: 4px;
  margin-bottom: 12px;
  border: 1px solid var(--tp-stroke-soft);
  border-radius: 100px;
  background: #f3f7ff;
}

.mode-button {
  border: 1px solid var(--tp-button-border);
  border-radius: 100px;
  padding: 8px 14px;
  background: var(--tp-button-bg);
  color: var(--tp-text);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background-color 150ms ease-out, color 150ms ease-out;
}

.mode-button.is-active {
  border-color: var(--tp-brand);
  background: var(--tp-brand);
  color: #ffffff;
}

.free-input-block .el-input {
  max-width: 420px;
}

.field-hint {
  margin: 8px 0 0;
  color: var(--tp-text-secondary);
  font-size: 12px;
}

.pace-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.pace-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--tp-button-border);
  border-radius: 16px;
  background: var(--tp-button-bg);
  color: var(--tp-text-secondary);
  text-align: left;
  cursor: pointer;
  transition: background-color 150ms ease-out, border-color 150ms ease-out;
}

.pace-card:hover {
  border-color: var(--tp-brand);
  background: var(--tp-button-hover);
}

.pace-card.is-active {
  border-color: var(--tp-brand);
  background: var(--tp-button-hover);
}

.pace-name {
  color: var(--tp-text);
  font-size: 15px;
  font-weight: 600;
}

.pace-desc {
  font-size: 11px;
  line-height: 1.5;
}

.error-line,
.task-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid rgba(255, 128, 128, 0.25);
  border-radius: 12px;
  background: rgba(255, 100, 100, 0.08);
  color: #b42318;
  font-size: 13px;
}

.task-line {
  border-color: var(--tp-button-border);
  background: var(--tp-button-bg);
  color: var(--tp-text-secondary);
}

.task-failure {
  color: #b42318;
}

.submit-button {
  width: 100%;
  height: 46px;
  border-radius: 14px;
  font-weight: 600;
}

@media (max-width: 640px) {
  .planning-shell {
    padding: 32px 14px 48px;
  }

  .city-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .pace-grid,
  .field-row {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
