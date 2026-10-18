<template>
  <section class="map-panel glass-panel">
    <div class="map-head">
      <span class="map-dot"></span>
      <h3>地图</h3>
    </div>
    <div ref="mapEl" class="map-canvas">
      <div v-if="!mapReady" class="map-state">
        {{ mapError || '地图加载中…' }}
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { ItineraryItem } from '../../api/types'

const props = defineProps<{
  items: ItineraryItem[]
  selectedId?: string | null
}>()

const emit = defineEmits<{
  error: [message: string]
  select: [item: ItineraryItem]
}>()

const mapEl = ref<HTMLElement>()
const mapReady = ref(false)
const mapError = ref<string | null>(null)

let map: any = null
let markers: any[] = []
let amapPromise: Promise<any> | null = null

const apiKey = import.meta.env.VITE_AMAP_JS_KEY

function fail(message: string) {
  mapError.value = message
  emit('error', message)
}

function loadAmap(key: string): Promise<any> {
  const existing = (window as any).AMap
  if (existing) return Promise.resolve(existing)
  if (amapPromise) return amapPromise
  amapPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}`
    script.async = true
    script.onload = () => {
      const AMap = (window as any).AMap
      AMap ? resolve(AMap) : reject(new Error('AMap 对象未就绪'))
    }
    script.onerror = () => reject(new Error('高德地图脚本加载失败'))
    document.head.appendChild(script)
  })
  return amapPromise
}

function validPoint(item: ItineraryItem): boolean {
  return item.latitude != null && item.longitude != null
}

function renderMarkers() {
  if (!map || !mapReady.value) return
  const AMap = (window as any).AMap
  markers.forEach((marker) => map.remove(marker))
  markers = []
  for (const item of props.items) {
    if (!validPoint(item)) continue
    const marker = new AMap.Marker({
      position: [item.longitude, item.latitude],
      title: item.name ?? item.description,
    })
    marker.on('click', () => emit('select', item))
    map.add(marker)
    markers.push(marker)
  }
}

function focusSelected() {
  if (!map || !mapReady.value || !props.selectedId) return
  const item = props.items.find((candidate) => candidate.id === props.selectedId)
  if (!item || !validPoint(item)) return
  map.setZoomAndCenter(14, [item.longitude, item.latitude])
}

onMounted(async () => {
  if (!apiKey) {
    fail('未配置高德 JS Key，地图已降级')
    return
  }
  try {
    const AMap = await loadAmap(apiKey)
    await nextTick()
    if (!mapEl.value) throw new Error('地图容器不存在')
    map = new AMap.Map(mapEl.value, {
      zoom: 11,
      center: [108.9398, 34.3416],
      mapStyle: 'amap://styles/whitesmoke',
    })
    mapReady.value = true
    renderMarkers()
    focusSelected()
  } catch (err) {
    fail(err instanceof Error ? err.message : '地图加载失败，已降级')
  }
})

watch(() => props.items, renderMarkers, { deep: true })
watch(() => props.selectedId, focusSelected)

onUnmounted(() => {
  markers = []
  map?.destroy?.()
  map = null
})
</script>

<style scoped>
.map-panel {
  overflow: hidden;
  min-height: 520px;
  border-radius: 20px;
}

.map-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 18px 12px;
}

.map-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.map-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #78d7a4;
  box-shadow: 0 0 0 3px rgba(120, 215, 164, 0.12);
}

.map-canvas {
  position: relative;
  height: 470px;
  margin: 0 12px 12px;
  overflow: hidden;
  border: 1px solid var(--tp-stroke-soft);
  border-radius: 14px;
  background: #eaf2ff;
}

.map-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--tp-text-secondary);
  font-size: 12px;
}
</style>
