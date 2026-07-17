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
          <code class="eo-code">{{ project.envVars || '-' }}</code>
        </a-descriptions-item>
      </a-descriptions>

      <!-- JVM 参数 -->
      <a-descriptions title="⚙️ JVM 参数" bordered size="small" :column="1" style="margin-bottom: 16px">
        <a-descriptions-item label="JVM 参数（G1GC）">
          <pre v-if="project.jvmOpts" class="eo-code">{{ project.jvmOpts }}</pre>
          <span v-else>-</span>
        </a-descriptions-item>
      </a-descriptions>

      <!-- 脚本 -->
      <a-descriptions title="📜 部署脚本" bordered size="small" :column="3" style="margin-bottom: 16px">
        <a-descriptions-item label="启动脚本" :span="3">
          <pre v-if="project.startScript" class="eo-code eo-code--success">{{ project.startScript }}</pre>
          <span v-else class="eo-text-muted">未配置</span>
        </a-descriptions-item>
        <a-descriptions-item label="停止脚本" :span="3">
          <pre v-if="project.stopScript" class="eo-code eo-code--danger">{{ project.stopScript }}</pre>
          <span v-else class="eo-text-muted">未配置</span>
        </a-descriptions-item>
        <a-descriptions-item label="重启脚本" :span="3">
          <pre v-if="project.restartScript" class="eo-code eo-code--warn">{{ project.restartScript }}</pre>
          <span v-else class="eo-text-muted">未配置</span>
        </a-descriptions-item>
      </a-descriptions>

      <!-- 节点操作 -->
      <a-card title="🖥️ 节点操作" size="small" style="margin-bottom: 16px">
        <a-table
          :data-source="nodeList"
          :columns="nodeColumns"
          :pagination="false"
          size="small"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-badge
                :status="record.status === 1 ? 'success' : 'default'"
                :text="record.status === 1 ? '在线' : '离线'"
              />
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a-popconfirm
                  :title="`确认启动 ${record.name}？`"
                  @confirm="doOperate(record.id, 'start')"
                >
                  <a-button type="primary" size="small" :loading="operatingId === record.id + '-start'">
                    <play-circle-outlined /> 启动
                  </a-button>
                </a-popconfirm>
                <a-popconfirm
                  :title="`确认停止 ${record.name}？`"
                  ok-text="停止"
                  ok-type="danger"
                  @confirm="doOperate(record.id, 'stop')"
                >
                  <a-button danger size="small" :loading="operatingId === record.id + '-stop'">
                    <pause-circle-outlined /> 停止
                  </a-button>
                </a-popconfirm>
                <a-popconfirm
                  :title="`确认重启 ${record.name}？`"
                  ok-text="重启"
                  ok-type="primary"
                  @confirm="doOperate(record.id, 'restart')"
                >
                  <a-button type="primary" ghost size="small" :loading="operatingId === record.id + '-restart'">
                    <reload-outlined /> 重启
                  </a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

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
import { message } from 'ant-design-vue'
import type { ProjectModel, NodeModel } from '../types'
import { getProject, operateProjectNode } from '../api/project'
import { getNodes } from '../api/node'
import {
  FolderOpenOutlined,
  ArrowLeftOutlined,
  EditOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const project = ref<ProjectModel>()
const nodeNames = ref<string[]>([])
const nodeList = ref<NodeModel[]>([])
const operatingId = ref<string>('')

const nodeColumns = [
  { title: '节点名称', dataIndex: 'name', key: 'name' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 280 }
]

function resolveNodeNames(nodeIds?: string) {
  if (!nodeIds) return []
  return nodeIds
    .split(',')
    .map(id => id.trim())
    .filter(Boolean)
}

async function doOperate(nodeId: number, action: 'start' | 'stop' | 'restart') {
  const id = route.params.id as string
  const actionLabel = { start: '启动', stop: '停止', restart: '重启' }[action]
  operatingId.value = `${nodeId}-${action}`
  try {
    await operateProjectNode(id, String(nodeId), action)
    message.success(`${actionLabel}指令已发送`)
  } catch (e: any) {
    message.error(`${actionLabel}失败: ${e?.message || '未知错误'}`)
  } finally {
    operatingId.value = ''
  }
}

onMounted(async () => {
  const id = route.params.id as string
  const res = await getProject(id)
  project.value = res.data

  const ids = resolveNodeNames(res.data.nodeIds)
  if (ids.length > 0) {
    try {
      const nodeRes = await getNodes(1, 1000)
      const allNodes: NodeModel[] = nodeRes.data.list
      const map = new Map<string, string>()
      allNodes.forEach((n: any) => map.set(String(n.id), n.name))
      nodeNames.value = ids.map(id => map.get(id) || id)
      // 按 nodeIds 顺序筛选出属于该项目的节点
      nodeList.value = ids
        .map(id => allNodes.find((n: any) => String(n.id) === id))
        .filter(Boolean) as NodeModel[]
    } catch {
      nodeNames.value = ids
    }
  }
})
</script>
