<!--
  SquareBody 组件（广场正文）
  职责：
  - 渲染后端分类与站点卡片，支持分区标题吸附与滚动定位；
  - 顶部品牌区与副标题，增强识别与可访问性。
  联动与滚动：
  - 暴露 scrollTo(id) 供侧栏调用，实现精确滚动定位与偏移控制；
  - 使用 contentRef 作为唯一滚动容器，避免双滚动与测量误差。
  性能与体验：
  - 合理的卡片布局与阴影，移动端适配；
  - 分类数据懒加载与分块渲染，减轻首屏压力。
-->
<template>
  <!-- 页面主体容器：负责布局、滚动等核心功能 -->
  <div class="container">
    <!-- 页面头部：拾言广场标题 -->
    <header class="square-header">
      <!-- 标题区美化：增加品牌图标容器、渐变标题与副标题
           设计目标：
           - 在不改变整体布局的前提下，提升页面识别度与质感；
           - 使用轻量阴影与柔和渐变，保证与内容区的协调；
           - 文本与图标均可访问（ARIA 标签），兼顾语义与兼容。 -->
      <!-- 文案重命名：品牌统一为“拾·言” -->
      <div class="brand" aria-label="拾·言 · 广场 标题区">
        <!-- 品牌图标容器：圆角背景 + 轻微阴影，强调视觉焦点 -->
        <div class="logo-wrap" aria-hidden="true">
          <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="24" height="24" />
        </div>
        <!-- 标题与副标题：主标题采用渐变文本，副标题为轻提示语 -->
        <div class="title-wrap">
          <h1 class="title">拾言 · 广场</h1>
          <p class="subtitle">精选站点与工具，发现更高效的灵感</p>
        </div>
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
              <!-- 子分类卡片：副标题绑定为子分类的描述；若无描述则回退为“推荐站点” -->
              <NavigationSiteList
                v-for="child in section.children"
                :key="child.id"
                :id="child.id"
                :title="child.label"
                :subtitle="child.description || '推荐站点'"
                :categoryId="child.categoryId"
                :deferLoad="!(activeId === child.id || forceLoadIds.has(child.id))"
              />
            </template>
            <!-- 无子分类：直接渲染父分类卡片 -->
            <template v-else>
              <!-- 父分类卡片：副标题绑定为该分类的描述；若无描述则回退为“推荐站点” -->
              <NavigationSiteList
                :id="section.id"
                :title="section.label"
                :subtitle="section.description || '推荐站点'"
                :categoryId="section.categoryId"
                :deferLoad="!(activeId === section.id || forceLoadIds.has(section.id))"
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
import { ref, onMounted, onUnmounted, computed, watch, reactive } from 'vue'
/**
 * 变更说明（详细注释）：
 * 为解决“侧边栏子导航点击后打开其他导航”的问题，
 * 本组件新增 props.sections，并优先使用父组件传入的导航数据进行渲染与滚动定位。
 * 背景与原因：
 * - Square.vue 与 SquareBody.vue 之前各自调用 useNavigation()，
 *   由于组合式函数是按调用实例化，两个组件会持有“各自独立的状态源”。
 * - 在分类数据异步加载或回退为默认硬编码数据的时序差异下，
 *   左侧 SideNav 与右侧卡片使用的 sections 可能不一致（如一方是默认、另一方是后端数据）。
 * - 当用户点击子导航时，传递的 id 与右侧实际渲染的卡片 id 不一致，
 *   导致 scrollTo('#'+id) 命中错误元素或找不到目标，从而出现“滚到其他导航”的现象。
 * 解决方案：
 * - 通过 props.sections 接收并复用父组件中 SideNav 使用的同一份导航数据，
 *   保证左右两侧的 id 与结构完全一致，避免因多源状态造成的错配。
 * - 当未传入 sections（保持向后兼容）时，才回退为内部 useNavigation 的 sideNavSections。
 */
