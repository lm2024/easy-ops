<template>
  <div>
    <a-card :bordered="false" class="console-card">
      <template #title>
        <a-space>
          <code-outlined class="title-icon" />
          <span style="font-weight: 600">远程控制台</span>
          <a-badge v-if="connected" status="success" text="已连接" />
          <a-badge v-else status="default" text="未连接" />
          <a-tag v-if="connected" color="blue">{{ nodeName }} : {{ shortCwd }}</a-tag>
        </a-space>
      </template>

      <template #extra>
        <a-space>
          <a-tooltip title="Ctrl+L 清屏">
            <a-button size="small" :disabled="!connected" @click="clearScreen">
              <clear-outlined /> 清屏
            </a-button>
          </a-tooltip>
        </a-space>
      </template>

      <a-row :gutter="12" class="toolbar">
        <a-col :xs="24" :md="8">
          <a-select
            v-model:value="selectedProject"
            style="width: 100%"
            placeholder="选择项目"
            @change="onProjectChange"
          >
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :md="8">
          <a-select v-model:value="selectedNode" style="width: 100%" placeholder="选择节点">
            <a-select-option v-for="n in nodes" :key="n.id" :value="n.id">
              {{ n.name }} ({{ n.ip }})
            </a-select-option>
          </a-select>
        </a-col>
        <a-col :xs="24" :md="8">
          <a-space>
            <a-button
              type="primary"
              :danger="connected"
              :loading="connecting"
              :disabled="!selectedProject || !selectedNode"
              @click="toggleConnect"
            >
              <template v-if="connected"><disconnect-outlined /> 断开</template>
              <template v-else><link-outlined /> 连接</template>
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <div ref="terminalRef" class="terminal-container" :class="{ connected }" @click="focusTerminal" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import type { ProjectModel, NodeModel } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { useConsoleTerminal } from '../composables/useConsoleTerminal'
import {
  CodeOutlined,
  LinkOutlined,
  DisconnectOutlined,
  ClearOutlined
} from '@ant-design/icons-vue'

const projects = ref<ProjectModel[]>([])
const nodes = ref<NodeModel[]>([])
const selectedProject = ref('')
const selectedNode = ref('')
const connecting = ref(false)
const terminalRef = ref<HTMLDivElement>()

let ws: WebSocket | null = null

const {
  connected,
  cwd,
  nodeName,
  init,
  fit,
  focus,
  clearScreen,
  setConnected,
  handleServerMessage
} = useConsoleTerminal(terminalRef, {
  onExec: (command) => sendJson({ type: 'exec', command }),
  onComplete: (line, cursor) => sendJson({ type: 'complete', line, cursor })
})

const shortCwd = computed(() => {
  const path = cwd.value
  if (path.startsWith('/home/')) {
    const idx = path.indexOf('/', 6)
    return idx === -1 ? '~' : '~' + path.slice(idx)
  }
  return path.length > 24 ? '…' + path.slice(-22) : path
})

async function fetchData() {
  const [pRes, nRes] = await Promise.all([getProjects(), getNodes()])
  projects.value = pRes.data.list
  nodes.value = nRes.data.list
}

function onProjectChange() {
  selectedNode.value = ''
}

function sendJson(payload: Record<string, unknown>) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload))
  }
}

function toggleConnect() {
  if (connected.value) {
    disconnectWS()
  } else {
    connectWS()
  }
}

function connectWS() {
  if (!selectedProject.value || !selectedNode.value) return
  const token = localStorage.getItem('token')
  if (!token) {
    handleServerMessage(JSON.stringify({ type: 'error', text: '未登录或登录已过期，请重新登录' }))
    return
  }

  connecting.value = true
  const params = new URLSearchParams({
    projectId: String(selectedProject.value),
    nodeId: String(selectedNode.value),
    token
  })
  const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${wsProtocol}//${location.host}/api/ws/console?${params}`)

  ws.onopen = () => {
    connecting.value = false
    setConnected(true)
    nextTick(() => {
      fit()
      focus()
    })
  }

  ws.onmessage = (event) => {
    handleServerMessage(String(event.data))
  }

  ws.onclose = () => {
    setConnected(false)
    connecting.value = false
    handleServerMessage(JSON.stringify({ type: 'error', text: '连接已断开' }))
  }

  ws.onerror = () => {
    setConnected(false)
    connecting.value = false
    handleServerMessage(JSON.stringify({ type: 'error', text: '连接失败，请检查节点是否在线' }))
  }
}

function disconnectWS() {
  ws?.close()
  ws = null
  setConnected(false)
}

function focusTerminal() {
  focus()
}

onMounted(async () => {
  await fetchData()
  nextTick(() => {
    init()
    fit()
  })
})
</script>

<style scoped>
.console-card {
  border-radius: 10px;
}

.title-icon {
  color: #13c2c2;
}

.toolbar {
  margin-bottom: 12px;
}

.terminal-container {
  width: 100%;
  height: min(68vh, 620px);
  min-height: 420px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--eo-border);
  background: #0d1117;
  padding: 4px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.terminal-container.connected {
  border-color: rgba(110, 231, 183, 0.45);
  box-shadow: 0 0 0 1px rgba(110, 231, 183, 0.15);
}

.terminal-container :deep(.xterm) {
  height: 100%;
}

.terminal-container :deep(.xterm-viewport) {
  overflow-y: auto !important;
}
</style>
