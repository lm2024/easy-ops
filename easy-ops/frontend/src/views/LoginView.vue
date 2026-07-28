<template>
  <div class="login-page" :data-theme="themeMode">
    <!-- 背景装饰 -->
    <div class="login-bg">
      <div class="login-bg__gradient" />
      <div class="login-bg__grid" />
    </div>

    <!-- 主容器 -->
    <div class="login-container">
      <!-- 左侧品牌 -->
      <div class="login-brand">
        <div class="login-brand__content">
          <div class="login-brand__logo">
            <div class="login-brand__icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
              </svg>
            </div>
            <span class="login-brand__name">EasyOps</span>
          </div>

          <h1 class="login-brand__title">
            智能运维
            <br />
            <span class="login-brand__title--accent">管理平台</span>
          </h1>

          <p class="login-brand__desc">
            一站式分布式运维解决方案
            <br />
            监控 · 部署 · 管理 · 自愈
          </p>

          <div class="login-brand__features">
            <div class="login-brand__feature">
              <div class="login-brand__feature-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
                </svg>
              </div>
              <span>实时监控</span>
            </div>
            <div class="login-brand__feature">
              <div class="login-brand__feature-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
                </svg>
              </div>
              <span>一键部署</span>
            </div>
            <div class="login-brand__feature">
              <div class="login-brand__feature-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                </svg>
              </div>
              <span>安全可靠</span>
            </div>
          </div>
        </div>

        <!-- 主题切换 -->
        <button class="login-theme-btn" @click="toggleTheme" :title="themeMode === 'dark' ? '切换白天模式' : '切换夜间模式'">
          <svg v-if="themeMode === 'dark'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="5" />
            <line x1="12" y1="1" x2="12" y2="3" />
            <line x1="12" y1="21" x2="12" y2="23" />
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
            <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
            <line x1="1" y1="12" x2="3" y2="12" />
            <line x1="21" y1="12" x2="23" y2="12" />
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
            <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
          </svg>
        </button>
      </div>

      <!-- 右侧登录表单 -->
      <div class="login-form-wrapper">
        <div class="login-form-card">
          <div class="login-form-header">
            <h2 class="login-form-title">欢迎回来</h2>
            <p class="login-form-subtitle">请输入您的账号登录系统</p>
          </div>

          <a-config-provider :theme="formTheme">
            <a-form :model="formState" @finish="handleLogin" layout="vertical" class="login-form" size="large">
              <a-form-item name="username" :rules="[{ required: true, message: '请输入用户名' }]">
                <a-input v-model:value="formState.username" placeholder="用户名" autocomplete="username">
                  <template #prefix>
                    <svg class="login-form__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                      <circle cx="12" cy="7" r="4" />
                    </svg>
                  </template>
                </a-input>
              </a-form-item>

              <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }]">
                <a-input-password v-model:value="formState.password" placeholder="密码" autocomplete="current-password">
                  <template #prefix>
                    <svg class="login-form__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                    </svg>
                  </template>
                </a-input-password>
              </a-form-item>

              <a-form-item name="captchaCode" :rules="[{ required: true, message: '请输入验证码' }]">
                <div class="login-captcha">
                  <div class="login-captcha__input">
                    <a-input v-model:value="formState.captchaCode" placeholder="验证码" maxlength="5" size="large">
                      <template #prefix>
                        <svg class="login-form__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                          <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                        </svg>
                      </template>
                    </a-input>
                  </div>
                  <div class="login-captcha__wrapper">
                    <div class="login-captcha__img" @click="loadCaptcha" :title="captchaExpired ? '验证码已过期，点击刷新' : '点击刷新验证码'">
                      <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
                      <span v-else class="login-captcha__loading">加载中...</span>
                      <div v-if="captchaExpired" class="login-captcha__expired-overlay">
                        <span>已过期</span>
                      </div>
                    </div>
                    <div class="login-captcha__timer">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="login-captcha__timer-icon">
                        <circle cx="12" cy="12" r="10" />
                        <polyline points="12 6 12 12 16 14" />
                      </svg>
                      <span :class="{ 'login-captcha__timer--expired': captchaExpired }">
                        {{ captchaExpired ? '已过期' : captchaCountdown + 's' }}
                      </span>
                    </div>
                  </div>
                </div>
              </a-form-item>

              <a-form-item>
                <a-button type="primary" html-type="submit" block :loading="loading" :disabled="loading" class="login-btn">
                  {{ loading ? '登录中...' : '登 录' }}
                </a-button>
              </a-form-item>
            </a-form>
          </a-config-provider>

          <div class="login-form-footer">
            <span class="login-form-hint">请输入您的账号密码登录系统</span>
          </div>
        </div>

        <p class="login-copyright">© 2024 EasyOps. All rights reserved.</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useAppStore } from '../stores/app'
