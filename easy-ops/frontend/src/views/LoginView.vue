<template>
  <div
    ref="shellRef"
    class="login-shell"
    :style="mouseVars"
    @mousemove="onMouseMove"
    @mousedown="onClick"
  >
    <div class="grid-glow" aria-hidden="true" />

    <span
      v-for="r in ripples"
      :key="r.id"
      class="click-pulse"
      :style="{ left: `${r.x}px`, top: `${r.y}px` }"
      aria-hidden="true"
    />

  <section class="narrative">
    <header class="brand">
      <span class="brand-icon mono">&gt;_</span>
      <span class="brand-name">EasyOps</span>
    </header>

    <div class="hero">
      <h1 class="headline">
        让每一次发布，都可<span class="headline-highlight">观测</span>、可<span class="headline-highlight">回滚</span>、可<span class="headline-highlight">审计</span>
      </h1>
      <div class="capabilities">
        <template v-for="(cap, i) in capabilities" :key="cap">
          <span v-if="i > 0" class="cap-sep">|</span>
          <span class="mono cap-text"><span class="prompt">&gt;_</span> {{ cap }}</span>
        </template>
      </div>
    </div>

    <div ref="topologyWrapRef" class="topology-wrap">
      <svg class="topology" viewBox="0 0 800 520" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
        <defs>
          <radialGradient id="sunGlow" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stop-color="#e8ff59" stop-opacity="0.45" />
            <stop offset="45%" stop-color="#e8ff59" stop-opacity="0.12" />
            <stop offset="100%" stop-color="#e8ff59" stop-opacity="0" />
          </radialGradient>
          <filter id="hubBlur" x="-100%" y="-100%" width="300%" height="300%">
            <feGaussianBlur stdDeviation="9" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <!-- 太阳光晕 -->
        <circle class="sun-halo" cx="400" cy="260" r="190" fill="url(#sunGlow)" />
        <g class="radar-rings">
          <circle class="ring ring-static" cx="400" cy="260" r="78" />
          <circle class="ring ring-static" cx="400" cy="260" r="120" />
          <circle class="ring ring-static" cx="400" cy="260" r="165" />
          <circle class="ring ring-1" cx="400" cy="260" r="78" />
          <circle class="ring ring-2" cx="400" cy="260" r="120" />
          <circle class="ring ring-3" cx="400" cy="260" r="165" />
        </g>

        <g class="topo-edges">
          <line v-for="(e, i) in edges" :key="i" :x1="e.x1" :y1="e.y1" :x2="e.x2" :y2="e.y2" />
        </g>

        <g class="topo-nodes">
          <g v-for="n in nodes" :key="n.id">
            <circle
              :cx="n.x" :cy="n.y"
              :r="n.hub ? 14 : 5"
              :class="{ hub: n.hub }"
              :filter="n.hub ? 'url(#hubBlur)' : undefined"
            />
            <text :x="n.tx" :y="n.ty" :text-anchor="'start'" class="mono node-label">{{ n.label }}</text>
          </g>
        </g>
      </svg>
    </div>
  </section>

  <section class="auth">
    <div class="auth-inner">
      <h2 class="auth-title">登录 EasyOps 控制台</h2>
      
      <a-config-provider :theme="formTheme">
        <a-form :model="formState" @finish="handleLogin" layout="vertical" class="auth-form">
          <a-form-item name="username" :rules="[{ required: true, message: '请输入用户名' }]">
            <label class="field-label">用户名</label>
            <a-input v-model:value="formState.username" placeholder="请输入用户名" size="large" autocomplete="username">
              <template #prefix><svg class="field-icon" viewBox="0 0 16 16" fill="none"><circle cx="8" cy="5.5" r="2.5" stroke="currentColor" stroke-width="1.2"/><path d="M3 14c0-2.8 2.2-5 5-5s5 2.2 5 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/></svg></template>
            </a-input>
          </a-form-item>
          <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <label class="field-label">密码</label>
            <a-input-password v-model:value="formState.password" placeholder="请输入密码" size="large" autocomplete="current-password">
              <template #prefix><svg class="field-icon" viewBox="0 0 16 16" fill="none"><rect x="3.5" y="7" width="9" height="6.5" rx="1" stroke="currentColor" stroke-width="1.2"/><path d="M5.5 7V5.5a2.5 2.5 0 015 0V7" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/></svg></template>
            </a-input-password>
          </a-form-item>
          <a-form-item name="captchaCode" :rules="[{ required: true, message: '请输入验证码' }]">
            <label class="field-label">验证码</label>
            <div class="captcha-row">
              <a-input v-model:value="formState.captchaCode" placeholder="请输入验证码" size="large" maxlength="6" />
              <img v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="验证码" title="点击刷新" @click="loadCaptcha" />
              <a-button size="large" @click="loadCaptcha">刷新</a-button>
            </div>
          </a-form-item>
          <a-form-item>
            <button ref="ctaRef" type="submit" class="cta" :disabled="loading">{{ loading ? '登录中…' : '登 录' }}</button>
          </a-form-item>
        </a-form>
          <div class="reset-row">
            <a class="reset-link" @click="handleReset">管理员密码重置为默认</a>
          </div>

      </a-config-provider>
    </div>
    <p class="copyright mono">© 2024 EasyOps. All rights reserved.</p>
  </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login, getCaptcha, resetAdminPassword } from '../api/auth'
