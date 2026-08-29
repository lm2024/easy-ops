<template>
  <div class="trace-tab">
    <div class="toolbar">
      <a-space wrap>
        <a-input
          v-model:value="className"
          placeholder="类名（支持通配符 *）"
          style="width: 240px"
          size="small"
        />
        <a-input
          v-model:value="methodName"
          placeholder="方法名（支持通配符 *）"
          style="width: 180px"
          size="small"
        />
        <a-input-number v-model:value="traceCount" :min="1" :max="10" size="small" addon-after="次" style="width: 100px" />
        <a-switch v-model:checked="skipJDK" checked-children="跳过JDK" un-checked-children="含JDK" size="small" />
        <a-button type="primary" size="small" @click="startTrace" :loading="tracing" :disabled="!className || !methodName">
          <play-circle-outlined /> 开始追踪
        </a-button>
        <a-button size="small" @click="stopTrace" :disabled="!tracing">
          <stop-outlined /> 停止
        </a-button>
      </a-space>
    </div>

    <a-spin :spinning="tracing">
      <!-- 追踪结果 -->
      <div v-if="traceResults.length > 0" class="trace-results">
        <div class="section-title">
          追踪结果 ({{ traceResults.length }} 次调用)
          <a-button type="link" size="small" @click="clearResults">清空</a-button>
        </div>
        <a-collapse v-model:activeKey="activeResultKey">
          <a-collapse-panel
            v-for="(result, idx) in traceResults"
            :key="idx"
            :header="`调用 #${idx + 1} - 总耗时 ${result.totalCost}ms`"
          >
            <div class="trace-summary">
              <a-descriptions :column="3" size="small">
                <a-descriptions-item label="总耗时">{{ result.totalCost }}ms</a-descriptions-item>
                <a-descriptions-item label="调用深度">{{ result.maxDepth }}</a-descriptions-item>
                <a-descriptions-item label="方法数">{{ result.methodCount }}</a-descriptions-item>
              </a-descriptions>
            </div>
            <div class="trace-tree">
              <trace-node :node="result.tree" :level="0" />
            </div>
          </a-collapse-panel>
        </a-collapse>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <a-empty description="输入类名和方法名，点击开始追踪方法调用耗时">
          <a-button type="primary" :disabled="!className || !methodName" @click="startTrace">开始追踪</a-button>
        </a-empty>
      </div>

      <!-- 常用追踪模板 -->
      <div class="section">
        <div class="section-title">常用追踪模板</div>
        <a-space wrap>
          <a-tag color="blue" style="cursor: pointer" @click="useTemplate('com.example.service.*', '*')">Service 层全部</a-tag>
          <a-tag color="green" style="cursor: pointer" @click="useTemplate('com.example.controller.*', '*')">Controller 层全部</a-tag>
          <a-tag color="orange" style="cursor: pointer" @click="useTemplate('com.example.dao.*', '*')">DAO 层全部</a-tag>
          <a-tag color="purple" style="cursor: pointer" @click="useTemplate('org.springframework.*', '*')">Spring 框架</a-tag>
        </a-space>
      </div>

      <!-- 追踪说明 -->
      <div class="section">
        <a-alert type="info" show-icon>
          <template #message>方法追踪说明</template>
          <template #description>
            <ul>
              <li><b>trace</b> 命令会在方法调用时记录耗时和调用栈，对性能有一定影响</li>
              <li>支持通配符 <code>*</code>，如 <code>com.example.service.*</code> 匹配所有 Service 类</li>
              <li>追踪期间目标方法每被调用一次就会生成一条结果</li>
              <li>建议先缩小范围，避免追踪过多方法导致性能下降</li>
              <li>红色标注的方法是耗时占比超过 50% 的热点方法</li>
            </ul>
          </template>
        </a-alert>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, h, defineComponent, inject } from 'vue'
import { message } from 'ant-design-vue'
import { PlayCircleOutlined, StopOutlined } from '@ant-design/icons-vue'
import { execArthasCommand } from '@/api/arthas'
import { parseTraceTree } from '@/utils/arthasParse'
import { friendlyMessage } from '@/utils/arthasError'

const onArthasError = inject('onArthasError', (_e: any) => {})

const props = defineProps<{ sessionId: string }>()

const className = ref('')
const methodName = ref('')
const traceCount = ref(1)
const skipJDK = ref(true)
const tracing = ref(false)
const traceResults = ref<any[]>([])
const activeResultKey = ref<string[]>([])

let traceTimer: number | null = null

const TraceNode = defineComponent({
  name: 'TraceNode',
  props: {
    node: { type: Object, required: true },
    level: { type: Number, default: 0 }
  },
  setup(propsNode) {
    return () => {
      const node = propsNode.node
      const isHot = node.costPercent >= 50
      const costColor = node.costPercent >= 80 ? '#ff4d4f' : node.costPercent >= 50 ? '#faad14' : '#52c41a'
      return h('div', { class: 'trace-node', style: { paddingLeft: propsNode.level * 20 + 'px' } }, [
        h('div', { class: 'trace-node-line' }, [
          h('span', { class: 'trace-cost', style: { color: costColor, minWidth: '80px', display: 'inline-block' } },
            `${node.cost}ms (${node.costPercent}%)`),
          h('span', { class: 'trace-method', style: { color: isHot ? '#ff4d4f' : 'inherit', fontWeight: isHot ? '600' : 'normal' } },
            node.className + '.' + node.methodName),
          node.children && node.children.length > 0
            ? h('span', { class: 'trace-children-count' }, ` [${node.children.length}]`)
            : null
        ]),
        node.children && node.children.length > 0
          ? h('div', { class: 'trace-children' },
              node.children.map((child: any, i: number) =>
                h(TraceNode, { key: i, node: child, level: propsNode.level + 1 })
              )
            )
          : null
      ])
    }
  }
})

