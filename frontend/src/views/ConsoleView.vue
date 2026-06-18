<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-select v-model:value="selectedProject" style="width: 200px" @change="onProjectChange">
        <a-select-option v-for="p in projects" :key="p.id" :value="p.id">
          {{ p.name }}
        </a-select-option>
      </a-select>
      <a-select v-model:value="selectedNode" style="width: 150px">
        <a-select-option v-for="n in nodes" :key="n.id" :value="n.id">
          {{ n.name }}
        </a-select-option>
      </a-select>
      <a-button @click="connectWS" :loading="connecting">连接</a-button>
      <a-button @click="disconnectWS" :disabled="connected">断开</a-button>
    </a-space>

    <div ref="consoleRef" class="console-output">
      <div v-for="(line, i) in logs" :key="i" class="log-line">{{ line }}</div>
      <div ref="bottomRef"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import type { ProjectModel, NodeModel } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'

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
  projects.value = pRes.data
  nodes.value = nRes.data
}

function onProjectChange() {
  selectedNode.value = ''
}

function connectWS() {
  if (!selectedProject.value || !selectedNode.value) return
  connecting.value = true
  const token = localStorage.getItem('token')
  const wsUrl = `/api/ws/console?projectId=${selectedProject.value}&nodeId=${selectedNode.value}`
  ws = new WebSocket(`ws://${location.host}${wsUrl}`)

  ws.onopen = () => {
    connected.value = true
    connecting.value = false
    logs.value = []
  }

  ws.onmessage = (event) => {
    logs.value.push(event.data)
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

onUnmounted(() => disconnectWS())

onMounted(fetchData)
</script>

<style scoped>
.console-output {
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  height: 500px;
  overflow-y: auto;
  padding: 12px;
  border-radius: 4px;
}
.log-line {
  white-space: pre-wrap;
  line-height: 1.5;
}
</style>