import { message, Modal } from 'ant-design-vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const shellRef = ref<HTMLElement | null>(null)
const topologyWrapRef = ref<HTMLElement | null>(null)
const ctaRef = ref<HTMLButtonElement | null>(null)

const capabilities = ['节点编排', '部署流水线', '实时监控']

const HUB_X = 400
const HUB_Y = 260

const nodes = [
  { id: 1, x: HUB_X, y: HUB_Y, tx: 422, ty: 264, label: 'api-gateway  v2.4.3', hub: true },
  { id: 2, x: 140, y: 95, tx: 32, ty: 91, label: 'user-service  v1.8.2' },
  { id: 3, x: 660, y: 88, tx: 672, ty: 84, label: 'order-service  v1.0.0' },
  { id: 4, x: 95, y: 400, tx: 12, ty: 404, label: 'payment-service  v2.3.1' },
  { id: 5, x: 700, y: 395, tx: 712, ty: 391, label: 'mysql  8.0.34' },
  { id: 6, x: 530, y: 445, tx: 542, ty: 449, label: 'inventory-service  v1.9.0' },
  { id: 7, x: 265, y: 72, tx: 277, ty: 68, label: 'redis-cache  v6.2.1' },
  { id: 8, x: 555, y: 112, tx: 567, ty: 108, label: 'nginx-ingress  v1.9.0' },
  { id: 9, x: 72, y: 252, tx: 2, ty: 256, label: 'monitor-agent  v3.2.0' },
  { id: 10, x: 725, y: 268, tx: 737, ty: 272, label: 'log-collector  v2.1.0' }
]

const edges = [
  ...nodes.filter(n => !n.hub).map(n => ({ x1: HUB_X, y1: HUB_Y, x2: n.x, y2: n.y })),
  { x1: 140, y1: 95, x2: 660, y2: 88 },
  { x1: 140, y1: 95, x2: 265, y2: 72 },
  { x1: 660, y1: 88, x2: 555, y2: 112 },
  { x1: 265, y1: 72, x2: 555, y2: 112 },
  { x1: 95, y1: 400, x2: 700, y2: 395 },
  { x1: 72, y1: 252, x2: 140, y2: 95 },
  { x1: 72, y1: 252, x2: 95, y2: 400 },
  { x1: 725, y1: 268, x2: 700, y2: 395 },
  { x1: 725, y1: 268, x2: 660, y2: 88 },
  { x1: 530, y1: 445, x2: 700, y2: 395 },
  { x1: 530, y1: 445, x2: 95, y2: 400 }
]

const formState = reactive({ username: '', password: '', captchaCode: '' })
const captchaId = ref('')
const captchaImage = ref('')

async function loadCaptcha() {
  try {
    const res = await getCaptcha()
    captchaId.value = res.data.captchaId
    captchaImage.value = res.data.imageBase64
    formState.captchaCode = ''
  } catch { /* interceptor */ }
}

const formTheme: ThemeConfig = {
  token: {
    colorBgContainer: '#0f0f10',
    colorText: '#e5e5e5',
    colorTextPlaceholder: '#525252',
    colorBorder: '#2a2a2a',
    colorPrimary: '#e8ff59',
    controlHeightLG: 48,
    borderRadius: 6,
    fontSize: 14
  }
}

interface Ripple { id: number; x: number; y: number }
const ripples = ref<Ripple[]>([])
let rippleId = 0
const mouse = reactive({ x: 0, y: 0 })

const mouseVars = computed(() => ({
  '--mx': `${mouse.x}px`,
  '--my': `${mouse.y}px`
}))

function onMouseMove(e: MouseEvent) {
  if (!shellRef.value) return
  const rect = shellRef.value.getBoundingClientRect()
  mouse.x = e.clientX - rect.left
  mouse.y = e.clientY - rect.top
}

