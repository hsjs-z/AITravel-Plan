<template>
  <main class="itinerary-list-page">
    <el-card class="page-card">
      <div class="head-row">
        <div>
          <h2>我的行程</h2>
          <p>查看草稿、已确认和已完成的旅行计划。</p>
        </div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>

      <el-table
        v-if="itineraries.length"
        :data="itineraries"
        class="itinerary-table"
        @row-click="open"
      >
        <el-table-column prop="destination" label="目的地" min-width="140" />
        <el-table-column label="天数" width="90">
          <template #default="{ row }">{{ daysOf(row) }} 天</template>
        </el-table-column>
        <el-table-column label="日期" min-width="220">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click.stop="open(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!loading" description="还没有行程，先去生成一份计划吧" />
    </el-card>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listItineraries } from '../../api/itinerary'
import type { ItineraryStatus, ItinerarySummary } from '../../api/types'

const router = useRouter()
const itineraries = ref<ItinerarySummary[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    itineraries.value = await listItineraries()
  } finally {
    loading.value = false
  }
}

function daysOf(itinerary: ItinerarySummary): number {
  const start = new Date(`${itinerary.startDate}T00:00:00`).getTime()
  const end = new Date(`${itinerary.endDate}T00:00:00`).getTime()
  return Math.max(1, Math.round((end - start) / 86400000) + 1)
}

function statusLabel(status: ItineraryStatus): string {
  return {
    DRAFT: '草稿',
    CONFIRMED: '已确认',
    LOCKED: '已锁定',
    COMPLETED: '已完成',
  }[status]
}

function statusType(status: ItineraryStatus): 'info' | 'success' | 'warning' {
  return {
    DRAFT: 'info',
    CONFIRMED: 'success',
    LOCKED: 'warning',
    COMPLETED: 'info',
  }[status] as 'info' | 'success' | 'warning'
}

function formatTime(value: string): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function open(itinerary: ItinerarySummary) {
  router.push(`/itineraries/${itinerary.id}`)
}

onMounted(load)
</script>

<style scoped>
.itinerary-list-page {
  min-height: calc(100vh - 64px);
  padding: 36px 20px 64px;
}

.page-card {
  max-width: 1040px;
  margin: 0 auto;
}

.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.head-row h2 {
  margin: 0;
  color: var(--tp-text);
}

.head-row p {
  margin: 8px 0 0;
  color: var(--tp-text-secondary);
  font-size: 13px;
}

.itinerary-table {
  cursor: pointer;
}
</style>
