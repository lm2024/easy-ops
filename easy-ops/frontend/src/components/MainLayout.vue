<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider
      v-model:collapsed="appStore.sidebarCollapsed"
      collapsible
      :width="220"
      :collapsed-width="64"
      :theme="siderTheme"
      class="app-sider"
      :class="appStore.themeMode === 'dark' ? 'app-sider--dark' : 'app-sider--light'"
    >
      <div class="logo">
        <cloud-server-outlined class="logo-icon" />
        <h2 v-if="!appStore.sidebarCollapsed" class="logo-text">EasyOps</h2>
      </div>

      <!-- 展开状态 -->
      <a-menu
        v-if="!appStore.sidebarCollapsed"
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        :theme="siderTheme"
        mode="inline"
        @select="handleMenuSelect"
      >
        <!-- 运维核心 -->
        <a-sub-menu key="sub-core">
          <template #title>
            <cluster-outlined />
            <span>运维核心</span>
          </template>
          <a-menu-item key="nodes"><cluster-outlined /><span>节点管理</span></a-menu-item>
          <a-menu-item key="projects"><folder-open-outlined /><span>应用管理</span></a-menu-item>
          <a-menu-item key="versions"><tag-outlined /><span>版本管理</span></a-menu-item>
          <a-menu-item key="deploy"><rocket-outlined /><span>一键部署</span></a-menu-item>
        </a-sub-menu>

        <!-- 运维工具 -->
        <a-sub-menu key="sub-tools">
          <template #title>
            <code-outlined />
            <span>运维工具</span>
          </template>
          <a-menu-item key="console"><code-outlined /><span>控制台</span></a-menu-item>
          <a-menu-item key="config-manage"><setting-outlined /><span>配置文件管理</span></a-menu-item>
          <a-menu-item key="log-manage"><file-text-outlined /><span>日志管理</span></a-menu-item>
        </a-sub-menu>

        <!-- 监控告警 -->
        <a-sub-menu key="sub-monitor">
          <template #title>
            <dashboard-outlined />
            <span>监控告警</span>
          </template>
          <a-menu-item key="monitor"><dashboard-outlined /><span>仪表盘</span></a-menu-item>
          <a-menu-item key="app-monitor"><fund-outlined /><span>应用监控</span></a-menu-item>
          <a-menu-item key="alarms"><alert-outlined /><span>告警中心</span></a-menu-item>
          <a-menu-item key="alarm-config"><setting-outlined /><span>告警配置</span></a-menu-item>
          <a-menu-item key="self-heal"><medicine-box-outlined /><span>自愈策略</span></a-menu-item>
        </a-sub-menu>

        <!-- 运维文档 -->
        <a-sub-menu key="sub-knowledge">
          <template #title>
            <book-outlined />
            <span>运维文档</span>
          </template>
          <a-menu-item key="knowledge"><book-outlined /><span>文档管理</span></a-menu-item>
        </a-sub-menu>

        <!-- 系统设置 -->
        <a-sub-menu key="sub-system">
          <template #title>
            <bulb-outlined />
            <span>系统设置</span>
          </template>
          <a-menu-item key="db-manage"><database-outlined /><span>H2 表结构维护</span></a-menu-item>
          <a-menu-item key="ai-config"><bulb-outlined /><span>AI 配置</span></a-menu-item>
          <a-menu-item key="users"><team-outlined /><span>用户管理</span></a-menu-item>
          <a-menu-item key="operations"><audit-outlined /><span>操作审计</span></a-menu-item>
        </a-sub-menu>
      </a-menu>

      <!-- 折叠状态：扁平图标列表，每个图标用 tooltip 显示名称 -->
      <a-menu
        v-else
        v-model:selectedKeys="selectedKeys"
        :theme="siderTheme"
        mode="inline"
        :inline-collapsed="true"
        @select="handleMenuSelect"
      >
        <a-tooltip placement="right" v-for="item in coreItems" :key="item.key" trigger="hover">
          <template #title>{{ item.title }}</template>
          <a-menu-item :key="item.key" class="collapsed-menu-item" @click="handleCollapsedClick(item.key)">
            <component :is="item.icon" />
          </a-menu-item>
        </a-tooltip>
        <a-tooltip placement="right" v-for="item in toolsItems" :key="item.key" trigger="hover">
          <template #title>{{ item.title }}</template>
          <a-menu-item :key="item.key" class="collapsed-menu-item" @click="handleCollapsedClick(item.key)">
            <component :is="item.icon" />
          </a-menu-item>
        </a-tooltip>
        <a-tooltip placement="right" v-for="item in monitorItems" :key="item.key" trigger="hover">
          <template #title>{{ item.title }}</template>
          <a-menu-item :key="item.key" class="collapsed-menu-item" @click="handleCollapsedClick(item.key)">
            <component :is="item.icon" />
          </a-menu-item>
        </a-tooltip>
        <a-tooltip placement="right" v-for="item in knowledgeItems" :key="item.key" trigger="hover">
          <template #title>{{ item.title }}</template>
          <a-menu-item :key="item.key" class="collapsed-menu-item" @click="handleCollapsedClick(item.key)">
            <component :is="item.icon" />
          </a-menu-item>
        </a-tooltip>
        <a-tooltip placement="right" v-for="item in systemItems" :key="item.key" trigger="hover">
          <template #title>{{ item.title }}</template>
          <a-menu-item :key="item.key" class="collapsed-menu-item" @click="handleCollapsedClick(item.key)">
            <component :is="item.icon" />
          </a-menu-item>
        </a-tooltip>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="app-header">
        <a-row justify="space-between" align="middle" style="height: 100%">
          <a-col>
            <menu-unfold-outlined
              v-if="appStore.sidebarCollapsed"
              class="trigger"
              @click="appStore.toggleSidebar"
            />
            <menu-fold-outlined
              v-else
              class="trigger"
              @click="appStore.toggleSidebar"
            />
          </a-col>
          <a-col>
            <a-space :size="16">
              <a-tooltip :title="appStore.themeMode === 'dark' ? '切换为白天模式' : '切换为夜间模式'">
                <a-button
                  type="text"
                  class="theme-toggle"
                  :aria-label="appStore.themeMode === 'dark' ? '切换为白天模式' : '切换为夜间模式'"
                  @click="appStore.toggleTheme()"
                >
                  <bulb-outlined v-if="appStore.themeMode === 'dark'" />
                  <skin-outlined v-else />
                </a-button>
              </a-tooltip>
              <NotificationBell @unacked-alerts="onUnackedAlerts" />
              <AlertModal :alerts="unackedAlerts" />
            <a-dropdown>
              <div class="user-info">
                <a-avatar :size="32" class="user-avatar">
                  <template #icon><user-outlined /></template>
                </a-avatar>
                <span class="username">{{ authStore.user?.username || 'Admin' }}</span>
                <down-outlined class="user-caret" />
              </div>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="handleLogout">
                    <logout-outlined />
                    <span style="margin-left: 8px">退出登录</span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            </a-space>
          </a-col>
        </a-row>
      </a-layout-header>
      <a-layout-content class="app-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'