function onClick(e: MouseEvent) {
  if (!shellRef.value) return
  const rect = shellRef.value.getBoundingClientRect()
  const id = ++rippleId
  ripples.value.push({ id, x: e.clientX - rect.left, y: e.clientY - rect.top })
  setTimeout(() => { ripples.value = ripples.value.filter(r => r.id !== id) }, 550)
}

async function handleLogin() {
  if (loading.value) return
  try {
    loading.value = true
    const res = await login(formState.username, formState.password, captchaId.value, formState.captchaCode)
    authStore.setToken(res.data.token)
    authStore.setUser({
      id: 0,
      username: res.data.username,
      role: res.data.role === 'ADMIN' ? 'ADMIN' : 'OPERATOR',
      status: 1
    })
    await router.push('/')
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '登录失败'
    message.error(msg)
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

async function handleReset() {
async function handleReset() {
  Modal.confirm({
    title: "确认重置管理员密码?",
    content: "密码将恢复为默认值 Admin123!",
    okText: "确认重置",
    cancelText: "取消",
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await resetAdminPassword()
        message.success("管理员密码已重置为 Admin123!")
        loadCaptcha()
      } catch {
        message.error("重置失败，请联系管理员")
      }
    }
  })
}

  }
}


function alignSunWithButton() {
  const shell = shellRef.value
  const wrap = topologyWrapRef.value
  const btn = ctaRef.value
  if (!shell || !wrap || !btn) return
  const shellRect = shell.getBoundingClientRect()
  const btnRect = btn.getBoundingClientRect()
  const centerY = btnRect.top + btnRect.height / 2 - shellRect.top
  wrap.style.top = `${centerY}px`
}

onMounted(() => {
  authStore.logout()
  loadCaptcha()
  nextTick(alignSunWithButton)
  window.addEventListener('resize', alignSunWithButton)
})

onUnmounted(() => {
  window.removeEventListener('resize', alignSunWithButton)
})
</script>

<style scoped>
.login-shell {
  --bg: #0a0a0b;
  --surface: #0f0f10;
  --text: #fafafa;
  --muted: #737373;
  --accent: #e8ff59;
  --accent-text: #0a0a0b;
  --sans: 'Geist', 'IBM Plex Sans', system-ui, sans-serif;
  --mono: 'JetBrains Mono', ui-monospace, monospace;

  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  min-height: 100dvh;
  background: var(--bg);
  color: var(--text);
  font-family: var(--sans);
  position: relative;
  overflow-x: hidden;
  overflow-y: auto;
}

.mono { font-family: var(--mono); }

.grid-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    560px circle at var(--mx, 50%) var(--my, 50%),
    rgba(232, 255, 89, 0.06) 0%,
    transparent 52%
  );
  pointer-events: none;
  z-index: 0;
}

.click-pulse {
  position: absolute;
  width: 8px;
  height: 8px;
  margin: -4px 0 0 -4px;
  border-radius: 50%;
  background: var(--accent);
  opacity: 0.8;
  pointer-events: none;
  z-index: 5;
  animation: click-fade 0.55s ease-out forwards;
}

@keyframes click-fade {
  to { transform: scale(14); opacity: 0; }
}

/* ── 左侧 ── */
.narrative {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  padding: 2.25rem 2.5rem 1.5rem;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  background: rgba(232, 255, 89, 0.1);
  border: 1px solid rgba(232, 255, 89, 0.4);
  border-radius: 6px;
  color: var(--accent);
  font-size: 13px;
}

.brand-name {
  font-size: 19px;
  font-weight: 600;
}

.hero {
  flex-shrink: 0;
  margin-top: 2rem;
  max-width: 540px;
  position: relative;
  z-index: 2;
}

.headline {
  font-size: clamp(1.75rem, 2.6vw, 2.5rem);
  font-weight: 600;
  line-height: 1.32;
  letter-spacing: -0.025em;
  margin: 0 0 1.25rem;
}

.headline-highlight {
  color: var(--accent);
  font-size: 1.125em;
}

.capabilities {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 16px;
}