import { useRoute } from 'vue-router'
import { getToken } from '@/utils/auth'
import NavigationSiteList from '@/components/NavigationSiteList.vue'
import { useNavigation } from '@/composables/useNavigation'

// 暴露事件：向父组件更新当前高亮项，以联动左侧 SideNav 的 v-model
const emit = defineEmits(['update:activeId'])

// 路由引用：在 setup 阶段创建，避免 onMounted 内部未初始化导致的空引用
const route = useRoute()

// Props：可选由父组件传入的导航数据（优先使用以保证左右一致）
const props = defineProps({
  /**
   * sections：父组件传入的导航结构数组
   * 结构示例：
   * [
   *   { id: 'category-1', label: '开发工具', children: [ { id:'category-7', label:'在线编辑器' } ] },
   *   { id: 'site', label: '聚合拾言' }
   * ]
   */
  sections: { type: Array, default: () => [] },
  /**
   * query：搜索关键词（保留原功能）
   */
  query: { type: String, default: '' }
})

// 内部导航数据管理：仅在未传入 props.sections 时启用
const { sideNavSections: internalSections, fetchCategories } = useNavigation()

// 统一的导航数据入口：优先使用 props.sections，其次使用 internalSections
const navigationSections = computed(() => {
  const arr = Array.isArray(props.sections) ? props.sections : []
  return arr.length ? arr : (internalSections?.value || [])
})

// 响应式状态管理
const tokenRef = ref('')
const isLoggedIn = computed(() => !!(tokenRef.value && tokenRef.value.trim()))
const activeId = ref('site')
const contentRef = ref(null)
/**
 * forceLoadIds：强制预加载集合（响应式 Set）
 * 用途：
 * - 首屏：对“可见区域 + 少量预备数据”的卡片，加入此集合以关闭懒加载，立即加载数据；
 * - 空闲后台：在浏览器空闲时逐步把后续卡片加入此集合，后台预取，提升后续滚动体验；
 * - 优先加载：当用户点击某导航项时（已通过 activeId 触发），也可以冪等地加入集合以保证即时加载。
 * 说明：
 * - Vue 3 对 Map/Set 有原生响应式支持；此处使用 reactive(new Set()) 以确保模板中 has() 能触发更新；
 */
const forceLoadIds = reactive(new Set())

/**
 * 预取参数（可按需微调）：
 * - INITIAL_PREFETCH：初始加载的“少量预备数据”数量（除当前活跃卡片外）；
 * - IDLE_CHUNK：后台空闲每次预取的卡片数量；
 */
const INITIAL_PREFETCH = 3
const IDLE_CHUNK = 2

/**
 * allCardIds：按渲染顺序扁平化后的卡片 id 列表
 * 结构：优先使用 props.sections（保证与侧栏一致），有子分类则取子分类 id，否则取父分类 id
 */
const allCardIds = computed(() => {
  const res = []
  const sections = navigationSections.value || []
  for (const s of sections) {
    if (s?.children?.length) {
      for (const c of s.children) {
        if (c?.id) res.push(c.id)
      }
    } else {
      if (s?.id) res.push(s.id)
    }
  }
  return res
})

// 背景空闲调度句柄（兼容 rIC 与 setTimeout）与进度索引
let idleHandle = null
const prefetchIndex = ref(0)

/**
 * 初始化分层预加载：
 * - 将当前活跃项与前若干个卡片加入 forceLoadIds，立即加载；
 * - 启动后台空闲预取，在不打扰主线程的情况下逐步加载剩余卡片。
 */
function initLayeredPrefetch(){
  try {
    // 1) 当前活跃项优先：点击/默认选中项必须立即加载
    if (activeId.value) forceLoadIds.add(activeId.value)

    // 2) 初始少量预备数据：按渲染顺序选取前 N 个卡片
    const ids = allCardIds.value
    prefetchIndex.value = 0
    const take = Math.min(INITIAL_PREFETCH, Math.max(0, ids.length - prefetchIndex.value))
    for (let i = 0; i < take; i++) {
      const id = ids[prefetchIndex.value++]
      if (id) forceLoadIds.add(id)
    }

    // 3) 启动空闲后台预取：逐块将后续卡片加入预取集合
    startIdlePrefetch()
  } catch (e) {
    console.warn('初始化分层预加载失败，继续常规懒加载：', e)
  }
}

