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
        <a-button type="primary" @click="$router.push('/users/add')">
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
          <template v-if="column.key === 'role'">
            <a-tag :color="record.role === 'admin' ? 'red' : 'blue'">
              {{ record.role === 'admin' ? '管理员' : '操作员' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'status'">
            <a-badge :status="record.status === 1 ? 'success' : 'default'"
                     :text="record.status === 1 ? '启用' : '禁用'" />
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="editUser(record)">
                <edit-outlined /> 编辑
              </a-button>
              <a-popconfirm title="确定删除?" ok-text="确定" cancel-text="取消" @confirm="deleteUserAction(record.id)">
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { UserModel } from '../types'
import { getUsers, deleteUser } from '../api/auth'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  TeamOutlined
} from '@ant-design/icons-vue'

const router = useRouter()
const users = ref<UserModel[]>([])
const loading = ref(false)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '角色', dataIndex: 'role', key: 'role', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
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
