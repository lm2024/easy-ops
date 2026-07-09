<template>
  <a-card :bordered="false" style="border-radius: 8px; max-width: 860px">
    <template #title>
      <a-space>
        <folder-open-outlined style="color: #722ed1" />
        <span style="font-weight: 600">{{ isEdit ? '编辑应用' : '新增应用' }}</span>
      </a-space>
    </template>

    <!-- 环境模板快速填入 JVM 参数（直接生成 start.sh） -->
    <a-card size="small" style="margin-bottom: 16px; background: #1a1a1c" :bordered="true">
      <template #title>
        <a-space>
          <thunderbolt-outlined style="color: #faad14" />
          <span style="font-weight: 500">一键填入 JVM 模板</span>
          <a-tooltip title="根据节点硬件和运行环境，自动生成标准的 start.sh 和 stop.sh 脚本">
            <info-circle-outlined style="color: #999" />
          </a-tooltip>
        </a-space>
      </template>
      <a-space wrap>
        <a-select v-model:value="templateEnv" style="width: 140px" placeholder="选择环境">
          <a-select-option value="dev">🌱 开发环境</a-select-option>
          <a-select-option value="test">🧪 测试环境</a-select-option>
          <a-select-option value="prod">🚀 生产环境</a-select-option>
        </a-select>
        <a-select v-model:value="templateNodeId" style="width: 200px" placeholder="选择参考节点">
          <a-select-option v-for="n in nodeOptions" :key="n.id" :value="n.id">{{ n.name }} ({{ n.ip }})</a-select-option>
        </a-select>
        <a-tooltip title="选择一个典型节点作为参考，根据其 CPU/内存生成 JVM 参数和脚本，适用于所有部署节点。如节点硬件差异大，建议选配置最低的节点，生成后可手动微调。">
          <info-circle-outlined style="color: #999; cursor: help" />
        </a-tooltip>
        <a-button type="primary" ghost @click="fillTemplate" :loading="templateLoading">
          <thunderbolt-outlined /> 一键填入
        </a-button>
        <span v-if="nodeSpecs" style="font-size: 12px; color: #888">
          检测到 {{ nodeSpecs.cpuCores }} 核 / {{ formatMB(nodeSpecs.totalMemoryMB) }}
        </span>
      </a-space>
    </a-card>

    <a-form ref="formRef" :model="formState" :rules="rules" layout="vertical" @finish="handleSubmit">
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item label="应用名称" name="name">
            <a-input v-model:value="formState.name" placeholder="例如: 订单服务" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="Jar 包名">
            <a-input v-model:value="formState.jarName" placeholder="app.jar" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="部署目录">
            <a-input v-model:value="formState.deployDir" placeholder="/app/data/apps/order" />
            <template #extra><span style="font-size:12px;color:#888">Agent 上存放 jar 和脚本的目录，start.sh/stop.sh 在此目录下执行</span></template>
          </a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="启动脚本 start.sh">
            <template #extra><span style="font-size:12px;color:#888">部署时自动同步到节点的部署目录并执行，无需手动拷贝</span></template>
            <a-textarea v-model:value="formState.startScript" :rows="6" placeholder="#!/bin/bash&#10;nohup java -jar app.jar > logs/startup.log 2>&1 &" style="font-family:'JetBrains Mono',monospace;font-size:12px" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="停止脚本 stop.sh">
            <template #extra><span style="font-size:12px;color:#888">部署时自动同步到节点的部署目录并执行，无需手动拷贝</span></template>
            <a-textarea v-model:value="formState.stopScript" :rows="6" placeholder="#!/bin/bash&#10;PID_FILE=pid&#10;if [ -f &quot;$PID_FILE&quot; ]; then kill $(cat $PID_FILE); rm -f $PID_FILE; fi" style="font-family:'JetBrains Mono',monospace;font-size:12px" />
          </a-form-item>
        </a-col>
      </a-row>

      <a-form-item label="部署节点">
        <a-select v-model:value="formState.nodeIds" mode="multiple" style="width: 100%" placeholder="选择要部署到的节点" :max-tag-count="3">
          <a-select-option v-for="n in nodeOptions" :key="n.id" :value="n.id">
            <span>{{ n.name }} ({{ n.ip }})</span>
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-divider orientation="left" style="font-size: 13px; color: #888">部署后健康检查</a-divider>
      <a-form-item label="启用健康检查">
        <template #extra><span style="font-size:12px;color:#888">部署后探测应用是否存活；若你的工程暂无健康地址，可关闭此项，部署将跳过健康检查直接判定成功</span></template>
        <a-switch v-model:checked="formState.healthCheckEnabled" checked-children="开" un-checked-children="关" />
      </a-form-item>
      <a-row :gutter="16" v-if="formState.healthCheckEnabled">
        <a-col :span="8">
          <a-form-item label="端口">
            <a-input-number v-model:value="formState.healthCheckPort" :min="1" :max="65535" style="width: 100%" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="路径">
            <a-input v-model:value="formState.healthCheckPath" placeholder="/hello" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="关键字(逗号分隔)">
            <a-input v-model:value="formState.healthCheckKeyword" placeholder="Hello,DEPLOYED" />
          </a-form-item>
        </a-col>
      </a-row>

      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="loading"><save-outlined /> 保存</a-button>
          <a-button @click="$router.back()">取消</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </a-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { ProjectModel, NodeModel } from '../types'
