<template>
  <div class="container">
    <!-- 页面头部：云便签广场标题 -->
    <header class="square-header">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="28" height="28" />
        <h1>云便签 · 广场</h1>
      </div>
    </header>

    <section class="layout">
      <!-- 主要内容区域：滚动容器 -->
      <div class="content-scroll" ref="contentRef">
        <!-- 动态导航渲染：根据后端分类数据生成卡片 -->
        <!-- 说明：
             - 当 useNavigation 成功加载到分类时，根据导航结构动态渲染右侧卡片；
             - 一级分类无子分类：直接渲染一个卡片；有子分类：为每个子分类渲染卡片；
             - 使用 NavigationSiteList 组件，支持骨架加载、分页、移动端加载更多、点击计数等功能。 -->
        <template v-if="navigationSections && navigationSections.length">
          <div v-for="section in navigationSections" :key="section.id">
            <!-- 有子分类：为每个子分类渲染独立卡片 -->
            <template v-if="section.children && section.children.length">
              <NavigationSiteList
                v-for="child in section.children"
                :key="child.id"
                :id="child.id"
                :title="child.label"
                :subtitle="section.label + ' · ' + child.label"
                :categoryId="child.categoryId"
              />
            </template>
            <!-- 无子分类：直接渲染父分类卡片 -->
            <template v-else>
              <NavigationSiteList
                :id="section.id"
                :title="section.label"
                subtitle="推荐站点"
                :categoryId="section.categoryId"
              />
            </template>
          </div>
        </template>

        <!-- 后备显示：当导航数据加载失败或为空时显示提示 -->
        <template v-else>
          <div class="empty-state">
            <div class="empty-icon">📋</div>
            <div class="empty-title">正在加载导航分类...</div>
            <div class="empty-desc">如果长时间未加载，请检查后端导航接口是否正常</div>
          </div>
        </template>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getToken } from '@/utils/auth'
import NavigationSiteList from '@/components/NavigationSiteList.vue'
import { useNavigation } from '@/composables/useNavigation'

// 暴露事件：向父组件更新当前高亮项，以联动左侧 SideNav 的 v-model
const emit = defineEmits(['update:activeId'])

// 路由引用：在 setup 阶段创建，避免 onMounted 内部未初始化导致的空引用
const route = useRoute()

// 导航数据管理：使用新的导航系统
const { sideNavSections: navigationSections, fetchCategories } = useNavigation()

// 响应式状态管理
const tokenRef = ref('')
const isLoggedIn = computed(() => !!(tokenRef.value && tokenRef.value.trim()))
const activeId = ref('site')
const contentRef = ref(null)

// 工具函数：刷新登录状态
function refreshAuth(){
  try{ 
    tokenRef.value = String(getToken() || '') 
  } catch { 
    tokenRef.value = '' 
  }
}

// 滚动控制函数：获取滚动容器
function getScrollContainer(){
  const base = contentRef.value
  if (!base) return null
  let el = base
  while (el && el !== document.body){
    try{
      const s = getComputedStyle(el)
      const oy = String(s.overflowY || '').toLowerCase()
      if (oy === 'auto' || oy === 'scroll') return el
    }catch{ /* 忽略异常，继续向上查找 */ }
    el = el.parentElement
  }
  return base
}

// 滚动到指定锚点
function scrollTo(id){
  const container = getScrollContainer()
  if (!container) return

  const el = (contentRef.value || container).querySelector('#' + id)
  if (!el) return

  // 计算滚动偏移量，避免标题遮挡
  const isRightMain = container.classList?.contains('right-main')
  const titleEl = !isRightMain ? document.querySelector('.square-header') : null
  const titleH = titleEl ? titleEl.offsetHeight : 0
  const containerStyles = getComputedStyle(container)
  const containerPadTop = parseFloat(containerStyles.paddingTop || '0')
  const extra = isRightMain ? 12 : 24

  // 基于可见位置计算滚动距离
  const elRect = el.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  const visibleDelta = elRect.top - containerRect.top
  const offset = titleH + containerPadTop + extra
  const targetTop = Math.max(0, container.scrollTop + visibleDelta - offset)
  
  container.scrollTo({ top: targetTop, behavior: 'smooth' })

  // 更新活跃项并通知父组件
  activeId.value = id
  try{ 
    emit('update:activeId', id) 
  } catch { 
    /* 忽略异常以保证滚动稳定 */ 
  }
}

