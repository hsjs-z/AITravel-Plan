<template>
  <el-container class="planning-page">
    <el-main>
      <el-card class="form-card">
        <h2>智能规划</h2>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="submit"
        >
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12">
              <el-form-item label="目的地" prop="destination">
                <el-input v-model="form.destination" placeholder="如：云南" />
              </el-form-item>
            </el-col>
            <el-col :xs="12" :sm="6">
              <el-form-item label="天数（1–30）" prop="days">
                <el-input-number v-model="form.days" :min="1" :max="30" />
              </el-form-item>
            </el-col>
            <el-col :xs="12" :sm="6">
              <el-form-item label="人数（1–20）" prop="partySize">
                <el-input-number v-model="form.partySize" :min="1" :max="20" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12">
              <el-form-item label="预算（元）" prop="budget">
                <el-input-number v-model="form.budget" :min="1" :step="100" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="偏好（可选）">
                <el-input v-model="form.preferences" placeholder="如：亲子、美食、节奏轻松" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-alert v-if="error" :title="error" type="error" :closable="false" class="block-hint" />
          <el-button type="primary" native-type="submit" :loading="submitting">开始规划</el-button>
        </el-form>
      </el-card>

      <el-card v-if="task" class="task-card">
        <div class="task-head">
          <span class="task-id">任务 {{ shortId(task.id) }}</span>
          <el-tag :type="statusTagType" size="small">{{ statusLabel }}</el-tag>
        </div>

        <template v-if="task.status === 'SUCCEEDED'">
          <el-alert
            title="规划成功"
            type="success"
            :closable="false"
            class="block-hint"
          />
          <p v-if="task.itineraryId" class="itinerary-hint">
            已生成草稿行程（ID：{{ task.itineraryId }}），行程查看页将在 T065 接入。
          </p>
          <div v-if="result" class="result-preview">
            <h3>{{ result.destination }} · {{ result.totalDays ?? form.days }} 天 · 预算约 {{ result.estimatedBudget ?? form.budget }} 元</h3>
            <el-collapse>
              <el-collapse-item v-for="day in result.days ?? []" :key="day.day" :title="`第 ${day.day} 天`">
                <p class="day-arrangements">{{ (day.arrangements ?? []).join('；') || '自由活动' }}</p>
              </el-collapse-item>
              <el-collapse-item title="推荐酒店">
                <p v-for="item in result.hotels ?? []" :key="item.name">{{ item.name }}</p>
              </el-collapse-item>
              <el-collapse-item title="推荐景点">
                <p v-for="item in result.attractions ?? []" :key="item.name">{{ item.name }}</p>
              </el-collapse-item>
              <el-collapse-item title="推荐美食">
                <p v-for="item in result.foods ?? []" :key="item.name">{{ item.name }}</p>
              </el-collapse-item>
            </el-collapse>
          </div>
        </template>

        <template v-if="task.status === 'FAILED'">
          <el-alert
            :title="`规划失败：${task.failureReason || '未知原因'}`"
            type="error"
            :closable="false"
            class="block-hint"
          />
          <el-button type="warning" :loading="submitting" @click="retry">重试</el-button>
        </template>
      </el-card>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onUnmounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { createPlanningTask, getPlanningTask, retryPlanningTask } from '../../api/planning'
import type { PlanningResult, PlanningTask } from '../../api/types'

const formRef = ref<FormInstance>()
const form = reactive({
  destination: '',
  days: 5,
  partySize: 2,
  budget: 8000,
  preferences: '',
})

const rules: FormRules = {
  destination: [{ required: true, message: '请输入目的地', trigger: 'blur' }],
  days: [{ required: true, message: '请输入天数', trigger: 'change' }],
  partySize: [{ required: true, message: '请输入人数', trigger: 'change' }],
  budget: [{ required: true, message: '请输入预算', trigger: 'change' }],
}

const submitting = ref(false)
const error = ref<string | null>(null)
const task = ref<PlanningTask | null>(null)
const result = computed<PlanningResult | null>(() => {
  if (!task.value?.result) return null
  try {
    return JSON.parse(task.value.result) as PlanningResult
  } catch {
    return null
  }
})

let pollTimer: ReturnType<typeof setInterval> | null = null

const statusLabel = computed(() => {
  switch (task.value?.status) {
    case 'PENDING':
      return '已受理'
    case 'RUNNING':
      return '处理中'
    case 'SUCCEEDED':
      return '已成功'
    case 'FAILED':
      return '已失败'
    default:
      return ''
  }
})

const statusTagType = computed(() => {
  switch (task.value?.status) {
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
      return 'warning'
    default:
      return 'info'
  }
})

function shortId(id: string): string {
  return id.length > 8 ? id.slice(0, 8) : id
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling(taskId: string) {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const next = await getPlanningTask(taskId)
      task.value = next
      if (next.status === 'SUCCEEDED' || next.status === 'FAILED') {
        stopPolling()
      }
    } catch (err) {
      stopPolling()
      error.value = err instanceof Error ? err.message : '查询任务失败'
    }
  }, 2000)
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  error.value = null
  task.value = null
  stopPolling()
  try {
    const created = await createPlanningTask({
      destination: form.destination.trim(),
      days: form.days,
      partySize: form.partySize,
      budget: form.budget,
      preferences: form.preferences.trim() || undefined,
    })
    task.value = { id: created.taskId, status: 'PENDING', itineraryId: null, failureReason: null, result: null }
    startPolling(created.taskId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '提交失败'
  } finally {
    submitting.value = false
  }
}

async function retry() {
  if (!task.value) return
  submitting.value = true
  error.value = null
  try {
    const next = await retryPlanningTask(task.value.id)
    task.value = next
    startPolling(next.id)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '重试失败'
  } finally {
    submitting.value = false
  }
}

onUnmounted(stopPolling)
</script>

<style scoped>
.planning-page {
  min-height: 100vh;
}

.form-card,
.task-card {
  max-width: 960px;
  margin: 0 auto 16px;
}

.block-hint {
  margin-bottom: 16px;
}

.task-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.task-id {
  color: #909399;
  font-size: 13px;
}

.itinerary-hint {
  color: #606266;
}

.result-preview h3 {
  margin: 16px 0 8px;
}

.day-arrangements {
  margin: 0;
  color: #606266;
}
</style>
