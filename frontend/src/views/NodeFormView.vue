<template>
  <a-card :bordered="false" style="border-radius: 8px; max-width: 700px">
    <template #title>
      <a-space>
        <cluster-outlined style="color: #1890ff" />
        <span style="font-weight: 600">{{ isEdit ? '编辑节点' : '新增节点' }}</span>
      </a-space>
    </template>

    <a-form
      ref="formRef"
      :model="formState"
      :rules="rules"
      layout="vertical"
      @finish="handleSubmit"
    >
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="节点名称" name="name">
            <a-input v-model:value="formState.name" placeholder="请输入节点名称" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="IP地址" name="ip">
            <a-input v-model:value="formState.ip" placeholder="192.168.1.100" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="端口" name="port">
            <a-input-number v-model:value="formState.port" style="width: 100%" :min="1" :max="65535" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="Token" name="token">
            <a-input v-model:value="formState.token" placeholder="Agent认证Token" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="loading">
            <save-outlined /> 保存
          </a-button>
          <a-button @click="$router.back()">取消</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </a-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { NodeModel } from '../types'
import { createNode, updateNode, getNode } from '../api/node'
import type { FormInstance, Rule } from 'ant-design-vue'
import {
  SaveOutlined,
  ClusterOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = computed(() => !!route.params.id)

const formState = ref<Partial<NodeModel>>({
  name: '',
  ip: '',
  port: 2123,
  token: ''
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入节点名称' }],
  ip: [{ required: true, message: '请输入IP地址' }]
}

async function handleSubmit() {
  try {
    loading.value = true
    const id = route.params.id as string
    if (id) {
      await updateNode(id, formState.value as NodeModel)
    } else {
      await createNode(formState.value as NodeModel)
    }
    router.push('/nodes')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const id = route.params.id as string
  if (id) {
    const res = await getNode(id)
    formState.value = res.data
  }
})
</script>
