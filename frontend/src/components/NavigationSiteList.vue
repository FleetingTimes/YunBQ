<template>
  <!-- 导航站点列表组件：根据导航分类动态显示站点卡片 -->
  <div class="card" :id="id">
    <div class="card-title">{{ title }}</div>
    <div class="card-desc" style="display:flex; align-items:center; justify-content:space-between; gap:8px;">
      <span>{{ subtitle }}</span>
    </div>
    <ul class="note-list">
      <!-- 骨架加载：请求期间显示占位，避免空白跳变 -->
      <template v-if="isLoading">
        <li class="note-item skeleton" v-for="i in 6" :key="'skel-' + i">
          <div class="title skeleton-line" style="width:70%"></div>
          <div class="content skeleton-line" style="width:90%"></div>
          <div class="meta">
            <div class="left"><span class="author skeleton-pill" style="width:60px"></span></div>
            <div class="right"><span class="time skeleton-pill" style="width:80px"></span></div>
          </div>
        </li>
      </template>
      <!-- 列表项：显示站点信息，点击打开链接 -->
      <li class="note-item" v-for="site in pagedSites" :key="site.id" @click="openSite(site)" role="button" v-show="!isLoading">
        <div class="title">{{ site.name }}</div>
        <!-- 站点描述：鼠标悬停时显示完整内容 -->
        <div class="content content-with-tooltip" 
             :title="site.description || '暂无描述'">
          {{ site.description || '暂无描述' }}
        </div>
      </li>
      <!-- 空态：仅在非加载且无数据时显示 -->
      <li v-if="!isLoading && !total" class="empty">暂无{{ title }}</li>
    </ul>
    <!-- 分页控件：上一页/下一页 + 页数指示；仅在显示分页时展示 -->
    <div class="pagination" v-if="showPagination && total">
      <button class="pager-btn" :disabled="!hasPrev" @click="prevPage">上一页</button>
      <span class="pager-info">第 {{ page }} 页 / 共 {{ totalPages }} 页</span>
      <!-- 页码输入跳转：输入页码后按回车或点击"跳转" -->
      <input class="pager-input" type="number" v-model.number="pageInput" :min="1" :max="totalPages" @keyup.enter="jumpPage" aria-label="页码输入" />
      <button class="pager-btn" @click="jumpPage">跳转</button>
      <button class="pager-btn" :disabled="!hasNext" @click="nextPage">下一页</button>
    </div>

    <!-- 移动端加载更多：小屏隐藏分页，仅保留"加载更多"，并在到底部时自动加载下一页 -->
    <div class="load-more" v-if="isMobile && total">
      <button class="load-btn" :disabled="!hasNext" @click="loadMore">加载更多</button>
      <!-- 自动加载触发哨兵：进入视口时尝试加载下一页（仅移动端） -->
      <div ref="loadMoreSentinel" class="load-sentinel" aria-hidden="true"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
// 导航 API：按需导入函数（命名导出）
import { getSitesByCategory, incrementClickCount } from '@/api/navigation'

/**
 * Props 约定：
 * - id：区块 id（用于页面内导航锚点）
 * - title：区块标题，如"代码托管"
 * - subtitle：副标题，如"代码托管平台"
 * - categoryId：导航分类ID，用于获取该分类下的站点
 * - pageSize：每页显示的站点数量
 * - showPagination：是否显示分页控件
 */
const props = defineProps({
  id: { type: String, required: true },
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  categoryId: { type: Number, required: true },
  pageSize: { type: Number, default: 12 },
  showPagination: { type: Boolean, default: true }
})

// 响应式数据
const sites = ref([]) // 站点列表
const isLoading = ref(false) // 加载状态
const page = ref(1) // 当前页码
const pageInput = ref(1) // 页码输入框

/**
 * 加载站点列表：根据分类ID获取站点数据
 */
