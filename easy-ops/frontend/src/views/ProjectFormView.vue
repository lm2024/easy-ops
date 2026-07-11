<template>
  <a-card :bordered="false" style="border-radius: 8px; max-width: 960px">
    <template #title>
      <a-space>
        <folder-open-outlined style="color: #722ed1" />
        <span style="font-weight: 600">{{ isEdit ? '编辑应用' : '新增应用' }}</span>
      </a-space>
    </template>

    <!-- 环境模板快速填入 JVM 参数（直接生成 start.sh） -->
    <a-card size="small" class="template-card" style="margin-bottom: 16px" :bordered="true">
      <template #title>
        <a-space>
          <thunderbolt-outlined style="color: #faad14" />
          <span style="font-weight: 500">一键填入 JVM 模板</span>
          <a-tooltip title="根据节点硬件和运行环境，自动生成 G1 精简版 start.sh / stop.sh，并写入 JVM 参数字段（保存后进数据库，部署时同步到 Agent 部署目录）">
            <info-circle-outlined style="color: #999" />
          </a-tooltip>
        </a-space>
      </template>
      <a-space wrap style="margin-bottom: 12px">
        <a-select v-model:value="templateEnv" style="width: 140px" placeholder="选择环境">
          <a-select-option value="dev">🌱 开发环境</a-select-option>
          <a-select-option value="test">🧪 测试环境</a-select-option>
          <a-select-option value="prod">🚀 生产环境</a-select-option>
        </a-select>
        <a-select v-model:value="templateNodeId" style="width: 200px" placeholder="选择参考节点">
          <a-select-option v-for="n in nodeOptions" :key="n.id" :value="n.id">{{ n.name }} ({{ n.ip }})</a-select-option>
        </a-select>
        <a-tooltip title="选择典型节点作为硬件参考；多节点硬件差异大时选配置最低者，生成后按下方说明微调。">
          <info-circle-outlined style="color: #999; cursor: help" />
        </a-tooltip>
        <a-button type="primary" ghost @click="fillTemplate" :loading="templateLoading">
          <thunderbolt-outlined /> 一键填入
        </a-button>
        <span v-if="nodeSpecs" style="font-size: 12px; color: #888">
          参考节点 {{ nodeSpecs.cpuCores }} 核 / {{ formatMB(nodeSpecs.totalMemoryMB) }}
        </span>
      </a-space>

      <a-alert type="info" show-icon style="margin-bottom: 12px">
        <template #message>
          <span style="font-weight: 500">{{ currentEnvProfile.icon }} {{ currentEnvProfile.label }}</span>
        </template>
        <template #description>
          <div class="env-desc">{{ currentEnvProfile.summary }}</div>
          <ul class="env-list">
            <li v-for="(g, i) in currentEnvProfile.goals" :key="'g' + i">{{ g }}</li>
          </ul>
          <div class="env-meta">
            <div><strong>堆策略：</strong>{{ currentEnvProfile.heapStrategy }}</div>
            <div><strong>GC 策略：</strong>{{ currentEnvProfile.gcStrategy }}</div>
          </div>
        </template>
      </a-alert>

      <template v-if="templatePlan">
        <a-divider orientation="left" style="margin: 12px 0; font-size: 12px; color: #888">
          本次生成 · {{ templatePlan.envProfile.label }} · {{ templatePlan.hardware.cpuCores }}核 / {{ formatMB(templatePlan.hardware.totalMemoryMB) }}
        </a-divider>
        <div class="guide-row">
          <a-tag color="blue">内存</a-tag>
          <span>{{ templatePlan.memoryGuide }}</span>
        </div>
        <div class="guide-row">
          <a-tag color="green">CPU</a-tag>
          <span>{{ templatePlan.cpuGuide }}</span>
        </div>
        <a-table
          size="small"
          :pagination="false"
          :columns="paramColumns"
          :data-source="templatePlan.params"
          row-key="flag"
          style="margin-top: 8px"
        />
        <div style="font-size: 12px; color: #888; margin-top: 8px">
          保存应用后，<code>startScript</code> / <code>stopScript</code> / <code>jvmOpts</code> 写入数据库；部署时由 Server 下发至 Agent，在部署目录生成 <code>start.sh</code> / <code>stop.sh</code> 并执行。
        </div>
      </template>

      <a-collapse v-else ghost style="margin-top: 4px">
        <a-collapse-panel key="guide" header="参数说明（填入前可先阅读）">
          <p class="guide-intro">以下为各环境通用说明；点击「一键填入」后将根据所选节点给出<strong>推荐值</strong>与<strong>可调范围</strong>。</p>
          <a-table
            size="small"
            :pagination="false"
            :columns="staticParamColumns"
            :data-source="staticParamGuide"
            row-key="flag"
          />
          <a-divider style="margin: 12px 0" />
          <div v-for="profile in envProfiles" :key="profile.key" class="env-block">
            <div class="env-block-title">{{ profile.icon }} {{ profile.label }}</div>
            <p>{{ profile.summary }}</p>
            <ul>
              <li v-for="(item, idx) in profile.whenToTune" :key="idx">{{ item }}</li>
            </ul>
          </div>
        </a-collapse-panel>
      </a-collapse>
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
            <a-input v-model:value="formState.deployDir" :placeholder="defaultDeployDir || '/app/data/apps/应用名'" />
            <template #extra><span style="font-size:12px;color:#888">全局根目录: {{ globalPaths?.deployBaseDir || '加载中...' }}，Jar 和脚本存放目录</span></template>
          </a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="前端部署目录（dist.zip 解压目标）">
            <a-input v-model:value="formState.frontendDeployDir" :placeholder="defaultFrontendDir || '留空则使用 部署目录/frontend'" />
            <template #extra><span style="font-size:12px;color:#888">上传 dist.zip 后解压到此目录，供 Nginx 等静态服务使用</span></template>
          </a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :span="24">
          <a-form-item label="JVM 参数（G1，保存至数据库）">
            <template #extra><span style="font-size:12px;color:#888">一键填入会自动生成；修改后请同步更新 start.sh 中的 java 参数行</span></template>
            <a-textarea v-model:value="formState.jvmOpts" :rows="2" placeholder="-Xms512m -Xmx1024m -XX:+UseG1GC ..." style="font-family:'JetBrains Mono',monospace;font-size:12px" />
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
import { getGlobalPaths, type GlobalPaths } from '../api/system'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import {
  SaveOutlined,
  FolderOpenOutlined,
  ThunderboltOutlined,
  InfoCircleOutlined
} from '@ant-design/icons-vue'
import {
  buildJvmTemplatePlan,
  getEnvProfile,
  type EnvType,
  type JvmTemplatePlan
} from '../utils/jvmTemplate'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()
const nodeOptions = ref<NodeModel[]>([])
const isEdit = computed(() => !!route.params.id)

