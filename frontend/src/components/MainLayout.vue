<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider
      v-model:collapsed="appStore.sidebarCollapsed"
      collapsible
      :width="220"
      theme="dark"
    >
      <div class="logo">
        <h2 style="color: white; text-align: center; margin: 16px 0">
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
          <span>节点管理</span>
        </a-menu-item>
        <a-menu-item key="projects">
          <span>项目管理</span>
        </a-menu-item>
        <a-menu-item key="versions">
          <span>版本管理</span>
        </a-menu-item>
        <a-sub-menu>
          <template #title><span>部署运维</span></template>
          <a-menu-item key="deploy">部署记录</a-menu-item>
          <a-menu-item key="console">控制台</a-menu-item>
        </a-sub-menu>
        <a-sub-menu>
          <template #title><span>文件管理</span></template>
          <a-menu-item key="FileLogs">日志查看</a-menu-item>
          <a-menu-item key="ConfigEditor">配置编辑</a-menu-item>
          <a-menu-item key="BatchDownload">批量下载</a-menu-item>
        </a-sub-menu>
        <a-sub-menu>
          <template #title><span>监控告警</span></template>
          <a-menu-item key="Dashboard">仪表盘</a-menu-item>
          <a-menu-item key="Alarms">告警中心</a-menu-item>
          <a-menu-item key="AlarmConfig">告警配置</a-menu-item>
        </a-sub-menu>
        <a-sub-menu>
          <template #title><span>系统管理</span></template>
          <a-menu-item key="Users">用户管理</a-menu-item>
          <a-menu-item key="Operations">操作审计</a-menu-item>
        </a-sub-menu>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header style="background: #fff; padding: 0 16px">
        <a-row justify="space-between">
          <a-col>
            <menu-unfold-outlined
              v-if="appStore.sidebarCollapsed"
              style="font-size: 18px; cursor: pointer"
              @click="appStore.toggleSidebar"
            />
            <menu-fold-outlined
              v-else
              style="font-size: 18px; cursor: pointer"
              @click="appStore.toggleSidebar"
            />
          </a-col>
          <a-col>
            <a-dropdown>
              <a class="ant-dropdown-link" @click.prevent>
                {{ authStore.user?.username || 'Admin' }}
                <down-outlined />
              </a>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="handleLogout">
                    <span>退出登录</span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-col>
        </a-row>
      </a-layout-header>
      <a-layout-content style="margin: 16px; padding: 24px; background: #fff">
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
import { MenuUnfoldOutlined, MenuFoldOutlined, DownOutlined } from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

const selectedKeys = ref([route.name || 'nodes'])

function handleMenuSelect(e: any) {
  appStore.setMenu(e.key)
  selectedKeys.value = [e.key]
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.logo {
  height: 32px;
  margin: 16px;
  text-align: center;
}
</style>
