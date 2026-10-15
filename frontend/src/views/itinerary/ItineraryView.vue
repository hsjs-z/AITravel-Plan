<template>
  <el-container class="itinerary-page">
    <el-main>
      <el-card v-if="error" class="page-card">
        <el-result icon="error" title="无法加载行程" :sub-title="error">
          <template #extra>
            <el-button type="primary" @click="$router.replace('/planning')">返回规划</el-button>
          </template>
        </el-result>
      </el-card>

      <template v-else-if="itinerary">
        <el-card class="page-card">
          <div class="head-row">
            <h2>{{ editing ? '编辑行程' : `${itinerary.destination} · ${itinerary.status === 'COMPLETED' ? '已完成' : statusLabel}` }}</h2>
            <div class="head-actions">
              <template v-if="!editing">
                <el-button
                  v-if="itinerary.status === 'DRAFT' || itinerary.status === 'CONFIRMED'"
                  type="primary"
                  plain
                  @click="startEdit"
                >
                  编辑
                </el-button>
                <el-button v-if="itinerary.status === 'DRAFT'" type="success" @click="confirm">
                  确认行程
                </el-button>
                <el-button
                  v-if="itinerary.status === 'CONFIRMED'"
                  type="primary"
                  @click="router.push(`/orders/new?itineraryId=${itinerary.id}`)"
                >
                  去预订
                </el-button>
                <el-button v-if="!itinerary.hasOrder" type="danger" plain @click="remove">
                  删除
                </el-button>
              </template>
              <template v-else>
                <el-button type="primary" :loading="saving" @click="save">保存</el-button>
                <el-button @click="cancelEdit">取消</el-button>
              </template>
            </div>
          </div>

          <el-descriptions v-if="!editing" :column="2" border class="meta-block">
            <el-descriptions-item label="目的地">{{ itinerary.destination }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType" size="small">{{ statusLabel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="日期">
              {{ itinerary.startDate }} ~ {{ itinerary.endDate }}
            </el-descriptions-item>
            <el-descriptions-item label="人数">{{ itinerary.partySize }} 人</el-descriptions-item>
            <el-descriptions-item label="预算">{{ itinerary.budget }} 元</el-descriptions-item>
            <el-descriptions-item label="已有订单">{{ itinerary.hasOrder ? '是（不可删除）' : '否' }}</el-descriptions-item>
          </el-descriptions>

          <el-form v-else :model="form" label-position="top" class="meta-block">
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12">
                <el-form-item label="目的地" required>
                  <el-input v-model="form.destination" />
                </el-form-item>
              </el-col>
              <el-col :xs="12" :sm="6">
                <el-form-item label="开始日期" required>
                  <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
                </el-form-item>
              </el-col>
              <el-col :xs="12" :sm="6">
                <el-form-item label="结束日期" required>
                  <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :xs="12" :sm="6">
                <el-form-item label="人数" required>
                  <el-input-number v-model="form.partySize" :min="1" />
                </el-form-item>
              </el-col>
              <el-col :xs="12" :sm="6">
                <el-form-item label="预算（元）" required>
                  <el-input-number v-model="form.budget" :min="1" :step="100" />
                </el-form-item>
              </el-col>
            </el-row>

            <div class="items-editor">
              <div class="items-head">
                <h3>每日安排</h3>
                <el-button size="small" type="primary" plain @click="addItem">添加安排</el-button>
              </div>
              <div v-for="(item, index) in editableItems" :key="index" class="item-row">
                <el-date-picker
                  v-model="item.dayDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 150px"
                />
                <el-input v-model="item.description" placeholder="安排内容" />
                <el-tag v-if="item.resourceType" size="small" type="warning">{{ item.resourceType }}</el-tag>
                <el-tag v-else size="small" type="info">手动</el-tag>
                <el-button size="small" type="danger" text @click="removeItem(index)">删除</el-button>
              </div>
            </div>
          </el-form>

          <div v-if="!editing" class="items-view">
            <h3>每日安排</h3>
            <template v-if="groupedDays.length">
              <div v-for="day in groupedDays" :key="day.date" class="day-block">
                <div class="day-title">
                  <span>{{ day.date }}</span>
                  <span class="day-count">{{ day.items.length }} 项</span>
                </div>
                <el-table :data="day.items" size="small">
                  <el-table-column prop="description" label="安排" min-width="240" />
                  <el-table-column label="类型" width="120">
                    <template #default="{ row }">
                      <el-tag v-if="row.resourceType" size="small" type="warning">{{ row.resourceType }}</el-tag>
                      <el-tag v-else size="small" type="info">文本</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="来源" width="100">
                    <template #default="{ row }">
                      {{ row.source === 'AI' ? 'AI' : '手动' }}
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
            <el-empty v-else description="暂无安排" />
          </div>
        </el-card>
      </template>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmItinerary, deleteItinerary, getItinerary, updateItinerary } from '../../api/itinerary'
