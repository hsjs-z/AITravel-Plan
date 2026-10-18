<template>
  <main class="auth-shell">
    <section class="auth-brand" aria-hidden="true">
      <div class="brand-mountains">
        <svg class="mountain mountain-back" viewBox="0 0 900 260" preserveAspectRatio="none">
          <path d="M0 220 L120 120 L210 190 L330 80 L450 200 L580 110 L700 190 L820 90 L900 170 V260 H0 Z" />
        </svg>
        <svg class="mountain mountain-middle" viewBox="0 0 900 260" preserveAspectRatio="none">
          <path d="M0 240 L100 180 L220 220 L340 150 L460 230 L600 160 L720 225 L840 170 L900 210 V260 H0 Z" />
        </svg>
        <svg class="mountain mountain-front" viewBox="0 0 900 260" preserveAspectRatio="none">
          <path d="M0 255 L150 210 L280 245 L420 195 L560 250 L700 205 L820 245 L900 225 V260 H0 Z" />
        </svg>
      </div>

      <span class="brand-orb orb-a" />
      <span class="brand-orb orb-b" />

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
  background:
    radial-gradient(circle at 14% 12%, rgba(255, 255, 255, 0.94), transparent 27%),
    radial-gradient(circle at 82% 18%, rgba(232, 243, 255, 0.9), transparent 30%),
    linear-gradient(180deg, #dcf0ff 0%, #eff7ff 58%, #fff8f2 100%);
}

.auth-brand::before {
  content: "";
  position: absolute;
  inset: auto 0 0;
  height: 38%;
  pointer-events: none;
  background:
    radial-gradient(ellipse 80% 90% at 18% 100%, rgba(255, 170, 110, 0.3), transparent 65%),
    radial-gradient(ellipse 70% 80% at 78% 100%, rgba(255, 145, 145, 0.24), transparent 68%),
    radial-gradient(ellipse 52% 70% at 50% 100%, rgba(255, 210, 150, 0.22), transparent 70%);
}

.brand-mountains {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1;
  height: 34%;
  pointer-events: none;
}

.mountain {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
}

.mountain-back {
  height: 100%;
  fill: rgba(77, 107, 254, 0.08);
}

.mountain-middle {
  height: 78%;
  fill: rgba(77, 107, 254, 0.12);
}

.mountain-front {
  height: 56%;
  fill: rgba(77, 107, 254, 0.18);
}

.brand-orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(8px);
  background: radial-gradient(circle, rgba(255, 255, 255, 0.72), transparent 70%);
  animation: brand-float 18s ease-in-out infinite;
}

.orb-a {
  top: 10%;
  left: 8%;
  width: 220px;
  height: 220px;
}

.orb-b {
  top: 52%;
  right: 16%;
  width: 150px;
  height: 150px;
  animation-delay: -6s;
  animation-duration: 14s;
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

@keyframes brand-float {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }

  50% {
    transform: translate3d(0, -12px, 0);
  }
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
