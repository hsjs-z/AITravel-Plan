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
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.035);
  transition: border-color 150ms ease-out, background-color 150ms ease-out;
}

.attraction-card:hover {
  border-color: rgba(106, 132, 255, 0.35);
  background: rgba(77, 107, 254, 0.06);
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
  color: #c8d2ff;
  font-size: 12px;
}

.price-label.is-free {
  color: #9fe6c1;
}

.price-label.is-unavailable {
  color: #ffc78a;
}

.price-note {
  color: var(--tp-text-secondary);
  font-size: 11px;
}

.locate-link {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--tp-brand-hover);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.delete-button {
  align-self: flex-start;
  border: 1px solid rgba(255, 128, 128, 0.2);
  border-radius: 8px;
  padding: 4px 7px;
  background: transparent;
  color: #ffb4b4;
  font-size: 11px;
  cursor: pointer;
  transition: background-color 150ms ease-out, border-color 150ms ease-out;
}

.delete-button:hover {
  border-color: rgba(255, 128, 128, 0.5);
  background: rgba(255, 100, 100, 0.08);
}
</style>
