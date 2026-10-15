<template>
  <el-container class="content-page">
    <el-header class="content-header">
      <h2>内容资源</h2>
      <p class="subtitle">美食、文化体验、景点与线路，仅作展示与规划参考，不提供预订。</p>
    </el-header>

    <el-main>
      <el-empty v-if="!hasToken" description="请先登录后浏览内容资源" />

      <template v-else>
        <el-form :inline="true" class="search-bar" @submit.prevent="search">
          <el-form-item label="关键词">
            <el-input v-model="query.keyword" placeholder="名称或描述" clearable />
          </el-form-item>
          <el-form-item label="目的地">
            <el-input v-model="query.destination" placeholder="如：云南 / 北京" clearable />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="query.category" placeholder="全部" clearable style="width: 150px">
              <el-option
                v-for="option in categoryOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" native-type="submit">检索</el-button>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="message"
          :title="message"
          type="info"
          :closable="false"
          class="result-hint"
        />
        <el-alert v-if="error" :title="error" type="error" :closable="false" class="result-hint" />

        <el-row :gutter="16">
          <el-col v-for="item in items" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
            <el-card class="resource-card" shadow="hover" @click="openDetail(item.id)">
              <div class="card-head">
                <span class="resource-name">{{ item.name }}</span>
                <el-tag type="info" size="small">内容</el-tag>
              </div>
              <div class="card-meta">
                <el-tag size="small" type="success">{{ item.destination }}</el-tag>
                <el-tag v-if="item.contentCategory" size="small" effect="plain">
                  {{ categoryLabel(item.contentCategory) }}
                </el-tag>
              </div>
              <p class="no-entry-hint">仅展示 · 不提供预订</p>
            </el-card>
          </el-col>
        </el-row>

        <el-empty v-if="!loading && items.length === 0 && !message" description="没有匹配的内容资源" />

        <el-dialog v-model="detailVisible" :title="detail?.name" width="520px">
          <template v-if="detail">
            <div class="detail-meta">
              <el-tag type="success">{{ detail.destination }}</el-tag>
              <el-tag v-if="detail.contentCategory" effect="plain">
                {{ categoryLabel(detail.contentCategory) }}
              </el-tag>
              <el-tag type="info">内容资源</el-tag>
            </div>
            <p class="detail-description">{{ detail.description || '暂无描述' }}</p>
            <el-alert
              title="该资源仅作展示与规划参考，不提供任何下单或支付入口。"
              type="warning"
              :closable="false"
            />
          </template>
        </el-dialog>
      </template>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getResource, searchResources } from '../../api/resources'
import type { ResourceDetail, ResourceSummary } from '../../api/types'

const CATEGORY_LABELS: Record<string, string> = {
  FOOD: '美食',
  CULTURE: '文化体验',
  ATTRACTION: '景点',
  ROUTE: '线路',
}

const categoryOptions = Object.entries(CATEGORY_LABELS).map(([value, label]) => ({ value, label }))

const hasToken = ref(Boolean(localStorage.getItem('token')))
const items = ref<ResourceSummary[]>([])
const total = ref(0)
const message = ref<string | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref<ResourceDetail | null>(null)

const query = reactive<{ keyword: string; destination: string; category: string }>({
  keyword: '',
  destination: '',
  category: '',
})

function categoryLabel(category: string | null): string {
  return (category && CATEGORY_LABELS[category]) || category || '其他'
}

async function search() {
  if (!hasToken.value) return
  loading.value = true
  error.value = null
  message.value = null
  try {
    const result = await searchResources({
      type: 'CONTENT',
      keyword: query.keyword.trim() || undefined,
      destination: query.destination.trim() || undefined,
      category: query.category || undefined,
    })
    items.value = result.items
    total.value = result.total
    message.value = result.message
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function openDetail(id: string) {
  try {
    detail.value = await getResource(id)
    detailVisible.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
}

onMounted(() => {
  if (hasToken.value) {
    search()
  }
})
</script>

<style scoped>
.content-page {
  min-height: 100vh;
}

.content-header {
  padding-top: 24px;
}

.subtitle {
  color: #909399;
  margin: 4px 0 0;
}

.search-bar {
  margin-bottom: 16px;
}

.result-hint {
  margin-bottom: 16px;
}

.resource-card {
  cursor: pointer;
  margin-bottom: 16px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.resource-name {
  font-weight: 600;
}

.card-meta {
  display: flex;
  gap: 8px;
  margin: 12px 0 8px;
}

.no-entry-hint {
  color: #b88230;
  font-size: 12px;
  margin: 0;
}

.detail-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-description {
  color: #606266;
  line-height: 1.7;
}
</style>