// 模板相关
const templateEnv = ref<EnvType>('dev')
const templateNodeId = ref<string>('')
const templateLoading = ref(false)
const nodeSpecs = ref<{ cpuCores: number; totalMemoryMB: number } | null>(null)
const templatePlan = ref<JvmTemplatePlan | null>(null)
const globalPaths = ref<GlobalPaths | null>(null)

const currentEnvProfile = computed(() => getEnvProfile(templateEnv.value))

const envProfiles = computed(() => [
  getEnvProfile('dev'),
  getEnvProfile('test'),
  getEnvProfile('prod')
])

const paramColumns = [
  { title: '参数', dataIndex: 'flag', key: 'flag', width: 200 },
  { title: '推荐值', dataIndex: 'recommended', key: 'recommended', width: 100 },
  { title: '可调范围', dataIndex: 'range', key: 'range', width: 160 },
  { title: '作用', dataIndex: 'purpose', key: 'purpose', width: 120 },
  { title: '为何如此设置', dataIndex: 'reason', key: 'reason' }
]

const staticParamColumns = [
  { title: '参数', dataIndex: 'flag', key: 'flag', width: 180 },
  { title: '作用', dataIndex: 'purpose', key: 'purpose', width: 120 },
  { title: '说明', dataIndex: 'reason', key: 'reason' }
]

