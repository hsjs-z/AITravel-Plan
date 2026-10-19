<template>
  <main class="auth-shell">
    <section
      class="auth-brand"
      :style="{ backgroundImage: `url(${authBrandWatercolor})` }"
      aria-hidden="true"
    >

      <div class="brand-content">
        <p class="brand-eyebrow">AI TRAVEL STUDIO</p>
        <h1 class="brand-title">
          <span class="brand-title-line">半程<span class="text-warm">烟火</span>，</span>
          <span class="brand-title-line is-blue">半程<span class="text-cool">山河</span></span>
        </h1>
        <p class="brand-subtitle">本次山水的行程，AI 已备好</p>
        <p class="brand-english">Plan less, travel more.</p>
      </div>

      <p class="brand-poem">步履不停，山海皆有回音</p>
      <p class="brand-footnote">AI 规划 · 真实景区库存 · 一键预订</p>
    </section>

    <section class="auth-form-area">
      <div class="auth-card">
        <p class="auth-product">AI TRAVEL STUDIO</p>
        <slot />
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import authBrandWatercolor from '../../assets/auth/auth-brand-watercolor.jpg';
</script>

<style scoped>
.auth-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 55fr 45fr;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 84% 10%, rgba(255, 255, 255, 0.8), transparent 28%),
    linear-gradient(180deg, #dcf0ff, #f7fbff 64%, #fff8f2);
}

.auth-brand {
  position: relative;
  display: flex;
  align-items: center;
  overflow: hidden;
  padding: 8vh 11% 8vh 9%;
  background-position: center bottom;
  background-repeat: no-repeat;
  background-size: cover;
}

.auth-brand::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background-image: linear-gradient(to right, transparent 78%, #eff7fc 100%);
}

.brand-content {
  position: relative;
  z-index: 3;
  max-width: 560px;
  padding-right: 48px;
}

.brand-eyebrow {
  margin: 0 0 18px;
  color: rgba(31, 39, 51, 0.42);
  font-size: 11px;
  letter-spacing: 0.2em;
}

.brand-title {
  margin: 0;
  color: var(--tp-text);
  font-size: clamp(38px, 4.4vw, 60px);
  font-weight: 700;
  letter-spacing: 0.01em;
  line-height: 1.2;
}

.brand-title-line {
  display: block;
  white-space: nowrap;
}

.brand-title-line.is-blue {
  color: #4d6bfe;
}

.text-warm,
.text-cool {
  color: transparent;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.text-warm {
  background-image: linear-gradient(90deg, #f59e0b, #fb7185);
}

.text-cool {
  background-image: linear-gradient(90deg, #4d6bfe, #3a57e8);
}

.brand-subtitle {
  margin: 22px 0 0;
  color: var(--tp-text-secondary);
  font-size: 15px;
  letter-spacing: 0.08em;
}

.brand-english {
  margin: 12px 0 0;
  color: rgba(31, 39, 51, 0.38);
  font-size: 12px;
  letter-spacing: 0.14em;
}

.brand-poem {
  position: absolute;
  top: 50%;
  right: 10px;
  z-index: 4;
  margin: 0;
  padding: 6px 0;
  color: rgba(31, 39, 51, 0.28);
  font-size: 15px;
  letter-spacing: 0.28em;
  line-height: 1;
  transform: translateY(-50%);
  writing-mode: vertical-rl;
}

.brand-footnote {
  position: absolute;
  bottom: 24px;
  left: 9%;
  z-index: 4;
  margin: 0;
  color: rgba(31, 39, 51, 0.36);
  font-size: 11px;
  letter-spacing: 0.12em;
}

.auth-form-area {
  display: grid;
  place-items: center;
  min-width: 0;
  padding: 32px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.12), rgba(255, 250, 246, 0.42));
}

.auth-card {
  position: relative;
  width: min(400px, 100%);
  padding: 40px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.65);
  box-shadow:
    0 24px 64px rgba(77, 107, 254, 0.14),
    0 6px 18px rgba(31, 39, 51, 0.04);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.auth-product {
  margin: 0 0 22px;
  color: var(--tp-brand);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-align: center;
}

:deep(.auth-title) {
  margin: 0 0 24px;
  color: var(--tp-text);
  font-size: 24px;
  font-weight: 600;
}

@media (max-width: 899px) {
  .auth-shell {
    display: block;
  }

  .auth-brand {
    display: none;
  }

  .auth-form-area {
    min-height: 100vh;
    padding: 24px 16px;
  }

  .auth-card {
    padding: 32px 24px;
  }
}
</style>