async function loadSites() {
  // 1) 分类ID合法性校验：避免发起 /category/undefined 等错误请求
  // 说明：当使用默认后备导航（无后端分类）时，传入的 categoryId 可能为 undefined；
  // 此处先判断是否为正整数，若非法则直接清空数据并退出，保证组件稳健。
  const isValidId = Number.isInteger(props.categoryId) && props.categoryId > 0
  if (!isValidId) {
    sites.value = []
    return
  }

  try {
    isLoading.value = true
    const resp = await getSitesByCategory(props.categoryId)
    // 后端现在返回 camelCase 格式的字段名，直接使用即可
    // MyBatis 和 Jackson 配置确保了字段名的一致性
    const rawList = Array.isArray(resp?.data) ? resp.data : []
    sites.value = rawList
    // 每次重新加载时将页码重置为 1，并同步页码输入
    page.value = 1
    pageInput.value = 1
  } catch (error) {
    // 3) 失败兜底：记录错误到控制台，避免中断交互；UI 显示骨架或空态
    console.error('加载站点失败:', error)
    sites.value = []
  } finally {
    isLoading.value = false
  }
}

/**
 * 打开站点：增加点击次数并在新窗口打开
 */
async function openSite(site) {
  if (!site.url) return
  
  try {
    // 增加点击次数
    await incrementClickCount(site.id)
    // 在新窗口打开站点
    window.open(site.url, '_blank', 'noopener')
  } catch (error) {
    console.error('打开站点失败:', error)
    // 即使增加点击次数失败，也要打开站点
    window.open(site.url, '_blank', 'noopener')
  }
}

// 初次挂载时加载数据
onMounted(() => {
  loadSites()
  setupMobileMatch()
})

// 监听分类ID变化，重新加载数据
// 说明：仅在分类ID为有效正整数时才触发加载，避免无效 ID 造成不必要请求与错误提示
watch(() => props.categoryId, (newId) => {
  if (Number.isInteger(newId) && newId > 0) {
    loadSites()
  } else {
    // 非法ID：清空数据，保持空态
    sites.value = []
  }
})

// 客户端分页：对站点列表进行切片
const total = computed(() => sites.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / props.pageSize)))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value < totalPages.value)
const pagedSites = computed(() => {
  const start = (page.value - 1) * props.pageSize
  return sites.value.slice(start, start + props.pageSize)
})

// 同步页码输入与当前页变化（用户翻页时更新输入框）
watch(page, (p) => { pageInput.value = p })

function goPage(p) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
}

function prevPage() { 
  if (hasPrev.value) page.value -= 1 
}

function nextPage() { 
  if (hasNext.value) page.value += 1 
}

function jumpPage() {
  let p = Number(pageInput.value)
  if (!Number.isFinite(p)) return
  p = Math.floor(p)
  if (p < 1) p = 1
  if (p > totalPages.value) p = totalPages.value
  goPage(p)
}

function loadMore() { 
  if (hasNext.value) nextPage() 
}

// 移动端：隐藏分页、仅保留"加载更多"，并支持到底部自动加载
const isMobile = ref(false)
const loadMoreSentinel = ref(null)
let io = null
let mq = null

const onMobileChange = () => { 
  isMobile.value = mq?.matches ?? false 
}

function setupMobileMatch() {
  try {
    mq = window.matchMedia('(max-width: 640px)')
    onMobileChange()
    // 监听断点变化以动态切换移动端行为
    mq.addEventListener('change', onMobileChange)
    
    // 移动端自动加载：监听哨兵元素进入视口
    if (window.IntersectionObserver && loadMoreSentinel.value) {
      io = new IntersectionObserver((entries) => {
        if (entries[0]?.isIntersecting && hasNext.value && !isLoading.value) {
          loadMore()
        }
      }, { threshold: 0.1 })
      io.observe(loadMoreSentinel.value)
    }
  } catch (error) {
    console.error('移动端匹配设置失败:', error)
  }
}

onUnmounted(() => {
  if (mq) {
    mq.removeEventListener('change', onMobileChange)
  }
  if (io) {
    io.disconnect()
  }
})
</script>

