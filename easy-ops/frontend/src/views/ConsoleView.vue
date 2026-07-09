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

      <a-space style="margin-bottom: 12px">
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
        <a-button @click="toggleConnect" :loading="connecting" type="primary" :danger="connected">
          <template v-if="connected"><disconnect-outlined /> 断开</template>
          <template v-else><link-outlined /> 连接</template>
        </a-button>
        <a-button @click="clearLogs">
          <clear-outlined /> 清空
        </a-button>
      </a-space>

      <!-- xterm.js 终端输出区域 -->
      <div ref="terminalRef" class="terminal-container"></div>

      <!-- 命令输入栏 -->
      <div class="input-bar" :class="{ connected }">
        <span class="prompt">$</span>
        <input
          ref="inputRef"
          v-model="commandInput"
          class="cmd-input"
          type="text"
          placeholder="输入命令，按 Enter 执行..."
          :disabled="!connected"
          @keydown.enter.prevent="sendCommand"
        />
        <a-button size="small" type="primary" :disabled="!connected || !commandInput.trim()" @click="sendCommand">
          执行
        </a-button>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import type { ProjectModel, NodeModel } from '../types'
import { getProjects } from '../api/project'
import { getNodes } from '../api/node'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'
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
const commandInput = ref('')
const terminalRef = ref<HTMLDivElement>()
const inputRef = ref<HTMLInputElement>()

let ws: WebSocket | null = null
let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null

async function fetchData() {
  const [pRes, nRes] = await Promise.all([getProjects(), getNodes()])
  projects.value = pRes.data.list
  nodes.value = nRes.data.list
}

function onProjectChange() {
  selectedNode.value = ''
}

function initTerminal() {
  if (!terminalRef.value) return

  const termTheme: any = {
    background: '#1e1e1e',
    foreground: '#d4d4d4',
    cursor: '#d4d4d4',
    selection: '#264f78',
    black: '#000000',
    red: '#f44747',
    green: '#6a9955',
    yellow: '#d7ba7d',
    blue: '#569cd6',
    magenta: '#c586c0',
    cyan: '#4ec9b0',
    white: '#d4d4d4',
    brightBlack: '#808080',
    brightRed: '#f44747',
    brightGreen: '#6a9955',
    brightYellow: '#d7ba7d',
    brightBlue: '#569cd6',
    brightMagenta: '#c586c0',
    brightCyan: '#4ec9b0',
    brightWhite: '#ffffff'
  }

  terminal = new Terminal({
    cursorBlink: false,
    cursorStyle: 'block',
    fontSize: 14,
    fontFamily: "'JetBrains Mono', 'Fira Code', 'Courier New', monospace",
    theme: termTheme,
    disableStdin: true,   // 输入由下方 input 栏处理
    convertEol: true
  })

  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)

  terminal.open(terminalRef.value)
  fitAddon.fit()

  // 窗口大小变化时自适应
  window.addEventListener('resize', handleResize)

  // 初始提示
  terminal.writeln('\x1b[33m┌─────────────────────────────────────────────┐\x1b[0m')
  terminal.writeln('\x1b[33m│  选择一个项目和节点，点击连接后输入命令    │\x1b[0m')
  terminal.writeln('\x1b[33m└─────────────────────────────────────────────┘\x1b[0m')
}

function handleResize() {
  if (fitAddon) {
    try { fitAddon.fit() } catch (_) { }
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
    if (terminal) terminal.writeln('\r\n\x1b[31m[连接失败] 未登录或登录已过期，请重新登录\x1b[0m')
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
    connected.value = true
    connecting.value = false
    commandInput.value = ''
    if (terminal) terminal.clear()
    nextTick(() => inputRef.value?.focus())
  }

  ws.onmessage = (event) => {
    if (terminal) terminal.write(event.data)
  }

  ws.onclose = () => {
    if (terminal) terminal.writeln('\r\n\x1b[31m[连接已断开]\x1b[0m')
    connected.value = false
    connecting.value = false
  }

  ws.onerror = () => {
    if (terminal) terminal.writeln('\r\n\x1b[31m[连接失败] 请检查节点是否在线或网络可达\x1b[0m')
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

function sendCommand() {
  const cmd = commandInput.value.trim()
  if (!cmd || !ws || !connected.value) return
  ws.send(cmd)
  commandInput.value = ''
  nextTick(() => inputRef.value?.focus())
}

function clearLogs() {
  if (terminal) terminal.clear()
}

onMounted(() => {
  fetchData()
  nextTick(() => initTerminal())
})

onUnmounted(() => {
  disconnectWS()
  window.removeEventListener('resize', handleResize)
  if (terminal) {
    terminal.dispose()
    terminal = null
  }
})
</script>

<style scoped>
.terminal-container {
  width: 100%;
  height: 460px;
  border-radius: 8px 8px 0 0;
  overflow: hidden;
  border: 1px solid #333;
}

.input-bar {
  display: flex;
  align-items: center;
  gap: 0;
  background: #252526;
  border: 1px solid #333;
  border-top: none;
  border-radius: 0 0 8px 8px;
  padding: 6px 12px;
}

.input-bar.connected {
  border-color: #6a9955;
}

.prompt {
  color: #6a9955;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 14px;
  margin-right: 8px;
  font-weight: bold;
}

.cmd-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: #d4d4d4;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  caret-color: #d4d4d4;
}

.cmd-input::placeholder {
  color: #555;
}

.cmd-input:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
