<template>
  <section class="day-section" :class="{ 'is-open': open }">
    <button type="button" class="day-header" @click="$emit('toggle')">
      <div class="day-title-block">
        <span class="day-index">第{{ day }}天</span>
        <span class="day-theme">{{ destination }} · {{ theme || `第${day}天` }}</span>
        <span v-if="warnings.length" class="warning-count">⚠ {{ warnings.length }}</span>
      </div>
      <span class="chevron">{{ open ? '⌃' : '⌄' }}</span>
    </button>

    <div v-if="warnings.length" class="day-warnings">
      <p v-for="warning in warnings" :key="warning">{{ warning }}</p>
    </div>

    <div v-show="open" class="day-body">
      <div v-for="slot in slotOrder" :key="slot" class="slot-block">
        <div class="slot-head">
          <div class="slot-title">
            <span>{{ slotLabels[slot] }}</span>
            <span class="slot-hours">{{ slotHours(slot) }}</span>
          </div>
          <button
            v-if="draft"
            type="button"
            class="add-button"
            :disabled="(slots[slot] ?? []).length >= 3"
            @click="$emit('add', slot)"
          >
            {{ (slots[slot] ?? []).length >= 3 ? '已满' : '+ 添加' }}
          </button>
        </div>

        <div v-if="(slots[slot] ?? []).length" class="item-stack">
          <AttractionCard
            v-for="item in slots[slot]"
            :key="item.id"
            :item="item"
            :draft="draft"
            @locate="$emit('locate', $event)"
            @delete="$emit('delete', $event)"
          />
        </div>
        <p v-else class="empty-slot">行程暂未安排景点{{ draft ? '，可点击添加。' : '。' }}</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import AttractionCard from './AttractionCard.vue'
import type { ItineraryItem, TimeSlot } from '../../api/types'

const props = defineProps<{
  day: number
  destination: string
  theme?: string
  slots: Record<string, ItineraryItem[]>
  warnings: string[]
  draft: boolean
  open: boolean
}>()

defineEmits<{
  toggle: []
  add: [slot: TimeSlot]
  locate: [item: ItineraryItem]
  delete: [item: ItineraryItem]
}>()

const slotOrder: TimeSlot[] = ['MORNING', 'AFTERNOON', 'EVENING']
const slotLabels: Record<TimeSlot, string> = {
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚上',
}

function slotHours(slot: TimeSlot): string {
  const total = (props.slots[slot] ?? []).reduce(
    (sum, item) => sum + (item.suggestedHours ?? 0),
    0,
  )
  return `${total}h`
}
</script>

<style scoped>
.day-section {
  overflow: hidden;
  border: 1px solid var(--tp-stroke);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  transition: border-color 150ms ease-out;
}

.day-section + .day-section {
  margin-top: 12px;
}

.day-section.is-open {
  border-color: rgba(106, 132, 255, 0.45);
}

.day-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 16px 18px;
  border: 0;
  background: transparent;
  color: var(--tp-text);
  text-align: left;
  cursor: pointer;
}

.day-title-block {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.day-index {
  flex: none;
  font-weight: 650;
}

.day-theme {
  overflow: hidden;
  color: var(--tp-text-secondary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.warning-count {
  flex: none;
  color: #ffc78a;
  font-size: 11px;
}

.chevron {
  color: var(--tp-text-secondary);
  font-size: 18px;
}

.day-warnings {
  margin: 0 18px 12px;
  padding: 9px 12px;
  border-left: 2px solid #ffc78a;
  border-radius: 0 8px 8px 0;
  background: rgba(255, 199, 138, 0.08);
}

.day-warnings p {
  margin: 0;
  color: #ffd5a5;
  font-size: 11px;
  line-height: 1.6;
}

.day-body {
  padding: 0 14px 14px;
}

.slot-block {
  padding: 12px;
  border: 1px solid var(--tp-stroke-soft);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.025);
}

.slot-block + .slot-block {
  margin-top: 10px;
}

.slot-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.slot-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tp-text);
  font-size: 13px;
  font-weight: 600;
}

.slot-hours {
  padding: 2px 7px;
  border: 1px solid var(--tp-stroke-soft);
  border-radius: 100px;
  color: var(--tp-text-secondary);
  font-size: 10px;
  font-weight: 400;
}

.add-button {
  border: 1px solid rgba(106, 132, 255, 0.45);
  border-radius: 100px;
  padding: 5px 11px;
  background: rgba(77, 107, 254, 0.12);
  color: var(--tp-brand-hover);
  font-size: 11px;
  cursor: pointer;
  transition: background-color 150ms ease-out, border-color 150ms ease-out;
}

.add-button:hover:not(:disabled) {
  border-color: var(--tp-brand-hover);
  background: rgba(106, 132, 255, 0.16);
}

.add-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.item-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-slot {
  margin: 0;
  padding: 8px 0 2px;
  color: var(--tp-text-secondary);
  font-size: 11px;
}
</style>
