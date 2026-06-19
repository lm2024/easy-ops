import request from '../utils/request'

/** 获取节点系统硬件信息（CPU、内存等） */
export function getNodeSysInfo(nodeId: string) {
  return request.get<any, Result<{ cpuCores: number; totalMemoryMB: number; osName: string; osVersion: string; osArch: string; javaVersion: string; jvmMaxHeapMB: number }>>(`/agent/${nodeId}/sys-info`)
}
