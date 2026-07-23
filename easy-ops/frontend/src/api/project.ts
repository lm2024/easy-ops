import request from '../utils/request'
import type { ProjectModel, Result } from '../types'

/** 获取项目列表 */
export function getProjects(page = 1, pageSize = 20, keyword = '') {
  return request.get<any, Result<{ list: ProjectModel[]; total: number }>>('/projects', {
    params: { page, pageSize, keyword }
  })
}

/** 获取项目详情 */
export function getProject(id: string) {
  return request.get<any, Result<ProjectModel>>(`/projects/${id}`)
}

/** 新增项目 */
export function createProject(project: ProjectModel) {
  return request.post<any, Result<ProjectModel>>('/projects', project)
}

/** 更新项目 */
export function updateProject(id: string, project: ProjectModel) {
  return request.put<any, Result<ProjectModel>>(`/projects/${id}`, project)
}

/** 删除项目 */
export function deleteProject(id: string) {
  return request.delete<any, Result>(`/projects/${id}`)
}

/** 对单个节点执行 start / stop / restart（异步，返回 taskId） */
export function operateProjectNode(projectId: string, nodeId: string, action: 'start' | 'stop' | 'restart') {
  return request.post<any, Result<{ taskId: string; action: string; nodeName: string }>>(`/process/${projectId}/${nodeId}/${action}`)
}

/** 查询异步操作任务状态 */
export function getProcessTaskStatus(taskId: string) {
  return request.get<any, Result<{
    status: string
    action: string
    nodeName: string
    projectName: string
    result?: string
    error?: string
  }>>(`/process/task/${taskId}`)
}