import type { Itinerary, ItineraryItem, ItineraryItemSource } from '../../api/types'

const route = useRoute()
const router = useRouter()

const itineraryId = route.params.itineraryId as string
const itinerary = ref<Itinerary | null>(null)
const error = ref<string | null>(null)
const editing = ref(false)
const saving = ref(false)

interface EditableItem {
  dayDate: string
  description: string
  resourceType: string | null
  resourceId: string | null
  specId: string | null
  source: ItineraryItemSource
}

const editableItems = ref<EditableItem[]>([])

const form = reactive({
  destination: '',
  startDate: '',
  endDate: '',
  partySize: 2,
  budget: 0,
})

const statusLabel = computed(() => {
  switch (itinerary.value?.status) {
    case 'DRAFT':
      return '草稿'
    case 'CONFIRMED':
      return '已确认'
    case 'LOCKED':
      return '已锁定'
    case 'COMPLETED':
      return '已完成'
    default:
      return ''
  }
})

const statusTagType = computed(() => {
  switch (itinerary.value?.status) {
    case 'CONFIRMED':
      return 'success'
    case 'LOCKED':
      return 'warning'
    case 'COMPLETED':
      return 'info'
    default:
      return 'primary'
  }
})

const groupedDays = computed(() => {
  const items = itinerary.value?.items ?? []
  const byDate = new Map<string, ItineraryItem[]>()
  for (const item of items) {
    const list = byDate.get(item.dayDate) ?? []
    list.push(item)
    byDate.set(item.dayDate, list)
  }
  return [...byDate.entries()]
    .map(([date, list]) => ({ date, items: list }))
    .sort((a, b) => a.date.localeCompare(b.date))
})

async function load() {
  error.value = null
  try {
    itinerary.value = await getItinerary(itineraryId)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
}

function startEdit() {
  if (!itinerary.value) return
  form.destination = itinerary.value.destination
  form.startDate = itinerary.value.startDate
  form.endDate = itinerary.value.endDate
  form.partySize = itinerary.value.partySize
  form.budget = itinerary.value.budget
  editableItems.value = itinerary.value.items.map((item) => ({
    dayDate: item.dayDate,
    description: item.description,
    resourceType: item.resourceType,
    resourceId: item.resourceId,
    specId: item.specId,
    source: item.source,
  }))
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

function addItem() {
  editableItems.value.push({
    dayDate: form.startDate,
    description: '',
    resourceType: null,
    resourceId: null,
    specId: null,
    source: 'MANUAL',
  })
}

function removeItem(index: number) {
  editableItems.value.splice(index, 1)
}

async function save() {
  if (!form.destination.trim()) {
    ElMessage.warning('请填写目的地')
    return
  }
  if (!form.startDate || !form.endDate) {
    ElMessage.warning('请填写起止日期')
    return
  }
  if (editableItems.value.some((item) => !item.description.trim() || !item.dayDate)) {
    ElMessage.warning('每条安排都需要日期与内容')
    return
  }

  saving.value = true
  try {
    itinerary.value = await updateItinerary(itineraryId, {
      destination: form.destination.trim(),
      startDate: form.startDate,
      endDate: form.endDate,
      partySize: form.partySize,
      budget: form.budget,
      items: editableItems.value.map((item) => ({
        dayDate: item.dayDate,
        description: item.description.trim(),
        ...(item.resourceType ? { resourceType: item.resourceType } : {}),
        ...(item.resourceId ? { resourceId: item.resourceId } : {}),
        ...(item.specId ? { specId: item.specId } : {}),
        source: item.source,
      })),
    })
    editing.value = false
    ElMessage.success('已保存（编辑使行程回到草稿）')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function confirm() {
  if (!itinerary.value) return
  try {
    await ElMessageBox.confirm('确认后行程可发起一键预订，是否继续？', '确认行程', { type: 'info' })
  } catch {
    return
  }
  try {
    itinerary.value = await confirmItinerary(itineraryId)
    ElMessage.success('行程已确认，可发起预订')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '确认失败')
  }
}

async function remove() {
  if (!itinerary.value) return
  try {
    await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除行程', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteItinerary(itineraryId)
    ElMessage.success('行程已删除')
    router.replace('/planning')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.itinerary-page {
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
  gap: 16px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.meta-block {
  margin: 16px 0;
}

.items-editor {
  margin-top: 8px;
}

.items-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.item-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.item-row .el-input {
  flex: 1;
}

.day-block {
  margin-bottom: 16px;
}

.day-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-weight: 600;
}

.day-count {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}
</style>
