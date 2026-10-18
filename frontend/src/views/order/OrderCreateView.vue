<template>
  <el-container class="order-page">
    <el-main>
      <el-card v-if="error" class="page-card">
        <el-result icon="error" title="无法发起预订" :sub-title="error" />
      </el-card>

      <el-card v-else class="page-card">
        <h2>一键预订</h2>
        <el-alert
          v-if="itinerary && itinerary.status !== 'CONFIRMED'"
          :title="itinerary.status === 'LOCKED' ? '行程已锁定（存在进行中的订单），暂不可再次预订' : '仅已确认的行程可发起预订'"
          type="warning"
          :closable="false"
          class="block-hint"
        />

        <template v-if="itinerary && itinerary.status === 'CONFIRMED'">
          <p class="hint">
            只展示行程内可预订门票，默认数量为出行人数；可调整数量，免费景点不在预订范围。
          </p>
          <el-table :data="rows" class="items-table">
            <el-table-column width="50">
              <template #default="{ row }">
                <el-checkbox v-model="row.selected" :disabled="!row.bookable" />
              </template>
            </el-table-column>
            <el-table-column label="资源" min-width="160">
              <template #default="{ row }">{{ row.resourceName }}</template>
            </el-table-column>
            <el-table-column label="规格" min-width="160">
              <template #default="{ row }">{{ row.specName }}</template>
            </el-table-column>
            <el-table-column label="日期" width="120">
              <template #default="{ row }">{{ row.date }}</template>
            </el-table-column>
            <el-table-column label="单价" width="100">
              <template #default="{ row }">{{ row.unitPrice ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="数量" width="150">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.quantity"
                  :min="1"
                  :max="row.bookable ? Math.max(row.available ?? 1, 1) : 1"
                  :disabled="!row.bookable"
                  size="small"
                />
              </template>
            </el-table-column>
            <el-table-column label="可订" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.bookable" size="small" type="success">可预订</el-tag>
                <el-tag v-else size="small" type="danger">售罄</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="!rows.length"
            description="当前行程没有可预订门票"
            class="empty-tickets"
          />

          <div class="summary">
            <span>已选 {{ selectedCount }} 项</span>
            <span class="total">合计 {{ totalAmount }} 元</span>
          </div>

          <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" class="block-hint" />
          <div class="actions">
            <el-button @click="$router.back()">返回</el-button>
            <el-button type="primary" :loading="submitting" :disabled="selectedCount === 0" @click="submit">
              提交预订
            </el-button>
          </div>
        </template>
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getItinerary } from '../../api/itinerary'
import { getResource } from '../../api/resources'
import { createOrder } from '../../api/orders'
import type { Itinerary, ItineraryItem } from '../../api/types'

const route = useRoute()
const router = useRouter()
const itineraryId = route.query.itineraryId as string

const itinerary = ref<Itinerary | null>(null)
const error = ref<string | null>(null)
const submitting = ref(false)
const submitError = ref<string | null>(null)

interface BookingRow {
  item: ItineraryItem
  resourceName: string
  specName: string
  date: string
  unitPrice: number | null
  available: number | null
  bookable: boolean
  quantity: number
  selected: boolean
}

const rows = ref<BookingRow[]>([])

const selectedCount = computed(() => rows.value.filter((row) => row.selected).length)
const totalAmount = computed(() =>
  rows.value
    .filter((row) => row.selected)
    .reduce((sum, row) => sum + (row.unitPrice ?? 0) * row.quantity, 0),
)

async function load() {
  error.value = null
  if (!itineraryId) {
    error.value = '缺少行程 ID'
    return
  }
  try {
    itinerary.value = await getItinerary(itineraryId)
    if (itinerary.value.status !== 'CONFIRMED') return
    await loadRows(itinerary.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
}

async function loadRows(itineraryValue: Itinerary) {
  const bookableItems = itineraryValue.items.filter((item) => item.resourceId && item.specId)
  const loaded: BookingRow[] = []
  for (const item of bookableItems) {
    try {
      const detail = await getResource(item.resourceId as string, item.dayDate)
      if (detail.type !== 'ATTRACTION') continue
      const spec = (detail.specs ?? []).find(
        (s) => s.specId === item.specId && s.specType === 'TICKET_TYPE',
      )
      if (!spec) continue
      loaded.push({
        item,
        resourceName: detail.name,
        specName: spec.specName ?? item.specId ?? '',
        date: item.dayDate,
        unitPrice: spec.price ?? null,
        available: spec.available ?? null,
        bookable: spec.bookable ?? false,
        quantity: Math.min(
          Math.max(itineraryValue.partySize, 1),
          Math.max(spec.available ?? 1, 1),
        ),
        selected: spec.bookable ?? false,
      })
    } catch {
      continue
    }
  }
  rows.value = loaded
}

async function submit() {
  if (!itinerary.value) return
  const selected = rows.value.filter((row) => row.selected && row.unitPrice != null)
  if (!selected.length) {
    ElMessage.warning('请至少选择一项可预订资源')
    return
  }
  submitting.value = true
  submitError.value = null
  try {
    const order = await createOrder({
      itineraryId: itinerary.value.id,
      items: selected.map((row) => ({
        resourceId: row.item.resourceId as string,
        specId: row.item.specId as string,
        date: row.date,
        quantity: row.quantity,
        unitPrice: row.unitPrice as number,
      })),
    })
    if (order.status === 'FAILED') {
      submitError.value = `预订失败：${order.failureReason || '部分资源不可用'}`
    } else {
      ElMessage.success('预订已提交')
      router.replace(`/orders/${order.id}`)
    }
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : '提交失败'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.order-page {
  min-height: 100vh;
}

.page-card {
  max-width: 960px;
  margin: 0 auto;
}

.hint {
  color: var(--tp-text-secondary);
}

.block-hint {
  margin-bottom: 16px;
}

.items-table {
  margin-bottom: 16px;
}

.summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.total {
  font-weight: 600;
  color: var(--tp-brand);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
