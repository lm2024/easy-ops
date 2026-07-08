<template>
  <a-config-provider :theme="easyOpsTheme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { easyOpsTheme } from './theme/easyOps'
</script>

<style>
body {
  margin: 0;
  background: #0a0a0b;
  color: #f4f4f5;
  font-family: 'Geist', 'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.ant-card {
  border-radius: 8px;
  border: 1px solid #2a2a2a;
  box-shadow: none;
}

.ant-card:hover {
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.35);
}

.ant-table-wrapper {
  border-radius: 8px;
  overflow: hidden;
}

.ant-table-thead > tr > th {
  background: #1a1a1c !important;
  font-weight: 600;
  color: #f4f4f5 !important;
  border-bottom: 1px solid #2a2a2a !important;
}

.ant-table-tbody > tr > td {
  border-bottom: 1px solid #2a2a2a !important;
}

.ant-table-tbody > tr:hover > td {
  background: #1f1f23 !important;
}

.ant-btn {
  border-radius: 6px;
}

.ant-input,
.ant-input-affix-wrapper,
.ant-select-selector,
.ant-picker {
  border-radius: 6px !important;
}

.ant-badge-status-text {
  font-size: 13px;
}

.ant-descriptions-item-label {
  font-weight: 500;
  color: #a1a1aa;
}

.ant-message-notice-content {
  border-radius: 8px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.45);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
