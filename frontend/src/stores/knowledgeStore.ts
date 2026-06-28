import { defineStore } from 'pinia'
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import type {
  KbCategoryModel,
  KbDocumentModel,
  KbTagModel,
  KbTemplateModel,
  KbCommentModel,
  KbDocumentPermissionModel,
  KbShareLinkModel,
  KbFavoriteModel,
  KbRecentAccessModel
} from '../types'
import {
  listCategories, createCategory, updateCategory, deleteCategory,
  listDocuments, createDocument, getDocument, updateDocument, deleteDocument,
  listComments, addComment,
  listFavorites, addFavorite, removeFavorite,
  listRecentAccess
} from '../api/knowledge'
import { listTags, createTag, addDocumentTag, removeDocumentTag, getDocumentTags } from '../api/knowledge-tag'
import { listTemplates, createFromTemplate } from '../api/knowledge-template'

export const useKnowledgeStore = defineStore('knowledge', () => {
  // ====== 分类 ======
  const categories = ref<KbCategoryModel[]>([])
  const currentCategoryId = ref<number | null>(null)

  // ====== 文档 ======
  const documents = ref<KbDocumentModel[]>([])
  const currentDocument = ref<KbDocumentModel | null>(null)
  const editorMode = ref<'richtext' | 'markdown'>('richtext')
  const saving = ref(false)

  // ====== 标签 ======
  const tags = ref<KbTagModel[]>([])
  const selectedTagId = ref<number | null>(null)

  // ====== 模板 ======
  const templates = ref<KbTemplateModel[]>([])

  // ====== 评论 ======
  const comments = ref<KbCommentModel[]>([])

  // ====== 权限 ======
  const permissions = ref<KbDocumentPermissionModel[]>([])

  // ====== 收藏 ======
  const favorites = ref<KbFavoriteModel[]>([])

  // ====== 最近访问 ======
  const recentAccessList = ref<KbRecentAccessModel[]>([])

  // ====== 分享 ======
  const shareLinks = ref<KbShareLinkModel[]>([])

  // ====== 协作 ======
  const onlineUsers = ref<number[]>([])

  // ====== 搜索 ======
  const searchQuery = ref('')
  const searchResults = ref<KbDocumentModel[]>([])
  const searchTotal = ref(0)

  // ====== 视图模式 ======
  /** 当前左侧面板视图：category / favorites / recent */
  const currentView = ref<'category' | 'favorites' | 'recent'>('category')

  // ====== 分类 actions ======

  /** 获取分类树 */
  async function fetchCategories() {
    try {
      const res = await listCategories()
      categories.value = res.data || []
    } catch (e: any) {
      message.error('获取分类失败: ' + (e.message || '未知错误'))
    }
  }

  /** 创建分类 */
  async function addCategory(data: Partial<KbCategoryModel>) {
    try {
      await createCategory(data)
      await fetchCategories()
      message.success('分类已创建')
    } catch (e: any) {
      message.error('创建分类失败: ' + (e.message || '未知错误'))
    }
  }

  /** 更新分类 */
  async function updateCategoryAction(id: number, data: Partial<KbCategoryModel>) {
    try {
      await updateCategory(id, data)
      await fetchCategories()
      message.success('分类已更新')
    } catch (e: any) {
      message.error('更新分类失败: ' + (e.message || '未知错误'))
    }
  }

  /** 删除分类 */
  async function deleteCategoryAction(id: number) {
    try {
      await deleteCategory(id)
      await fetchCategories()
      // 如果删除的是当前选中分类，清空关联数据
      if (currentCategoryId.value === id) {
        currentCategoryId.value = null
        documents.value = []
        currentDocument.value = null
      }
      message.success('分类已删除')
    } catch (e: any) {
      message.error('删除分类失败: ' + (e.message || '未知错误'))
    }
  }

  // ====== 文档 actions ======

  /** 获取分类下的文档列表 */
  async function fetchDocuments(categoryId: number) {
    try {
      const res = await listDocuments(categoryId)
      documents.value = res.data?.list || []
      currentCategoryId.value = categoryId
    } catch (e: any) {
      message.error('获取文档列表失败: ' + (e.message || '未知错误'))
    }
  }

  /** 获取文档详情 */
  async function fetchDocument(id: number) {
    try {
      const res = await getDocument(id)
      currentDocument.value = res.data
    } catch (e: any) {
      message.error('获取文档详情失败: ' + (e.message || '未知错误'))
    }
  }

  /** 保存文档（新建或更新） */
  async function saveDocument() {
    if (!currentDocument.value) return
    saving.value = true
    try {
      if (currentDocument.value.id) {
        // 更新已有文档
        const res = await updateDocument(currentDocument.value.id, {
          title: currentDocument.value.title,
          content: currentDocument.value.content,
          categoryId: currentDocument.value.categoryId,
          status: currentDocument.value.status,
          versionNo: currentDocument.value.versionNo
        })
        currentDocument.value = res.data
        message.success('文档已保存')
      } else {
        // 创建新文档
        const res = await createDocument({
          categoryId: currentDocument.value.categoryId,
          title: currentDocument.value.title,
          content: currentDocument.value.content || ''
        })
        currentDocument.value = res.data
        message.success('文档已创建')
        // 重新加载文档列表
        if (currentCategoryId.value) {
          await fetchDocuments(currentCategoryId.value)
        }
      }
    } catch (e: any) {
      message.error('保存文档失败: ' + (e.message || '未知错误'))
    } finally {
      saving.value = false
    }
  }

  /** 删除文档 */
  async function deleteDoc(id: number) {
    try {
      await deleteDocument(id)
      if (currentCategoryId.value) {
        await fetchDocuments(currentCategoryId.value)
      }
      if (currentDocument.value?.id === id) {
        currentDocument.value = null
      }
      message.success('文档已删除')
    } catch (e: any) {
      message.error('删除文档失败: ' + (e.message || '未知错误'))
    }
  }

  // ====== 评论 actions ======

  /** 获取文档评论 */
  async function fetchComments(documentId: number) {
    try {
      const res = await listComments(documentId)
      comments.value = res.data || []
    } catch (e: any) {
      message.error('获取评论失败: ' + (e.message || '未知错误'))
    }
  }

  /** 添加评论 */
  async function addCommentAction(documentId: number, content: string) {
    try {
      await addComment(documentId, { content })
      await fetchComments(documentId)
      message.success('评论已发表')
    } catch (e: any) {
      message.error('发表评论失败: ' + (e.message || '未知错误'))
    }
  }

  // ====== 标签 actions ======

  /** 获取所有标签 */
  async function fetchTags() {
    try {
      const res = await listTags()
      tags.value = res.data || []
    } catch (e: any) {
      message.error('获取标签失败: ' + (e.message || '未知错误'))
    }
  }

  /** 创建标签 */
  async function addTag(data: { name: string; color?: string }) {
    try {
      await createTag(data)
      await fetchTags()
      message.success('标签已创建')
    } catch (e: any) {
      message.error('创建标签失败: ' + (e.message || '未知错误'))
    }
  }

  /** 为文档添加标签 */
  async function addTagToDocument(docId: number, tagId: number) {
    try {
      await addDocumentTag(docId, tagId)
      message.success('标签已添加')
    } catch (e: any) {
      message.error('添加标签失败: ' + (e.message || '未知错误'))
    }
  }

  /** 移除文档标签 */
  async function removeTagFromDocument(docId: number, tagId: number) {
    try {
      await removeDocumentTag(docId, tagId)
      message.success('标签已移除')
    } catch (e: any) {
      message.error('移除标签失败: ' + (e.message || '未知错误'))
    }
  }

  /** 获取文档标签 */
  async function fetchDocumentTags(docId: number) {
    try {
      const res = await getDocumentTags(docId)
      // 将标签信息合并到当前文档
      if (currentDocument.value && currentDocument.value.id === docId) {
        // 标签已通过 API 获取，可在组件中使用
      }
      return res.data || []
    } catch (e: any) {
      message.error('获取文档标签失败: ' + (e.message || '未知错误'))
      return []
    }
  }

  // ====== 模板 actions ======

  /** 获取模板列表 */
  async function fetchTemplates(category?: string) {
    try {
      const res = await listTemplates(category)
      templates.value = res.data || []
    } catch (e: any) {
      message.error('获取模板失败: ' + (e.message || '未知错误'))
    }
  }

  /** 从模板创建文档 */
  async function createFromTemplateAction(templateId: number, categoryId: number) {
    try {
      const res = await createFromTemplate(templateId, categoryId)
      message.success('文档已从模板创建')
      // 重新加载文档列表
      if (currentCategoryId.value) {
        await fetchDocuments(currentCategoryId.value)
      }
      // 加载新创建的文档
      const newDocId = res.data
      if (newDocId) {
        await fetchDocument(newDocId)
      }
      return newDocId
    } catch (e: any) {
      message.error('从模板创建失败: ' + (e.message || '未知错误'))
      return null
    }
  }

  // ====== 收藏 actions ======

  /** 切换收藏状态 */
  async function toggleFavorite(docId: number) {
    try {
      // 检查是否已收藏
      const isFav = favorites.value.some(f => f.documentId === docId)
      if (isFav) {
        await removeFavorite(docId)
        message.success('已取消收藏')
      } else {
        await addFavorite(docId)
        message.success('已收藏')
      }
      await fetchFavorites()
    } catch (e: any) {
      message.error('收藏操作失败: ' + (e.message || '未知错误'))
    }
  }

  /** 获取收藏列表 */
  async function fetchFavorites() {
    try {
      const res = await listFavorites()
      favorites.value = res.data || []
    } catch (e: any) {
      message.error('获取收藏列表失败: ' + (e.message || '未知错误'))
    }
  }

  // ====== 最近访问 actions ======

  /** 获取最近访问列表 */
  async function fetchRecentAccess(limit = 20) {
    try {
      const res = await listRecentAccess(limit)
      recentAccessList.value = res.data || []
    } catch (e: any) {
      message.error('获取最近访问失败: ' + (e.message || '未知错误'))
    }
  }

  // ====== setter（保留，供外部直接赋值使用） ======

  function setCategories(data: KbCategoryModel[]) {
    categories.value = data
  }

  function setCurrentCategoryId(id: number | null) {
    currentCategoryId.value = id
  }

  function setDocuments(data: KbDocumentModel[]) {
    documents.value = data
  }

  function setCurrentDocument(doc: KbDocumentModel | null) {
    currentDocument.value = doc
  }

  function setEditorMode(mode: 'richtext' | 'markdown') {
    editorMode.value = mode
  }

  function setTags(data: KbTagModel[]) {
    tags.value = data
  }

  function setTemplates(data: KbTemplateModel[]) {
    templates.value = data
  }

  function setCurrentView(view: 'category' | 'favorites' | 'recent') {
    currentView.value = view
  }

  return {
    // state
    categories,
    currentCategoryId,
    documents,
    currentDocument,
    editorMode,
    saving,
    tags,
    selectedTagId,
    templates,
    comments,
    permissions,
    favorites,
    recentAccessList,
    shareLinks,
    onlineUsers,
    searchQuery,
    searchResults,
    searchTotal,
    currentView,

    // setters
    setCategories,
    setCurrentCategoryId,
    setDocuments,
    setCurrentDocument,
    setEditorMode,
    setTags,
    setTemplates,
    setCurrentView,

    // async actions
    fetchCategories,
    addCategory,
    updateCategoryAction,
    deleteCategoryAction,
    fetchDocuments,
    fetchDocument,
    saveDocument,
    deleteDoc,
    fetchComments,
    addCommentAction,
    fetchTags,
    addTag,
    addTagToDocument,
    removeTagFromDocument,
    fetchDocumentTags,
    fetchTemplates,
    createFromTemplateAction,
    toggleFavorite,
    fetchFavorites,
    fetchRecentAccess
  }
})
