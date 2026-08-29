<template>
  <div class="flamegraph-tab">
    <div class="toolbar">
      <a-space>
        <a-select v-model:value="eventType" style="width: 120px" :disabled="profiling">
          <a-select-option value="cpu">CPU</a-select-option>
          <a-select-option value="alloc">内存分配</a-select-option>
          <a-select-option value="lock">锁竞争</a-select-option>
          <a-select-option value="wall">Wall Clock</a-select-option>
        </a-select>
        <a-input-number v-model:value="duration" :min="5" :max="120" :disabled="profiling" addon-after="秒" style="width: 120px" />
        <a-button type="primary" size="small" @click="startProfiler" :disabled="profiling">
          <play-circle-outlined /> 开始采样
        </a-button>
        <a-button size="small" @click="stopProfiler" :disabled="!profiling">
          <stop-outlined /> 停止并生成
        </a-button>
        <a-button size="small" @click="loadFlameGraph" :disabled="!flameGraphHtml">
          <reload-outlined /> 重新加载
        </a-button>
      </a-space>
      <span v-if="profiling" class="profiling-status">
        <a-tag color="processing">采样中... {{ elapsedSeconds }}s / {{ duration }}s</a-tag>
      </span>
    </div>

    <a-spin :spinning="profiling">
      <!-- 空状态 -->
      <div v-if="!flameGraphHtml && !profiling" class="empty-state">
        <a-empty description="选择事件类型和采样时长，点击开始采样生成火焰图">
          <a-button type="primary" @click="startProfiler">开始采样</a-button>
        </a-empty>
      </div>

      <!-- 火焰图 -->
      <div v-if="flameGraphHtml" class="flamegraph-container">
        <div class="flamegraph-header">
          <a-tag color="blue">{{ eventType.toUpperCase() }} 火焰图</a-tag>
          <span class="flamegraph-info">采样时长: {{ duration }}s</span>
          <a-input
            v-model:value="searchKeyword"
            placeholder="搜索方法名（如: importData）"
            style="width: 250px"
            size="small"
            allow-clear
            @press-enter="searchInFlameGraph"
          >
            <template #prefix><search-outlined /></template>
          </a-input>
          <a-button type="link" size="small" @click="searchInFlameGraph" :disabled="!searchKeyword">
            搜索
          </a-button>
          <a-button type="link" size="small" @click="downloadFlameGraph">
            <download-outlined /> 下载 HTML
          </a-button>
        </div>

        <!-- 排查问题三步（简化版） -->
        <div class="flamegraph-guide">
          <div class="guide-section">
            <div class="guide-title">🔍 快速排查</div>
            <div class="steps">
              <div class="step-item"><span class="step-num">1</span>在上方搜索框输入你要找的方法名（如: importData）</div>
              <div class="step-item"><span class="step-num">2</span>点击搜索，火焰图会高亮该方法</div>
              <div class="step-item"><span class="step-num">3</span>看这个方法的<b style="color:#ff4d4f">宽度</b>，越宽说明越耗资源</div>
            </div>
            <div class="guide-tips">
              💡 提示：找<b>最宽的绿色块</b>，那就是最耗 CPU/内存的地方
            </div>
          </div>
        </div>

        <iframe
          ref="flamegraphIframe"
          :srcdoc="flameGraphHtml"
          class="flamegraph-iframe"
          sandbox="allow-scripts allow-same-origin allow-modals"
        />
      </div>

      <!-- 采样说明 -->
      <div class="section">
        <a-alert type="info" show-icon>
          <template #message>火焰图说明</template>
          <template #description>
            <ul>
              <li><b>CPU</b>：分析 CPU 消耗，定位热点方法</li>
              <li><b>内存分配</b>：分析对象分配，定位内存泄漏源头</li>
              <li><b>锁竞争</b>：分析 synchronized 和 ReentrantLock 竞争</li>
              <li><b>Wall Clock</b>：分析线程实际运行时间，包括等待</li>
            </ul>
            <p style="margin-top: 8px; color: #faad14">注意：采样期间会对目标应用性能有轻微影响，建议在低峰期使用。</p>
          </template>
        </a-alert>
      </div>

      <!-- 火焰图历史列表 -->
      <div class="section history-section">
        <div class="history-header">
          <span class="history-title"><HistoryOutlined /> 历史火焰图</span>
          <a-button type="link" size="small" @click="loadHistory" :loading="loadingHistory">刷新</a-button>
        </div>
        <a-spin :spinning="loadingHistory">
          <div v-if="flamegraphHistory.length === 0" class="history-empty">
            暂无历史火焰图文件
          </div>
          <a-table
            v-else
            :data-source="flamegraphHistory"
            :pagination="false"
            size="small"
            row-key="fileName"
            class="history-table"
          >
            <a-table-column title="文件名" data-index="fileName" key="fileName" />
            <a-table-column title="大小" data-index="size" key="size" width="100">
              <template #default="{ record }">{{ formatSize(record.size) }}</template>
            </a-table-column>
            <a-table-column title="创建时间" data-index="lastModified" key="lastModified" width="180">
              <template #default="{ record }">{{ formatTime(record.lastModified) }}</template>
            </a-table-column>
            <a-table-column title="操作" key="action" width="100">
              <template #default="{ record }">
                <a-button type="link" size="small" @click="downloadHistory(record.fileName)">
                  <DownloadOutlined /> 下载
                </a-button>
              </template>
            </a-table-column>
          </a-table>
        </a-spin>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount, inject, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlayCircleOutlined, StopOutlined, ReloadOutlined, DownloadOutlined, HistoryOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { execArthasCommand, getFlamegraphList } from '@/api/arthas'
