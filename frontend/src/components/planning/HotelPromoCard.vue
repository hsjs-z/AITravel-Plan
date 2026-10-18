<template>
  <section class="hotel-promo">
    <div class="hotel-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
        <path d="M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16" />
        <path d="M4 21h16M8 7h2M8 11h2M8 15h2M14 7h1M14 11h1M14 15h1M9 21v-3h4v3" />
      </svg>
    </div>
    <div class="hotel-copy">
      <h3>看看酒店</h3>
      <p>行程规划完成后，可以继续查看适合本次旅行的酒店。</p>
    </div>
    <div class="hotel-action">
      <el-button type="primary" class="hotel-button" @click="openHotels">看看酒店</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
function openHotels() {
  window.open('https://hk.trip.com/?locale=zh_hk', '_blank', 'noopener')
}
</script>

<style scoped>
.hotel-promo {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  padding: 20px 22px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.hotel-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 12px;
  color: #4d6bfe;
  background: rgba(77, 107, 254, 0.12);
}

.hotel-icon svg {
  width: 24px;
  height: 24px;
}

.hotel-copy {
  min-width: 0;
  flex: 1;
}

.hotel-copy h3 {
  margin: 0;
  color: var(--tp-text);
  font-size: 15px;
  font-weight: 600;
}

.hotel-copy p {
  margin: 6px 0 0;
  color: var(--tp-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.hotel-action {
  display: flex;
  flex: none;
  align-items: center;
}

.hotel-button {
  --el-button-bg-color: #4d6bfe;
  --el-button-border-color: #4d6bfe;
  --el-button-hover-bg-color: #3a57e8;
  --el-button-hover-border-color: #3a57e8;
  --el-button-active-bg-color: #3a57e8;
  --el-button-active-border-color: #3a57e8;
  color: #fff;
  text-align: center;
}

@media (max-width: 640px) {
  .hotel-promo {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .hotel-action {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