import { useAuthStore } from '../stores/auth'
import NotificationBell from './NotificationBell.vue'
import AlertModal from './AlertModal.vue'
import type { NotificationRecordModel } from '../types'
import {
  MenuUnfoldOutlined, MenuFoldOutlined, DownOutlined,
  UserOutlined, LogoutOutlined, CloudServerOutlined,
  ClusterOutlined, FolderOpenOutlined, TagOutlined, RocketOutlined,
  CodeOutlined, FileTextOutlined, SettingOutlined,
  DashboardOutlined, AlertOutlined,
  DatabaseOutlined, BulbOutlined, TeamOutlined, AuditOutlined,
  FundOutlined, MedicineBoxOutlined, BookOutlined,
  SkinOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

/** 侧栏菜单始终跟随明暗主题，暗色下强制 dark 菜单配色 */
const siderTheme = computed(() => appStore.themeMode)

// 有效菜单 key 集合（不含分组 key sub-core, sub-tools 等）
const validMenuKeys = new Set([
  'nodes', 'projects', 'versions', 'deploy',
  'console', 'config-manage', 'log-manage',
  'monitor', 'app-monitor', 'alarms', 'alarm-config', 'self-heal',
  'knowledge',
  'db-manage', 'ai-config', 'users', 'operations', 'batch-download'
])

const unackedAlerts = ref<NotificationRecordModel[]>([])
function onUnackedAlerts(alerts: NotificationRecordModel[]) {
  unackedAlerts.value = alerts
}

// 路由前缀 -> 菜单 key
const pathPrefixMap: Record<string, string> = {
  'nodes': 'nodes', 'projects': 'projects', 'versions': 'versions', 'deploy': 'deploy',
  'console': 'console', 'config-manage': 'config-manage', 'log-manage': 'log-manage',
  'monitor': 'monitor', 'app-monitor': 'app-monitor', 'alarms': 'alarms', 'alarm-config': 'alarm-config',
  'self-heal': 'self-heal', 'knowledge': 'knowledge',
  'db-manage': 'db-manage', 'ai-config': 'ai-config', 'users': 'users', 'operations': 'operations',
  'batch-download': 'batch-download'
}

// 路由前缀 -> 分组 key
const prefixToSub: Record<string, string> = {
  nodes: 'sub-core', projects: 'sub-core', versions: 'sub-core', deploy: 'sub-core',
  console: 'sub-tools', 'config-manage': 'sub-tools', 'log-manage': 'sub-tools',
  monitor: 'sub-monitor', 'app-monitor': 'sub-monitor', alarms: 'sub-monitor', 'alarm-config': 'sub-monitor',
  'self-heal': 'sub-monitor',
  knowledge: 'sub-knowledge',
  'db-manage': 'sub-system', 'ai-config': 'sub-system', users: 'sub-system', operations: 'sub-system'
}

const selectedKeys = ref<string[]>([])
const openKeys = ref<string[]>(['sub-core'])

// 解析当前路由对应的菜单 key
function resolveMenuKey(): string | null {
  const pathParts = route.path.replace(/^\//, '').split('/')
  const prefix = pathParts[0]
  return pathPrefixMap[prefix] || null
}

// 同步菜单状态到当前路由
function syncMenuFromRoute() {
  const key = resolveMenuKey()
  if (key) {
    selectedKeys.value = [key]
    const subKey = prefixToSub[key]
    if (subKey && !openKeys.value.includes(subKey)) {
      openKeys.value.push(subKey)
    }
  } else {
    selectedKeys.value = []
  }
}

// 监听路由变化，同步菜单选中状态
watch(() => route.path, () => {
  syncMenuFromRoute()
}, { immediate: false })

// 初始化
const initKey = resolveMenuKey()
if (initKey) {
  selectedKeys.value = [initKey]
  const parent = prefixToSub[initKey]
  if (parent) {
    openKeys.value.push(parent)
  }
}

// 折叠菜单项定义
const coreItems = [
  { key: 'nodes', title: '节点管理', icon: ClusterOutlined },
  { key: 'projects', title: '应用管理', icon: FolderOpenOutlined },
  { key: 'versions', title: '版本管理', icon: TagOutlined },
  { key: 'deploy', title: '一键部署', icon: RocketOutlined },
]
const toolsItems = [
  { key: 'console', title: '控制台', icon: CodeOutlined },
  { key: 'config-manage', title: '配置文件管理', icon: SettingOutlined },
  { key: 'log-manage', title: '日志管理', icon: FileTextOutlined },
]
const monitorItems = [
  { key: 'monitor', title: '仪表盘', icon: DashboardOutlined },
  { key: 'app-monitor', title: '应用监控', icon: FundOutlined },
  { key: 'alarms', title: '告警中心', icon: AlertOutlined },
  { key: 'alarm-config', title: '告警配置', icon: SettingOutlined },
  { key: 'self-heal', title: '自愈策略', icon: MedicineBoxOutlined },
]
const knowledgeItems = [
  { key: 'knowledge', title: '运维文档', icon: BookOutlined },
]
const systemItems = [
  { key: 'db-manage', title: 'H2 表结构维护', icon: DatabaseOutlined },
  { key: 'ai-config', title: 'AI 配置', icon: BulbOutlined },
  { key: 'users', title: '用户管理', icon: TeamOutlined },
  { key: 'operations', title: '操作审计', icon: AuditOutlined },
]

// 展开模式下菜单点击（展开模式下 e.key 可能是子菜单 key 或分组 key）
function handleMenuSelect(e: any) {
  const key = typeof e.key === 'string' ? e.key : (Array.isArray(e.key) ? e.key[0] : null)
  if (!key || !validMenuKeys.has(key)) return // 分组 key 只展开/折叠，不跳转
  appStore.setMenu(key)
  selectedKeys.value = [key]
  router.push('/' + key)
}

// 折叠模式下图标按钮点击（通过 tooltip 包裹，直接绑定 click 确保事件可达）
function handleCollapsedClick(key: string) {
  appStore.setMenu(key)
  selectedKeys.value = [key]
  router.push('/' + key)
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-sider {
  box-shadow: 2px 0 12px var(--eo-shadow);
  z-index: 10;
  background: var(--eo-sider-bg) !important;
}

/* ===== 暗色侧栏菜单：未选中项浅灰，选中项荧光黄 ===== */
.app-sider--dark :deep(.ant-menu-dark) {
  background: transparent;
  color: rgba(255, 255, 255, 0.78);
}

.app-sider--dark :deep(.ant-menu-dark .ant-menu-item),
.app-sider--dark :deep(.ant-menu-dark .ant-menu-submenu-title) {
  color: rgba(255, 255, 255, 0.78) !important;
}

.app-sider--dark :deep(.ant-menu-dark .ant-menu-item .anticon),
.app-sider--dark :deep(.ant-menu-dark .ant-menu-submenu-title .anticon) {
  color: rgba(255, 255, 255, 0.78) !important;
}

.app-sider--dark :deep(.ant-menu-dark .ant-menu-submenu-arrow) {
  color: rgba(255, 255, 255, 0.45) !important;
}

.app-sider--dark :deep(.ant-menu-dark .ant-menu-item:hover),
.app-sider--dark :deep(.ant-menu-dark .ant-menu-submenu-title:hover) {
  color: #ffffff !important;
  background: rgba(255, 255, 255, 0.08) !important;
}

.app-sider--dark :deep(.ant-menu-dark .ant-menu-item:hover .anticon),
.app-sider--dark :deep(.ant-menu-dark .ant-menu-submenu-title:hover .anticon) {
  color: #ffffff !important;
}

.app-sider--dark :deep(.ant-menu-dark .ant-menu-item-selected) {
  background: rgba(232, 255, 89, 0.12) !important;
  color: #e8ff59 !important;
}

.app-sider--dark :deep(.ant-menu-dark .ant-menu-item-selected .anticon) {
  color: #e8ff59 !important;
}

/* ===== 亮色侧栏菜单 ===== */
.app-sider--light :deep(.ant-menu-light .ant-menu-item),
.app-sider--light :deep(.ant-menu-light .ant-menu-submenu-title) {
  color: #3a3a3c !important;
}

.app-sider--light :deep(.ant-menu-light .ant-menu-item .anticon),
.app-sider--light :deep(.ant-menu-light .ant-menu-submenu-title .anticon) {
  color: #3a3a3c !important;
}

.app-sider--light :deep(.ant-menu-light .ant-menu-item-selected) {
  color: #65a30d !important;
  background: rgba(101, 163, 13, 0.1) !important;
}

.app-sider--light :deep(.ant-menu-light .ant-menu-item-selected .anticon) {
  color: #65a30d !important;
}

.app-sider :deep(.ant-layout-sider-trigger) {
  background: var(--eo-menu-hover);
  color: var(--eo-text-secondary);
  transition: background 0.2s ease, color 0.2s ease;
}

.app-sider :deep(.ant-layout-sider-trigger:hover) {
  color: var(--eo-primary);
}

.app-sider :deep(.ant-menu-item) {
  margin: 2px 8px;
  border-radius: 10px;
  height: 38px;
  line-height: 38px;
}

.app-sider :deep(.ant-menu-item:hover),
.app-sider :deep(.ant-menu-submenu-title:hover) {
  background: var(--eo-menu-hover) !important;
}

.app-sider :deep(.ant-menu-submenu-title) {
  height: 36px;
  line-height: 36px;
  margin: 0;
  padding: 0 24px !important;
  border-radius: 10px;
}

.app-sider :deep(.collapsed-menu-item) {
  margin: 2px 8px;
  border-radius: 10px;
  height: 38px;
  line-height: 38px;
}

.app-sider :deep(.collapsed-menu-item .anticon) {
  font-size: 16px;
  margin: 0 auto;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  border-bottom: 1px solid var(--eo-border);
}

.logo-icon {
  font-size: 24px;
  color: var(--eo-primary);
}

.logo-text {
  color: var(--eo-text);
  margin: 0 0 0 10px;
  font-size: 18px;
  font-weight: 600;
}

.app-header {
  background: var(--eo-header-bg);
  padding: 0 24px;
  border-bottom: 1px solid var(--eo-border);
  z-index: 9;
  height: 64px;
  line-height: 64px;
  transition: background 0.28s ease, border-color 0.28s ease;
}

.trigger,
.theme-toggle {
  font-size: 18px;
  cursor: pointer;
  transition: transform 0.15s cubic-bezier(0.25, 0.1, 0.25, 1), color 0.2s ease, background 0.2s ease;
  padding: 4px;
  color: var(--eo-text-secondary);
  border-radius: 8px;
}

.trigger:hover,
.theme-toggle:hover {
  color: var(--eo-primary);
  background: var(--eo-menu-hover);
}

.trigger:active,
.theme-toggle:active {
  transform: scale(0.95);
}

.user-avatar {
  background-color: var(--eo-menu-selected) !important;
  color: var(--eo-primary) !important;
  margin-right: 8px;
}

.user-caret {
  margin-left: 4px;
  color: var(--eo-text-muted);
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s ease, transform 0.15s ease;
}

.user-info:hover {
  background: var(--eo-menu-hover);
}

.user-info:active {
  transform: scale(0.98);
}

.username {
  font-size: 14px;
  color: var(--eo-text);
}

.app-content {
  margin: 16px;
  padding: 24px;
  background: var(--eo-content-bg);
  min-height: calc(100vh - 64px - 32px);
  border-radius: 12px;
  overflow-y: auto;
  overflow-x: hidden;
  transition: background 0.28s ease;
}
</style>
