<template>
  <div class="collab-status-bar">
    <div class="status-indicator">
      <span v-if="connected" class="status-dot connected" />
      <span v-else class="status-dot disconnected" />
      <span class="status-text">{{ connected ? '协作已连接' : '协作未连接' }}</span>
    </div>
    <div class="online-users" v-if="onlineUsers.length > 0">
      <a-avatar
        v-for="uid in onlineUsers"
        :key="uid"
        :size="22"
        :style="{
          backgroundColor: getUserColor(uid),
          fontSize: '10px',
          marginLeft: '4px',
          border: '1px solid #2a2a2a'
        }"
      >
        U{{ uid }}
      </a-avatar>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  connected: boolean
  onlineUsers: number[]
}>()

const cursorColors = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6']

function getUserColor(userId: number): string {
  const index = userId % cursorColors.length
  return cursorColors[index]
}
</script>

<style scoped>
.collab-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px;
  background: #1a1a1b;
  border-bottom: 1px solid #2a2a2a;
  min-height: 32px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.connected {
  background: #10b981;
  box-shadow: 0 0 4px rgba(16, 185, 129, 0.5);
}

.status-dot.disconnected {
  background: #71717a;
}

.status-text {
  font-size: 12px;
  color: #a1a1aa;
}

.status-indicator .status-dot.connected + .status-text {
  color: #10b981;
}

.online-users {
  display: flex;
  align-items: center;
}
</style>
