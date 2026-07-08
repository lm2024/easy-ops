<template>
  <a-modal
    v-model:open="visible"
    :title="currentAlert?.title || '严重告警'"
    :closable="false"
    :mask-closable="false"
    :keyboard="false"
    :footer="null"
    width="520px"
    class="alert-modal"
  >
    <a-result status="error" :title="currentAlert?.title">
      <template #subTitle>
        <div class="alert-content">{{ currentAlert?.content }}</div>
        <div v-if="currentAlert?.createTime" class="alert-time">
          {{ formatTime(currentAlert.createTime) }}
        </div>
      </template>
      <template #extra>
        <a-button type="primary" danger :loading="acking" @click="handleAck">
          确认并关闭
        </a-button>
      </template>
    </a-result>
    <div v-if="queue.length > 1" class="alert-queue-hint">
      还有 {{ queue.length - 1 }} 条待确认告警
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import dayjs from 'dayjs'
import type { NotificationRecordModel } from '../types'
import { ackNotification } from '../api/notification'

const props = defineProps<{ alerts: NotificationRecordModel[] }>()

const visible = ref(false)
const queue = ref<NotificationRecordModel[]>([])
const currentAlert = ref<NotificationRecordModel | null>(null)
const acking = ref(false)

function formatTime(ts: number) {
  return dayjs(ts).format('YYYY-MM-DD HH:mm:ss')
}

function showNext() {
  const critical = queue.value.filter(
    a => a.level === 'CRITICAL' && a.requireAck === 1 && a.ackStatus !== 1
  )
  if (critical.length > 0) {
    currentAlert.value = critical[0]
    visible.value = true
  } else {
    currentAlert.value = null
    visible.value = false
  }
}

watch(() => props.alerts, (alerts) => {
  queue.value = alerts.filter(a => a.level === 'CRITICAL' && a.requireAck === 1 && a.ackStatus !== 1)
  if (!visible.value && queue.value.length > 0) {
    showNext()
  }
}, { immediate: true, deep: true })

async function handleAck() {
  if (!currentAlert.value) return
  try {
    acking.value = true
    await ackNotification(currentAlert.value.id)
    queue.value = queue.value.filter(a => a.id !== currentAlert.value!.id)
    showNext()
  } finally {
    acking.value = false
  }
}
</script>

<style scoped>
.alert-content { color: #f4f4f5; font-size: 14px; line-height: 1.6; margin-top: 8px; }
.alert-time { color: #71717a; font-size: 12px; margin-top: 8px; }
.alert-queue-hint { text-align: center; color: #a1a1aa; font-size: 12px; margin-top: 8px; }
</style>
