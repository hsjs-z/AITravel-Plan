<template>
  <section class="content-gallery">
    <p v-if="loadFailed" class="gallery-error">图片加载失败，请稍后重试</p>

    <div v-else class="gallery-columns">
      <div
        v-for="(column, columnIndex) in columns"
        :key="columnIndex"
        class="gallery-column"
        :class="{ 'is-down': columnIndex === 1 }"
      >
        <div class="gallery-track">
          <article
            v-for="(item, itemIndex) in column"
            :key="`${item.resourceId}-${itemIndex}`"
            class="gallery-card"
            @click="openDetail(item)"
          >
            <img class="gallery-image" :src="item.imageUrl" :alt="item.name" />
          </article>
        </div>

        <div v-if="columnIndex === 1" class="gallery-center-card">
          <h2 class="gallery-center-text">
            <span>去更远的地方</span>
            <span>看更美的风景</span>
          </h2>
        </div>
      </div>
    </div>

    <div v-if="selectedItem" class="gallery-modal" @click="closeDetail">
      <section class="gallery-modal-box" @click.stop>
        <img class="gallery-modal-image" :src="selectedItem.imageUrl" :alt="selectedItem.name" />
        <p class="gallery-modal-name">{{ selectedItem.name }}</p>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { searchAttractions } from '../../api/resources'
import type { AttractionItem } from '../../api/types'

type GalleryAttractionQuery = Omit<Parameters<typeof searchAttractions>[0], 'destination'> & {
  city: string
}

type GalleryItem = Omit<AttractionItem, 'imageUrl'> & { imageUrl: string }

const GALLERY_CITY = '图片'
const GALLERY_LIMIT = 50

const attractions = ref<GalleryItem[]>([])
const loadFailed = ref(false)
const selectedItem = ref<GalleryItem | null>(null)

const columns = computed<GalleryItem[][]>(() => {
  const distributed: GalleryItem[][] = [[], [], []]
  attractions.value.forEach((item, index) => {
    distributed[index % 3].push(item)
  })

  return distributed.map((column) => [...column, ...column])
})

function openDetail(item: GalleryItem) {
  selectedItem.value = item
}

function closeDetail() {
  selectedItem.value = null
}

onMounted(async () => {
  try {
    const query: GalleryAttractionQuery = {
      city: GALLERY_CITY,
      limit: GALLERY_LIMIT,
    }
    const page = await searchAttractions(query as unknown as Parameters<typeof searchAttractions>[0])

    attractions.value = page.items.flatMap((item) =>
      item.imageUrl ? [{ ...item, imageUrl: item.imageUrl }] : [],
    )
  } catch {
    loadFailed.value = true
  }
})
</script>

<style scoped>
.content-gallery {
  position: relative;
  height: calc(100vh - 64px);
  padding: 18px 20px;
  overflow: hidden;
}

.gallery-error {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  margin: 0;
  color: var(--tp-text-secondary);
  font-size: 15px;
}

.gallery-columns {
  display: grid;
  width: 100%;
  height: 100%;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.gallery-column {
  position: relative;
  min-width: 0;
  overflow: hidden;
}

.gallery-track {
  display: flex;
  flex-direction: column;
  will-change: transform;
  animation: gallery-up 40s linear infinite;
}

.gallery-column.is-down .gallery-track {
  animation-name: gallery-down;
}

.gallery-column:hover .gallery-track {
  animation-play-state: paused;
}

.gallery-card {
  margin-bottom: 18px;
  padding: 8px;
  border-radius: 22px;
  cursor: pointer;
  background-image: linear-gradient(145deg, rgba(245, 247, 251, 0.96), rgba(232, 237, 246, 0.9));
  box-shadow: 0 10px 26px rgba(31, 39, 51, 0.06);
}

.gallery-image {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 16px;
  object-fit: cover;
  box-shadow: inset 1px 1px 0 rgba(255, 255, 255, 0.6);
}

.gallery-center-card {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 2;
  width: min(380px, 82%);
  padding: 34px 36px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.65);
  box-shadow: 0 20px 52px rgba(31, 39, 51, 0.14);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.gallery-center-text {
  display: grid;
  gap: 8px;
  margin: 0;
  color: transparent;
  font-size: clamp(28px, 2.6vw, 40px);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0.08em;
  text-align: center;
  background-image: linear-gradient(105deg, #4d6bfe, #7c3aed 46%, #f59e0b);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.gallery-center-text span {
  display: block;
  white-space: nowrap;
}

.gallery-modal {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  place-items: center;
  background: rgba(18, 24, 38, 0.62);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.gallery-modal-box {
  width: 752px;
  padding: 16px 16px 22px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.65);
  box-shadow:
    inset 1px 1px 0 rgba(255, 255, 255, 0.6),
    0 28px 72px rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.gallery-modal-image {
  display: block;
  width: 720px;
  height: 480px;
  border-radius: 12px;
  object-fit: cover;
}

.gallery-modal-name {
  margin: 18px 0 0;
  color: var(--tp-text);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-align: center;
}

@keyframes gallery-up {
  from {
    transform: translateY(0);
  }

  to {
    transform: translateY(-50%);
  }
}

@keyframes gallery-down {
  from {
    transform: translateY(-50%);
  }

  to {
    transform: translateY(0);
  }
}
</style>
