<template>
  <a-card :bordered="false" style="border-radius: 8px; max-width: 700px">
    <template #title>
      <a-space>
        <bulb-outlined style="color: #722ed1" />
        <span style="font-weight: 600">AI 智能分析配置</span>
        <a-tooltip title="对接公司内网部署的 OpenAI 兼容大模型（vLLM / Ollama / Xinference 等），用于日志分析、异常诊断。">
          <info-circle-outlined style="color: #999; cursor: help" />
        </a-tooltip>
      </a-space>
    </template>

    <a-result v-if="testResult" :status="testResult.status" :title="testResult.title" :sub-title="testResult.subTitle" style="padding: 16px 0">
      <template #extra>
        <a-button size="small" @click="testResult = null">关闭</a-button>
      </template>
    </a-result>

    <a-form layout="vertical" :model="formState">
      <a-form-item label="启用 AI 分析">
        <template #extra>
          <span style="font-size: 12px; color: #888">开启后，部署详情页会增加「AI 分析」按钮，可自动分析日志</span>
        </template>
        <a-switch v-model:checked="formState.enabled" checked-children="开启" un-checked-children="关闭" />
      </a-form-item>

      <a-form-item label="AI 接口地址">
        <template #extra>
          <span style="font-size: 12px; color: #888">
            支持 OpenAI 兼容格式。例如: <code>http://192.168.1.100:8000/v1</code> 或 <code>https://api.openai.com/v1</code>
          </span>
        </template>
        <a-input v-model:value="formState.endpoint" placeholder="http://192.168.1.100:8000/v1" />
      </a-form-item>

      <a-form-item label="模型名称">
        <template #extra>
          <span style="font-size: 12px; color: #888">
            支持的模型名称。例如: <code>gpt-3.5-turbo</code>、<code>qwen2.5:7b</code>、<code>deepseek-chat</code>
          </span>
        </template>
        <a-input v-model:value="formState.model" placeholder="gpt-3.5-turbo" />
      </a-form-item>

      <a-form-item label="API Key">
        <template #extra>
          <span style="font-size: 12px; color: #888">如果 AI 服务需要认证，填写 API Key。内网部署通常不需要。</span>
        </template>
        <a-input-password v-model:value="formState.apiKey" placeholder="sk-..." />
      </a-form-item>

      <a-form-item>
        <a-space>
          <a-button type="primary" @click="saveConfig" :loading="saving">
            <save-outlined /> 保存配置
          </a-button>
          <a-button @click="testConnection" :loading="testing">
            <api-outlined /> 测试连接
          </a-button>
        </a-space>
      </a-form-item>
    </a-form>

    <a-divider />

    <a-alert
      type="info"
      show-icon
      message="💡 使用场景"
      description="1. 在部署详情页点击「AI 分析」可自动分析部署日志中的异常。&#13;2. 在日志查看页选中日志后可使用 AI 分析异常原因。&#13;3. AI 会自动给出修复建议，帮助快速定位问题。"
    />

    <a-divider />

    <a-typography-title :level="5">📋 推荐内网部署方案</a-typography-title>
    <a-typography-paragraph>
      <pre style="background: #f5f5f5; padding: 12px; border-radius: 6px; font-size: 12px">
# 方案一: Ollama（最简单）
curl -fsSL https://ollama.com/install.sh | sh
ollama pull qwen2.5:7b
# 服务地址: http://localhost:11434/v1
# 模型: qwen2.5:7b

# 方案二: vLLM（高吞吐，适合生产）
docker run --gpus all -p 8000:8000 vllm/vllm-openai \
  --model Qwen/Qwen2.5-7B-Instruct
# 服务地址: http://localhost:8000/v1
# 模型: Qwen/Qwen2.5-7B-Instruct</pre>
    </a-typography-paragraph>
  </a-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getAIConfig, saveAIConfig, aiChat } from '../api/ai'
import {
  SaveOutlined,
  ApiOutlined,
  BulbOutlined,
  InfoCircleOutlined
} from '@ant-design/icons-vue'

const saving = ref(false)
const testing = ref(false)
const testResult = ref<{ status: 'success' | 'error'; title: string; subTitle: string } | null>(null)

const formState = ref({
  enabled: false,
  endpoint: '',
  model: '',
  apiKey: ''
})

async function fetchConfig() {
  try {
    const res = await getAIConfig()
    formState.value = {
      enabled: res.data.enabled === 'true',
      endpoint: res.data.endpoint || '',
      model: res.data.model || '',
      apiKey: res.data.apiKey || ''
    }
  } catch {
    // 默认值
  }
}

async function saveConfig() {
  saving.value = true
  try {
    await saveAIConfig({
      enabled: formState.value.enabled ? 'true' : 'false',
      endpoint: formState.value.endpoint,
      model: formState.value.model,
      apiKey: formState.value.apiKey
    })
    message.success('✅ AI 配置已保存')
  } catch {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  if (!formState.value.endpoint) {
    message.warning('请先填写 AI 接口地址')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const res = await aiChat('用一句话回复: 你好，回复"连接成功"即可')
    testResult.value = {
      status: 'success',
      title: '✅ 连接成功',
      subTitle: `AI 回复: ${res.data.reply}`
    }
  } catch (e: any) {
    testResult.value = {
      status: 'error',
      title: '❌ 连接失败',
      subTitle: e.message || '请检查接口地址和网络连通性'
    }
  } finally {
    testing.value = false
  }
}

onMounted(fetchConfig)
</script>