import { createProject, updateProject, getProject } from '../api/project'
import { getNodes } from '../api/node'
import { getNodeSysInfo } from '../api/agent'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import {
  SaveOutlined,
  FolderOpenOutlined,
  ThunderboltOutlined,
  InfoCircleOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()
const nodeOptions = ref<NodeModel[]>([])
const isEdit = computed(() => !!route.params.id)

// 模板相关
const templateEnv = ref<string>('dev')
const templateNodeId = ref<string>('')
const templateLoading = ref(false)
const nodeSpecs = ref<{ cpuCores: number; totalMemoryMB: number } | null>(null)

const formState = ref<any>({
  name: '',
  jarName: '',
  deployDir: '',
  startScript: '',
  stopScript: '',
  jvmOpts: '',
  envVars: '',
  nodeIds: [] as string[],
  healthCheckEnabled: true,
  healthCheckPort: 8080,
  healthCheckPath: '/hello',
  healthCheckKeyword: 'Hello,DEPLOYED'
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入应用名称' }],
  jarName: [{ required: true, message: '请输入 Jar 包名（如 demo-test-app.jar）' }],
  startScript: [{
    validator: (_rule: Rule, value: string) => {
      if (!value || !formState.value.jarName) return Promise.resolve()
      const jarNameInScript = extractJarNameFromScript(value)
      if (jarNameInScript && jarNameInScript !== formState.value.jarName) {
        return Promise.reject(`脚本中 JAR_NAME=${jarNameInScript}，但 Jar 包名填写的是 ${formState.value.jarName}，不一致！`)
      }
      return Promise.resolve()
    }
  }]
}

/** 从 startScript 中提取 JAR_NAME=xxx 的值 */
function extractJarNameFromScript(script: string): string | null {
  const match = script.match(/JAR_NAME=(\S+)/)
  return match ? match[1] : null
}

/** 自动修正 startScript 中的 JAR_NAME 使其与 jarName 一致 */
function fixStartScriptJarName(script: string, jarName: string): string {
  if (!script || !jarName) return script
  return script.replace(/JAR_NAME=\S+/, `JAR_NAME=${jarName}`)
}

/** 格式化内存显示 */
function formatMB(mb: number): string {
  if (mb >= 1024) return (mb / 1024).toFixed(1) + 'GB'
  return mb + 'MB'
}

/** 根据环境和硬件信息计算 JVM 参数 */
function generateJVMOpts(env: string, _cpuCores: number, totalMemMB: number): string {
  // 各环境的内存分配比例
  const ratios: Record<string, { xmsRatio: number; xmxRatio: number; pause: number }> = {
    dev:  { xmsRatio: 0.25, xmxRatio: 0.50, pause: 200 },
    test: { xmsRatio: 0.40, xmxRatio: 0.65, pause: 200 },
    prod: { xmsRatio: 0.50, xmxRatio: 0.75, pause: 100 }
  }
  const cfg = ratios[env] || ratios.dev

  // 计算堆大小（MB），设上限避免过度分配
  let xms = Math.round(totalMemMB * cfg.xmsRatio)
  let xmx = Math.round(totalMemMB * cfg.xmxRatio)
  const maxHeap = Math.min(totalMemMB, env === 'prod' ? 32768 : 16384)
  xms = Math.min(xms, maxHeap)
  xmx = Math.min(xmx, maxHeap)
  // 保底
  xms = Math.max(xms, 256)
  xmx = Math.max(xmx, 512)

  const lines: string[] = []

  // 核心：堆 + GC
  lines.push(`-Xms${xms}m -Xmx${xmx}m`)
  lines.push('-XX:+UseG1GC')
  lines.push(`-XX:MaxGCPauseMillis=${cfg.pause}`)

  // OOM 安全退出
  lines.push('-XX:+ExitOnOutOfMemoryError')

  // 非 dev 环境保留 HeapDump
  if (env !== 'dev') {
    lines.push('-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./logs/dump.hprof')
  }

  // GC 日志（精简，只保留关键标志）
  lines.push('-Xloggc:./logs/gc.log -XX:+PrintGCDateStamps')

  // 编码
  lines.push('-Dfile.encoding=UTF-8')

  return lines.join(' ')
}

/** 生成启动脚本 start.sh（在部署目录下执行） */
function generateStartScript(jarName: string, jvmOpts: string): string {
  if (!jarName) jarName = 'app.jar'
  return `#!/bin/bash
# 自动生成的启动脚本 - 请在部署目录下执行
JAR_NAME=${jarName}
JVM_OPTS="${jvmOpts}"
mkdir -p logs
nohup java $JVM_OPTS -jar $JAR_NAME > logs/startup.log 2>&1 &
PID=$!
echo $PID > pid
echo "Started PID=$PID"`
}

/** 生成停止脚本 stop.sh（在部署目录下执行） */
function generateStopScript(): string {
  return `#!/bin/bash
# 自动生成的停止脚本 - 请在部署目录下执行
PID_FILE=pid
if [ -f "$PID_FILE" ]; then
  PID=$(cat "$PID_FILE")
  echo "Stopping PID=$PID"
  kill $PID 2>/dev/null
  sleep 3
  # 强制杀死
  kill -9 $PID 2>/dev/null
  rm -f "$PID_FILE"
  echo "Stopped"
else
  echo "PID file not found, trying pkill..."
  pkill -f "$(basename $(pwd))/" 2>/dev/null || true
fi`
}

/** 一键填入模板 */
async function fillTemplate() {
  if (!templateEnv.value || !templateNodeId.value) return
  templateLoading.value = true

  try {
    // 获取节点硬件信息
    const res = await getNodeSysInfo(templateNodeId.value)
    const specs = res.data
    nodeSpecs.value = { cpuCores: specs.cpuCores, totalMemoryMB: specs.totalMemoryMB }

    const cpuCores = specs.cpuCores || 2
    const totalMemMB = specs.totalMemoryMB || 4096
    const jarName = formState.value.jarName || 'app.jar'

    // JVM 参数直接嵌入到 start.sh
    const jvmOpts = generateJVMOpts(templateEnv.value, cpuCores, totalMemMB)
    const startScript = generateStartScript(jarName, jvmOpts)
    const stopScript = generateStopScript()
    const deployDir = '/app/data/apps/' + (formState.value.name || 'app').toLowerCase().replace(/\s+/g, '-')

    formState.value.startScript = startScript
    formState.value.stopScript = stopScript
    formState.value.deployDir = deployDir
    formState.value.jarName = jarName
  } catch {
    nodeSpecs.value = null
    const jarName = formState.value.jarName || 'app.jar'
    const jvmOpts = generateJVMOpts(templateEnv.value, 2, 4096)
    const startScript = generateStartScript(jarName, jvmOpts)
    const stopScript = generateStopScript()
    const deployDir = '/app/data/apps/' + (formState.value.name || 'app').toLowerCase().replace(/\s+/g, '-')
    formState.value.startScript = startScript
    formState.value.stopScript = stopScript
    formState.value.deployDir = deployDir
    formState.value.jarName = jarName
  } finally {
    templateLoading.value = false
  }
}

// 当 jarName 变化时，自动修正 startScript 中的 JAR_NAME
watch(() => formState.value.jarName, (newJarName) => {
  if (newJarName && formState.value.startScript) {
    formState.value.startScript = fixStartScriptJarName(formState.value.startScript, newJarName)
  }
})

async function handleSubmit() {
  // 保存前自动修正 startScript 中 JAR_NAME 与 jarName 不一致的问题
  if (formState.value.jarName && formState.value.startScript) {
    const jarNameInScript = extractJarNameFromScript(formState.value.startScript)
    if (jarNameInScript && jarNameInScript !== formState.value.jarName) {
      formState.value.startScript = fixStartScriptJarName(formState.value.startScript, formState.value.jarName)
    }
  }

  try {
    loading.value = true
    const id = route.params.id as string
    const payload = { ...formState.value } as any
    // nodeIds 在前端是多选数组，后端字段是 String，提交前转为逗号分隔字符串
    if (Array.isArray(payload.nodeIds)) {
      payload.nodeIds = payload.nodeIds.join(',')
    }
    if (id) {
      await updateProject(id, payload as ProjectModel)
    } else {
      await createProject(payload as ProjectModel)
    }
    router.push('/projects')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const res = await getNodes()
  nodeOptions.value = res.data.list

  const id = route.params.id as string
  if (id) {
    const project = await getProject(id)
    // nodeIds 是逗号分隔字符串，转成数字数组匹配下拉选项（后端是 Long）
    const data = project.data
    const nodeIds = typeof data.nodeIds === 'string'
      ? data.nodeIds.split(',').map((s: string) => Number(s.trim())).filter((n: number) => !isNaN(n))
      : []
    formState.value = { ...data, nodeIds }
  }
})

</script>
