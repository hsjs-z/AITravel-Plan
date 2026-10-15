<template>
  <el-container class="notification-page">
    <el-main>
      <el-card class="page-card">
        <div class="head-row">
          <h2>通知中心</h2>
          <div class="head-actions">
            <el-tag v-if="unread > 0" type="danger" size="small">{{ unread }} 条未读</el-tag>
            <el-tag v-else type="success" size="small">全部已读</el-tag>
            <el-button @click="load">刷新</el-button>
          </div>
        </div>

        <el-table :data="notifications" class="list-table">
          <el-table-column label="标题" min-width="160">
            <template #default="{ row }">
              <span :class="{ unread: row.readStatus === 'UNREAD' }">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="130">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ typeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="内容" min-width="240">
            <template #default="{ row }">{{ row.body }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.readStatus === 'READ' ? 'success' : 'danger'" size="small">
                {{ row.readStatus === 'READ' ? '已读' : '未读' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button
                v-if="row.readStatus === 'UNREAD'"
                size="small"
                @click="markRead(row.id)"
              >
                标为已读
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!notifications.length && !loading" description="暂无通知" />
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listNotifications, markNotificationRead, unreadCount } from '../../api/notifications'
import type { NotificationItem } from '../../api/types'

const TYPE_LABELS: Record<string, string> = {
  BOOKING_SUCCEEDED: '预订成功',
  BOOKING_FAILED: '预订失败',
  PAYMENT_SUCCEEDED: '支付成功',
  PAYMENT_FAILED: '支付失败',
  REFUND_SUCCEEDED: '退款成功',
  ORDER_CONFIRMED: '订单确认',
  ORDER_CANCELLED: '订单取消',
  ITINERARY_CHANGED: '行程变更',
}

function typeLabel(type: string): string {
  return TYPE_LABELS[type] || type
}

const notifications = ref<NotificationItem[]>([])
const unread = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [items, count] = await Promise.all([listNotifications(), unreadCount()])
    notifications.value = items
    unread.value = count.unread
  } finally {
    loading.value = false
  }
}

async function markRead(id: string) {
  try {
    await markNotificationRead(id)
    const item = notifications.value.find((n) => n.id === id)
    if (item) {
      item.readStatus = 'READ'
    }
    const count = await unreadCount()
    unread.value = count.unread
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  }
}

onMounted(load)
</script>

<style scoped>
.notification-page {
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

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.list-table {
  margin-top: 16px;
}

.unread {
  font-weight: 600;
}
</style>
