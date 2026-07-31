import request from '../utils/request'
import type { Result, NginxAccessSourceModel } from '../types'

/** Nginx 流量查询时间范围 */
export interface NginxTimeQuery {
  sourceIds?: number[]
  windowMinutes?: number
  startTime?: number
  endTime?: number
}

export interface NginxPageQuery {
  page?: number
  pageSize?: number
}

export interface NginxRankPageResult {
  list: Record<string, unknown>[]
  total: number
  page: number
  pageSize: number
  sortBy: string
  sortOrder: string
}

function buildTimeParams(query?: NginxTimeQuery) {
  if (!query) return {}
  const params: Record<string, unknown> = {}
  if (query.sourceIds?.length) params.sourceIds = query.sourceIds
  if (query.startTime != null && query.endTime != null) {
    params.startTime = query.startTime
    params.endTime = query.endTime
  } else if (query.windowMinutes != null) {
    params.windowMinutes = query.windowMinutes
  }
  return params
}

export function listNginxSources() {
  return request.get<any, Result<NginxAccessSourceModel[]>>('/nginx-traffic/sources')
}

export function saveNginxSource(source: Partial<NginxAccessSourceModel>) {
  return request.post<any, Result<NginxAccessSourceModel>>('/nginx-traffic/sources', source)
}

export function deleteNginxSource(id: number) {
  return request.delete<any, Result<null>>(`/nginx-traffic/sources/${id}`)
}

export function listNginxAlarmRules(sourceId: number) {
  return request.get<any, Result<import('../types').NginxTrafficAlarmRuleModel[]>>(
    `/nginx-traffic/sources/${sourceId}/alarm-rules`
  )
}

export function saveNginxAlarmRules(sourceId: number, rules: import('../types').NginxTrafficAlarmRuleModel[]) {
  return request.put<any, Result<import('../types').NginxTrafficAlarmRuleModel[]>>(
    `/nginx-traffic/sources/${sourceId}/alarm-rules`,
    rules
  )
}

export function getNginxOverview(query?: NginxTimeQuery) {
  return request.get<any, Result<Record<string, unknown>>>('/nginx-traffic/overview', {
    params: buildTimeParams(query)
  })
}

export function getNginxRankIp(query?: NginxTimeQuery, keyword?: string, pageQuery?: NginxPageQuery) {
  return request.get<any, Result<NginxRankPageResult>>('/nginx-traffic/rank/ip', {
    params: { ...buildTimeParams(query), keyword, page: pageQuery?.page, pageSize: pageQuery?.pageSize }
  })
}

export function getNginxRankUri(query?: NginxTimeQuery, keyword?: string, pageQuery?: NginxPageQuery) {
  return request.get<any, Result<NginxRankPageResult>>('/nginx-traffic/rank/uri', {
    params: { ...buildTimeParams(query), keyword, page: pageQuery?.page, pageSize: pageQuery?.pageSize }
  })
}

export function getNginxRankIpUri(
  query?: NginxTimeQuery,
  clientIp?: string,
  uri?: string,
  pageQuery?: NginxPageQuery
) {
  return request.get<any, Result<NginxRankPageResult>>('/nginx-traffic/rank/ip-uri', {
    params: { ...buildTimeParams(query), clientIp, uri, page: pageQuery?.page, pageSize: pageQuery?.pageSize }
  })
}

export function getNginxRankSlow(query?: NginxTimeQuery, pageQuery?: NginxPageQuery) {
  return request.get<any, Result<NginxRankPageResult>>('/nginx-traffic/rank/slow', {
    params: { ...buildTimeParams(query), page: pageQuery?.page, pageSize: pageQuery?.pageSize }
  })
}

export interface NginxTrendResult {
  granularity: 'minute' | 'day'
  startTime: number
  endTime: number
  points: Record<string, unknown>[]
}

export function getNginxTrend(query?: NginxTimeQuery) {
  return request.get<any, Result<NginxTrendResult>>('/nginx-traffic/trend', {
    params: buildTimeParams(query)
  })
}
