<template>
  <div v-if="project">
    <a-descriptions title="项目详情" bordered :column="2">
      <a-descriptions-item label="ID">{{ project.id }}</a-descriptions-item>
      <a-descriptions-item label="名称">{{ project.name }}</a-descriptions-item>
      <a-descriptions-item label="Jar包名">{{ project.jarName }}</a-descriptions-item>
      <a-descriptions-item label="JVM参数">{{ project.jvmOpts }}</a-descriptions-item>
      <a-descriptions-item label="启动脚本" :span="2">
        <pre style="margin: 0; white-space: pre-wrap">{{ project.startScript }}</pre>
      </a-descriptions-item>
      <a-descriptions-item label="节点ID" :span="2">{{ project.nodeIds }}</a-descriptions-item>
      <a-descriptions-item label="创建时间">{{ project.createTime }}</a-descriptions-item>
    </a-descriptions>
    <a-space style="margin-top: 16px">
      <a-button @click="$router.push('/projects')">返回列表</a-button>
      <a-button type="primary" @click="$router.push(`/projects/${project.id}/edit`)">编辑</a-button>
    </a-space>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import type { ProjectModel } from '../types'
import { getProject } from '../api/project'

const route = useRoute()
const project = ref<ProjectModel>()

onMounted(async () => {
  const id = route.params.id as string
  const res = await getProject(id)
  project.value = res.data
})
</script>

<style scoped>
pre {
  background: #f5f5f5;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
}
</style>
