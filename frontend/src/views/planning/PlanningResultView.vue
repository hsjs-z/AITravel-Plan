<template>
  <main class="result-shell">
    <header class="result-hero">
      <div>
        <p class="eyebrow">AI TRAVEL PLAN</p>
        <h1>{{ itinerary?.destination || taskResult?.destination || '旅行计划结果' }}</h1>
        <p class="hero-sub">你的专属旅行计划 · {{ taskResult?.totalDays || dayViews.length || '—' }} 天</p>
      </div>
      <div v-if="itinerary" class="hero-actions">
        <span class="status-chip">{{ statusLabel }}</span>
        <el-button
          v-if="itinerary.status === 'DRAFT'"
          type="primary"
          :loading="confirming"
          @click="confirmDraft"
        >
          确认行程
        </el-button>
      </div>
    </header>

    <PlanningWait v-if="loading" :has-preferences="hasPreferences" />
    <div v-else-if="error" class="result-state is-error">
      <p>{{ error }}</p>
      <el-button type="primary" plain @click="load">重新加载</el-button>
    </div>

    <template v-else-if="itinerary">
      <el-alert
        v-if="globalWarnings.length"
        type="warning"
        :closable="false"
        class="global-warning"
        :title="globalWarnings.join('；')"
      />

      <div class="result-grid" :class="{ 'is-map-unavailable': mapUnavailable }">
        <section class="plan-column">
          <div class="section-heading">
            <span class="heading-mark">▦</span>
            <h2>行程安排</h2>
          </div>

          <DayAccordion
            v-for="day in dayViews"
            :key="day.day"
            :day="day.day"
            :destination="itinerary.destination"
            :theme="day.theme"
            :slots="day.slots"
            :warnings="day.warnings"
            :draft="isDraft"
            :open="openDays.has(day.day)"
            @toggle="toggleDay(day.day)"
            @add="openPicker(day, $event)"
            @locate="locate"
            @delete="removeItem"
          />
        </section>

        <aside v-if="!mapUnavailable" class="map-column">
          <AmapPanel
            :items="itinerary.items"
            :selected-id="selectedItemId"
            @error="handleMapError"
            @select="selectFromMap"
          />
        </aside>
      </div>

      <TravelTips
        :tips="tips"
        :loading="tipsState === 'generating'"
        :error="tipsState === 'failed' ? '生成失败，请刷新重试' : ''"
        :pending="itinerary?.status !== 'CONFIRMED'"
      />
      <HotelPromoCard />
      <p v-if="mapUnavailable" class="map-fallback">地图暂不可用，已切换为纯行程列表。</p>

      <AttractionPicker
        v-model="pickerOpen"
        :destination="itinerary.destination"
        :date="pickerDate"
        :time-slot="pickerSlot"
        @select="handlePicked"
      />
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPlanningTask } from '../../api/planning'
import {
  addItineraryItem,
  confirmItinerary,
  deleteItineraryItem,
  getItinerary,
} from '../../api/itinerary'
import type {
  AttractionItem,
  Itinerary,
  ItineraryItem,
  PlanningResult,
  PlanningTask,
  TimeSlot,
} from '../../api/types'
import DayAccordion from '../../components/planning/DayAccordion.vue'
import AmapPanel from '../../components/planning/AmapPanel.vue'
import TravelTips from '../../components/planning/TravelTips.vue'
import AttractionPicker from '../../components/planning/AttractionPicker.vue'
import PlanningWait from '../../components/planning/PlanningWait.vue'
import HotelPromoCard from '../../components/planning/HotelPromoCard.vue'

interface DayView {
  day: number
  date: string
  theme: string
  slots: Record<TimeSlot, ItineraryItem[]>
  warnings: string[]
}

const route = useRoute()
const taskId = route.params.taskId as string
const task = ref<PlanningTask | null>(null)
const itinerary = ref<Itinerary | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const openDays = ref(new Set<number>())
const pickerOpen = ref(false)
const pickerDate = ref('')
const pickerSlot = ref<TimeSlot>('MORNING')
const selectedItemId = ref<string | null>(null)
const mapUnavailable = ref(false)
const confirming = ref(false)
const tipsState = ref<'idle' | 'generating' | 'failed'>('idle')
let tipsPolling = false
let disposed = false

const taskResult = computed<PlanningResult | null>(() => {
  if (!task.value?.result) return null
  try {
    return JSON.parse(task.value.result) as PlanningResult
  } catch {
    return null
  }
})

