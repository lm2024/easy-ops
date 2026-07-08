import { ref } from 'vue'
import type { KbDocumentModel } from '../types'
import { fullTextSearch, advancedSearch } from '../api/knowledge-search'

/**
 * 搜索 composable — 封装搜索 API 调用、筛选逻辑和搜索状态管理
 */
export function useSearch() {
  const query = ref('')
  const results = ref<KbDocumentModel[]>([])
  const total = ref(0)
  const loading = ref(false)
  const highlights = ref<string[]>([])

  /** 执行全文搜索 */
  async function search(searchQuery: string, page = 1, size = 20) {
    query.value = searchQuery
    loading.value = true
    try {
      const res = await fullTextSearch(searchQuery, page, size)
      results.value = res.data?.list || []
      total.value = res.data?.total || 0
      highlights.value = res.data?.highlights || []
    } catch {
      results.value = []
      total.value = 0
      highlights.value = []
    } finally {
      loading.value = false
    }
  }

  /** 执行高级搜索（含分类、标签筛选） */
  async function searchAdvanced(params: {
    query?: string
    categoryId?: number
    tags?: string
    page?: number
    size?: number
  }) {
    loading.value = true
    try {
      const res = await advancedSearch(params)
      results.value = res.data?.list || []
      total.value = res.data?.total || 0
    } catch {
      results.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  /** 清空搜索结果 */
  function clearSearch() {
    query.value = ''
    results.value = []
    total.value = 0
    highlights.value = []
  }

  return {
    query,
    results,
    total,
    loading,
    highlights,
    search,
    searchAdvanced,
    clearSearch
  }
}
