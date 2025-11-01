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
 */
export function getRootCategories() {
  return http.get('/api/navigation/categories')
}

/**
 * 获取指定分类的子分类
 * @param {number} parentId 父分类ID
 * @returns {Promise} 返回子分类列表
 */
export function getSubCategories(parentId) {
  return http.get(`/api/navigation/categories/${parentId}/children`)
}

/**
 * 获取所有启用的分类（包含一级和二级）
 * @returns {Promise} 返回所有启用分类列表
 */
export function getAllEnabledCategories() {
  return http.get('/api/navigation/categories/all')
}

/**
 * 根据分类ID获取站点列表
 * @param {number} categoryId 分类ID
 * @returns {Promise} 返回站点列表
 */
export function getSitesByCategory(categoryId) {
  return http.get(`/api/navigation/sites/category/${categoryId}`)
}

/**
 * 获取推荐站点
 * @param {number} limit 限制数量，默认10
 * @returns {Promise} 返回推荐站点列表
 */
export function getFeaturedSites(limit = 10) {
  return http.get('/api/navigation/sites/featured', { params: { limit } })
}

/**
 * 获取热门站点
 * @param {number} limit 限制数量，默认10
 * @returns {Promise} 返回热门站点列表
 */
export function getPopularSites(limit = 10) {
  return http.get('/api/navigation/sites/popular', { params: { limit } })
}

/**
 * 搜索站点
 * @param {string} keyword 搜索关键词
 * @param {number} page 页码，默认1
 * @param {number} size 每页大小，默认10
 * @returns {Promise} 返回搜索结果
 */
export function searchSites(keyword, page = 1, size = 10) {
  return http.get('/api/navigation/sites/search', {
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
  return http.get(`/api/navigation/sites/tags/${tags}`, { params: { limit } })
}

/**
 * 增加站点点击次数
 * @param {number} id 站点ID
 * @returns {Promise} 返回更新后的站点信息
 */
export function incrementClickCount(id) {
  return http.post(`/api/navigation/sites/${id}/click`)
}

/**
 * 获取用户添加的站点（需要登录）
 * @returns {Promise} 返回用户站点列表
 */
export function getUserSites() {
  return http.get('/api/navigation/sites/my')
}

/**
 * 用户创建导航站点（需要登录）
 * @param {Object} site 站点信息
 * @returns {Promise} 返回创建的站点信息
 */
export function createUserSite(site) {
  return http.post('/api/navigation/sites', site)
}

// ==================== 管理员接口 ====================

/**
 * 分页查询导航分类（管理员接口）
 * @param {Object} params 查询参数
 * @returns {Promise} 返回分页结果
 */
export function listCategories(params = {}) {
  return http.get('/api/navigation/admin/categories', { params })
}

/**
 * 根据ID获取导航分类
 * @param {number} id 分类ID
 * @returns {Promise} 返回分类信息
 */
export function getCategoryById(id) {
  return http.get(`/api/navigation/categories/${id}`)
}

/**
 * 创建导航分类（管理员接口）
 * @param {Object} category 分类信息
 * @returns {Promise} 返回创建的分类信息
 */
export function createCategory(category) {
  return http.post('/api/navigation/admin/categories', category)
}

/**
 * 更新导航分类（管理员接口）
 * @param {number} id 分类ID
 * @param {Object} category 分类信息
 * @returns {Promise} 返回更新的分类信息
 */
export function updateCategory(id, category) {
  return http.put(`/api/navigation/admin/categories/${id}`, category)
}

/**
 * 删除导航分类（管理员接口）
 * @param {number} id 分类ID
 * @returns {Promise} 返回删除结果
 */
export function deleteCategory(id) {
  return http.delete(`/api/navigation/admin/categories/${id}`)
}

/**
 * 切换分类启用状态（管理员接口）
 * @param {number} id 分类ID
 * @returns {Promise} 返回更新的分类信息
 */
export function toggleCategoryEnabled(id) {
  return http.patch(`/api/navigation/admin/categories/${id}/toggle`)
}

/**
 * 批量更新分类排序（管理员接口）
 * @param {Array} categoryIds 分类ID数组
 * @param {number} parentId 父分类ID
 * @returns {Promise} 返回更新结果
 */
export function updateCategoriesOrder(categoryIds, parentId = null) {
  return http.put('/api/navigation/admin/categories/order', {
    categoryIds,
    parentId
  })
}

/**
 * 分页查询导航站点（管理员接口）
 * @param {Object} params 查询参数
 * @returns {Promise} 返回分页结果
 */
export function listSites(params = {}) {
  return http.get('/api/navigation/admin/sites', { params })
}

/**
 * 根据ID获取导航站点
 * @param {number} id 站点ID
 * @returns {Promise} 返回站点信息
 */
export function getSiteById(id) {
  return http.get(`/api/navigation/sites/${id}`)
}

/**
 * 创建导航站点（管理员接口）
 * @param {Object} site 站点信息
 * @returns {Promise} 返回创建的站点信息
 */
export function createSite(site) {
  return http.post('/api/navigation/admin/sites', site)
}

/**
 * 更新导航站点（管理员接口）
 * @param {number} id 站点ID
 * @param {Object} site 站点信息
 * @returns {Promise} 返回更新的站点信息
 */
export function updateSite(id, site) {
  return http.put(`/api/navigation/admin/sites/${id}`, site)
}

/**
 * 删除导航站点（管理员接口）
 * @param {number} id 站点ID
 * @returns {Promise} 返回删除结果
 */
export function deleteSite(id) {
  return http.delete(`/api/navigation/admin/sites/${id}`)
}

/**
 * 切换站点启用状态（管理员接口）
 * @param {number} id 站点ID
 * @returns {Promise} 返回更新的站点信息
 */
export function toggleSiteEnabled(id) {
  return http.patch(`/api/navigation/admin/sites/${id}/toggle`)
}

/**
 * 切换站点推荐状态（管理员接口）
 * @param {number} id 站点ID
 * @returns {Promise} 返回更新的站点信息
 */
export function toggleSiteFeatured(id) {
  return http.patch(`/api/navigation/admin/sites/${id}/featured`)
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