import { login, getCaptcha } from '../api/auth'
import { message } from 'ant-design-vue'
import { getAntTheme } from '../theme/themes'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
import type { ThemeMode } from '../theme/themes'

const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()
const loading = ref(false)

const themeMode = computed<ThemeMode>(() => appStore.themeMode)

function toggleTheme() {
  appStore.toggleTheme()
}

const formTheme = computed<ThemeConfig>(() => getAntTheme(appStore.themeMode))

const formState = reactive({ username: '', password: '', captchaCode: '' })
const captchaId = ref('')
const captchaImage = ref('')

// 验证码倒计时相关
const CAPTCHA_EXPIRE_SECONDS = 120 // 验证码有效期2分钟
const captchaCountdown = ref(CAPTCHA_EXPIRE_SECONDS)
const captchaExpired = ref(false)
let countdownTimer: ReturnType<typeof setInterval> | null = null

// 启动倒计时
function startCountdown() {
  // 清除之前的定时器
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  
  captchaCountdown.value = CAPTCHA_EXPIRE_SECONDS
  captchaExpired.value = false
  
  countdownTimer = setInterval(() => {
    captchaCountdown.value--
    
    if (captchaCountdown.value <= 0) {
      captchaExpired.value = true
      clearInterval(countdownTimer!)
      countdownTimer = null
      
      // 自动刷新验证码
      message.warning('验证码已过期，正在自动刷新...')
      loadCaptcha()
    }
  }, 1000)
}

// 加载验证码
async function loadCaptcha() {
  try {
    const res = await getCaptcha()
    captchaId.value = res.data.captchaId
    captchaImage.value = res.data.imageBase64
    formState.captchaCode = ''
    
    // 重新启动倒计时
    startCountdown()
  } catch { /* interceptor handles error */ }
}

// 登录处理
async function handleLogin() {
  if (loading.value) return
  
  // 检查验证码是否过期
  if (captchaExpired.value) {
    message.warning('验证码已过期，请刷新验证码')
    return
  }
  
  try {
    loading.value = true
    const res = await login(formState.username, formState.password, captchaId.value, formState.captchaCode)
    authStore.setToken(res.data.token)
    authStore.setUser({
      id: 0,
      username: res.data.username,
      role: res.data.role?.toUpperCase() === 'ADMIN' ? 'ADMIN' : 'OPERATOR',
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

onMounted(() => {
  authStore.logout()
  loadCaptcha()
})

onUnmounted(() => {
  // 清理定时器
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})
</script>

<style scoped>
/* ===== 登录页面样式 - 参考现代大厂风格 ===== */

/* 页面容器 */
.login-page {
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  font-family: 'Geist', 'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* 背景装饰 */
.login-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
}

.login-bg__gradient {
  position: absolute;
  inset: 0;
  background: 
    radial-gradient(ellipse 80% 50% at 50% -20%, rgba(232, 255, 89, 0.15), transparent),
    radial-gradient(ellipse 60% 40% at 100% 0%, rgba(56, 189, 248, 0.1), transparent),
    radial-gradient(ellipse 50% 50% at 0% 100%, rgba(168, 85, 247, 0.08), transparent);
}

[data-theme='light'] .login-bg__gradient {
  background: 
    radial-gradient(ellipse 80% 50% at 50% -20%, rgba(101, 163, 13, 0.15), transparent),
    radial-gradient(ellipse 60% 40% at 100% 0%, rgba(37, 99, 235, 0.08), transparent),
    radial-gradient(ellipse 50% 50% at 0% 100%, rgba(168, 85, 247, 0.05), transparent);
}

.login-bg__grid {
  position: absolute;
  inset: 0;
  background-image: 
    linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 60px 60px;
}

[data-theme='light'] .login-bg__grid {
  background-image: 
    linear-gradient(rgba(0, 0, 0, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.03) 1px, transparent 1px);
}

/* 主容器 */
.login-container {
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  max-width: 1200px;
  min-height: 100dvh;
  padding: 2rem;
}

/* 左侧品牌 */
.login-brand {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 3rem;
  position: relative;
}

.login-brand__content {
  max-width: 560px;
}

.login-brand__logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 3rem;
}

.login-brand__icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--eo-primary);
  border-radius: 12px;
  color: var(--eo-primary-text);
}

.login-brand__icon svg {
  width: 28px;
  height: 28px;
}

.login-brand__name {
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--eo-text);
}