/**
 * 空闲后台预取：在浏览器空闲时逐步加载后续卡片
 * 策略：每次空闲期加入少量（IDLE_CHUNK）卡片到 forceLoadIds，直到耗尽
 * 兼容：优先使用 requestIdleCallback，若不可用则回退 setTimeout
 */
function startIdlePrefetch(){
  const ids = allCardIds.value
  const runner = (deadline) => {
    try {
      let added = 0
      while (added < IDLE_CHUNK && prefetchIndex.value < ids.length) {
        const id = ids[prefetchIndex.value++]
        if (id && !forceLoadIds.has(id)) {
          forceLoadIds.add(id)
          added++
        }
        // 若剩余空闲时间不足则提前退出，留到下一轮
        if (deadline && typeof deadline.timeRemaining === 'function' && deadline.timeRemaining() < 8) {
          break
        }
      }
    } catch (e) {
      // 忽略，保持后续轮次继续
    } finally {
      // 若尚未覆盖全部卡片，继续安排下一轮
      if (prefetchIndex.value < ids.length) {
        scheduleNextIdle()
      } else {
        idleHandle = null
      }
    }
  }

  function scheduleNextIdle(){
    try {
      if ('requestIdleCallback' in window) {
        idleHandle = window.requestIdleCallback(runner, { timeout: 1200 })
      } else {
        // 回退为轻量定时器，避免占用主线程：延迟一段时间再执行一小块
        idleHandle = setTimeout(() => runner(), 800)
      }
    } catch (e) {
      // 极端环境：直接同步推进少量，避免完全停滞
      runner()
    }
  }

  scheduleNextIdle()
}

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
  // 提前设置活跃项：让目标卡片在本次点击时立即关闭懒加载并触发数据加载
  // 说明：这一步使得 NavigationSiteList 收到 deferLoad=false，从而在 watch 中立刻调用 loadSites()
  // 这样既保留整体懒加载策略，又保证“点击即加载、滚动即有内容”，避免空白卡片体验。
  activeId.value = id
  try{ emit('update:activeId', id) }catch{}
  // 冪等加入强制预取集合：确保该卡片无论懒加载状态如何都立即加载
  try { if (id) forceLoadIds.add(id) } catch {}

  const container = getScrollContainer()
  if (!container) return

  let el = (contentRef.value || container).querySelector('#' + id)
  /**
   * 锚点缺失的首次点击保护（详细注释）：
   * 背景：
   * - 当父组件的 sections 刚从“默认后备数据”切换为“后端真实分类数据”时，
   *   右侧卡片的 DOM（含锚点 #id）可能尚未完成渲染；
   * - 此时第一次点击底部子导航，scrollTo 立即查找锚点可能返回 null，
   *   若直接退出，会让后续高亮逻辑以“最近锚点”命中错误卡片，出现错位。
   * 方案：
   * - 在锚点未找到时，进行短时重试（最多 8 次，每次 60ms，总计约 480ms），
   *   等待 Vue 完成渲染与 HMR 更新，确保首次点击也能正确滚动到目标卡片。
   * - 若重试后仍不存在，则优雅退出，不做错误滚动。
   */
  if (!el){
    let attempts = 0
    const max = 8
    const timer = setInterval(() => {
      try{
        el = (contentRef.value || container).querySelector('#' + id)
        if (el || attempts >= max){ clearInterval(timer) }
        if (el){
          // 找到锚点后执行滚动（与下方逻辑一致）
          const isScrollableContent = container.classList?.contains('scrollable-content')
          const isContentScroll = container.classList?.contains('content-scroll')
          const containerStyles = getComputedStyle(container)
          const containerPadTop = parseFloat(containerStyles.paddingTop || '0')
          let offset = containerPadTop
          offset += (isScrollableContent || isContentScroll) ? 16 : ((document.querySelector('.square-header')?.offsetHeight || 0) + 24)
          const elRect = el.getBoundingClientRect()
          const containerRect = container.getBoundingClientRect()
          const visibleDelta = elRect.top - containerRect.top
          const targetTop = Math.max(0, container.scrollTop + visibleDelta - offset)
          container.scrollTo({ top: targetTop, behavior: 'smooth' })
          activeId.value = id
          try{ emit('update:activeId', id) }catch{}
        }
        attempts++
      }catch{ attempts++ }
    }, 60)
    return
  }

  // 修复滚动偏移计算：针对 TwoPaneLayout 布局优化
  // 说明：
  // - 在 TwoPaneLayout 中，.square-header 与卡片内容都在同一个 .scrollable-content 容器内；
  // - 标题不会"遮挡"卡片，因为它们是垂直排列的，标题在上方，卡片在下方；
  // - 因此不需要减去标题高度，只需要一个小的安全间距即可。
  const isScrollableContent = container.classList?.contains('scrollable-content')
  const isContentScroll = container.classList?.contains('content-scroll')
  const containerStyles = getComputedStyle(container)
  const containerPadTop = parseFloat(containerStyles.paddingTop || '0')
  
  // 设置合适的安全间距：
  // - 在 TwoPaneLayout 的右侧滚动容器（.scrollable-content）或本组件内部滚动容器（.content-scroll）中，
  //   都不需要减去标题高度（因为标题不在这些滚动容器内），仅保留较小的安全间距 (16px)。
  // - 仅当滚动容器不是上述两者时，才回退为“标题高度 + 24px”的兼容逻辑。
  let offset = containerPadTop
  if (isScrollableContent || isContentScroll) {
    // 统一处理：当前滚动容器不包含 .square-header，无需扣减标题高度
    offset += 16
  } else {
    // 其他布局：保持原有逻辑（向后兼容）
    const titleEl = document.querySelector('.square-header')
    const titleH = titleEl ? titleEl.offsetHeight : 0
    offset += titleH + 24
  }

  // 基于可见位置计算滚动距离
  const elRect = el.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  const visibleDelta = elRect.top - containerRect.top
  const targetTop = Math.max(0, container.scrollTop + visibleDelta - offset)
  
  container.scrollTo({ top: targetTop, behavior: 'smooth' })

  // 更新活跃项并通知父组件（冪等：前面已设置一次，这里保持一致）
  activeId.value = id
  try{ emit('update:activeId', id) } catch { /* 忽略异常以保证滚动稳定 */ }
}

