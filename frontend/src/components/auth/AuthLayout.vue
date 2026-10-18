<template>
  <main class="auth-shell">
    <section class="auth-brand" aria-hidden="true">
      <span class="brand-shape shape-1" />
      <span class="brand-shape shape-2" />
      <span class="brand-shape shape-3" />
      <span class="brand-shape shape-4" />

      <div class="brand-content">
        <p class="brand-eyebrow">AI TRAVEL STUDIO</p>
        <h1 class="brand-title">
          半程烟火，半程<span class="brand-gradient">山河</span>
        </h1>
        <p class="brand-subtitle">本次山水的行程，AI 已备好</p>
        <p class="brand-english">Plan less, travel more.</p>
      </div>
      <p class="brand-poem">步履不停，山海皆有回音</p>
    </section>

    <section class="auth-form-area">
      <div class="auth-card">
        <slot />
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: 55fr 45fr;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 82% 12%, rgba(255, 255, 255, 0.72), transparent 28%),
    linear-gradient(180deg, #dcf0ff, #fafbff);
}

.auth-brand {
  position: relative;
  display: flex;
  align-items: center;
  overflow: hidden;
  padding: 8vh 10% 8vh 9%;
  background:
    radial-gradient(circle at 16% 14%, rgba(255, 255, 255, 0.92), transparent 27%),
    radial-gradient(circle at 84% 22%, rgba(232, 243, 255, 0.85), transparent 30%),
    radial-gradient(circle at 24% 82%, rgba(191, 217, 255, 0.32), transparent 34%),
    linear-gradient(180deg, #dcf0ff, #f6f9ff 68%, #fafbff);
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 520px;
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
  font-size: clamp(40px, 4.2vw, 64px);
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1.18;
}

.brand-gradient {
  color: transparent;
  background: linear-gradient(90deg, #4d6bfe, #3a57e8);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
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
  top: 18%;
  right: 9%;
  margin: 0;
  color: rgba(31, 39, 51, 0.3);
  font-size: 15px;
  letter-spacing: 0.28em;
  line-height: 1;
  writing-mode: vertical-rl;
}

.brand-shape {
  position: absolute;
  pointer-events: none;
  border: 1px solid rgba(77, 107, 254, 0.08);
  background: rgba(191, 217, 255, 0.22);
  animation: brand-float 16s ease-in-out infinite;
}

.shape-1 {
  top: 16%;
  left: 10%;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  animation-duration: 18s;
}

.shape-2 {
  top: 62%;
  left: 16%;
  width: 76px;
  height: 76px;
  border-radius: 22px;
  background: rgba(232, 243, 255, 0.58);
  animation-delay: -4s;
  animation-duration: 14s;
}

.shape-3 {
  top: 26%;
  right: 14%;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: rgba(191, 217, 255, 0.28);
  animation-delay: -7s;
  animation-duration: 20s;
}

.shape-4 {
  right: 22%;
  bottom: 14%;
  width: 54px;
  height: 54px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.45);
  animation-delay: -2s;
  animation-duration: 12s;
}

@keyframes brand-float {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }

  50% {
    transform: translate3d(0, -14px, 0);
  }
}

.auth-form-area {
  display: grid;
  place-items: center;
  min-width: 0;
  padding: 32px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(250, 251, 255, 0.5));
}

.auth-card {
  width: min(420px, 100%);
  padding: 32px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.65);
  box-shadow: 0 10px 32px rgba(77, 107, 254, 0.08);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
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
    padding: 26px 22px;
  }
}
</style>
