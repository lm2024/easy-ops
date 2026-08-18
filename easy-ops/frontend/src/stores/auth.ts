import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { UserModel } from '../types'
import { switchTenant as switchTenantApi } from '../api/tenant'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const role = ref<string | null>(localStorage.getItem('role'))
  const user = ref<UserModel | null>(null)
  const tenantId = ref<number | null>(localStorage.getItem('tenantId') ? Number(localStorage.getItem('tenantId')) : null)
  const tenantRole = ref<string | null>(localStorage.getItem('tenantRole'))
  const tenantName = ref<string | null>(localStorage.getItem('tenantName'))

  /** 平台管理员（sys_user.role = admin，兼容大小写） */
  const isSuperAdmin = computed(() => {
    const r = (user.value?.role ?? role.value ?? '').toUpperCase()
    return r === 'ADMIN' || r === 'SUPER_ADMIN'
  })
  /** 租户管理员（tenant_user.role = TENANT_ADMIN） */
  const isTenantAdmin = computed(() => (tenantRole.value ?? '').toUpperCase() === 'TENANT_ADMIN')
  /** 只读 VIEWER */
  const isViewer = computed(() => (tenantRole.value ?? '').toUpperCase() === 'VIEWER')

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUser(u: UserModel) {
    user.value = u
    role.value = u.role
    if (u.role) localStorage.setItem('role', u.role)
  }

  /** 登录成功后写入用户 + 租户上下文 */
  function setAuth(auth: { user: UserModel; tenantId?: number; tenantRole?: string; tenantName?: string }) {
    user.value = auth.user
    role.value = auth.user.role
    localStorage.setItem('role', auth.user.role)
    tenantId.value = auth.tenantId ?? null
    tenantRole.value = auth.tenantRole ?? null
    tenantName.value = auth.tenantName ?? null
    if (auth.tenantId != null) localStorage.setItem('tenantId', String(auth.tenantId))
    else localStorage.removeItem('tenantId')
    if (auth.tenantRole) localStorage.setItem('tenantRole', auth.tenantRole)
    else localStorage.removeItem('tenantRole')
    if (auth.tenantName) localStorage.setItem('tenantName', auth.tenantName)
    else localStorage.removeItem('tenantName')
  }

  /** 平台管理员切换当前生效租户视角（tenantId=0/null → 平台视图全量） */
  async function switchTenant(targetTenantId: number) {
    const res = await switchTenantApi(targetTenantId)
    const view = res.data
    tenantId.value = view.tenantId ?? null
    tenantRole.value = view.tenantRole ?? null
    tenantName.value = view.tenantName ?? null
    if (view.tenantId != null) localStorage.setItem('tenantId', String(view.tenantId))
    else localStorage.removeItem('tenantId')
    if (view.tenantRole) localStorage.setItem('tenantRole', view.tenantRole)
    else localStorage.removeItem('tenantRole')
    if (view.tenantName) localStorage.setItem('tenantName', view.tenantName)
    else localStorage.removeItem('tenantName')
    return view
  }

  function logout() {
    token.value = ''
    user.value = null
    role.value = null
    tenantId.value = null
    tenantRole.value = null
    tenantName.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('role')
    localStorage.removeItem('tenantId')
    localStorage.removeItem('tenantRole')
    localStorage.removeItem('tenantName')
  }

  return {
    token, role, user, tenantId, tenantRole, tenantName,
    isSuperAdmin, isTenantAdmin, isViewer,
    setToken, setUser, setAuth, switchTenant, logout
  }
})
