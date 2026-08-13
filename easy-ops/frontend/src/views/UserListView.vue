<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <team-outlined style="color: #2f54eb" />
          <span style="font-weight: 600">用户管理</span>
        </a-space>
      </template>
      <template #extra>
        <a-button v-if="isAdmin" type="primary" @click="$router.push('/users/add')">
          <plus-outlined /> 新增用户
        </a-button>
      </template>

      <a-table
        :columns="columns"
        :data-source="users"
        :loading="loading"
        :pagination="false"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-badge :status="record.status === 1 ? 'success' : 'default'"
                     :text="record.status === 1 ? '启用' : '禁用'" />
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button v-if="isAdmin || record.id === authStore.user?.id" type="link" size="small" @click="editUser(record)">
                <edit-outlined /> 编辑
              </a-button>
              <a-popconfirm v-if="isAdmin" title="确定删除?" ok-text="确定" cancel-text="取消" @confirm="deleteUserAction(record.id)">
                <a-button type="link" size="small" danger>
                  <delete-outlined /> 删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import type { UserModel } from '../types'
import { getUsers, deleteUser } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  TeamOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isSuperAdmin)
const users = ref<UserModel[]>([])
const loading = ref(false)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80, sorter: (a: any, b: any) => a.id - b.id },
  { title: '用户名', dataIndex: 'username', key: 'username', sorter: (a: any, b: any) => (a.username || '').localeCompare(b.username || '') },
  { title: '平台角色', dataIndex: 'role', key: 'role', width: 110, sorter: (a: any, b: any) => (a.role || '').localeCompare(b.role || '') },
  { title: '所属租户', dataIndex: 'tenantName', key: 'tenantName', width: 150 },
  { title: '租户角色', dataIndex: 'tenantRole', key: 'tenantRole', width: 120 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100, sorter: (a: any, b: any) => (a.status || 0) - (b.status || 0) },
  { title: '操作', key: 'action', width: 160, fixed: 'right' as const }
]

async function fetchUsers() {
  try {
    loading.value = true
    const res = await getUsers()
    users.value = res.data.list
  } finally {
    loading.value = false
  }
}

function editUser(record: UserModel) {
  router.push(`/users/${record.id}/edit`)
}

async function deleteUserAction(id: number) {
  await deleteUser(id)
  fetchUsers()
}

onMounted(fetchUsers)
</script>