.login-brand__title {
  font-size: clamp(2rem, 4vw, 3.5rem);
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: -0.03em;
  color: var(--eo-text);
  margin: 0 0 1.5rem;
}

.login-brand__title--accent {
  background: linear-gradient(135deg, var(--eo-primary), #38bdf8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.login-brand__desc {
  font-size: 1.1rem;
  line-height: 1.6;
  color: var(--eo-text-secondary);
  margin: 0 0 3rem;
}

.login-brand__features {
  display: flex;
  gap: 2rem;
}

.login-brand__feature {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: var(--eo-bg-elevated);
  border: 1px solid var(--eo-border);
  border-radius: 12px;
  transition: all 0.2s ease;
}

.login-brand__feature:hover {
  border-color: var(--eo-primary);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px var(--eo-shadow);
}

.login-brand__feature-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(232, 255, 89, 0.1);
  border-radius: 8px;
  color: var(--eo-primary);
}

[data-theme='light'] .login-brand__feature-icon {
  background: rgba(101, 163, 13, 0.1);
}

.login-brand__feature-icon svg {
  width: 20px;
  height: 20px;
}

.login-brand__feature span {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--eo-text);
}

/* 主题切换按钮 */
.login-theme-btn {
  position: absolute;
  bottom: 2rem;
  left: 3rem;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--eo-bg-elevated);
  border: 1px solid var(--eo-border);
  border-radius: 50%;
  color: var(--eo-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.login-theme-btn:hover {
  color: var(--eo-primary);
  border-color: var(--eo-primary);
  transform: scale(1.05);
}

.login-theme-btn svg {
  width: 20px;
  height: 20px;
}

/* 右侧登录表单 */
.login-form-wrapper {
  flex: 0 0 480px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
}

.login-form-card {
  width: 100%;
  max-width: 400px;
  background: var(--eo-bg-elevated);
  border: 1px solid var(--eo-border);
  border-radius: 20px;
  padding: 2.5rem;
  box-shadow: 0 20px 60px var(--eo-shadow);
  backdrop-filter: blur(20px);
}

.login-form-header {
  margin-bottom: 2rem;
  text-align: center;
}

.login-form-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--eo-text);
  margin: 0 0 0.5rem;
  letter-spacing: -0.02em;
}

.login-form-subtitle {
  font-size: 0.875rem;
  color: var(--eo-text-secondary);
  margin: 0;
}

/* 表单样式 */
.login-form {
  margin-bottom: 1.5rem;
}

.login-form :deep(.ant-form-item) {
  margin-bottom: 1.25rem;
}

.login-form :deep(.ant-input),
.login-form :deep(.ant-input-affix-wrapper) {
  height: 48px;
  border-radius: 12px;
  background: var(--eo-bg-muted);
  border: 1px solid var(--eo-border);
  transition: all 0.2s ease;
}

.login-form :deep(.ant-input:hover),
.login-form :deep(.ant-input-affix-wrapper:hover) {
  border-color: var(--eo-primary);
}

.login-form :deep(.ant-input:focus),
.login-form :deep(.ant-input-affix-wrapper-focused) {
  border-color: var(--eo-primary);
  box-shadow: 0 0 0 3px rgba(232, 255, 89, 0.15);
}

