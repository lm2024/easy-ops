import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue')
  },
  {
    path: '/knowledge/share/:token',
    name: 'KnowledgeShare',
    component: () => import('../views/knowledge/ShareView.vue')
  },
  {
    path: '/',
    component: () => import('../components/MainLayout.vue'),
    redirect: '/nodes',
    children: [
      { path: 'nodes', name: 'Nodes', component: () => import('../views/NodeListView.vue') },
      { path: 'nodes/add', name: 'AddNode', component: () => import('../views/NodeFormView.vue') },
      { path: 'nodes/:id/edit', name: 'EditNode', component: () => import('../views/NodeFormView.vue') },
      { path: 'projects', name: 'Projects', component: () => import('../views/ProjectListView.vue') },
      { path: 'projects/add', name: 'AddProject', component: () => import('../views/ProjectFormView.vue') },
      { path: 'projects/:id/edit', name: 'EditProject', component: () => import('../views/ProjectFormView.vue') },
      { path: 'projects/:id', name: 'ProjectDetail', component: () => import('../views/ProjectDetailView.vue') },
      { path: 'versions', name: 'Versions', component: () => import('../views/VersionListView.vue') },
      { path: 'deploy', name: 'Deploy', component: () => import('../views/DeployListView.vue') },
      { path: 'deploy/detail/:id', name: 'DeployDetail', component: () => import('../views/DeployDetailView.vue') },
      { path: 'console', name: 'Console', component: () => import('../views/ConsoleView.vue') },
      { path: 'logs', redirect: '/log-manage' },
      { path: 'log-manage', name: 'LogManage', component: () => import('../views/LogManageView.vue') },
      { path: 'config-editor', redirect: '/config-manage' },
      { path: 'config-manage', name: 'ConfigManage', component: () => import('../views/ConfigManageView.vue') },
      { path: 'app-monitor', name: 'AppMonitor', component: () => import('../views/AppMonitorView.vue') },
      { path: 'knowledge', name: 'Knowledge', component: () => import('../views/KnowledgeView.vue') },
      { path: 'self-heal', name: 'SelfHeal', component: () => import('../views/SelfHealPolicyView.vue') },
      { path: 'monitor', name: 'Dashboard', component: () => import('../views/DashboardView.vue') },
      { path: 'alarms', name: 'Alarms', component: () => import('../views/AlarmListView.vue') },
      { path: 'alarm-config', name: 'AlarmConfig', component: () => import('../views/AlarmConfigView.vue') },
      { path: 'users', name: 'Users', component: () => import('../views/UserListView.vue') },
      { path: 'users/add', name: 'AddUser', component: () => import('../views/UserFormView.vue') },
      { path: 'users/:id/edit', name: 'EditUser', component: () => import('../views/UserFormView.vue') },
      { path: 'operations', name: 'Operations', component: () => import('../views/OperationLogView.vue') },
      { path: 'batch-download', name: 'BatchDownload', component: () => import('../views/BatchDownloadView.vue') },
      { path: 'ai-config', name: 'AIConfig', component: () => import('../views/AIConfigView.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !to.path.startsWith('/knowledge/share') && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