import { friendlyMessage } from '@/utils/arthasError'

const onArthasError = inject('onArthasError', (_e: any) => {})

const props = defineProps<{ sessionId: string }>()

const eventType = ref('cpu')
const duration = ref(30)
const profiling = ref(false)
const elapsedSeconds = ref(0)
const flameGraphHtml = ref('')
const flamegraphIframe = ref()
const searchKeyword = ref('')

let profilerTimer: number | null = null

// 火焰图历史列表
const flamegraphHistory = ref<Array<{ fileName: string; filePath: string; size: number; lastModified: number }>>([])
const loadingHistory = ref(false)
const downloadBaseUrl = ref('')

// 加载历史列表
async function loadHistory() {
  if (!props.sessionId) return
  loadingHistory.value = true
  try {
    const res = await getFlamegraphList(props.sessionId)
    if (res.code === 200 || res.code === 0) {
      flamegraphHistory.value = res.data.list || []
      downloadBaseUrl.value = res.data.downloadBaseUrl || ''
    }
  } catch (e: any) {
    console.error('加载火焰图历史列表失败:', e)
  } finally {
    loadingHistory.value = false
  }
}

// 格式化文件大小
function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB'
}

// 格式化时间
function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// 下载历史火焰图
async function downloadHistory(fileName: string) {
  if (!downloadBaseUrl.value) return
  try {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token') || ''
    const url = downloadBaseUrl.value + encodeURIComponent(fileName)
    const response = await fetch(url, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = fileName
    a.click()
    URL.revokeObjectURL(blobUrl)
  } catch (e: any) {
    message.error('下载火焰图失败: ' + (e.message || e))
  }
}

onMounted(() => {
  loadHistory()
})

async function execCommand(command: string, timeoutMs = 10000) {
  const res = await execArthasCommand({ sessionId: props.sessionId, command, timeoutMs })
  if (res.data && res.data.success && res.data.results) {
    return res.data.results
  }
  throw new Error(res.data?.errorMsg || '命令执行失败')
}

async function startProfiler() {
  Modal.confirm({
    title: '开始火焰图采样',
    content: `将对目标应用进行 ${duration.value} 秒的 ${eventType.value} 采样，期间可能会轻微影响性能，确定继续吗？`,
    okText: '开始采样',
    cancelText: '取消',
    onOk: async () => {
      try {
        await execCommand(`profiler start --event ${eventType.value}`, 10000)
        profiling.value = true
        elapsedSeconds.value = 0
        flameGraphHtml.value = ''
        message.success('采样已开始')
        startTimer()
      } catch (e: any) {
        onArthasError(e)
        message.error(friendlyMessage('启动采样失败', e))
      }
    }
  })
}

function startTimer() {
  stopTimer()
  profilerTimer = window.setInterval(() => {
    elapsedSeconds.value++
    if (elapsedSeconds.value >= duration.value) {
      stopProfiler()
    }
  }, 1000)
}

function stopTimer() {
  if (profilerTimer) {
    clearInterval(profilerTimer)
    profilerTimer = null
  }
}

async function stopProfiler() {
  if (!profiling.value) return
  stopTimer()
  try {
    message.loading('正在生成火焰图...')
    const results = await execCommand(`profiler stop --format html`, 30000)
    if (results.length > 0) {
      const data = results[0]
      let html = ''
      if (typeof data === 'string') {
        html = data
      } else if (data.htmlContent) {
        html = data.htmlContent
      } else if (data.tooLarge) {
        // Agent 侧对超大火焰图只回传路径，避免网络与内存开销
        const mb = data.fileSizeBytes ? (data.fileSizeBytes / 1024 / 1024).toFixed(1) : '未知'
        message.warning(`火焰图过大（${mb}MB），已跳过页面内联预览，请到「历史文件」下载后本地打开`)
        html = ''
      } else if (data.output) {
        html = data.output
      } else if (data.result) {
        html = data.result
      } else if (data.outputFile) {
        message.warning('火焰图文件已生成: ' + data.outputFile)
        html = ''
      } else {
        html = JSON.stringify(data, null, 2)
      }
      // 强制注入暗色主题 CSS，让火焰图跟页面暗色主题协调
      if (html && html.includes('</head>')) {
        const darkCss = '<style>:root{--bg:#1e1e1e!important;--fg:#cccccc!important;--hl-bg:#3a3a00!important;--hl-border:#8a7000!important;--link:#58a6ff!important;--legend-bg:#333333!important;--legend-border:#888888!important}body{background-color:#1e1e1e!important;color:#cccccc!important;margin:0;padding:10px}#canvas{background-color:#1e1e1e!important;height:500px!important;width:100%!important}</style>'
        html = html.replace('</head>', darkCss + '</head>')
      }
      flameGraphHtml.value = html
    }
    profiling.value = false
    message.success('火焰图生成完成')
  } catch (e: any) {
    onArthasError(e)
    profiling.value = false
    message.error(friendlyMessage('生成火焰图失败', e))
  }
}

function loadFlameGraph() {
  if (flameGraphHtml.value && flamegraphIframe.value) {
    flamegraphIframe.value.srcdoc = flameGraphHtml.value
  }
}

function searchInFlameGraph() {
  if (!searchKeyword.value || !flamegraphIframe.value) return
  // 向 iframe 发送搜索消息
  flamegraphIframe.value.contentWindow?.postMessage({
    type: 'flamegraph-search',
    keyword: searchKeyword.value
  }, '*')
  message.info(`搜索: ${searchKeyword.value}`)
}

function downloadFlameGraph() {
  if (!flameGraphHtml.value) return
  const blob = new Blob([flameGraphHtml.value], { type: 'text/html' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `flamegraph-${eventType.value}-${Date.now()}.html`
  a.click()
  URL.revokeObjectURL(url)
}

onBeforeUnmount(() => {
  stopTimer()
  if (profiling.value) {
    stopProfiler().catch(() => {})
  }
})
</script>

<style scoped>
.flamegraph-tab { padding: 0 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.profiling-status { display: flex; align-items: center; }
.empty-state { display: flex; align-items: center; justify-content: center; min-height: 300px; }
.flamegraph-container { margin-bottom: 16px; }
.flamegraph-header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.flamegraph-info { color: #8c8c8c; font-size: 13px; }

/* 读图指南 */
.flamegraph-guide {
  display: flex;
  gap: 16px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #1f1f1f;
  border: 1px solid #303030;
  border-radius: 6px;
  flex-wrap: wrap;
}
.guide-section { flex: 1; min-width: 200px; }
.guide-title {
  font-size: 13px;
  font-weight: 600;
  color: #e6e6e6;
  margin-bottom: 8px;
}
.guide-axis { display: flex; flex-direction: column; gap: 6px; }
.axis-item { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.axis-label {
  display: inline-block;
  padding: 2px 8px;
  background: #262626;
  border-radius: 3px;
  color: #1890ff;
  font-size: 11px;
  white-space: nowrap;
}
.axis-desc { color: #bfbfbf; }

/* 颜色图例 */
.color-legend { display: flex; flex-direction: column; gap: 4px; }
.color-item { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #bfbfbf; }
.color-block {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 2px;
  border: 1px solid rgba(255,255,255,0.1);
}

/* 排查步骤 */
.steps { display: flex; flex-direction: column; gap: 6px; }
.step-item { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #bfbfbf; }
.step-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  background: #1890ff;
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
.guide-tips {
  margin-top: 8px;
  padding: 6px 10px;
  background: #262626;
  border-radius: 4px;
  font-size: 11px;
  color: #8c8c8c;
}

.flamegraph-iframe {
  width: 100%;
  height: 600px;
  border: 1px solid #303030;
  border-radius: 4px;
  background-color: #141414;
}
/* 暗色主题下 iframe 容器背景 */
:deep(.flamegraph-iframe) {
  background-color: #141414;
}
.section { margin-top: 16px; }

/* 历史列表 */
.history-section { margin-top: 20px; }
.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.history-title {
  font-size: 14px;
  font-weight: 600;
  color: #e6e6e6;
}
.history-empty {
  padding: 20px;
  text-align: center;
  color: #8c8c8c;
  font-size: 13px;
  background: #1a1a1a;
  border-radius: 4px;
}
.history-table {
  background: #1a1a1a;
  border-radius: 4px;
}
.history-table :deep(.ant-table) {
  background: transparent;
}
.history-table :deep(.ant-table-thead > tr > th) {
  background: #262626;
  color: #bfbfbf;
  border-bottom: 1px solid #303030;
}
.history-table :deep(.ant-table-tbody > tr > td) {
  background: #1a1a1a;
  color: #d9d9d9;
  border-bottom: 1px solid #303030;
}
.history-table :deep(.ant-table-tbody > tr:hover > td) {
  background: #262626;
}
</style>