[data-theme='light'] .login-form :deep(.ant-input:focus),
[data-theme='light'] .login-form :deep(.ant-input-affix-wrapper-focused) {
  box-shadow: 0 0 0 3px rgba(101, 163, 13, 0.15);
}

.login-form__icon {
  width: 18px;
  height: 18px;
  color: var(--eo-text-muted);
}

/* 验证码 */
.login-captcha {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.login-captcha__input {
  flex: 1;
  min-width: 0;
}

.login-captcha__input :deep(.ant-input-affix-wrapper) {
  height: 48px;
  border-radius: 12px;
  background: var(--eo-bg-muted);
  border: 1px solid var(--eo-border);
}

.login-captcha__wrapper {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.login-captcha__img {
  width: 160px;
  height: 48px;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--eo-border);
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--eo-bg-muted);
  position: relative;
}

.login-captcha__img:hover {
  border-color: var(--eo-primary);
  transform: translateY(-1px);
}

.login-captcha__img img {
  width: 100%;
  height: 100%;
  object-fit: fill;
}

.login-captcha__loading {
  font-size: 0.75rem;
  color: var(--eo-text-muted);
}

.login-captcha__expired-overlay {
  position: absolute;
  inset: 0;
  background: rgba(239, 68, 68, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 0.75rem;
  font-weight: 600;
  backdrop-filter: blur(2px);
}

.login-captcha__timer {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.7rem;
  color: var(--eo-text-muted);
  transition: color 0.2s ease;
}

.login-captcha__timer-icon {
  width: 12px;
  height: 12px;
}

.login-captcha__timer--expired {
  color: #ef4444;
  font-weight: 600;
}

/* 登录按钮 */
.login-btn {
  height: 48px !important;
  border-radius: 12px !important;
  font-size: 1rem !important;
  font-weight: 600 !important;
  background: var(--eo-primary) !important;
  border-color: var(--eo-primary) !important;
  color: var(--eo-primary-text) !important;
  box-shadow: 0 4px 16px rgba(232, 255, 89, 0.3);
  transition: all 0.2s ease !important;
}

[data-theme='light'] .login-btn {
  box-shadow: 0 4px 16px rgba(101, 163, 13, 0.3);
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 24px rgba(232, 255, 89, 0.4) !important;
}

[data-theme='light'] .login-btn:hover:not(:disabled) {
  box-shadow: 0 6px 24px rgba(101, 163, 13, 0.4) !important;
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
}

/* 底部 */
.login-form-footer {
  text-align: center;
  padding-top: 1rem;
  border-top: 1px solid var(--eo-border);
}

.login-form-hint {
  font-size: 0.75rem;
  color: var(--eo-text-muted);
}

.login-copyright {
  margin-top: 2rem;
  font-size: 0.75rem;
  color: var(--eo-text-muted);
  text-align: center;
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .login-container {
    flex-direction: column;
    min-height: auto;
    padding: 1rem;
  }

  .login-brand {
    padding: 2rem 1.5rem;
    text-align: center;
  }

  .login-brand__content {
    max-width: 100%;
  }

  .login-brand__logo {
    justify-content: center;
  }

  .login-brand__title {
    font-size: 2rem;
    margin-bottom: 1rem;
  }

  .login-brand__desc {
    margin-bottom: 2rem;
  }

  .login-brand__features {
    justify-content: center;
    flex-wrap: wrap;
    gap: 1rem;
  }

  .login-theme-btn {
    position: relative;
    bottom: auto;
    left: auto;
    margin: 2rem auto 0;
  }

  .login-form-wrapper {
    flex: 1;
    padding: 1rem;
  }

  .login-form-card {
    padding: 2rem;
  }
}

@media (max-width: 640px) {
  .login-brand__features {
    flex-direction: column;
    align-items: center;
  }

  .login-brand__feature {
    width: 100%;
    max-width: 280px;
    justify-content: center;
  }

  .login-form-card {
    padding: 1.5rem;
    border-radius: 16px;
  }
}
</style>
