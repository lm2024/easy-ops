<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-button type="primary" @click="$router.push('/users/add')">
        新增用户
      </a-button>
    </a-space>

    <a-table
      :columns="columns"
      :data-source="users"
      :loading="loading"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'role'">
          <a-tag :color="record.role === 'ADMIN' ? 'red' : 'blue'">
            {{ record.role === 'ADMIN' ? '管理员' : '操作员' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-switch
            checked-children="启用"
            un-checked-children="禁用"
            :checked="record.status === 1"
            @change="toggleStatus(record)"
          />
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="editUser(record)">编辑</a-button>
            <a-popconfirm title="确定删除?" @confirm="deleteUserAction(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { UserModel } from '../types'
import { getUsers, deleteUser } from '../api/auth'

const router = useRouter()
const users = ref<UserModel[]>([])
const loading = ref(false)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action' }
]

async function fetchUsers() {
  try {
    loading.value = true
    const res = await getUsers()
    users.value = res.data
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

async function toggleStatus(record: UserModel) {
  const newStatus = record.status === 1 ? 0 : 1
  record.status = newStatus
  // Update via API (placeholder)
}

onMounted(fetchUsers)
</script>