const hasPreferences = computed(() => {
  const taskPreference = (
    task.value as (PlanningTask & { preferences?: string | null }) | null
  )?.preferences
  const resultPreference = (taskResult.value as { preferences?: string | null } | null)?.preferences
  const queryPreference = route.query.preferences
  const stored = sessionStorage.getItem(`planning-preferences:${taskId}`)
  const value =
    taskPreference ??
    resultPreference ??
    (typeof queryPreference === 'string' ? queryPreference : undefined) ??
    stored
  return Boolean(value && value.trim())
})

const isDraft = computed(() => itinerary.value?.status === 'DRAFT')

const statusLabel = computed(() => {
  switch (itinerary.value?.status) {
    case 'DRAFT':
      return '草稿'
    case 'CONFIRMED':
      return '已确认'
    case 'LOCKED':
      return '已锁定'
    case 'COMPLETED':
      return '已完成'
    default:
      return ''
  }
})

const dayViews = computed<DayView[]>(() => {
  if (!itinerary.value) return []
  const start = itinerary.value.startDate
  const total = taskResult.value?.totalDays || daysBetween(start, itinerary.value.endDate) + 1
  const byDate = new Map<string, ItineraryItem[]>()
  for (const item of itinerary.value.items) {
    const list = byDate.get(item.dayDate) ?? []
    list.push(item)
    byDate.set(item.dayDate, list)
  }
  return Array.from({ length: total }, (_, index) => {
    const day = index + 1
    const date = addDays(start, index)
    const slots: Record<TimeSlot, ItineraryItem[]> = {
      MORNING: [],
      AFTERNOON: [],
      EVENING: [],
    }
    for (const item of byDate.get(date) ?? []) {
      if (item.timeSlot && slots[item.timeSlot]) slots[item.timeSlot].push(item)
    }
    for (const slot of Object.keys(slots) as TimeSlot[]) {
      slots[slot].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    }
    return {
      day,
      date,
      theme: taskResult.value?.days?.[index]?.theme ?? `第${day}天`,
      slots,
      warnings: (itinerary.value?.warnings ?? []).filter((warning) =>
        warning.startsWith(`第${day}天`),
      ),
    }
  })
})

const globalWarnings = computed(() => {
  const warnings = itinerary.value?.warnings ?? []
  const dayPrefixes = dayViews.value.map((day) => `第${day.day}天`)
  return warnings.filter((warning) => !dayPrefixes.some((prefix) => warning.startsWith(prefix)))
})

const tips = computed(() => itinerary.value?.tips ?? [])

function startTipsPolling() {
  if (tipsPolling || !itinerary.value || itinerary.value.status !== 'CONFIRMED') return
  if ((itinerary.value.tips ?? []).length) {
    tipsState.value = 'idle'
    return
  }
  tipsPolling = true
  tipsState.value = 'generating'
  void pollTips()
}

async function pollTips() {
  const id = itinerary.value?.id
  if (!id) {
    tipsPolling = false
    return
  }
  const deadline = Date.now() + 60000
  try {
    while (!disposed && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 2000))
      if (disposed) return
      try {
        const latest = await getItinerary(id)
        if (disposed) return
        itinerary.value = latest
        if ((latest.tips ?? []).length) {
          tipsState.value = 'idle'
          return
        }
      } catch {
        // keep polling until the 60s deadline
      }
    }
    if (!disposed) tipsState.value = 'failed'
  } finally {
    tipsPolling = false
  }
}