async function execCommand(command: string, timeoutMs = 10000) {
  const res = await execArthasCommand({ sessionId: props.sessionId, command, timeoutMs })
  if (res.data && res.data.success && res.data.results) {
    return res.data.results
  }
  throw new Error(res.data?.errorMsg || '命令执行失败')
}

function useTemplate(cls: string, method: string) {
  className.value = cls
  methodName.value = method
}

async function startTrace() {
  if (!className.value || !methodName.value) {
    message.warning('请输入类名和方法名')
    return
  }
  tracing.value = true
  traceResults.value = []
  const skipJdkArg = skipJDK.value ? '--skipJDKMethod false' : ''
  const countArg = `-n ${traceCount.value}`
  const command = `trace ${className.value} ${methodName.value} ${countArg} ${skipJdkArg}`.trim()

  try {
    message.loading('正在追踪，请触发目标方法调用...')
    const results = await execCommand(command, 60000)
    // trace 的返回里，第一项通常是 type='enhancer' 的增强回执，
    // 真正的调用树在 type='trace'（含 root 字段）的项上。
    // 不区分的话会把增强回执也当成一次调用渲染成 unknown 节点。
    const traceItems = (results || []).filter((r: any) => r && (r.type === 'trace' || r.root))
    if (traceItems.length > 0) {
      for (const r of traceItems) {
        // 用解析层下钻到真正的调用树根（root 只是包装节点，直接渲染会变成 unknown）
        const tree = parseTraceTree([r])
        const parsed = tree ? parseTraceResult(tree) : null
        if (parsed) traceResults.value.push(parsed)
      }
      activeResultKey.value = traceResults.value.map((_, i) => String(i))
      message.success(`追踪完成，共 ${traceResults.value.length} 次调用`)
    } else {
      message.info('未捕获到方法调用，请确保目标方法被执行')
    }
  } catch (e: any) {
    onArthasError(e)
    message.error(friendlyMessage('追踪失败', e))
  } finally {
    tracing.value = false
  }
}

function parseTraceResult(data: any): any | null {
  if (typeof data === 'string') {
    return { totalCost: '?', maxDepth: 0, methodCount: 0, tree: { className: 'raw', methodName: data, cost: '?', costPercent: 100, children: [] } }
  }
  // Arthas 4.x 把调用树放在 root 字段下；旧的 3.x 结构用 tree
  const tree = data.root || data.tree || data
  const totalCost = tree.cost || data.cost || '?'
  const stats = calculateStats(tree)
  return {
    totalCost,
    maxDepth: stats.maxDepth,
    methodCount: stats.methodCount,
    tree: normalizeNode(tree, totalCost)
  }
}

function normalizeNode(node: any, totalCost: any): any {
  const cost = node.cost || '0'
  const costNum = parseFloat(String(cost).replace(/[^0-9.]/g, '')) || 0
  const totalNum = parseFloat(String(totalCost).replace(/[^0-9.]/g, '')) || 1
  return {
    className: node.className || node.class || 'unknown',
    methodName: node.methodName || node.method || 'unknown',
    cost,
    costPercent: totalNum > 0 ? Math.round((costNum / totalNum) * 100) : 0,
    children: (node.children || []).map((c: any) => normalizeNode(c, totalCost))
  }
}

function calculateStats(node: any): { maxDepth: number; methodCount: number } {
  let maxDepth = 0
  let methodCount = 0
  function traverse(n: any, depth: number) {
    methodCount++
    if (depth > maxDepth) maxDepth = depth
    for (const child of (n.children || [])) {
      traverse(child, depth + 1)
    }
  }
  traverse(node, 0)
  return { maxDepth, methodCount }
}

function stopTrace() {
  if (traceTimer) {
    clearTimeout(traceTimer)
    traceTimer = null
  }
  tracing.value = false
  message.info('追踪已停止')
}

function clearResults() {
  traceResults.value = []
  activeResultKey.value = []
}
</script>

<style scoped>
.trace-tab { padding: 0 4px; }
.toolbar { margin-bottom: 16px; }
.trace-results { margin-bottom: 16px; }
.section-title { font-weight: 600; font-size: 14px; margin-bottom: 12px; color: #262626; border-left: 3px solid #1890ff; padding-left: 8px; display: flex; align-items: center; gap: 8px; }
.empty-state { display: flex; align-items: center; justify-content: center; min-height: 200px; }
.trace-summary { margin-bottom: 12px; }
.trace-tree { background: #fafafa; padding: 12px; border-radius: 4px; max-height: 500px; overflow: auto; }
:deep(.trace-node-line) { display: flex; align-items: center; padding: 4px 0; border-bottom: 1px solid #f0f0f0; font-size: 13px; }
:deep(.trace-cost) { font-family: monospace; font-weight: 600; }
:deep(.trace-method) { flex: 1; font-family: monospace; }
:deep(.trace-children-count) { color: #8c8c8c; font-size: 12px; }
.section { margin-top: 16px; }
</style>
