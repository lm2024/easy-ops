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
      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        @select="handleMenuSelect"
      >
        <a-menu-item key="nodes">
          <cluster-outlined />
          <span>节点管理</span>
        </a-menu-item>
        <a-menu-item key="projects">
          <folder-open-outlined />
          <span>项目管理</span>
        </a-menu-item>
        <a-menu-item key="versions">
          <tag-outlined />
          <span>版本管理</span>
        </a-menu-item>
        <a-menu-item key="deploy">
          <rocket-outlined />
          <span>部署记录</span>
        </a-menu-item>
        <a-menu-item key="console">
          <code-outlined />
          <span>控制台</span>
        </a-menu-item>
        <a-menu-item key="logs">
          <file-text-outlined />
          <span>日志查看</span>
        </a-menu-item>
        <a-menu-item key="config-editor">
          <setting-outlined />
          <span>配置编辑</span>
        </a-menu-item>
        <a-menu-item key="monitor">
          <dashboard-outlined />
          <span>仪表盘</span>
        </a-menu-item>
        <a-menu-item key="alarms">
          <alert-outlined />
          <span>告警中心</span>
        </a-menu-item>
        <a-menu-item key="alarm-config">
          <notification-outlined />
          <span>告警配置</span>
        </a-menu-item>
        <a-menu-item key="users">
          <team-outlined />
          <span>用户管理</span>
        </a-menu-item>
        <a-menu-item key="operations">
          <audit-outlined />
          <span>操作审计</span>
        </a-menu-item>
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
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'
import { useAuthStore } from '../stores/auth'
import {
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  DownOutlined,
  UserOutlined,
  LogoutOutlined,
  CloudServerOutlined,
  ClusterOutlined,
  FolderOpenOutlined,
  TagOutlined,
  RocketOutlined,
  CodeOutlined,
  FileTextOutlined,
  SettingOutlined,
  DashboardOutlined,
  AlertOutlined,
  NotificationOutlined,
  TeamOutlined,
  AuditOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

const selectedKeys = ref([route.name || 'nodes'])

function handleMenuSelect(e: any) {
  appStore.setMenu(e.key)
  selectedKeys.value = [e.key]
  router.push('/' + e.key)
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
  margin: 4px 8px;
  border-radius: 8px;
  height: 40px;
  line-height: 40px;
}

.app-sider :deep(.ant-menu-item-selected) {
  background: rgba(24, 144, 255, 0.2) !important;
}

.app-sider :deep(.ant-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
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

.trigger:hover {
  color: #1890ff;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.3s;
}

.user-info:hover {
  background: #f5f5f5;
}

.username {
  font-size: 14px;
  color: #333;
}

.app-content {
  margin: 16px;
  padding: 24px;
  background: #f0f2f5;
  min-height: calc(100vh - 64px - 32px);
  border-radius: 8px;
}
</style>