function addDays(date: string, offset: number): string {
  const value = new Date(`${date}T00:00:00`)
  value.setDate(value.getDate() + offset)
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function daysBetween(start: string, end: string): number {
  const a = new Date(`${start}T00:00:00`).getTime()
  const b = new Date(`${end}T00:00:00`).getTime()
  return Math.max(0, Math.round((b - a) / 86400000))
}

async function load() {
  loading.value = true
  error.value = null
  try {
    task.value = await getPlanningTask(taskId)
    while (task.value && (task.value.status === 'PENDING' || task.value.status === 'RUNNING')) {
      await new Promise((resolve) => setTimeout(resolve, 2000))
      if (disposed) return
      task.value = await getPlanningTask(taskId)
    }
    if (task.value?.status === 'FAILED') {
      throw new Error(task.value.failureReason || '规划失败')
    }
    if (!task.value?.itineraryId) {
      throw new Error('规划任务尚未生成草稿行程')
    }
    itinerary.value = await getItinerary(task.value.itineraryId)
    const open = new Set<number>()
    open.add(1)
    openDays.value = open
    selectedItemId.value =
      itinerary.value.items.find((item) => item.latitude != null && item.longitude != null)?.id ?? null
    startTipsPolling()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function toggleDay(day: number) {
  const next = new Set(openDays.value)
  next.has(day) ? next.delete(day) : next.add(day)
  openDays.value = next
}

function openPicker(day: DayView, slot: TimeSlot) {
  if (!isDraft.value) return
  if (day.slots[slot].length >= 3) {
    ElMessage.warning('该时段最多 3 个景点')
    return
  }
  pickerDate.value = day.date
  pickerSlot.value = slot
  pickerOpen.value = true
}

async function handlePicked(item: AttractionItem) {
  if (!itinerary.value) return
  try {
    itinerary.value = await addItineraryItem(itinerary.value.id, {
      dayDate: pickerDate.value,
      name: item.name,
      description: item.summary ?? undefined,
      suggestedHours: item.suggestedHours,
      timeSlot: pickerSlot.value,
      resourceId: item.resourceId,
      specId: item.free ? null : item.specId,
    })
    ElMessage.success('已加入行程')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '添加失败')
  }
}

async function removeItem(item: ItineraryItem) {
  if (!itinerary.value || !isDraft.value) return
  try {
    await ElMessageBox.confirm(`从草稿中移除「${item.name ?? item.description}」？`, '移除景点', {
      type: 'warning',
    })
    itinerary.value = await deleteItineraryItem(itinerary.value.id, item.id)
    ElMessage.success('已移除')
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err instanceof Error ? err.message : '移除失败')
    }
  }
}

async function confirmDraft() {
  if (!itinerary.value || itinerary.value.status !== 'DRAFT') return
  try {
    await ElMessageBox.confirm(
      '提交后将为你生成旅行建议，之后行程就不能再修改了。确定提交吗？',
      '确认行程',
      { type: 'info' },
    )
    confirming.value = true
    itinerary.value = await confirmItinerary(itinerary.value.id)
    await nextTick()
    document.getElementById('travel-tips')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    ElMessage.success('行程已确认')
    startTipsPolling()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err instanceof Error ? err.message : '确认失败')
    }
  } finally {
    confirming.value = false
  }
}

function locate(item: ItineraryItem) {
  if (item.latitude == null || item.longitude == null) {
    ElMessage.info('坐标待补，暂时无法定位')
    return
  }
  selectedItemId.value = item.id
}

function selectFromMap(item: ItineraryItem) {
  selectedItemId.value = item.id
}

function handleMapError() {
  mapUnavailable.value = true
}

onMounted(load)
onUnmounted(() => {
  disposed = true
})
</script>

<style scoped>
.result-shell {
  max-width: 1320px;
  margin: 0 auto;
  padding: 42px 24px 72px;
}

.result-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--tp-brand-hover);
  font-size: 11px;
  letter-spacing: 0.18em;
}

.result-hero h1 {
  margin: 0;
  color: var(--tp-text);
  font-size: clamp(28px, 4vw, 40px);
  font-weight: 650;
  letter-spacing: -0.03em;
}

.hero-sub {
  margin: 9px 0 0;
  color: var(--tp-text-secondary);
  font-size: 13px;
}

.status-chip {
  padding: 7px 14px;
  border: 1px solid var(--tp-button-border);
  border-radius: 100px;
  background: var(--tp-button-bg);
  color: var(--tp-brand);
  font-size: 12px;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.result-state {
  padding: 60px 0;
  color: var(--tp-text-secondary);
  text-align: center;
}

.result-state.is-error {
  color: #b42318;
}

.global-warning {
  margin-bottom: 16px;
}

.result-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 18px;
  align-items: start;
}

.result-grid.is-map-unavailable {
  grid-template-columns: minmax(0, 1fr);
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.heading-mark {
  color: var(--tp-brand-hover);
  font-size: 15px;
}

.section-heading h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.plan-column {
  min-width: 0;
}

.map-column {
  position: sticky;
  top: 84px;
}

.map-fallback {
  margin: 10px 0 0;
  color: var(--tp-text-secondary);
  font-size: 11px;
}

@media (max-width: 1080px) {
  .result-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .map-column {
    position: static;
    order: -1;
  }
}

@media (max-width: 640px) {
  .result-shell {
    padding: 28px 14px 48px;
  }

  .result-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
