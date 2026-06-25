<template>
  <div>
    <a-card :bordered="false" style="border-radius: 8px">
      <template #title>
        <a-space>
          <book-outlined style="color: #722ed1" />
          <span style="font-weight: 600">知识库</span>
        </a-space>
      </template>
      <template #extra>
        <a-input-search
          v-model:value="searchQ"
          placeholder="搜索文档..."
          style="width: 240px"
          @search="handleSearch"
        />
      </template>

      <a-row :gutter="16">
        <a-col :span="5">
          <a-tree
            v-if="categoryTree.length"
            :tree-data="categoryTree"
            :field-names="{ title: 'name', key: 'id', children: 'children' }"
            default-expand-all
            @select="onCategorySelect"
          />
          <a-empty v-else description="暂无分类" />
          <a-button block style="margin-top: 8px" size="small" @click="showAddCategory">
            <plus-outlined /> 新建分类
          </a-button>
        </a-col>

        <a-col :span="7">
          <a-list
            :data-source="documents"
            :loading="docsLoading"
            size="small"
            :locale="{ emptyText: '选择分类查看文档' }"
          >
            <template #renderItem="{ item }">
              <a-list-item
                class="doc-item"
                :class="{ active: currentDoc?.id === item.id }"
                @click="openDocument(item)"
              >
                <a-list-item-meta :title="item.title" :description="item.summary || '无摘要'" />
              </a-list-item>
            </template>
          </a-list>
          <a-button
            v-if="selectedCategoryId"
            block
            type="dashed"
            style="margin-top: 8px"
            @click="createNewDoc"
          >
            <plus-outlined /> 新建文档
          </a-button>
        </a-col>

        <a-col :span="12">
          <template v-if="currentDoc">
            <a-input v-model:value="editTitle" placeholder="文档标题" style="margin-bottom: 8px" />
            <a-textarea
              v-model:value="editContent"
              :rows="14"
              placeholder="Markdown 内容..."
              class="md-editor"
            />
            <a-space style="margin-top: 8px">
              <a-button type="primary" :loading="saving" @click="saveDoc">
                <save-outlined /> 保存
              </a-button>
              <a-upload :show-upload-list="false" :before-upload="handleImageUpload">
                <a-button><picture-outlined /> 上传图片</a-button>
              </a-upload>
              <a-button @click="handleExport"><export-outlined /> 导出</a-button>
            </a-space>

            <a-divider>评论</a-divider>
            <a-list :data-source="comments" size="small">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-rate v-if="item.rating" :value="item.rating" disabled style="font-size: 12px" />
                  <div>{{ item.content }}</div>
                </a-list-item>
              </template>
            </a-list>
            <a-space style="margin-top: 8px">
              <a-input v-model:value="newComment" placeholder="添加评论..." style="width: 300px" />
              <a-button type="primary" size="small" @click="submitComment">发表</a-button>
            </a-space>
          </template>
          <a-empty v-else description="选择或创建文档" />
        </a-col>
      </a-row>
    </a-card>

    <a-modal v-model:open="categoryModalVisible" title="新建分类" @ok="handleAddCategory">
      <a-input v-model:value="newCategoryName" placeholder="分类名称" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { KbCategoryModel, KbDocumentModel, KbCommentModel } from '../types'
import {
  listCategories, createCategory, listDocuments, getDocument,
  createDocument, updateDocument, lockDocument,
  listComments, addComment, uploadDocumentImage, searchDocuments, exportDocument
} from '../api/knowledge'
import {
  BookOutlined, PlusOutlined, SaveOutlined, PictureOutlined, ExportOutlined
} from '@ant-design/icons-vue'

const categoryTree = ref<KbCategoryModel[]>([])
const selectedCategoryId = ref<number>()
const documents = ref<KbDocumentModel[]>([])
const docsLoading = ref(false)
const currentDoc = ref<KbDocumentModel | null>(null)
const editTitle = ref('')
const editContent = ref('')
const saving = ref(false)
const comments = ref<KbCommentModel[]>([])
const newComment = ref('')
const searchQ = ref('')
const categoryModalVisible = ref(false)
const newCategoryName = ref('')

async function loadCategories() {
  const res = await listCategories()
  categoryTree.value = res.data || []
}

function onCategorySelect(keys: (string | number)[]) {
  if (keys.length) {
    selectedCategoryId.value = Number(keys[0])
    loadDocuments()
  }
}

async function loadDocuments() {
  if (!selectedCategoryId.value) return
  docsLoading.value = true
  try {
    const res = await listDocuments(selectedCategoryId.value)
    documents.value = res.data?.list || []
  } finally {
    docsLoading.value = false
  }
}

async function openDocument(doc: KbDocumentModel) {
  const res = await getDocument(doc.id!)
  currentDoc.value = res.data
  editTitle.value = res.data.title
  editContent.value = res.data.content || ''
  try { await lockDocument(doc.id!) } catch { /* lock conflict ok */ }
  const cRes = await listComments(doc.id!)
  comments.value = cRes.data || []
}

function createNewDoc() {
  currentDoc.value = { categoryId: selectedCategoryId.value!, title: '新文档', content: '' }
  editTitle.value = '新文档'
  editContent.value = ''
  comments.value = []
}

async function saveDoc() {
  saving.value = true
  try {
    if (currentDoc.value?.id) {
      await updateDocument(currentDoc.value.id, {
        title: editTitle.value,
        content: editContent.value,
        versionNo: currentDoc.value.versionNo
      })
      message.success('已保存')
    } else if (selectedCategoryId.value) {
      const res = await createDocument({
        categoryId: selectedCategoryId.value,
        title: editTitle.value,
        content: editContent.value
      })
      currentDoc.value = res.data
      message.success('已创建')
      loadDocuments()
    }
  } finally {
    saving.value = false
  }
}

async function handleImageUpload(file: File) {
  if (!currentDoc.value?.id) {
    message.warning('请先保存文档')
    return false
  }
  const res = await uploadDocumentImage(currentDoc.value.id, file)
  const imgUrl = `/api/kb/images/${res.data.id}`
  editContent.value += `\n![${file.name}](${imgUrl})\n`
  message.success('图片已插入')
  return false
}

async function handleExport() {
  if (!currentDoc.value?.id) return
  await exportDocument(currentDoc.value.id, 'md')
}

async function submitComment() {
  if (!currentDoc.value?.id || !newComment.value) return
  await addComment(currentDoc.value.id, { content: newComment.value })
  newComment.value = ''
  const cRes = await listComments(currentDoc.value.id)
  comments.value = cRes.data || []
}

async function handleSearch() {
  if (!searchQ.value) return
  const res = await searchDocuments(searchQ.value)
  documents.value = res.data?.list || []
  selectedCategoryId.value = undefined
}

function showAddCategory() {
  newCategoryName.value = ''
  categoryModalVisible.value = true
}

async function handleAddCategory() {
  if (!newCategoryName.value) return
  await createCategory({ name: newCategoryName.value, parentId: 0, sortOrder: 0 })
  categoryModalVisible.value = false
  loadCategories()
}

onMounted(loadCategories)
</script>

<style scoped>
.doc-item { cursor: pointer; border-radius: 4px; padding: 4px 8px; }
.doc-item.active { background: rgba(114, 46, 209, 0.12); }
.md-editor {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}
</style>
