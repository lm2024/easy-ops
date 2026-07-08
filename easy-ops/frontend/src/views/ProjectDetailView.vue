<template>
  <div v-if="project">
    <a-card :bordered="false" style="border-radius: 8px; max-width: 860px">
      <template #title>
        <a-space>
          <folder-open-outlined style="color: #722ed1" />
          <span style="font-weight: 600">应用详情</span>
        </a-space>
      </template>

      <!-- 基本信息 -->
      <a-descriptions title="📋 基本信息" bordered size="small" :column="3" style="margin-bottom: 16px">
        <a-descriptions-item label="项目名称" :span="1">
          <span style="font-weight: 600">{{ project.name }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="Jar 包名" :span="1">
          <a-tag color="blue">{{ project.jarName || '-' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="部署节点" :span="1">
          <a-space wrap>
            <a-tag v-for="name in nodeNames" :key="name" color="blue">{{ name }}</a-tag>
            <template v-if="!project.nodeIds">-</template>
          </a-space>
        </a-descriptions-item>
        <a-descriptions-item label="环境变量" :span="3">
          <code style="background: #f5f5f5; padding: 2px 8px; border-radius: 4px; font-size: 12px">{{ project.envVars || '-' }}</code>
        </a-descriptions-item>
      </a-descriptions>

      <!-- JVM 参数 -->
      <a-descriptions title="⚙️ JVM 参数" bordered size="small" :column="1" style="margin-bottom: 16px">
        <a-descriptions-item label="JVM 参数（G1GC）">
          <pre v-if="project.jvmOpts" style="margin:0; white-space:pre-wrap; background:#f5f5f5; padding:10px; border-radius:4px; font-size:12px; font-family:'JetBrains Mono',monospace; line-height:1.6">{{ project.jvmOpts }}</pre>
          <span v-else>-</span>
        </a-descriptions-item>
      </a-descriptions>

      <!-- 脚本 -->
      <a-descriptions title="📜 部署脚本" bordered size="small" :column="3" style="margin-bottom: 16px">
        <a-descriptions-item label="启动脚本" :span="3">
          <pre v-if="project.startScript" style="margin:0; white-space:pre-wrap; background:#f6ffed; padding:10px; border-radius:4px; font-size:12px; font-family:'JetBrains Mono',monospace; line-height:1.5; border-left:3px solid #52c41a">{{ project.startScript }}</pre>
          <span v-else style="color:#bbb">未配置</span>
        </a-descriptions-item>
        <a-descriptions-item label="停止脚本" :span="3">
          <pre v-if="project.stopScript" style="margin:0; white-space:pre-wrap; background:#fff2f0; padding:10px; border-radius:4px; font-size:12px; font-family:'JetBrains Mono',monospace; line-height:1.5; border-left:3px solid #ff4d4f">{{ project.stopScript }}</pre>
          <span v-else style="color:#bbb">未配置</span>
        </a-descriptions-item>
        <a-descriptions-item label="重启脚本" :span="3">
          <pre v-if="project.restartScript" style="margin:0; white-space:pre-wrap; background:#fffbe6; padding:10px; border-radius:4px; font-size:12px; font-family:'JetBrains Mono',monospace; line-height:1.5; border-left:3px solid #faad14">{{ project.restartScript }}</pre>
          <span v-else style="color:#bbb">未配置</span>
        </a-descriptions-item>
      </a-descriptions>

      <a-space>
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
import { getNodes } from '../api/node'
import {
  FolderOpenOutlined,
  ArrowLeftOutlined,
  EditOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const project = ref<ProjectModel>()
const nodeNames = ref<string[]>([])

function resolveNodeNames(nodeIds?: string) {
  if (!nodeIds) return []
  return nodeIds
    .split(',')
    .map(id => id.trim())
    .filter(Boolean)
}

onMounted(async () => {
  const id = route.params.id as string
  const res = await getProject(id)
  project.value = res.data

  // 解析节点名
  const ids = resolveNodeNames(res.data.nodeIds)
  if (ids.length > 0) {
    try {
      const nodeRes = await getNodes(1, 1000)
      const map = new Map<string, string>()
      nodeRes.data.list.forEach((n: any) => map.set(String(n.id), n.name))
      nodeNames.value = ids.map(id => map.get(id) || id)
    } catch {
      nodeNames.value = ids
    }
  }
})
</script>
