<template>
  <el-dialog
    :model-value="modelValue"
    title="从库存添加景点"
    width="640px"
    class="attraction-picker"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="load"
  >
    <p class="picker-sub">
      {{ destination }} · {{ date }} · {{ slotLabel }}
    </p>
    <div v-if="loading" class="picker-state">正在查询上架景区…</div>
    <div v-else-if="error" class="picker-state is-error">{{ error }}</div>
    <div v-else-if="!items.length" class="picker-state">没有找到匹配的上架景区。</div>
    <div v-else class="picker-list">
      <button
        v-for="item in items"
        :key="item.resourceId"
        type="button"
        class="picker-item"
        :disabled="!canSelect(item)"
        @click="select(item)"
      >
        <div class="picker-item-head">
          <strong>{{ item.name }}</strong>
          <span class="picker-price">{{ priceLabel(item) }}</span>
        </div>
        <div class="picker-meta">
          <span v-if="item.level">{{ item.level }}</span>
          <span v-if="item.honorTitle">{{ item.honorTitle }}</span>
          <span>{{ item.city }}</span>
        </div>
        <p class="picker-summary">{{ item.summary }}</p>
        <span v-if="!canSelect(item)" class="picker-disabled">暂不可预订</span>
      </button>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { searchAttractions } from '../../api/resources'
import type { AttractionItem, TimeSlot } from '../../api/types'

const props = defineProps<{
  modelValue: boolean
  destination: string
  date: string
  timeSlot: TimeSlot
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  select: [item: AttractionItem]
}>()

const loading = ref(false)
const error = ref<string | null>(null)
const items = ref<AttractionItem[]>([])

const slotLabel = computed(() => ({
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚上',
}[props.timeSlot]))

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) load()
  },
)

async function load() {
  loading.value = true
  error.value = null
  try {
    const page = await searchAttractions({
      destination: props.destination,
      date: props.date,
      limit: 50,
    })
    items.value = page.items ?? []
  } catch (err) {
    error.value = err instanceof Error ? err.message : '查询景区失败'
  } finally {
    loading.value = false
  }
}

function canSelect(item: AttractionItem): boolean {
  return item.free || Boolean(item.specId)
}

function priceLabel(item: AttractionItem): string {
  if (item.free) return '免费/无需购票'
  return item.price == null ? '暂不可预订' : `¥${item.price}`
}

function select(item: AttractionItem) {
  if (!canSelect(item)) return
  emit('select', item)
  emit('update:modelValue', false)
}
</script>

<style scoped>
.picker-sub {
  margin: -4px 0 14px;
  color: var(--tp-text-secondary);
  font-size: 12px;
}

.picker-state {
  padding: 28px 0;
  color: var(--tp-text-secondary);
  text-align: center;
  font-size: 13px;
}

.picker-state.is-error {
  color: #b42318;
}

.picker-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 52vh;
  overflow-y: auto;
  padding-right: 4px;
}

.picker-item {
  position: relative;
  display: block;
  width: 100%;
  padding: 13px 14px;
  border: 1px solid var(--tp-button-border);
  border-radius: 12px;
  background: var(--tp-button-bg);
  color: var(--tp-text);
  text-align: left;
  cursor: pointer;
  transition: background-color 150ms ease-out, border-color 150ms ease-out;
}

.picker-item:hover:not(:disabled) {
  border-color: var(--tp-brand);
  background: var(--tp-button-hover);
}

.picker-item:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.picker-item-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.picker-item-head strong {
  font-size: 14px;
}

.picker-price {
  color: var(--tp-brand);
  font-size: 12px;
}

.picker-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 5px;
  color: var(--tp-text-secondary);
  font-size: 11px;
}

.picker-summary {
  margin: 6px 0 0;
  color: var(--tp-text-secondary);
  font-size: 11px;
  line-height: 1.55;
}

.picker-disabled {
  display: inline-block;
  margin-top: 6px;
  color: #b26a00;
  font-size: 10px;
}
</style>
