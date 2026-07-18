<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <notification-outlined style="color: #fa541c" />
          <span style="font-weight: 600">告警配置</span>
        </a-space>
      </template>

      <a-alert type="info" show-icon style="margin-bottom: 16px">
        <template #message>配置监控告警的触发条件和阈值。告警信息会显示在顶部铃铛图标上，所有登录用户可见。</template>
      </a-alert>

      <a-form :model="formState" layout="vertical" @finish="handleSave" style="max-width: 650px">
        <!-- 健康检查告警 -->
        <a-divider orientation="left" style="font-size: 13px; color: #888">🏥 健康检查告警</a-divider>
        <a-form-item label="健康检查失败告警">
          <template #extra>应用进程停止或 HTTP 探针检测失败时触发告警</template>
          <a-switch v-model:checked="formState.healthCheckEnabled" checked-children="开" un-checked-children="关" />
        </a-form-item>

        <!-- CPU 告警 -->
        <a-divider orientation="left" style="font-size: 13px; color: #888">💻 CPU 告警</a-divider>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="CPU 告警开关">
              <a-switch v-model:checked="formState.cpuEnabled" checked-children="开" un-checked-children="关" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="CPU 阈值 (%)">
              <template #extra>主机 CPU 使用率超过此值时触发告警</template>
              <a-input-number v-model:value="formState.cpuThreshold" :min="50" :max="100" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 响应超时告警 -->
        <a-divider orientation="left" style="font-size: 13px; color: #888">⏱️ 响应超时告警</a-divider>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="响应超时告警开关">
              <a-switch v-model:checked="formState.responseEnabled" checked-children="开" un-checked-children="关" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="超时阈值 (ms)">
              <template #extra>HTTP 探针响应时间超过此值时触发告警</template>
              <a-input-number v-model:value="formState.responseThreshold" :min="1000" :max="30000" :step="1000" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 通用配置 -->
        <a-divider orientation="left" style="font-size: 13px; color: #888">⚙️ 通用配置</a-divider>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="告警冷却时间（分钟）">
              <template #extra>同一应用+节点+条件的告警，冷却时间内不重复提醒</template>
              <a-input-number v-model:value="formState.cooldownMinutes" :min="5" :max="1440" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="saving">
            <save-outlined /> 保存配置
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getAlarmConfig, saveAlarmConfig } from '../api/monitor'
import { SaveOutlined, NotificationOutlined } from '@ant-design/icons-vue'

const formState = ref({
  healthCheckEnabled: true,
  cpuEnabled: true,
  cpuThreshold: 90,
  responseEnabled: true,
  responseThreshold: 5000,
  nodeOfflineEnabled: true,
  cooldownMinutes: 30
})
const saving = ref(false)

async function fetchData() {
  const res = await getAlarmConfig()
  if (res.data) {
    formState.value = { ...formState.value, ...res.data }
  }
}

async function handleSave() {
  saving.value = true
  try {
    await saveAlarmConfig(formState.value)
    message.success('告警配置已保存')
  } catch (e: any) {
    message.error('保存失败: ' + (e?.message || ''))
  } finally {
    saving.value = false
  }
}

onMounted(fetchData)
</script>
