<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider
      v-model:collapsed="appStore.sidebarCollapsed"
      collapsible
      :width="220"
      :collapsed-width="64"
      theme="dark"
      class="app-sider"
    >
      <div class="logo">
        <cloud-server-outlined style="font-size: 24px; color: #1890ff" />
        <h2 v-if="!appStore.sidebarCollapsed" style="color: white; margin: 0 0 0 10px; font-size: 18px; font-weight: 600">
          EasyOps
        </h2>
      </div>

      <!-- 展开状态 -->
      <a-menu
        v-if="!appStore.sidebarCollapsed"
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        theme="dark"
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
          <a-menu-item key="logs"><file-text-outlined /><span>日志查看</span></a-menu-item>
          <a-menu-item key="config-editor"><setting-outlined /><span>配置编辑</span></a-menu-item>
        </a-sub-menu>

        <!-- 监控告警 -->
        <a-sub-menu key="sub-monitor">
          <template #title>
            <dashboard-outlined />
            <span>监控告警</span>
          </template>
          <a-menu-item key="monitor"><dashboard-outlined /><span>仪表盘</span></a-menu-item>
          <a-menu-item key="alarms"><alert-outlined /><span>告警中心</span></a-menu-item>
          <a-menu-item key="alarm-config"><notification-outlined /><span>告警配置</span></a-menu-item>
        </a-sub-menu>

        <!-- 系统设置 -->
        <a-sub-menu key="sub-system">
          <template #title>
            <bulb-outlined />
            <span>系统设置</span>
          </template>
          <a-menu-item key="ai-config"><bulb-outlined /><span>AI 配置</span></a-menu-item>
          <a-menu-item key="users"><team-outlined /><span>用户管理</span></a-menu-item>
          <a-menu-item key="operations"><audit-outlined /><span>操作审计</span></a-menu-item>
        </a-sub-menu>
      </a-menu>

      <!-- 折叠状态：扁平图标列表，每个图标用 tooltip 显示名称 -->
      <a-menu
        v-else
        v-model:selectedKeys="selectedKeys"
        theme="dark"
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
            <a-dropdown>
              <div class="user-info">
                <a-avatar :size="32" style="background-color: #1890ff; margin-right: 8px">
                  <template #icon><user-outlined /></template>
                </a-avatar>
                <span class="username">{{ authStore.user?.username || 'Admin' }}</span>
                <down-outlined style="margin-left: 4px; color: #8c8c8c" />
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
          </a-col>
        </a-row>
      </a-layout-header>
      <a-layout-content class="app-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'
import { useAuthStore } from '../stores/auth'
import {
  MenuUnfoldOutlined, MenuFoldOutlined, DownOutlined,
  UserOutlined, LogoutOutlined, CloudServerOutlined,
  ClusterOutlined, FolderOpenOutlined, TagOutlined, RocketOutlined,
  CodeOutlined, FileTextOutlined, SettingOutlined,
  DashboardOutlined, AlertOutlined, NotificationOutlined,
  BulbOutlined, TeamOutlined, AuditOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

// 有效菜单 key 集合（不含分组 key sub-core, sub-tools 等）
const validMenuKeys = new Set([
  'nodes', 'projects', 'versions', 'deploy',
  'console', 'logs', 'config-editor',
  'monitor', 'alarms', 'alarm-config',
  'ai-config', 'users', 'operations', 'batch-download'
])

// 路由前缀 -> 菜单 key
const pathPrefixMap: Record<string, string> = {
  'nodes': 'nodes', 'projects': 'projects', 'versions': 'versions', 'deploy': 'deploy',
  'console': 'console', 'logs': 'logs', 'config-editor': 'config-editor',
  'monitor': 'monitor', 'alarms': 'alarms', 'alarm-config': 'alarm-config',
  'ai-config': 'ai-config', 'users': 'users', 'operations': 'operations',
  'batch-download': 'batch-download'
}

// 路由前缀 -> 分组 key
const prefixToSub: Record<string, string> = {
  nodes: 'sub-core', projects: 'sub-core', versions: 'sub-core', deploy: 'sub-core',
  console: 'sub-tools', logs: 'sub-tools', 'config-editor': 'sub-tools',
  monitor: 'sub-monitor', alarms: 'sub-monitor', 'alarm-config': 'sub-monitor',
  'ai-config': 'sub-system', users: 'sub-system', operations: 'sub-system'
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
  { key: 'logs', title: '日志查看', icon: FileTextOutlined },
  { key: 'config-editor', title: '配置编辑', icon: SettingOutlined },
]
const monitorItems = [
  { key: 'monitor', title: '仪表盘', icon: DashboardOutlined },
  { key: 'alarms', title: '告警中心', icon: AlertOutlined },
  { key: 'alarm-config', title: '告警配置', icon: NotificationOutlined },
]
const systemItems = [
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
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
  z-index: 10;
}

.app-sider :deep(.ant-layout-sider-trigger) {
  background: rgba(0, 0, 0, 0.2);
}

.app-sider :deep(.ant-menu-dark) {
  background: transparent;
}

.app-sider :deep(.ant-menu-item) {
  margin: 2px 8px;
  border-radius: 8px;
  height: 38px;
  line-height: 38px;
}

.app-sider :deep(.ant-menu-item-selected) {
  background: rgba(24, 144, 255, 0.2) !important;
}

.app-sider :deep(.ant-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
}

.app-sider :deep(.ant-menu-submenu-title) {
  height: 36px;
  line-height: 36px;
  margin: 0;
  padding: 0 24px !important;
}

.app-sider :deep(.ant-menu-submenu-title:hover) {
  color: rgba(255,255,255,0.85) !important;
}

.app-sider :deep(.collapsed-menu-item) {
  margin: 2px 8px;
  border-radius: 8px;
  height: 38px;
  line-height: 38px;
}

.app-sider :deep(.collapsed-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
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
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.app-header {
  background: #fff;
  padding: 0 24px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 9;
  height: 64px;
  line-height: 64px;
}

.trigger {
  font-size: 18px;
  cursor: pointer;
  transition: color 0.3s;
  padding: 4px;
}

.trigger:hover { color: #1890ff; }

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.3s;
}

.user-info:hover { background: #f5f5f5; }

.username { font-size: 14px; color: #333; }

.app-content {
  margin: 16px;
  padding: 24px;
  background: #f0f2f5;
  min-height: calc(100vh - 64px - 32px);
  border-radius: 8px;
}
</style>