<style scoped>
/* 卡片基础样式：半透明背景提供柔和的视觉层次 */
.card { 
  border: 1px solid #e5e7eb; 
  border-radius: 12px; 
  padding: 16px; 
  background: rgba(255, 255, 255, 0.6); 
  /* 区块下边距：拉开不同列表区块之间的垂直距离 */
  margin-bottom: 24px; 
}
/* 卡片标题：使用黑色文本，与白色背景形成对比 */
.card-title { 
  color: #505050;
  font-weight: 600; 
  margin-bottom: 6px; 
}

/* 站点描述：使用黑色文本，与白色背景形成对比 */
.card-desc { 
  color: #838383; 
  margin-bottom: 8px; 
}

/* 列表基础样式：保持与页面统一的卡片风格与尺寸设置 */
.note-list { 
  list-style: none; 
  margin: 0; 
  padding: 0; 
  display: grid; 
  /* 网格布局：一行显示4个站点，每个站点最小宽度180px，自适应分配剩余空间 */
  grid-template-columns: repeat(4, minmax(180px, 1fr)); 
  gap: 8px; 
}

.note-item { 
  /* 站点卡片半透明背景：与页面背景自然融合，保持层次感 */
  background: rgba(255, 255, 255, 0.4); 
  border: 1px solid #ebeef5; 
  border-radius: 12px; 
  /* 卡片区站点项高度调整为 160px：
     说明：
     - 用户需求：每个站点的卡片高度设为 100px；
     - 原高度为 80px（在移除 meta 区域后紧凑布局），现统一提高到 160px，便于展示更舒展的标题与描述；
     - 如后续需要根据内容动态高度，可移除固定 height，改为 min-height + 内容自适应。
   */
  padding: 12px; 
  height: 100px; 
  box-shadow: 0 3px 10px rgba(0,0,0,0.05); 
  cursor: pointer; 
  transition: transform .15s ease, box-shadow .15s ease, background-color .2s ease; 
  display: flex; 
  flex-direction: column; 
  justify-content: flex-start; 
  gap: 6px;
  box-sizing: border-box; 
}

/* 站点项悬停效果：浅蓝色背景 + 轻微上移 + 阴影增强 */
.note-item:hover { 
  background-color: #e6f4ff; 
  transform: translateY(-2px); 
  box-shadow: 0 8px 20px rgba(0,0,0,0.08); 
}

/* 站点标题：使用黑色文本，与白色背景形成对比 */
.note-item .title { 
  color: #535353; 
  font-size: 14px; 
  font-weight: 600; 
  line-height: 1.5; 
  display: -webkit-box; 
  -webkit-line-clamp: 1; 
  -webkit-box-orient: vertical; 
  overflow: hidden; 
  word-break: break-word; 
  overflow-wrap: anywhere; 
}

.note-item .content { 
  color: #303133; 
  font-size: 12px; 
  line-height: 1.5; 
  display: -webkit-box; 
  -webkit-line-clamp: 2; 
  -webkit-box-orient: vertical; 
  overflow: hidden; 
  word-break: break-word; 
  overflow-wrap: anywhere; 
}

/* 简化 tooltip：仅使用浏览器原生 title 属性显示完整描述内容 */
.content-with-tooltip { 
  cursor: default; 
}

/* 站点元数据：使用较小字体、灰色文本，与白色背景形成对比 */
.note-item .meta { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  margin-top: 6px; 
  color: #606266; 
  font-size: 11px; 
}

/* 站点元数据左部：图标与文本组合，保持紧凑布局 */
.note-item .meta .left { 
  display: flex; 
  align-items: center; 
  gap: 6px; 
  min-width: 0; 
  flex: 1; 
}
/* 站点元数据右部：作者信息、创建时间等，使用较小字体、灰色文本，与白色背景形成对比 */
.note-item .meta .right { 
  display: flex; 
  align-items: center; 
  gap: 6px; 
  color: #909399; 
  flex: none; 
}

