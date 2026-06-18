<template>
  <div>
    <a-space style="margin-bottom: 16px">
      <a-select v-model:value="selectedNode" style="width: 200px">
        <a-select-option v-for="n in nodes" :key="n.id" :value="n.id">
          {{ n.name }} ({{ n.ip }})
        </a-select-option>
      </a-select>
      <a-input v-model:value="configPath" placeholder="配置文件路径" style="width: 300px" />
      <a-button type="primary" @click="fetchConfig">读取</a-button>
      <a-button @click="saveConfig" :loading="saving">保存</a-button>
    </a-space>

    <MonacoEditor v-model:value="content" lang="yaml" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { NodeModel } from '../types'
import { getNodes } from '../api/node'
import { getConfigFile, saveConfigFile } from '../api/fileApi'

const nodes = ref<NodeModel[]>([])
const selectedNode = ref('')
const configPath = ref('./application.yml')
const content = ref('')
const saving = ref(false)

async function fetchData() {
  const res = await getNodes()
  nodes.value = res.data
}

async function fetchConfig() {
  if (!selectedNode.value) return
  const res = await getConfigFile(selectedNode.value, configPath.value)
  content.value = res.data
}

async function saveConfig() {
  try {
    saving.value = true
    await saveConfigFile(selectedNode.value, configPath.value, content.value)
  } finally {
    saving.value = false
  }
}

onMounted(fetchData)
</script>
