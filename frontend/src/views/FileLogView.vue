<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <file-text-outlined style="color: #52c41a" />
          <span style="font-weight: 600">日志查看</span>
        </a-space>
      </template>

      <a-space style="margin-bottom: 16px">
        <a-select v-model:value="selectedNode" style="width: 200px" placeholder="选择节点">
          <a-select-option v-for="n in nodes" :key="n.id" :value="n.id">
            {{ n.name }} ({{ n.ip }})
          </a-select-option>
        </a-select>
        <a-input v-model:value="logPath" placeholder="日志路径" style="width: 300px" />
        <a-input-number v-model:value="offset" style="width: 100px" :min="0" placeholder="偏移" />
        <a-button type="primary" @click="fetchLog">
          <search-outlined /> 查看
        </a-button>
      </a-space>

      <div class="log-content">
        <pre v-if="content">{{ content }}</pre>
        <a-empty v-else description="请选择节点和日志路径" />
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { NodeModel } from '../types'
import { getNodes } from '../api/node'
import { getLogFile } from '../api/fileApi'
import {
  SearchOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'

const nodes = ref<NodeModel[]>([])
const selectedNode = ref('')
const logPath = ref('./data/logs/')
const content = ref('')
const offset = ref(0)
const lines = ref(200)

async function fetchData() {
  const res = await getNodes()
  nodes.value = res.data.list
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
  font-family: 'JetBrains Mono', 'Fira Code', 'Courier New', monospace;
  font-size: 13px;
  min-height: 400px;
  max-height: 600px;
  padding: 16px;
  border-radius: 8px;
  overflow: auto;
  border: 1px solid #333;
}
pre {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>