.cap-sep { color: #3f3f46; font-size: 14px; }

.cap-text {
  font-size: 16px;
  color: #a3a3a3;
  letter-spacing: 0.02em;
}

.prompt {
  color: var(--accent);
  font-size: 16px;
}

/* 拓扑与右侧登录按钮水平对齐 */
.topology-wrap {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  justify-content: center;
  align-items: center;
  pointer-events: none;
  z-index: 1;
}

.topology {
  width: 118%;
  max-width: 900px;
  height: auto;
  overflow: visible;
}

.sun-halo {
  pointer-events: none;
}

.radar-rings .ring-static {
  fill: none;
  stroke: rgba(232, 255, 89, 0.1);
  stroke-width: 1;
}

.radar-rings .ring {
  fill: none;
  stroke: rgba(232, 255, 89, 0.22);
  stroke-width: 1;
  transform-origin: 400px 260px;
  transform-box: fill-box;
}

.ring-1 { animation: radar-pulse 3.2s ease-out infinite; }
.ring-2 { animation: radar-pulse 3.2s ease-out 1s infinite; }
.ring-3 { animation: radar-pulse 3.2s ease-out 2s infinite; }

@keyframes radar-pulse {
  0% { opacity: 0.65; transform: scale(1); }
  100% { opacity: 0; transform: scale(1.12); }
}

.topo-edges line {
  stroke: rgba(255, 255, 255, 0.14);
  stroke-width: 1;
}

.topo-nodes circle {
  fill: #52525b;
  stroke: rgba(255, 255, 255, 0.22);
  stroke-width: 1;
}

.topo-nodes circle.hub {
  fill: var(--accent);
  stroke: none;
}

.node-label {
  font-size: 10.5px;
  fill: #525252;
}

/* ── 右侧 ── */
.auth {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 3.5rem 3.5rem;
}

.auth-inner {
  width: 100%;
  max-width: 420px;
}

.auth-title {
  font-size: 1.7rem;
  font-weight: 600;
  letter-spacing: -0.02em;
  margin: 0 0 8px;
}
.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--accent);
  margin-bottom: 10px;
}
.field-icon {
  width: 16px;
  height: 16px;
  color: #525252;
}
.auth-form :deep(.ant-form-item) { margin-bottom: 1.35rem; }
.auth-form :deep(.ant-input),
.auth-form :deep(.ant-input-affix-wrapper) {
  background: var(--surface) !important;
  border: 1px solid #2a2a2a !important;
  border-radius: 6px !important;
  box-shadow: none !important;
  height: 48px !important;
  padding: 0 14px !important;
}
.auth-form :deep(.ant-input),
.auth-form :deep(.ant-input-password input) {
  color: #e5e5e5 !important;
  font-size: 14px !important;
}
.auth-form :deep(.ant-input-password input) {
  background: transparent !important;
}
.auth-form :deep(input::placeholder) { color: #525252 !important; }
.auth-form :deep(.ant-input-prefix) { margin-right: 10px !important; }
.auth-form :deep(.ant-input-affix-wrapper:hover) { border-color: #404040 !important; }
.auth-form :deep(.ant-input-affix-wrapper-focused) {
  border-color: rgba(232, 255, 89, 0.5) !important;
  box-shadow: 0 0 0 1px rgba(232, 255, 89, 0.1) !important;
}
.auth-form :deep(.ant-input-password-icon) { color: #525252 !important; }
.cta {
  width: 100%;
  height: 50px;
  margin-top: 0.75rem;
  border: none;
  border-radius: 6px;
  background: var(--accent);
  color: var(--accent-text);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.1em;
  cursor: pointer;
  transition: opacity 0.15s;
}
.cta:hover:not(:disabled) { opacity: 0.92; }
.cta:disabled { opacity: 0.5; }
.captcha-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.captcha-row :deep(.ant-input) {
  flex: 1;
}
.captcha-img {
  height: 48px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid #2a2a2a;
}
.copyright {
  position: absolute;
  right: 2.5rem;
  bottom: 1.5rem;
  font-size: 10px;
  color: #404040;
  margin: 0;
}
@media (max-width: 960px) {
  .login-shell { grid-template-columns: 1fr; }
  .narrative { min-height: auto; padding-bottom: 1rem; }
  .topology-wrap {
    position: relative;
    top: auto;
    transform: none;
    min-height: 320px;
    margin-top: 2rem;
  }
  .topology { width: 100%; }
  .auth { padding: 2rem 1.5rem 3rem; }
}
@media (prefers-reduced-motion: reduce) {
  .ring-1, .ring-2, .ring-3 { animation: none; opacity: 0; }
  .click-pulse { display: none; }
}
.reset-row {
  text-align: center;
  margin-top: -0.5rem;
}
.reset-link {
  font-size: 12px;
  color: #525252;
  cursor: pointer;
  transition: color 0.15s;
  text-decoration: none;
}
.reset-link:hover {
  color: var(--accent);
}
</style>
