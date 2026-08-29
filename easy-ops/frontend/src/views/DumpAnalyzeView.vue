<template>
  <div class="dump-analyze-view">
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <file-search-outlined style="color: #1890ff" />
          <span style="font-weight: 600">Heap Dump 分析</span>
        </a-space>
      </template>

      <!-- 上传区域 -->
      <div class="upload-section">
        <a-upload-dragger
          name="file"
          :multiple="false"
          :before-upload="beforeUpload"
          :show-upload-list="false"
          accept=".hprof,.core,.hprof.gz,.core.gz"
        >
          <p class="ant-upload-drag-icon">
            <inbox-outlined />
          </p>
          <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p class="ant-upload-hint">
            支持 Java heap dump（.hprof）或 Core dump（.core）文件<br/>
            也支持 gzip 压缩文件（.hprof.gz、.core.gz）<br/>
            最大 200MB（大文件请先压缩：gzip -k file.hprof）
          </p>
        </a-upload-dragger>
      </div>

      <!-- 分析进度 -->
      <div v-if="analyzing" class="analyzing-section">
        <a-spin size="large" />
        <div class="analyzing-text">正在分析 dump 文件...</div>
        <div class="analyzing-detail">{{ uploadingFileName }} ({{ uploadingFileSize }})</div>
      </div>

      <!-- 分析结果 -->
      <div v-if="analysisResult" class="result-section">
        <a-divider />

        <!-- 概览信息 -->
        <a-row :gutter="16" style="margin-bottom: 24px">
          <a-col :span="6">
            <a-statistic title="文件大小" :value="analysisResult.fileSizeFormatted" />
          </a-col>
          <a-col :span="6">
            <a-statistic title="总实例数" :value="formatNumber(analysisResult.totalInstances)" />
          </a-col>
          <a-col :span="6">
            <a-statistic title="总内存占用" :value="analysisResult.totalSizeFormatted" />
          </a-col>
          <a-col :span="6">
            <a-statistic title="类数量" :value="analysisResult.classCount" />
          </a-col>
        </a-row>

        <a-row :gutter="16" style="margin-bottom: 24px">
          <a-col :span="6">
            <a-statistic title="分析耗时" :value="analysisResult.durationMs + 'ms'" />
          </a-col>
          <a-col :span="6">
            <a-statistic title="状态">
              <template #value>
                <a-tag :color="analysisResult.success ? 'success' : 'error'">
                  {{ analysisResult.success ? '成功' : '失败' }}
                </a-tag>
              </template>
            </a-statistic>
          </a-col>
        </a-row>

        <!-- 错误信息 -->
        <a-alert
          v-if="analysisResult.errorMsg"
          type="error"
          :message="analysisResult.errorMsg"
          show-icon
          style="margin-bottom: 16px"
        />

        <!-- TOP 50 类 -->
        <div v-if="analysisResult.topClasses && analysisResult.topClasses.length > 0">
          <div class="section-title">
            <database-outlined style="color: #faad14" /> 内存占用 TOP 50 类
          </div>
          <a-table
            :dataSource="analysisResult.topClasses"
            :pagination="{ pageSize: 20, showSizeChanger: true, pageSizeOptions: ['20', '50', '100'] }"
            size="small"
            row-key="className"
            :scroll="{ x: 900 }"
          >
            <a-table-column title="#" width="60" align="center">
              <template #default="{ index }">
                <a-tag :color="getRankColor(index)" size="small">{{ index + 1 }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="类名" data-index="className" key="className" :min-width="300">
              <template #default="{ record }">
                <span class="class-name">{{ record.className }}</span>
              </template>
            </a-table-column>
            <a-table-column title="实例数" data-index="instanceCount" key="instanceCount" width="120" align="right">
              <template #default="{ record }">
                <span class="number-cell">{{ formatNumber(record.instanceCount) }}</span>
              </template>
            </a-table-column>
            <a-table-column title="内存占用" key="size" width="120" align="right">
              <template #default="{ record }">
                <span class="size-cell">{{ formatBytes(record.totalSize) }}</span>
              </template>
            </a-table-column>
            <a-table-column title="占比" key="percent" width="120" align="center">
              <template #default="{ record }">
                <a-progress
                  :percent="calcPercent(record.totalSize)"
                  :show-info="false"
                  :stroke-color="getPercentColor(record.totalSize)"
                  size="small"
                />
              </template>
            </a-table-column>
          </a-table>
        </div>

        <!-- 无数据提示 -->
        <a-empty v-else description="未分析到类数据" />
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { InboxOutlined, FileSearchOutlined, DatabaseOutlined } from '@ant-design/icons-vue'
import request from '@/utils/request'

const analyzing = ref(false)
const uploadingFileName = ref('')
const uploadingFileSize = ref('')
const analysisResult = ref<any>(null)

async function beforeUpload(file: File) {
  // 验证文件类型
  const validExtensions = ['.hprof', '.core', '.hprof.gz', '.core.gz']
  const isValidType = validExtensions.some(ext => file.name.endsWith(ext))
  if (!isValidType) {
    message.error('只支持 .hprof、.core 或 .gz 压缩文件')
    return false
  }

  // 验证文件大小（200MB）
  const maxSize = 200 * 1024 * 1024
  if (file.size > maxSize) {
    message.error('文件大小超过限制（最大 200MB）。请先压缩：gzip -k file.hprof')
    return false
  }

  analyzing.value = true
  uploadingFileName.value = file.name
  uploadingFileSize.value = formatBytes(file.size)
  analysisResult.value = null

  try {
    const formData = new FormData()
    formData.append('file', file)

    const res = await request.post('/dump/analyze', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000 // 5分钟超时
    })

    if (res.data && res.data.success) {
      analysisResult.value = res.data
      message.success('分析完成')
    } else {
      message.error(res.data?.errorMsg || '分析失败')
    }
  } catch (e: any) {
    message.error('上传失败: ' + (e.message || e))
  } finally {
    analyzing.value = false
  }
}

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return String(num)
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

function calcPercent(size: number): number {
  if (!analysisResult.value || !analysisResult.value.totalSize) return 0
  return Math.round((size / analysisResult.value.totalSize) * 100)
}

function getRankColor(index: number): string {
  if (index === 0) return 'red'
  if (index === 1) return 'orange'
  if (index === 2) return 'gold'
  return 'default'
}

function getPercentColor(size: number): string {
  const percent = calcPercent(size)
  if (percent > 20) return '#ff4d4f'
  if (percent > 10) return '#faad14'
  return '#52c41a'
}
</script>

<style scoped>
.dump-analyze-view {
  padding: 0;
}
.upload-section {
  margin-bottom: 24px;
}
.analyzing-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
}
.analyzing-text {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 500;
}
.analyzing-detail {
  margin-top: 8px;
  color: #8c8c8c;
  font-size: 13px;
}
.result-section {
  margin-top: 16px;
}
.section-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #262626;
  border-left: 3px solid #1890ff;
  padding-left: 8px;
}
.class-name {
  font-family: monospace;
  font-size: 12px;
  word-break: break-all;
}
.number-cell {
  font-family: monospace;
  font-weight: 500;
}
.size-cell {
  font-family: monospace;
  color: #faad14;
  font-weight: 500;
}
</style>
