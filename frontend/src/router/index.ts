import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    component: () => import('../components/MainLayout.vue'),
    redirect: '/nodes',
    children: [
      { path: 'nodes', name: 'Nodes', component: () => import('../views/NodeList.vue') },
      { path: 'nodes/add', name: 'AddNode', component: () => import('../views/NodeForm.vue') },
      { path: 'nodes/:id/edit', name: 'EditNode', component: () => import('../views/NodeForm.vue') },
      { path: 'projects', name: 'Projects', component: () => import('../views/ProjectList.vue') },
      { path: 'projects/add', name: 'AddProject', component: () => import('../views/ProjectForm.vue') },
      { path: 'projects/:id', name: 'ProjectDetail', component: () => import('../views/ProjectDetail.vue') },
      { path: 'versions', name: 'Versions', component: () => import('../views/VersionList.vue') },
      { path: 'deploy', name: 'Deploy', component: () => import('../views/DeployList.vue') },
      { path: 'deploy/detail/:id', name: 'DeployDetail', component: () => import('../views/DeployDetail.vue') },
      { path: 'console', name: 'Console', component: () => import('../views/ConsoleView.vue') },
      { path: 'logs', name: 'FileLogs', component: () => import('../views/FileLogView.vue') },
      { path: 'config-editor', name: 'ConfigEditor', component: () => import('../views/ConfigEditor.vue') },
      { path: 'monitor', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'alarms', name: 'Alarms', component: () => import('../views/AlarmList.vue') },
      { path: 'alarm-config', name: 'AlarmConfig', component: () => import('../views/AlarmConfigView.vue') },
      { path: 'users', name: 'Users', component: () => import('../views/UserList.vue') },
      { path: 'users/add', name: 'AddUser', component: () => import('../views/UserForm.vue') },
      { path: 'users/:id/edit', name: 'EditUser', component: () => import('../views/UserForm.vue') },
      { path: 'operations', name: 'Operations', component: () => import('../views/OperationLogView.vue') },
      { path: 'batch-download', name: 'BatchDownload', component: () => import('../views/BatchDownloadView.vue') }
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
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
