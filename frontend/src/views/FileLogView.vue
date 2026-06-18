<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-select v-model:value="selectedNode" style="width: 200px">
        <a-select-option v-for="n in nodes" :key="n.id" :value="n.id">
          {{ n.name }} ({{ n.ip }})
        </a-select-option>
      </a-select>
      <a-input v-model:value="logPath" placeholder="日志路径" style="width: 300px" />
      <a-button type="primary" @click="fetchLog">查看</a-button>
      <a-input-number v-model:value="offset" style="width: 100px" :min="0" /> 偏移
    </a-space>

    <div class="log-content">
      <pre v-if="content">{{ content }}</pre>
      <a-empty v-else description="请选择节点和日志路径" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { NodeModel } from '../types'
import { getNodes } from '../api/node'
import { getLogFile } from '../api/fileApi'

const nodes = ref<NodeModel[]>([])
const selectedNode = ref('')
const logPath = ref('./data/logs/')
const content = ref('')
const offset = ref(0)
const lines = ref(200)

async function fetchData() {
  const res = await getNodes()
  nodes.value = res.data
}

async function fetchLog() {
  if (!selectedNode.value) return
  const res = await getLogFile(selectedNode.value, logPath.value, offset.value, lines.value)
  content.value = res.data
}

onMounted(fetchData)
</script>

<style scoped>
.log-content {
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  min-height: 400px;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
}
pre {
  margin: 0;
  white-space: pre-wrap;
}
</style>
