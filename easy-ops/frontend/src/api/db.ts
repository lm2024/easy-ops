import request from '../utils/request'
import type { Result } from '../types'

/** 列出所有表 */
export function listTables() {
  return request.get<any, Result<any[]>>('/db/tables')
}

/** 表结构详情 */
export function getTableStructure(tableName: string) {
  return request.get<any, Result<any>>(`/db/table/${tableName}`)
}

/** 分页查询数据 */
export function queryTableData(tableName: string, page = 1, pageSize = 50, search?: string) {
  const params: any = { page, pageSize }
  if (search) params.search = search
  return request.get<any, Result<any>>(`/db/table/${tableName}/data`, { params })
}

/** 新增行 */
export function insertRow(tableName: string, row: Record<string, any>) {
  return request.post<any, Result<any>>(`/db/table/${tableName}/data`, row)
}

/** 更新行 */
export function updateRow(tableName: string, id: string, row: Record<string, any>) {
  return request.put<any, Result<any>>(`/db/table/${tableName}/data/${id}`, row)
}

/** 删除行 */
export function deleteRow(tableName: string, id: string) {
  return request.delete<any, Result<any>>(`/db/table/${tableName}/data/${id}`)
}

/** 导出表数据 */
export function exportTableData(tableName: string) {
  return request.get<any, Result<any>>(`/db/table/${tableName}/export`)
}

/** 导入数据 */
export function importTableData(tableName: string, mode: string, rows: Record<string, any>[]) {
  return request.post<any, Result<any>>(`/db/table/${tableName}/import?mode=${mode}`, { rows })
}

/** 全量导出所有表 */
export function exportAllData() {
  return request.get<any, Result<any>>('/db/export-all')
}

/** 全量导入所有表 */
export function importAllData(mode: string, tables: Record<string, any>) {
  return request.post<any, Result<any>>(`/db/import-all?mode=${mode}`, { tables })
}
