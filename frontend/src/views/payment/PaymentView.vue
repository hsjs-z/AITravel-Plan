<template>
  <el-container class="payment-page">
    <el-main>
      <el-card v-if="error" class="page-card">
        <el-result icon="error" title="无法加载支付" :sub-title="error" />
      </el-card>

      <el-card v-else-if="order" class="page-card">
        <h2>模拟支付</h2>
        <el-descriptions :column="2" border class="meta-block">
          <el-descriptions-item label="订单号">{{ order.id }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="statusTagType" size="small">{{ statusLabel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="应付金额">{{ order.totalAmount }} 元</el-descriptions-item>
          <el-descriptions-item label="支付通道">MOCK（模拟通道）</el-descriptions-item>
        </el-descriptions>

        <template v-if="order.status === 'BOOKED'">
          <el-alert
            title="这是模拟支付：点击后走 Mock 通道完成扣款，不产生真实资金变动。"
            type="info"
            :closable="false"
            class="block-hint"
          />
          <div class="actions">
            <el-button @click="$router.back()">返回</el-button>
            <el-button type="primary" :loading="paying" @click="pay">立即支付 {{ order.totalAmount }} 元</el-button>
          </div>
        </template>

        <template v-else-if="order.status === 'PAID' || order.status === 'CONFIRMED'">
          <el-alert
            :title="order.status === 'CONFIRMED' ? '支付成功，订单已确认' : '支付成功，订单确认中…'"
            type="success"
            :closable="false"
            class="block-hint"
          />
          <div class="actions">
            <router-link :to="`/orders/${order.id}`">
              <el-button>查看订单</el-button>
            </router-link>
            <router-link to="/notifications">
              <el-button type="primary">查看通知</el-button>
            </router-link>
          </div>
        </template>

        <el-alert v-else :title="`当前状态（${statusLabel}）不可支付`" type="warning" :closable="false" class="block-hint" />
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrder, payOrder } from '../../api/orders'
import type { Order, OrderStatus } from '../../api/types'

const route = useRoute()
const orderId = route.params.orderId as string
const order = ref<Order | null>(null)
const error = ref<string | null>(null)
const paying = ref(false)

let pollTimer: ReturnType<typeof setInterval> | null = null

const STATUS_LABELS: Record<OrderStatus, string> = {
  CREATED: '已创建',
  BOOKED: '已预订',
  PAID: '已支付',
  CONFIRMED: '已确认',
  CANCELLED: '已取消',
  FAILED: '已失败',
}

const statusLabel = computed(() => (order.value ? STATUS_LABELS[order.value.status] : ''))
const statusTagType = computed(() => {
  switch (order.value?.status) {
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
})

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function load() {
  error.value = null
  try {
    order.value = await getOrder(orderId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
}

async function pay() {
  paying.value = true
  try {
    order.value = await payOrder(orderId)
    ElMessage.success('支付成功')
    if (order.value.status === 'PAID') {
      pollUntilConfirmed()
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '支付失败')
  } finally {
    paying.value = false
  }
}

function pollUntilConfirmed() {
  stopPolling()
  let attempts = 0
  pollTimer = setInterval(async () => {
    attempts += 1
    try {
      order.value = await getOrder(orderId)
      if (order.value.status !== 'PAID' || attempts >= 10) {
        stopPolling()
      }
    } catch {
      stopPolling()
    }
  }, 1500)
}

onMounted(load)
onUnmounted(stopPolling)
</script>

<style scoped>
.payment-page {
  min-height: 100vh;
}

.page-card {
  max-width: 720px;
  margin: 0 auto;
}

.meta-block {
  margin: 16px 0;
}

.block-hint {
  margin-bottom: 16px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
