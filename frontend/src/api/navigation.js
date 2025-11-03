/**
 * 导航相关API接口
 * 提供导航分类和站点的数据获取功能
 * 
 * @author YunBQ
 * @since 2024-11-01
 */

import { http } from './http'

/**
 * 获取所有启用的一级分类
 * @returns {Promise} 返回一级分类列表
 *
 * 重要说明：axios 实例 `http` 的 baseURL 已设置为 `http://localhost:8080/api`，
 * 因此前端这里的路径不应再包含 `/api` 前缀，否则会形成 `.../api/api/...` 的错误地址。
 * 为避免重复前缀导致的 404，这里统一使用不含 `/api` 前缀的路径。
 */
export function getRootCategories() {
  // 正确路径：/navigation/categories（由 baseURL 拼接为 http://localhost:8080/api/navigation/categories）
  return http.get('/navigation/categories')
}

/**
 * 获取指定分类的子分类
 * @param {number} parentId 父分类ID
 * @returns {Promise} 返回子分类列表
 */
export function getSubCategories(parentId) {
  // 子分类查询，同样去掉重复的 /api 前缀
  return http.get(`/navigation/categories/${parentId}/children`)
}

/**
 * 获取所有启用的分类（包含一级和二级）
 * @returns {Promise} 返回所有启用分类列表
 */
export function getAllEnabledCategories() {
  // 获取所有启用分类（含一级与二级），用于侧栏与广场区动态渲染
  return http.get('/navigation/categories/all')
}

/**
 * 根据分类ID获取站点列表
 * @param {number} categoryId 分类ID
 * @returns {Promise} 返回站点列表
 */
export function getSitesByCategory(categoryId) {
  // 根据分类ID获取站点列表，用于卡片内容数据源
  return http.get(`/navigation/sites/category/${categoryId}`)
}

/**
 * 获取推荐站点
 * @param {number} limit 限制数量，默认10
 * @returns {Promise} 返回推荐站点列表
 */
export function getFeaturedSites(limit = 10) {
  // 推荐站点列表
  return http.get('/navigation/sites/featured', { params: { limit } })
}

/**
 * 获取热门站点
 * @param {number} limit 限制数量，默认10
 * @returns {Promise} 返回热门站点列表
 */
export function getPopularSites(limit = 10) {
  // 热门站点列表
  return http.get('/navigation/sites/popular', { params: { limit } })
}

/**
 * 搜索站点
 * @param {string} keyword 搜索关键词
 * @param {number} page 页码，默认1
 * @param {number} size 每页大小，默认10
 * @returns {Promise} 返回搜索结果
 */
export function searchSites(keyword, page = 1, size = 10) {
  // 关键词搜索站点
  return http.get('/navigation/sites/search', {
    params: { keyword, page, size }
  })
}

/**
 * 根据标签搜索站点
 * @param {string} tags 标签，多个标签用逗号分隔
 * @param {number} limit 限制数量，默认10
 * @returns {Promise} 返回站点列表
 */
export function searchByTags(tags, limit = 10) {
  // 标签搜索站点
  return http.get(`/navigation/sites/tags/${tags}`, { params: { limit } })
}

/**
 * 增加站点点击次数
 * @param {number} id 站点ID
 * @returns {Promise} 返回更新后的站点信息
 */
export function incrementClickCount(id) {
  // 增加点击次数
  return http.post(`/navigation/sites/${id}/click`)
}

/**
 * 获取用户添加的站点（需要登录）
 * @returns {Promise} 返回用户站点列表
 */
export function getUserSites() {
  // 获取当前用户添加的站点
  return http.get('/navigation/sites/my')
}

/**
 * 用户创建导航站点（需要登录）
 * @param {Object} site 站点信息
 * @returns {Promise} 返回创建的站点信息
 */
export function createUserSite(site) {
  // 修正：baseURL 已包含 /api 前缀，这里不再重复添加
  return http.post('/navigation/sites', site)
}

// ==================== 管理员接口 ====================

/**
 * 分页查询导航分类（管理员接口）
 * @param {Object} params 查询参数
 * @returns {Promise} 返回分页结果
 */
export function listCategories(params = {}) {
  // 管理员：分页查询导航分类
  return http.get('/navigation/admin/categories', { params })
}

/**
 * 根据ID获取导航分类
 * @param {number} id 分类ID
 * @returns {Promise} 返回分类信息
 */
export function getCategoryById(id) {
  // 根据ID获取导航分类
  return http.get(`/navigation/categories/${id}`)
}

/**
 * 创建导航分类（管理员接口）
 * @param {Object} category 分类信息
 * @returns {Promise} 返回创建的分类信息
 */
export function createCategory(category) {
  // 管理员：创建导航分类
  return http.post('/navigation/admin/categories', category)
}

/**
 * 更新导航分类（管理员接口）
 * @param {number} id 分类ID
 * @param {Object} category 分类信息
 * @returns {Promise} 返回更新的分类信息
 */