const staticParamGuide = [
  { flag: '-Xms / -Xmx', purpose: '堆内存', reason: 'dev 可 Xms<Xmx；test/prod 建议 Xms=Xmx。总堆不超过物理内存 70%，并为 OS、元空间、直接内存留余量。' },
  { flag: '-XX:+UseG1GC', purpose: '垃圾回收器', reason: 'Java 8 服务端默认推荐 G1，停顿可控，运维成本低。' },
  { flag: '-XX:MaxGCPauseMillis', purpose: '停顿目标', reason: 'prod 100ms、dev/test 200ms；是目标而非硬上限。' },
  { flag: '-XX:+ExitOnOutOfMemoryError', purpose: 'OOM 退出', reason: '配合监控/编排自动拉起，避免僵死进程。' },
  { flag: '-XX:+HeapDumpOnOutOfMemoryError', purpose: 'OOM 转储', reason: 'test/prod 开启；注意 dump 体积约等于堆大小。' },
  { flag: '-Dfile.encoding=UTF-8', purpose: '字符集', reason: '避免 Linux 默认编码导致日志与接口乱码。' }
]

const defaultDeployDir = computed(() => {
  if (!globalPaths.value) return ''
  const name = formState.value.name || 'app'
  const slug = name.toLowerCase().replace(/\s+/g, '-')
  return `${globalPaths.value.deployBaseDir}/${slug}`
})

const defaultFrontendDir = computed(() => {
  const base = formState.value.deployDir || defaultDeployDir.value
  if (!base || !globalPaths.value) return ''
  return `${base}/${globalPaths.value.frontendSubDir}`
})

const formState = ref<any>({
  name: '',
  jarName: '',
  deployDir: '',
  frontendDeployDir: '',
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

/** 应用一键填入结果到表单 */
function applyTemplatePlan(plan: JvmTemplatePlan, jarName: string) {
  templatePlan.value = plan
  formState.value.jvmOpts = plan.jvmOpts
  formState.value.startScript = plan.startScript
  formState.value.stopScript = plan.stopScript
  formState.value.deployDir = defaultDeployDir.value
  formState.value.frontendDeployDir = defaultFrontendDir.value
  formState.value.jarName = jarName
}

/** 一键填入模板 */
async function fillTemplate() {
  if (!templateEnv.value || !templateNodeId.value) return
  templateLoading.value = true

  const jarName = formState.value.jarName || 'app.jar'
  const fallbackHardware = { cpuCores: 2, totalMemoryMB: 4096 }

  try {
    const res = await getNodeSysInfo(templateNodeId.value)
    const specs = res.data
    nodeSpecs.value = { cpuCores: specs.cpuCores, totalMemoryMB: specs.totalMemoryMB }
    const plan = buildJvmTemplatePlan(
      templateEnv.value,
      { cpuCores: specs.cpuCores || 2, totalMemoryMB: specs.totalMemoryMB || 4096 },
      jarName
    )
    applyTemplatePlan(plan, jarName)
  } catch {
    nodeSpecs.value = fallbackHardware
    const plan = buildJvmTemplatePlan(templateEnv.value, fallbackHardware, jarName)
    applyTemplatePlan(plan, jarName)
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
  try {
    const gp = await getGlobalPaths()
    globalPaths.value = gp.data
  } catch { /* ignore */ }

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

<style scoped>
.template-card {
  background: var(--eo-bg-muted);
}
.env-desc {
  margin-bottom: 6px;
}
.env-list {
  margin: 0 0 8px 18px;
  padding: 0;
  font-size: 13px;
}
.env-meta {
  font-size: 12px;
  color: #666;
  line-height: 1.6;
}
.guide-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
}
.guide-intro {
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
}
.env-block {
  margin-bottom: 12px;
  font-size: 13px;
}
.env-block-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.env-block ul {
  margin: 4px 0 0 18px;
  padding: 0;
}
</style>
