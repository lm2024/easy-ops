<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-select v-model:value="selectedNode" style="width: 200px">
        <a-select-option v-for="n in nodes" :key="n.id" :value="n.id">
          {{ n.name }} ({{ n.ip }})
        </a-select-option>
      </a-select>
      <a-button type="primary" @click="download" :loading="downloading">
        批量下载
      </a-button>
    </a-space>

    <a-table
      :columns="columns"
      :data-source="fileList"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-checkbox
            :checked="selectedFiles.includes(record)"
            @change="toggleFile(record)"
          />
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { NodeModel } from '../types'
import { getNodes } from '../api/node'
import { batchDownload } from '../api/fileApi'

const nodes = ref<NodeModel[]>([])
const fileList = ref<{ path: string; type: string }[]>([])
const selectedFiles = ref<{ path: string; type: string }[]>([])
const selectedNode = ref('')
const downloading = ref(false)

const columns = [
  { title: '文件路径', dataIndex: 'path', key: 'path' },
  { title: '类型', dataIndex: 'type', key: 'type' },
  { title: '选择', key: 'action' }
]

async function fetchData() {
  const res = await getNodes()
  nodes.value = res.data
  // Placeholder: populate file list
  fileList.value = [
    { path: './data/logs/server.log', type: 'LOG' },
    { path: './application.yml', type: 'YML' }
  ]
}

function toggleFile(file: { path: string; type: string }) {
  const idx = selectedFiles.value.indexOf(file)
  if (idx >= 0) selectedFiles.value.splice(idx, 1)
  else selectedFiles.value.push(file)
}

async function download() {
  if (!selectedNode.value || selectedFiles.value.length === 0) return
  downloading.value = true
  try {
    const paths = selectedFiles.value.map(f => f.path)
    const res = await batchDownload(selectedNode.value, paths)
    // Create download link
    const blob = new Blob([res])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'files.zip'
    a.click()
    window.URL.revokeObjectURL(url)
  } finally {
    downloading.value = false
  }
}

onMounted(fetchData)
</script>
