/**
 * 导航数据管理组合式函数
 * 提供导航分类和站点数据的获取、转换和缓存功能
 * 
 * @author YunBQ
 * @since 2024-11-01
 */

import { ref, computed, onMounted } from 'vue'
import { getAllEnabledCategories, getSitesByCategory } from '@/api/navigation'

/**
 * 导航数据管理
 * @returns {Object} 导航相关的响应式数据和方法
 */
export function useNavigation() {
  // 响应式数据
  const categories = ref([]) // 所有分类数据
  const sites = ref({}) // 站点数据，按分类ID分组
  const loading = ref(false)
  const error = ref(null)

  /**
   * 获取所有启用的分类
   */
  async function fetchCategories() {
    try {
      loading.value = true
      error.value = null
      const response = await getAllEnabledCategories()
      // 后端现在返回 camelCase 格式的字段名，直接使用即可
      // MyBatis 和 Jackson 配置确保了字段名的一致性
      const raw = Array.isArray(response?.data) ? response.data : []
      categories.value = raw
    } catch (err) {
      console.error('获取导航分类失败:', err)
      error.value = err.message || '获取导航分类失败'
      // 如果API失败，使用默认的硬编码数据作为后备
      categories.value = getDefaultCategories()
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取指定分类的站点
   * @param {number} categoryId 分类ID
   */
  async function fetchSitesByCategory(categoryId) {
    if (!categoryId || sites.value[categoryId]) {
      return // 已经加载过或无效ID
    }

    try {
      const response = await getSitesByCategory(categoryId)
      sites.value[categoryId] = response.data || []
    } catch (err) {
      console.error(`获取分类 ${categoryId} 的站点失败:`, err)
      sites.value[categoryId] = []
    }
  }

  /**
   * 将数据库分类数据转换为侧边栏导航格式
   */
  const sideNavSections = computed(() => {
    if (!categories.value.length) {
      return getDefaultSections()
    }

    // 分离一级和二级分类
    const rootCategories = categories.value.filter(cat => !cat.parentId)
    const subCategories = categories.value.filter(cat => cat.parentId)

    // 构建导航结构
    return rootCategories.map(rootCat => {
      // 查找该一级分类下的子分类
      const children = subCategories
        .filter(subCat => subCat.parentId === rootCat.id)
        .map(subCat => ({
          id: `category-${subCat.id}`,
          label: subCat.name,
          categoryId: subCat.id,
          icon: subCat.icon
        }))
        .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))

      const section = {
        id: `category-${rootCat.id}`,
        label: rootCat.name,
        categoryId: rootCat.id,
        icon: rootCat.icon
      }

      // 如果有子分类，添加children和aliasTargets
      if (children.length > 0) {
        section.children = children
        // 点击父项时滚动到第一个子项
        section.aliasTargets = [children[0].id]
      }

      return section
    }).sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  })

  /**
   * 获取默认的硬编码分类数据（作为后备）
   */
  function getDefaultCategories() {
    return [
      { id: 1, name: '开发工具', parentId: null, icon: 'fas fa-code', sortOrder: 1, isEnabled: true },
      { id: 2, name: '设计资源', parentId: null, icon: 'fas fa-palette', sortOrder: 2, isEnabled: true },
      { id: 3, name: '学习资源', parentId: null, icon: 'fas fa-graduation-cap', sortOrder: 3, isEnabled: true },
      { id: 4, name: '娱乐休闲', parentId: null, icon: 'fas fa-gamepad', sortOrder: 4, isEnabled: true },
      { id: 5, name: '生活工具', parentId: null, icon: 'fas fa-tools', sortOrder: 5, isEnabled: true },
      // 二级分类
      { id: 15, name: '代码托管', parentId: 1, icon: 'fab fa-git-alt', sortOrder: 1, isEnabled: true },
      { id: 7, name: '在线编辑器', parentId: 1, icon: 'fas fa-edit', sortOrder: 2, isEnabled: true },
      { id: 8, name: '文档工具', parentId: 1, icon: 'fas fa-file-alt', sortOrder: 3, isEnabled: true },
      { id: 9, name: '图标库', parentId: 2, icon: 'fas fa-icons', sortOrder: 1, isEnabled: true },
      { id: 10, name: '配色方案', parentId: 2, icon: 'fas fa-palette', sortOrder: 2, isEnabled: true },
      { id: 11, name: '字体资源', parentId: 2, icon: 'fas fa-font', sortOrder: 3, isEnabled: true },
      { id: 12, name: '编程学习', parentId: 3, icon: 'fas fa-laptop-code', sortOrder: 1, isEnabled: true },
      { id: 13, name: '在线课程', parentId: 3, icon: 'fas fa-chalkboard-teacher', sortOrder: 2, isEnabled: true },
      { id: 14, name: '技术博客', parentId: 3, icon: 'fas fa-blog', sortOrder: 3, isEnabled: true }
    ]
  }

  /**
   * 获取默认的侧边栏导航配置（作为后备）
   */
  function getDefaultSections() {
    return [
      { id: 'site', label: '聚合便签' },
      { 
        id: 'knowledge', 
        label: '云盘集', 
        aliasTargets: ['knowledge-search'], 
        children: [
          { id: 'knowledge-search', label: '云盘搜索' },
          { id: 'knowledge-tool', label: '云盘工具' }
        ] 
      },
      { 
        id: 'git', 
        label: 'git集', 
        aliasTargets: ['git-media','git-tool','git-proxy'], 
        children: [
          { id: 'git-media', label: 'git影音' },
          { id: 'git-tool', label: 'git工具' },
          { id: 'git-proxy', label: 'git代理' }
        ] 
      },
      { 
        id: 'movie', 
        label: '影视集', 
        aliasTargets: ['movie-online'], 
        children: [
          { id: 'movie-online', label: '在线影视' },
          { id: 'movie-software', label: '影视软件' },
          { id: 'movie-short', label: '短视频' },
          { id: 'movie-download', label: '短视频下载' },
          { id: 'movie-anime', label: '在线动漫' }
        ] 
      },
      { 
        id: 'music', 
        label: '音乐集', 
        aliasTargets: ['music-online'], 
        children: [
          { id: 'music-online', label: '在线音乐' },
          { id: 'music-download', label: '音乐下载' }
        ] 
      },
      { 
        id: 'book', 
        label: '图书集', 
        aliasTargets: ['book-online'], 
        children: [
          { id: 'book-online', label: '在线图书' },
          { id: 'book-download', label: '图书下载' },
          { id: 'book-search', label: '图书搜索' }
        ] 
      },
      { 
        id: 'tool', 
        label: '工具集', 
        aliasTargets: ['tool-file'], 
        children: [
          { id: 'tool-file', label: '文件工具' },
          { id: 'tool-media', label: '影音工具' },
          { id: 'tool-magnet', label: '磁力工具' },
          { id: 'tool-plugin', label: '插件工具' },
          { id: 'tool-other', label: '其他工具' }
        ] 
      },
      { 
        id: 'ai', 
        label: 'AI集', 
        aliasTargets: ['ai-draw'], 
        children: [ 
          { id: 'ai-draw', label: 'AI绘图' },
          { id: 'ai-voice', label: 'AI语音' },
          { id: 'ai-video', label: 'AI视频' },
          { id: 'ai-tool', label: 'AI工具' }
        ] 
      }
    ]
  }

  /**
   * 根据导航ID获取对应的分类ID
   * @param {string} navId 导航项ID
   * @returns {number|null} 分类ID
   */
  function getCategoryIdByNavId(navId) {
    if (!navId || !navId.startsWith('category-')) {
      return null
    }
    return parseInt(navId.replace('category-', ''))
  }

  /**
   * 获取指定分类的站点数据
   * @param {number} categoryId 分类ID
   * @returns {Array} 站点列表
   */
  function getSitesByCategoryId(categoryId) {
    return sites.value[categoryId] || []
  }

  /**
   * 预加载所有分类的站点数据
   */
  async function preloadAllSites() {
    const categoryIds = categories.value
      .filter(cat => cat.isEnabled)
      .map(cat => cat.id)

    for (const categoryId of categoryIds) {
      await fetchSitesByCategory(categoryId)
    }
  }

  // 组件挂载时自动获取分类数据
  onMounted(() => {
    fetchCategories()
  })

  return {
    // 响应式数据
    categories,
    sites,
    loading,
    error,
    sideNavSections,

    // 方法
    fetchCategories,
    fetchSitesByCategory,
    getCategoryIdByNavId,
    getSitesByCategoryId,
    preloadAllSites
  }
}