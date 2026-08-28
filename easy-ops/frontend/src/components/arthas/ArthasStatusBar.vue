<template>
  <div class="arthas-status-bar">
    <a-space>
      <a-badge :status="connected ? 'success' : 'error'" :text="connected ? '已连接' : '未连接'" />
      <a-tag v-if="arthasVersion" color="blue">Arthas {{ arthasVersion }}</a-tag>
      <a-tag color="default">运行 {{ formattedElapsed }}</a-tag>
    </a-space>
    <a-space>
      <a-button size="small" @click="$emit('quick-checkup')" :disabled="!connected || loading">
        <medicine-box-outlined /> 一键体检
      </a-button>
      <a-button size="small" @click="$emit('generate-report')" :disabled="!connected">
        <file-text-outlined /> 生成报告
      </a-button>
      <a-button size="small" danger @click="$emit('stop')" :disabled="!connected">
        <close-circle-outlined /> 结束诊断
      </a-button>
    </a-space>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  MedicineBoxOutlined,
  FileTextOutlined,
  CloseCircleOutlined
} from '@ant-design/icons-vue'

const props = defineProps<{
  connected: boolean
  arthasVersion?: string
  elapsedSeconds: number
  loading?: boolean
}>()

defineEmits<{
  (e: 'quick-checkup'): void
  (e: 'generate-report'): void
  (e: 'stop'): void
}>()

const formattedElapsed = computed(() => {
  const m = Math.floor(props.elapsedSeconds / 60)
  const s = props.elapsedSeconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})
</script>

<style scoped>
.arthas-status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 6px;
  margin-bottom: 16px;
}
</style>