// 滚动高亮处理
function handleScroll(){
  const container = getScrollContainer()
  if (!container) return

  // 计算滚动偏移
  const isRightMain = container.classList?.contains('right-main')
  const titleEl = !isRightMain ? document.querySelector('.square-header') : null
  const titleH = titleEl ? titleEl.offsetHeight : 0
  const containerStyles = getComputedStyle(container)
  const containerPadTop = parseFloat(containerStyles.paddingTop || '0')
  const offset = titleH + containerPadTop + (isRightMain ? 12 : 24)

  // 收集所有锚点元素
  const nodes = []
  if (navigationSections.value) {
    for (const section of navigationSections.value) {
      const elTop = (contentRef.value || container).querySelector('#' + section.id)
      if (elTop) nodes.push({ id: section.id, el: elTop })
      
      if (section.children && section.children.length) {
        for (const child of section.children) {
          const elChild = (contentRef.value || container).querySelector('#' + child.id)
          if (elChild) nodes.push({ id: child.id, el: elChild })
        }
      }
    }
  }

  // 计算最接近的锚点
  const containerRect = container.getBoundingClientRect()
  let current = navigationSections.value?.[0]?.id || 'site'
  let minDelta = Infinity
  
  for (const n of nodes) {
    const elRect = n.el.getBoundingClientRect()
    const visibleDelta = elRect.top - containerRect.top
    const delta = Math.abs(visibleDelta - offset)
    if (delta < minDelta) { 
      minDelta = delta
      current = n.id 
    }
  }

  // 更新状态
  activeId.value = current
  try { 
    emit('update:activeId', current) 
  } catch { 
    /* 忽略异常，确保滚动流畅 */ 
  }
}

// 页面挂载时的初始化
onMounted(async () => {
  // 加载导航分类数据
  await fetchCategories()
  
  // 初始化登录状态
  refreshAuth()
  
  // 添加事件监听
  const onHashChange = () => refreshAuth()
  const onVisibilityChange = () => { 
    if (!document.hidden) refreshAuth() 
  }
  
  window.addEventListener('hashchange', onHashChange)
  window.addEventListener('visibilitychange', onVisibilityChange)
  
  // 轻量轮询确保登录状态同步
  const authPoller = setInterval(refreshAuth, 1000)
  
  // 路由变化监听
  watch(() => route && route.fullPath, () => refreshAuth())
  
  // 滚动监听
  const container = getScrollContainer()
  if (container) { 
    container.addEventListener('scroll', handleScroll, { passive: true })
  }
  
  // 清理函数
  onUnmounted(() => {
    window.removeEventListener('hashchange', onHashChange)
    window.removeEventListener('visibilitychange', onVisibilityChange)
    clearInterval(authPoller)
    if (container) {
      container.removeEventListener('scroll', handleScroll)
    }
  })
})

// 暴露方法给父组件
defineExpose({
  scrollTo
})
</script>

<style scoped>
.container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.square-header {
  padding: 16px 24px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
  flex-shrink: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
}

.layout {
  flex: 1;
  overflow: hidden;
}

.content-scroll {
  height: 100%;
  /* 右侧卡片区滚动容器：保持滚动，但隐藏滚动条
     需求：
     - “卡片的滚动条隐藏”，指用户不希望看到垂直滚动条占位影响视觉；
     - 保持滚动功能，采用各浏览器的隐藏滚动条方案：
       * WebKit（Chrome/Edge/Safari）：::-webkit-scrollbar 宽高设为 0；
       * Firefox：scrollbar-width: none；
       * 旧版 IE/Edge：-ms-overflow-style: none；
     注意：如果某些平台仍显示滚动指示，可考虑在容器内增加额外的内边距以弱化视觉干扰。
   */
  overflow-y: auto;
  padding: 24px;
  scroll-behavior: smooth;
}

/* 隐藏滚动条（各浏览器兼容方案） */
.content-scroll::-webkit-scrollbar { width: 0; height: 0; }
.content-scroll { scrollbar-width: none; -ms-overflow-style: none; }

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  text-align: center;
  color: #6b7280;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 18px;
  font-weight: 500;
  margin-bottom: 8px;
  color: #374151;
}

.empty-desc {
  font-size: 14px;
  max-width: 400px;
  line-height: 1.5;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .content-scroll {
    padding: 16px;
  }
  
  .square-header {
    padding: 12px 16px;
  }
  
  .brand h1 {
    font-size: 18px;
  }
}
</style>
