<template>
  <el-container class="order-page">
    <el-main>
      <el-card v-if="error" class="page-card">
        <el-result icon="error" title="无法加载订单" :sub-title="error" />
      </el-card>

      <el-card v-else-if="order" class="page-card">
        <div class="head-row">
          <h2>订单详情</h2>
          <el-tag :type="statusTagType(order.status)" size="small">{{ statusLabel(order.status) }}</el-tag>
        </div>

        <el-descriptions :column="2" border class="meta-block">
          <el-descriptions-item label="订单号">{{ order.id }}</el-descriptions-item>
          <el-descriptions-item label="总金额">{{ order.totalAmount }} 元</el-descriptions-item>
          <el-descriptions-item label="关联行程">
            <router-link :to="`/itineraries/${order.itineraryId}`">{{ order.itineraryId }}</router-link>
          </el-descriptions-item>
          <el-descriptions-item v-if="order.failureReason" label="失败原因">
            {{ order.failureReason }}
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="order.items" size="small" class="items-table">
          <el-table-column prop="resourceId" label="资源" min-width="160" />
          <el-table-column prop="specId" label="规格" min-width="160" />
          <el-table-column prop="date" label="日期" width="120" />
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column prop="unitPrice" label="单价" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ row.status }}</template>
          </el-table-column>
        </el-table>

        <div v-if="cancellable" class="actions">
          <el-button type="danger" @click="cancel">取消订单</el-button>
        </div>
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, getOrder } from '../../api/orders'
import type { Order, OrderStatus } from '../../api/types'

const route = useRoute()
const orderId = route.params.orderId as string
const order = ref<Order | null>(null)
const error = ref<string | null>(null)

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

const cancellable = computed(() => {
  const status = order.value?.status
  return status === 'CREATED' || status === 'BOOKED' || status === 'PAID'
})

async function load() {
  error.value = null
  try {
    order.value = await getOrder(orderId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
}

async function cancel() {
  try {
    await ElMessageBox.confirm('取消订单将自动退款并释放库存，是否继续？', '取消订单', { type: 'warning' })
  } catch {
    return
  }
  try {
    order.value = await cancelOrder(orderId)
    ElMessage.success('订单已取消')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '取消失败')
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

.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.meta-block {
  margin: 16px 0;
}

.items-table {
  margin-bottom: 16px;
}

.actions {
  display: flex;
  justify-content: flex-end;
}
</style>
