<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <code-outlined style="color: #13c2c2" />
          <span style="font-weight: 600">控制台</span>
          <a-badge v-if="connected" status="success" text="已连接" />
          <a-badge v-else status="default" text="未连接" />
        </a-space>
      </template>

      <a-space style="margin-bottom: 16px">
        <a-select v-model:value="selectedProject" style="width: 200px" @change="onProjectChange" placeholder="选择项目">
          <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.name }}
          </a-select-option>
        </a-select>
        <a-select v-model:value="selectedNode" style="width: 200px" placeholder="选择节点">
          <a-select-option v-for="n in nodes" :key="n.id" :value="n.id">
            {{ n.name }} ({{ n.ip }})
          </a-select-option>
        </a-select>
        <a-button @click="connectWS" :loading="connecting" :disabled="connected" type="primary">
          <link-outlined /> 连接
        </a-button>
        <a-button @click="disconnectWS" :disabled="!connected" danger>
          <disconnect-outlined /> 断开
        </a-button>
        <a-button @click="clearLogs">
          <clear-outlined /> 清空
        </a-button>
      </a-space>

      <div ref="consoleRef" class="console-output">
        <div v-for="(line, i) in logs" :key="i" class="log-line">{{ line }}</div>
        <div v-if="logs.length === 0" style="color: #595959; text-align: center; padding: 40px">
          等待连接...
        </div>
        <div ref="bottomRef"></div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import type { ProjectModel, NodeModel } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
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
const connected = ref(false)
const logs = ref<string[]>([])
const consoleRef = ref<HTMLDivElement>()
const bottomRef = ref<HTMLDivElement>()

let ws: WebSocket | null = null

async function fetchData() {
  const [pRes, nRes] = await Promise.all([getProjects(), getNodes()])
  projects.value = pRes.data.list
  nodes.value = nRes.data.list
}

function onProjectChange() {
  selectedNode.value = ''
}

function connectWS() {
  if (!selectedProject.value || !selectedNode.value) return
  connecting.value = true
  const wsUrl = `/api/ws/console?projectId=${selectedProject.value}&nodeId=${selectedNode.value}`
  ws = new WebSocket(`ws://${location.host}${wsUrl}`)

  ws.onopen = () => {
    connected.value = true
    connecting.value = false
    logs.value = []
  }

  ws.onmessage = (event) => {
    logs.value.push(event.data)
    nextTick(() => {
      if (consoleRef.value) {
        consoleRef.value.scrollTop = consoleRef.value.scrollHeight
      }
    })
  }

  ws.onclose = () => {
    connected.value = false
  }

  ws.onerror = () => {
    connected.value = false
    connecting.value = false
  }
}

function disconnectWS() {
  if (ws) {
    ws.close()
    ws = null
  }
}

function clearLogs() {
  logs.value = []
}

onMounted(fetchData)
onUnmounted(() => disconnectWS())
</script>

<style scoped>
.console-output {
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'JetBrains Mono', 'Fira Code', 'Courier New', monospace;
  font-size: 13px;
  height: 500px;
  overflow-y: auto;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #333;
}
.log-line {
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>