.note-item .meta .author { 
  overflow: hidden; 
  text-overflow: ellipsis; 
  white-space: nowrap; 
  min-width: 0; 
  flex: 1; 
}

.note-list .empty { 
  color: #909399; 
  /* 空状态半透明背景：与整体组件风格保持一致 */
  background: rgba(255, 255, 255, 0.3); 
  border: 1px dashed #e5e7eb; 
  display: flex;
  align-items: center;
  justify-content: center;
  grid-column: 1 / -1;
  padding: 20px;
}

/* 响应式断点：窄屏下减列以保证可读性 */
@media (max-width: 960px) { 
  .note-list { 
    grid-template-columns: repeat(2, minmax(160px, 1fr)); 
    gap: 8px; 
  } 
  /* 平板端区块间距：适配较窄屏幕 */
  .card { 
    margin-bottom: 16px; 
  }
}

@media (max-width: 640px) { 
  .note-list { 
    grid-template-columns: 1fr; 
    gap: 6px; 
  } 
  /* 手机端区块间距：更紧凑但保持分隔感 */
  .card { 
    margin-bottom: 12px; 
  }
}

/* 骨架加载样式：用于加载占位，避免空白跳变与布局抖动 */
.note-item.skeleton { 
  position: relative; 
  overflow: hidden; 
}

.note-item.skeleton .skeleton-line { 
  height: 14px; 
  border-radius: 6px; 
  background: #f2f3f5; 
}

.note-item.skeleton .skeleton-pill { 
  display: inline-block; 
  height: 12px; 
  border-radius: 6px; 
  background: #f2f3f5; 
}

.note-item.skeleton::after {
  content: '';
  position: absolute; 
  left: -40%; 
  top: 0; 
  width: 40%; 
  height: 100%;
  background: linear-gradient(90deg, rgba(255,255,255,0) 0%, rgba(255,255,255,.6) 50%, rgba(255,255,255,0) 100%);
  animation: shimmer 1.2s infinite;
}

@keyframes shimmer { 
  0% { left: -40%; } 
  100% { left: 100%; } 
}

/* 分页控件已移除：组件不再显示传统分页 */
.pagination { 
  display: none !important; 
}

.pager-btn { 
  padding: 6px 10px; 
  border-radius: 6px; 
  border: 1px solid #dcdfe6; 
  background: #fff; 
  color: #303133; 
  cursor: pointer; 
}

.pager-btn:disabled { 
  cursor: not-allowed; 
  color: #c0c4cc; 
  background: #f5f7fa; 
  border-color: #ebeef5; 
}

.pager-btn:hover:not(:disabled) { 
  background: #f5f7ff; 
  border-color: #e0e9ff; 
}

.pager-info { 
  color: #606266; 
  font-size: 12px; 
}

.pager-input { 
  width: 64px; 
  padding: 6px 8px; 
  border: 1px solid #dcdfe6; 
  border-radius: 6px; 
  outline: none; 
}

.pager-input:focus { 
  border-color: #409eff; 
}

/* 加载更多（移动端） */
.load-more { 
  display: none; 
  align-items: center; 
  justify-content: center; 
  gap: 8px; 
  margin-top: 10px; 
}

.load-btn { 
  padding: 8px 12px; 
  border-radius: 6px; 
  border: 1px solid #dcdfe6; 
  background: #fff; 
  color: #303133; 
  cursor: pointer; 
}

.load-btn:disabled { 
  cursor: not-allowed; 
  color: #c0c4cc; 
  background: #f5f7fa; 
  border-color: #ebeef5; 
}

.load-btn:hover:not(:disabled) { 
  background: #f5f7ff; 
  border-color: #e0e9ff; 
}

.load-sentinel { 
  width: 100%; 
  height: 1px; 
}

/* 移动端：隐藏分页，仅显示"加载更多" */
@media (max-width: 640px) {
  .pagination { 
    display: none; 
  }
  .load-more { 
    display: flex; 
  }
}
</style>