// 滚动高亮处理
function handleScroll(){
  const container = getScrollContainer()
  if (!container) return

  // 修复滚动偏移计算：与 scrollTo 方法保持一致
  // 说明：高亮判断的偏移量应该与滚动定位的偏移量一致，确保交互的连贯性
  const isScrollableContent = container.classList?.contains('scrollable-content')
  const isContentScroll = container.classList?.contains('content-scroll')
  const containerStyles = getComputedStyle(container)
  const containerPadTop = parseFloat(containerStyles.paddingTop || '0')
  
  // 使用与 scrollTo 相同的偏移计算逻辑
  let offset = containerPadTop
  if (isScrollableContent || isContentScroll) {
    // 当前滚动容器不包含标题，保持较小安全间距
    offset += 16
  } else {
    // 其他布局：保持原有逻辑
    const titleEl = document.querySelector('.square-header')
    const titleH = titleEl ? titleEl.offsetHeight : 0
    offset += titleH + 24
  }

  // 收集所有锚点元素
  const nodes = []
  // 使用统一的导航数据（props.sections 优先）进行高亮计算
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
  // 若未传入 sections，则加载导航分类数据以提供后备渲染
  if (!(Array.isArray(props.sections) && props.sections.length)){
    await fetchCategories()
  }
  
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

  // 初始化分层预加载：首屏少量 + 空闲后台
  initLayeredPrefetch()
  
  // 清理函数
  onUnmounted(() => {
    window.removeEventListener('hashchange', onHashChange)
    window.removeEventListener('visibilitychange', onVisibilityChange)
    clearInterval(authPoller)
    if (container) {
      container.removeEventListener('scroll', handleScroll)
    }
    // 释放后台预取调度句柄
    try {
      if (idleHandle && 'cancelIdleCallback' in window) {
        window.cancelIdleCallback(idleHandle)
      } else if (idleHandle) {
        clearTimeout(idleHandle)
      }
    } catch {}
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

  /* 局部覆盖全局 .container 的居中定宽规则
     背景：
     - 全局 style.css 为 .container 设定了 max-width:1080px 与 margin: 24px auto；
       这会导致本组件根容器被定宽并水平居中，从而在右侧列产生两侧空白。
     目标：
     - 广场正文需要“铺满父容器宽度”，不受全局规则影响；
     处理：
     - 在 scoped 样式中显式覆盖宽度、最大宽度与外边距；
     - 同时移除左右 padding，改由内部 .square-header/.content-scroll 控制自身安全边距。
   */
  width: 85%;          /* 占满父容器可用宽度 */
  max-width: none;      /* 取消 1080px 上限 */
  padding: 0px;           /* 移除全局左右 16px 内边距 */
}

.square-header {
  /* 标题区卡片化：柔和渐变背景 + 圆角 + 轻阴影
     说明：
     - 用浅色渐变提升层次，同时保持整体清爽；
     - 圆角与阴影仅在标题区内，避免影响右侧滚动容器；
     - 保留 flex-shrink 防止滚动时被压缩。 */
  padding: 16px 24px;
  background: linear-gradient(180deg, #f9fbff 0%, #ffffff 100%);
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  box-shadow: 0 8px 24px -12px rgba(31, 41, 55, 0.25);
  flex-shrink: 0;
}

.brand {
  /* 标题行：图标 + 文本块 */
  display: flex;
  align-items: center;
  gap: 14px;
}

/* 品牌图标容器：独立的圆角卡片，增强识别度 */
.logo-wrap {
  width: 40px;
  height: 40px;
  display: grid;           /* 居中图标 */
  place-items: center;
  background: #ffffff;
  border: 1px solid #e6effa;
  border-radius: 12px;
  box-shadow: 0 6px 18px -10px rgba(64, 158, 255, 0.35);
}

/* 文本块：主标题 + 副标题 */
.title-wrap { display: flex; flex-direction: column; }

/* 渐变主标题：使用背景裁剪实现柔和品牌色过渡 */
.title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  /* 渐变文本：从 #409eff 过渡到更浅的品牌色 */
  background-image: linear-gradient(90deg, #409eff 0%, #67a6ff 50%, #a0cfff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;          /* 让渐变作为文本填充 */
  letter-spacing: 0.2px;
}

/* 副标题：低对比度提示语，减少视觉负担 */
.subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6b7280;
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
  padding: 4px;
  scroll-behavior: smooth;
}

/* 隐藏滚动条（各浏览器兼容方案） */
.content-scroll::-webkit-scrollbar { width: 0; height: 0; }
.content-scroll { scrollbar-width: none; -ms-overflow-style: none; }

/* 空状态容器：垂直居中，保持内容居中对齐 */
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

/* 空状态描述：低对比度、中等宽度，保持可读性 */
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
    /* 移动端：收敛内边距与阴影大小，避免喧宾夺主 */
    padding: 12px 16px;
    box-shadow: 0 6px 18px -12px rgba(31, 41, 55, 0.25);
  }
  
  .title { font-size: 18px; }
}
</style>
