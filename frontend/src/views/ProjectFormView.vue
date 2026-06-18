<template>
  <a-card title="项目表单">
    <a-form
      ref="formRef"
      :model="formState"
      :rules="rules"
      layout="vertical"
      @finish="handleSubmit"
    >
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="项目名称" name="name">
            <a-input v-model:value="formState.name" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="Jar包名">
            <a-input v-model:value="formState.jarName" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="启动脚本">
        <a-textarea v-model:value="formState.startScript" :rows="2" />
      </a-form-item>
      <a-form-item label="停止脚本">
        <a-textarea v-model:value="formState.stopScript" :rows="2" />
      </a-form-item>
      <a-form-item label="重启脚本">
        <a-textarea v-model:value="formState.restartScript" :rows="2" />
      </a-form-item>
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="JVM参数">
            <a-input v-model:value="formState.jvmOpts" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="环境变量">
            <a-input v-model:value="formState.envVars" placeholder="KEY1=value1;KEY2=value2" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="节点ID">
        <a-select v-model:value="formState.nodeIds" mode="multiple" style="width: 100%">
          <a-select-option v-for="n in nodeOptions" :key="n.id" :value="n.id">
            {{ n.name }} ({{ n.ip }})
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="loading">保存</a-button>
          <a-button @click="$router.back()">取消</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </a-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { ProjectModel, NodeModel } from '../types'
import { createProject, updateProject, getProject } from '../api/project'
import { getNodes } from '../api/node'
import type { FormInstance, Rule } from 'ant-design-vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const formRef = ref<FormInstance>()
const nodeOptions = ref<NodeModel[]>([])

const formState = ref<Partial<ProjectModel>>({
  name: '',
  startScript: '',
  stopScript: '',
  restartScript: '',
  jvmOpts: '',
  envVars: '',
  nodeIds: []
})

const rules: Record<string, Rule[]> = {
  name: [{ required: true, message: '请输入项目名称' }]
}

async function handleSubmit() {
  try {
    loading.value = true
    const id = route.params.id as string
    if (id) {
      await updateProject(id, formState.value as ProjectModel)
    } else {
      await createProject(formState.value as ProjectModel)
    }
    router.push('/projects')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const res = await getNodes()
  nodeOptions.value = res.data

  const id = route.params.id as string
  if (id) {
    const project = await getProject(id)
    formState.value = project.data
  }
})
</script>
