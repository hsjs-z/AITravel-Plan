<template>
  <el-container class="order-page">
    <el-main>
      <el-card class="page-card">
        <div class="head-row">
          <h2>我的订单</h2>
          <el-button @click="load">刷新</el-button>
        </div>
        <el-table v-if="orders.length" :data="orders">
          <el-table-column label="订单号" width="130">
            <template #default="{ row }">
              <span :title="row.id">{{ shortOrderId(row.id) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="行程" min-width="230">
            <template #default="{ row }">{{ tripLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="120">
            <template #default="{ row }">{{ row.totalAmount }} 元</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="$router.push(`/orders/${row.id}`)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="!loading" description="暂无订单" />
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listOrders } from '../../api/orders'
import { listItineraries } from '../../api/itinerary'
import type { ItinerarySummary, Order, OrderStatus } from '../../api/types'

const orders = ref<Order[]>([])
const itineraryMap = ref<Record<string, ItinerarySummary>>({})
const loading = ref(false)

const STATUS_LABELS: Record<OrderStatus, string> = {
  CREATED: '已创建',
  BOOKED: '已预订',
  PAID: '已支付',
  CONFIRMED: '已确认',
  CANCELLED: '已取消',
  FAILED: '已失败',
}

function statusLabel(status: OrderStatus): string {
  return STATUS_LABELS[status] || status
}

function statusTagType(status: OrderStatus): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  switch (status) {
    case 'CONFIRMED':
      return 'success'
    case 'BOOKED':
    case 'PAID':
      return 'warning'
    case 'FAILED':
    case 'CANCELLED':
      return 'danger'
    default:
      return 'info'
  }
}

function shortOrderId(id: string): string {
  return `${id.slice(0, 8)}…`
}

function tripLabel(order: Order): string {
  const itinerary = itineraryMap.value[order.itineraryId]
  if (!itinerary) return '—'
  if (itinerary.startDate === itinerary.endDate) {
    return `${itinerary.destination} · ${itinerary.startDate}`
  }
  return `${itinerary.destination} · ${itinerary.startDate} ~ ${itinerary.endDate.slice(5)}`
}

async function load() {
  loading.value = true
  try {
    const [orderList, itineraries] = await Promise.all([
      listOrders(),
      listItineraries().catch(() => []),
    ])
    orders.value = orderList
    itineraryMap.value = Object.fromEntries(
      itineraries.map((itinerary) => [itinerary.id, itinerary]),
    )
  } finally {
    loading.value = false
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
  background: var(--tp-composer);
  border-color: var(--tp-composer-border);
  box-shadow: var(--tp-composer-shadow);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
