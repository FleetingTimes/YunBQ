// 管理后台相关 API 封装：日志导出
// 说明：统一提供四类日志的导出方法，支持 CSV/JSON；返回值为二进制 Blob，便于页面直接下载。
import { http } from './http';

/**
 * 导出审计日志
 * @param {Object} params 可选筛选参数：{ level }
 * @param {'csv'|'json'} format 导出格式，默认 'csv'
 * @returns {Promise<Blob>}
 */
export function exportAuditLogs(params = {}, format = 'csv') {
  return http.get('/admin/logs/export', { params: { ...params, format }, responseType: 'blob' })
    .then(res => res.data);
}

/**
 * 导出认证日志
 * @param {Object} params 可选筛选参数：{ success, username, requestId }
 * @param {'csv'|'json'} format 导出格式，默认 'csv'
 * @returns {Promise<Blob>}
 */
export function exportAuthLogs(params = {}, format = 'csv') {
  return http.get('/admin/auth-logs/export', { params: { ...params, format }, responseType: 'blob' })
    .then(res => res.data);
}

/**
 * 导出请求日志
 * @param {Object} params 可选筛选参数：{ uri, status, requestId }
 * @param {'csv'|'json'} format 导出格式，默认 'csv'
 * @returns {Promise<Blob>}
 */
export function exportRequestLogs(params = {}, format = 'csv') {
  return http.get('/admin/request-logs/export', { params: { ...params, format }, responseType: 'blob' })
    .then(res => res.data);
}

/**
 * 导出错误日志
 * @param {Object} params 可选筛选参数：{ exception, requestId }
 * @param {'csv'|'json'} format 导出格式，默认 'csv'
 * @returns {Promise<Blob>}
 */
export function exportErrorLogs(params = {}, format = 'csv') {
  return http.get('/admin/error-logs/export', { params: { ...params, format }, responseType: 'blob' })
    .then(res => res.data);
}