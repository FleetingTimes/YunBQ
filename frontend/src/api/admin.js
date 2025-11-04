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

/**
 * 高级导出用户信息（包含敏感字段：passwordHash）。
 * 使用说明：
 * - 仅管理员可调用；
 * - format 支持 'csv' 或 'json'；
 * - 可选筛选参数 q（按用户名/昵称/邮箱模糊搜索）。
 * 安全提示：导出文件包含密码哈希，务必妥善保存与传输。
 *
 * @param {Object} params 可选筛选参数：{ q }
 * @param {'csv'|'json'} format 导出格式，默认 'csv'
 * @returns {Promise<Blob>} 文件二进制内容
 */
export function exportUsersAdvanced(params = {}, format = 'csv') {
  return http.get('/admin/users/export/advanced', {
    params: { ...params, format },
    responseType: 'blob'
  }).then(res => res.data);
}

/**
 * 批量导入用户数据（管理员接口）
 *
 * 使用 `multipart/form-data` 方式上传一个 JSON 文件，字段名固定为 `file`。
 * 后端会根据 `email` 或 `username` 进行去重，存在则更新，不存在则创建；
 * 并返回导入结果统计：{ total, created, updated, errors: [] }。
 *
 * 约定与注意事项：
 * - JSON 文件内容必须是数组（例如：[{ username, email, nickname, password, role, ... }, ...]）。
 * - 如果需要更新密码，请提供明文密码，后端会统一进行哈希处理；不提供则保留原密码。
 * - 仅管理员可调用该接口；前端页面应在调用后刷新列表数据。
 *
 * @param {File} file 选择的 JSON 文件对象
 * @returns {Promise<{ total: number, created: number, updated: number, errors: Array }>} 导入统计结果
 */
export function importUsers(file) {
  const form = new FormData();
  form.append('file', file);
  // 注意：axios 在上传 FormData 时会自动设置合适的 Content-Type（含边界），无需手动指定
  return http.post('/admin/users/import', form).then(res => res.data);
}

// ==================== 用户管理（CRUD） ====================

/**
 * 创建用户（管理员）
 * 字段说明：
 * - 必填：`username`
 * - 可选：`nickname`, `email`, `signature`, `avatarUrl`, `role`(ADMIN/USER), `password`(明文)
 * - 若提供 `password` 明文，后端将进行 BCrypt 哈希；不提供则创建为“无密码”用户（不可登录）。
 * 返回：后端 UserSummary（不含密码/哈希），用于表格刷新。
 */
export function createUser(data) {
  // 约定后端路径：POST /admin/users
  // 安全性：仅管理员可调用，后端已做权限控制
  return http.post('/admin/users', data).then(res => res.data);
}

/**
 * 更新用户（管理员）
 * 字段说明：
 * - 可更新：`username`, `nickname`, `email`, `signature`, `avatarUrl`, `role`, `password`(明文)
 * - 邮箱与用户名若冲突，后端返回 409；前端需提示并保留表单值。
 * 返回：后端 UserSummary，用于表格刷新。
 */
export function updateUser(id, data) {
  // 约定后端路径：PUT /admin/users/{id}
  return http.put(`/admin/users/${id}`, data).then(res => res.data);
}

/**
 * 删除用户（管理员）
 * 返回：`{ ok: true }`
 */
export function deleteUser(id) {
  // 约定后端路径：DELETE /admin/users/{id}
  return http.delete(`/admin/users/${id}`).then(res => res.data);
}