<template>
  <div v-if="project">
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <folder-open-outlined style="color: #722ed1" />
          <span style="font-weight: 600">项目详情</span>
        </a-space>
      </template>

      <a-descriptions bordered :column="2">
        <a-descriptions-item label="ID">{{ project.id }}</a-descriptions-item>
        <a-descriptions-item label="名称">
          <span style="font-weight: 500">{{ project.name }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="节点ID">
          <a-tag color="blue">{{ project.nodeIds || '-' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="JVM参数">
          <code style="background: #f5f5f5; padding: 2px 6px; border-radius: 4px">{{ project.jvmOpts || '-' }}</code>
        </a-descriptions-item>
        <a-descriptions-item label="启动脚本" :span="2">
          <pre style="margin: 0; white-space: pre-wrap; background: #f5f5f5; padding: 8px; border-radius: 4px; font-size: 12px">{{ project.startScript || '-' }}</pre>
        </a-descriptions-item>
      </a-descriptions>

      <a-space style="margin-top: 24px">
        <a-button @click="$router.push('/projects')">
          <arrow-left-outlined /> 返回列表
        </a-button>
        <a-button type="primary" @click="$router.push(`/projects/${project.id}/edit`)">
          <edit-outlined /> 编辑
        </a-button>
      </a-space>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import type { ProjectModel } from '../types'
import { getProject } from '../api/project'
import {
  FolderOpenOutlined,
  ArrowLeftOutlined,
  EditOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const project = ref<ProjectModel>()

onMounted(async () => {
  const id = route.params.id as string
  const res = await getProject(id)
  project.value = res.data
})
</script>
