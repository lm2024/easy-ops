<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <setting-outlined style="color: #722ed1" />
          <span style="font-weight: 600">配置编辑</span>
        </a-space>
      </template>

      <a-space style="margin-bottom: 16px">
        <a-select v-model:value="selectedNode" style="width: 200px" placeholder="选择节点">
          <a-select-option v-for="n in nodes" :key="n.id" :value="n.id">
            {{ n.name }} ({{ n.ip }})
          </a-select-option>
        </a-select>
        <a-input v-model:value="configPath" placeholder="配置文件路径" style="width: 300px" />
        <a-button type="primary" @click="fetchConfig">
          <search-outlined /> 读取
        </a-button>
        <a-button @click="saveConfig" :loading="saving">
          <save-outlined /> 保存
        </a-button>
      </a-space>

      <a-textarea
        v-model:value="content"
        :rows="20"
        placeholder="配置文件内容..."
        style="font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 13px"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { NodeModel } from '../types'
import { getNodes } from '../api/node'
import { getConfigFile, saveConfigFile } from '../api/fileApi'
import {
  SearchOutlined,
  SaveOutlined,
  SettingOutlined
} from '@ant-design/icons-vue'

const nodes = ref<NodeModel[]>([])
const selectedNode = ref('')
const configPath = ref('./application.yml')
const content = ref('')
const saving = ref(false)

async function fetchData() {
  const res = await getNodes()
  nodes.value = res.data.list
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
