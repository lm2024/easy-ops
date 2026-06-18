<template>
  <a-card title="告警配置">
    <a-form :model="formState" layout="vertical" @finish="handleSave">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="SMTP服务器" name="smtpHost">
            <a-input v-model:value="formState.smtpHost" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="SMTP端口" name="smtpPort">
            <a-input-number v-model:value="formState.smtpPort" style="width: 100%" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="发件人邮箱" name="smtpUser">
            <a-input v-model:value="formState.smtpUser" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="邮箱密码" name="smtpPassword">
            <a-input-password v-model:value="formState.smtpPassword" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="接收地址" name="receiveAddress">
        <a-input v-model:value="formState.receiveAddress" placeholder="多个邮箱用逗号分隔" />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="loading">保存</a-button>
          <a-button @click="sendTest">发送测试邮件</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </a-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { AlarmConfigModel } from '../types'
import { getAlarmConfig, saveAlarmConfig } from '../api/monitor'

const formState = ref<Partial<AlarmConfigModel>>({
  smtpHost: '',
  smtpPort: 465,
  smtpUser: '',
  smtpPassword: '',
  receiveAddress: ''
})
const loading = ref(false)

async function fetchData() {
  const res = await getAlarmConfig()
  formState.value = res.data
}

async function handleSave() {
  try {
    loading.value = true
    await saveAlarmConfig(formState.value as AlarmConfigModel)
  } finally {
    loading.value = false
  }
}

async function sendTest() {
  // Placeholder: send test alarm
}

onMounted(fetchData)
</script>
