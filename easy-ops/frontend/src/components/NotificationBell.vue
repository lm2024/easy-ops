<template>
  <a-badge :count="unreadCount" :overflow-count="99" :offset="[-2, 2]">
    <bell-outlined class="bell-icon" @click="openDrawer" />
  </a-badge>

  <a-drawer
    v-model:open="drawerVisible"
    title="通知中心"
    placement="right"
    :width="400"
    @close="onDrawerClose"
  >
    <a-spin :spinning="loading">
      <a-list
        :data-source="notifications"
        :locale="{ emptyText: '暂无通知' }"
      >
        <template #renderItem="{ item }">
          <a-list-item
            class="notif-item"
            :class="{ unread: item.readStatus === 0 }"
            @click="handleRead(item)"
          >
            <a-list-item-meta>
              <template #title>
                <a-space>
                  <a-tag :color="levelColor(item.level)" size="small">{{ item.level }}</a-tag>
                  <span>{{ item.title }}</span>
                </a-space>
              </template>
              <template #description>
                <div class="notif-content">{{ item.content }}</div>
                <div class="notif-time">{{ formatTime(item.createTime) }}</div>
              </template>
            </a-list-item-meta>
            <template #actions>
              <a-button
                v-if="item.requireAck === 1 && item.ackStatus !== 1"
                type="link"
                size="small"
                @click.stop="handleAck(item)"
              >
                确认
              </a-button>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import dayjs from 'dayjs'
import type { NotificationRecordModel } from '../types'
import {
  getUnreadCount, getUnackedAlerts, listNotifications, markNotificationRead, ackNotification
} from '../api/notification'
import { BellOutlined } from '@ant-design/icons-vue'

const emit = defineEmits<{ (e: 'unacked-alerts', alerts: NotificationRecordModel[]): void }>()

const unreadCount = ref(0)
const drawerVisible = ref(false)
const loading = ref(false)
const notifications = ref<NotificationRecordModel[]>([])
let pollTimer: ReturnType<typeof setInterval> | null = null

function levelColor(level: string) {
  const map: Record<string, string> = {
    CRITICAL: 'red', WARNING: 'orange', INFO: 'blue'
  }
  return map[level] || 'default'
}

function formatTime(ts?: number) {
  return ts ? dayjs(ts).format('MM-DD HH:mm:ss') : ''
}

async function fetchUnread() {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data.count
  } catch { /* ignore */ }
}

async function fetchUnacked() {
  try {
    const res = await getUnackedAlerts()
    emit('unacked-alerts', res.data || [])
  } catch { /* ignore */ }
}

async function openDrawer() {
  drawerVisible.value = true
  loading.value = true
  try {
    const res = await listNotifications(1, 50)
    notifications.value = res.data.list || []
  } finally {
    loading.value = false
  }
}

function onDrawerClose() {
  fetchUnread()
}

async function handleRead(item: NotificationRecordModel) {
  if (item.readStatus === 0) {
    await markNotificationRead(item.id)
    item.readStatus = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
}

async function handleAck(item: NotificationRecordModel) {
  await ackNotification(item.id)
  item.ackStatus = 1
  fetchUnacked()
}

onMounted(() => {
  fetchUnread()
  fetchUnacked()
  pollTimer = setInterval(() => {
    fetchUnread()
    fetchUnacked()
  }, 30000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.bell-icon {
  font-size: 18px;
  cursor: pointer;
  color: #a1a1aa;
  padding: 4px 8px;
  transition: color 0.3s;
}
.bell-icon:hover { color: #e8ff59; }
.notif-item { cursor: pointer; border-radius: 6px; padding: 8px; }
.notif-item.unread { background: rgba(232, 255, 89, 0.06); }
.notif-content { color: #a1a1aa; font-size: 13px; margin-top: 4px; }
.notif-time { color: #71717a; font-size: 12px; margin-top: 4px; }
</style>
