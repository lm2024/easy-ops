<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 全局卡片样式美化 */
.ant-card {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.ant-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

/* 表格样式 */
.ant-table-wrapper {
  border-radius: 8px;
  overflow: hidden;
}

.ant-table {
  border-radius: 8px;
}

.ant-table-thead > tr > th {
  background: #fafafa !important;
  font-weight: 600;
  color: #262626;
}

/* 按钮圆角 */
.ant-btn {
  border-radius: 6px;
}

/* 输入框圆角 */
.ant-input,
.ant-input-affix-wrapper,
.ant-select-selector,
.ant-picker {
  border-radius: 6px !important;
}

/* Badge 美化 */
.ant-badge-status-text {
  font-size: 13px;
}

/* 描述列表 */
.ant-descriptions-item-label {
  font-weight: 500;
  color: #595959;
}

/* 消息提示 */
.ant-message-notice-content {
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 页面过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
