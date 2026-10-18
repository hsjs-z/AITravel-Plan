<template>
  <article class="attraction-card">
    <div class="card-main">
      <div class="card-title-row">
        <h4>{{ displayName }}</h4>
        <span class="hours-badge">{{ hoursLabel }}</span>
      </div>
      <p class="card-desc">{{ item.description }}</p>
      <div class="card-foot">
        <span class="price-label" :class="priceClass">{{ priceLabel }}</span>
        <span v-if="!item.resourceId" class="price-note">票价以官方为准</span>
        <button type="button" class="locate-link" @click="$emit('locate', item)">
          {{ hasCoordinates ? '点击查看地图位置' : '坐标待补' }}
        </button>
      </div>
    </div>
    <button
      v-if="draft"
      type="button"
      class="delete-button"
      aria-label="从草稿移除"
      @click="$emit('delete', item)"
    >
      删除
    </button>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ItineraryItem } from '../../api/types'

const props = defineProps<{
  item: ItineraryItem
  draft: boolean
}>()

defineEmits<{
  locate: [item: ItineraryItem]
  delete: [item: ItineraryItem]
}>()

const displayName = computed(() => {
  if (props.item.name?.trim()) return props.item.name
  return props.item.description?.split('：')[0]?.trim() || '未命名景点'
})

const hoursLabel = computed(() =>
  props.item.suggestedHours == null ? '—' : `${props.item.suggestedHours}h`,
)

const hasCoordinates = computed(() => {
  return props.item.latitude != null && props.item.longitude != null
})

const priceLabel = computed(() => {
  if (!props.item.resourceId) return '暂不可预订'
  if (props.item.ticketPrice === 0) return '免费/无需购票'
  return props.item.ticketPrice == null ? '暂不可预订' : `¥${props.item.ticketPrice}`
})

const priceClass = computed(() => ({
  'is-free': props.item.resourceId && props.item.ticketPrice === 0,
  'is-unavailable': !props.item.resourceId,
}))
</script>

<style scoped>
.attraction-card {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 15px 16px;
  border: 1px solid var(--tp-stroke-soft);
  border-radius: 14px;
  background: #ffffff;
  transition: border-color 150ms ease-out, background-color 150ms ease-out;
}

.attraction-card:hover {
  border-color: var(--tp-button-border);
  background: #f8faff;
}

.card-main {
  min-width: 0;
  flex: 1;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title-row h4 {
  margin: 0;
  color: var(--tp-text);
  font-size: 15px;
  font-weight: 600;
}

.hours-badge {
  flex: none;
  padding: 3px 8px;
  border: 1px solid var(--tp-stroke-soft);
  border-radius: 100px;
  color: var(--tp-text-secondary);
  font-size: 11px;
}

.card-desc {
  margin: 7px 0 10px;
  color: var(--tp-text-secondary);
  font-size: 12px;
  line-height: 1.65;
}

.card-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.price-label {
  color: var(--tp-brand);
  font-size: 12px;
}

.price-label.is-free {
  color: #1b8a5a;
}

.price-label.is-unavailable {
  color: #b26a00;
}

.price-note {
  color: var(--tp-text-secondary);
  font-size: 11px;
}

.locate-link {
  border: 1px solid var(--tp-button-border);
  border-radius: 100px;
  padding: 3px 9px;
  background: var(--tp-button-bg);
  color: var(--tp-brand);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.locate-link:hover {
  border-color: var(--tp-brand);
  background: var(--tp-button-hover);
}

.delete-button {
  align-self: flex-start;
  border: 1px solid var(--tp-button-border);
  border-radius: 8px;
  padding: 4px 7px;
  background: var(--tp-button-bg);
  color: var(--tp-text);
  font-size: 11px;
  cursor: pointer;
  transition: background-color 150ms ease-out, border-color 150ms ease-out;
}

.delete-button:hover {
  border-color: var(--tp-brand);
  background: var(--tp-button-hover);
}
</style>