export function updateCategory(id, category) {
  // 管理员：更新导航分类
  return http.put(`/navigation/admin/categories/${id}`, category)
}

/**
 * 删除导航分类（管理员接口）
 * @param {number} id 分类ID
 * @returns {Promise} 返回删除结果
 */
export function deleteCategory(id) {
  // 管理员：删除导航分类
  return http.delete(`/navigation/admin/categories/${id}`)
}

/**
 * 切换分类启用状态（管理员接口）
 * @param {number} id 分类ID
 * @returns {Promise} 返回更新的分类信息
 */
export function toggleCategoryEnabled(id) {
  // 管理员：启用/停用分类
  return http.patch(`/navigation/admin/categories/${id}/toggle`)
}

/**
 * 批量更新分类排序（管理员接口）
 * @param {Array} categoryIds 分类ID数组
 * @param {number} parentId 父分类ID
 * @returns {Promise} 返回更新结果
 */
export function updateCategoriesOrder(categoryIds, parentId = null) {
  // 管理员：批量更新分类排序
  return http.put('/navigation/admin/categories/order', {
    categoryIds,
    parentId
  })
}

/**
 * 导出全部分类（管理员接口）
 * @param {('csv'|'json')} format 导出格式，默认 'csv'
 * @returns {Promise<Blob>} 返回二进制文件数据（Blob）
 *
 * 重要说明：
 * - 后端响应头使用 `Content-Disposition: attachment` 指定文件名，`Content-Type` 为
 *   CSV: `text/csv; charset=UTF-8`（含 UTF-8 BOM，保证 Windows Excel 中文不乱码）
 *   JSON: `application/octet-stream`（避免浏览器对 `application/json` 的特殊处理）
 * - 前端通过 `responseType: 'blob'` 获取二进制数据，直接用于下载，不进行二次包装。
 */
export function exportAllCategories(format = 'csv') {
  return http.get('/navigation/admin/categories/export', {
    responseType: 'blob',
    params: { format }
  })
}

/**
 * 分页查询导航站点（管理员接口）
 * @param {Object} params 查询参数
 * @returns {Promise} 返回分页结果
 */
export function listSites(params = {}) {
  // 管理员：分页查询站点
  return http.get('/navigation/admin/sites', { params })
}

/**
 * 根据ID获取导航站点
 * @param {number} id 站点ID
 * @returns {Promise} 返回站点信息
 */
export function getSiteById(id) {
  // 根据ID获取站点
  return http.get(`/navigation/sites/${id}`)
}

/**
 * 创建导航站点（管理员接口）
 * @param {Object} site 站点信息
 * @returns {Promise} 返回创建的站点信息
 */
export function createSite(site) {
  // 管理员：创建站点
  return http.post('/navigation/admin/sites', site)
}

/**
 * 更新导航站点（管理员接口）
 * @param {number} id 站点ID
 * @param {Object} site 站点信息
 * @returns {Promise} 返回更新的站点信息
 */
export function updateSite(id, site) {
  // 管理员：更新站点
  return http.put(`/navigation/admin/sites/${id}`, site)
}

/**
 * 删除导航站点（管理员接口）
 * @param {number} id 站点ID
 * @returns {Promise} 返回删除结果
 */
export function deleteSite(id) {
  // 管理员：删除站点
  return http.delete(`/navigation/admin/sites/${id}`)
}

/**
 * 切换站点启用状态（管理员接口）
 * @param {number} id 站点ID
 * @returns {Promise} 返回更新的站点信息
 */
export function toggleSiteEnabled(id) {
  // 管理员：启用/停用站点
  return http.patch(`/navigation/admin/sites/${id}/toggle`)
}

/**
 * 切换站点推荐状态（管理员接口）
 * @param {number} id 站点ID
 * @returns {Promise} 返回更新的站点信息
 */
export function toggleSiteFeatured(id) {
  // 管理员：推荐/取消推荐站点
  return http.patch(`/navigation/admin/sites/${id}/featured`)
}

/**
 * 批量更新站点排序（管理员接口）
 * @param {Array} siteIds 站点ID数组
 * @param {number} categoryId 分类ID
 * @returns {Promise} 返回更新结果
 */
export function updateSitesOrder(siteIds, categoryId = null) {
  return http.put('/api/navigation/admin/sites/order', {
    siteIds,
    categoryId
  })
}

/**
 * 导出所有站点（管理员接口）
 * @param {('csv'|'json')} format 导出格式，默认 'csv'
 * @returns {Promise<Blob>} 返回文件二进制内容（Axios `data` 为 Blob）
 *
 * 说明：
 * - 后端接口路径为 /navigation/admin/sites/export；
 * - 通过 responseType: 'blob' 获取二进制，以便在前端触发下载；
 * - 文件名及 MIME 类型在前端由调用方决定（CSV 为 text/csv，JSON 为 application/json）。
 */
export function exportAllSites(format = 'csv') {
  return http.get('/navigation/admin/sites/export', {
    params: { format },
    responseType: 'blob'
  })
}