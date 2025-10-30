<template>
  <!-- 通用站点便签列表组件：用于展示同类型的“网站便签”（如网站/Git/其他站点类便签） -->
  <div class="card" :id="id">
    <div class="card-title">{{ title }}</div>
    <div class="card-desc" style="display:flex; align-items:center; justify-content:space-between; gap:8px;">
      <span>{{ subtitle }}</span>
      <!-- 切换控件：登录后显示，可在公开/我的之间切换；未登录默认公开且不显示控件 -->
      <template v-if="isLoggedIn && showToggle">
        <el-radio-group v-model="source" size="small" @change="onSourceChange">
          <el-radio-button label="public">公开</el-radio-button>
          <!-- 撤销聚合模式：恢复固定“我的”选项，仅登录可用 -->
          <el-radio-button label="mine">我的</el-radio-button>
        </el-radio-group>
      </template>
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
      <!-- 列表项：统一展示站点名与介绍，点击打开链接 -->
      <li class="note-item" v-for="it in pagedNotes" :key="it.id" @click="open(it)" role="button" v-show="!isLoading">
        <div class="title">{{ siteName(it) }}</div>
        <!-- 便签简介：鼠标悬停时显示完整内容，使用浏览器原生 title 属性 -->
        <div class="content content-with-tooltip" 
             :title="siteDesc(it) || snippet(it.content || '')">
          {{ siteDesc(it) || snippet(it.content || '') }}
        </div>
        <div class="meta">
          <div class="left"><span class="author">{{ authorLabel }}</span></div>
          <div class="right"><span class="time"></span></div>
        </div>
      </li>
      <!-- 空态：仅在非加载且无数据时显示 -->
      <li v-if="!isLoading && !total" class="empty">暂无{{ title }}</li>
    </ul>
    <!-- 分页控件：上一页/下一页 + 页数指示；仅在显示分页时展示 -->
    <div class="pagination" v-if="showPagination && total">
      <button class="pager-btn" :disabled="!hasPrev" @click="prevPage">上一页</button>
      <span class="pager-info">第 {{ page }} 页 / 共 {{ totalPages }} 页</span>
      <!-- 页码输入跳转：输入页码后按回车或点击“跳转” -->
      <input class="pager-input" type="number" v-model.number="pageInput" :min="1" :max="totalPages" @keyup.enter="jumpPage" aria-label="页码输入" />
      <button class="pager-btn" @click="jumpPage">跳转</button>
      <button class="pager-btn" :disabled="!hasNext" @click="nextPage">下一页</button>
    </div>

    <!-- 移动端加载更多：小屏隐藏分页，仅保留“加载更多”，并在到底部时自动加载下一页 -->
    <div class="load-more" v-if="isMobile && total">
      <button class="load-btn" :disabled="!hasNext" @click="loadMore">加载更多</button>
      <!-- 自动加载触发哨兵：进入视口时尝试加载下一页（仅移动端） -->
      <div ref="loadMoreSentinel" class="load-sentinel" aria-hidden="true"></div>
    </div>
  </div>
</template>

<script setup>
// 通用列表组件脚本逻辑：提供来源切换、数据请求与严格标签过滤。
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { http } from '@/api/http'
import { getToken } from '@/utils/auth'
// 站点便签工具函数：统一解析站点信息与跳转逻辑，严格标签过滤
import { snippet, siteName, siteDesc, hasTag, openSite } from '@/utils/siteNoteUtils'

/**
 * Props 约定：
 * - id：区块 id（用于页面内导航锚点）。
 * - title：区块标题，如“网站便签 / Git便签”。
 * - subtitle：副标题，如“推荐站点 / 常用 Git 命令与参考”。
 * - tag：严格过滤使用的标签（大小写不敏感），如 '网站' 或 'git'。
 * - showToggle：是否显示“公开/我的”切换，默认 true。
 * - pageSize：分页的每页条数（客户端分页），默认 12。
 * - showPagination：是否显示底部分页控件，默认 true。
 * - authorLabel：列表项左侧标签文案，默认 '站点'。
*/
const props = defineProps({
  id: { type: String, required: true },
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  tag: { type: String, required: true },
  showToggle: { type: Boolean, default: true },
  pageSize: { type: Number, default: 12 },
  showPagination: { type: Boolean, default: true },
  authorLabel: { type: String, default: '站点' },
})

// 登录态（响应式）。退出登录后自动回退为“公开”来源。
const tokenRef = ref('')
const isLoggedIn = computed(() => !!(tokenRef.value && tokenRef.value.trim()))
function refreshAuth(){
  try{ tokenRef.value = String(getToken() || '') }catch{ tokenRef.value = '' }
}

// 数据来源：'public' 公开；'mine' 我的（仅登录时可用）。
const source = ref('public')
// 全量数据：请求后过滤得到的完整列表，用于客户端分页
const notes = ref([])
// 当前页：从 1 开始；切换来源/登录态变化时重置为 1
const page = ref(1)
// 页码输入：与当前页同步，用户输入后跳转
const pageInput = ref(1)
const isLoading = ref(false)

// 打开站点链接：复用工具函数，统一跳转行为。
function open(it){ openSite(it) }

/**
 * 加载便签列表：与网站/Git 区一致，按来源请求并在前端严格过滤标签。
 * - 通过 q=tag 做粗筛；最终严格以标签为准（大小写不敏感）。
 */
/**
 * 加载便签列表：按来源请求并在前端进行严格标签过滤（大小写不敏感）。
 * - public：仅公开；
 * - mine：仅我的（需登录）。
 */
async function load(){
  try{
    isLoading.value = true
    // 单一来源：公开或我的
    const params = { page: 1, size: 100, q: props.tag }
    if (source.value === 'mine' && isLoggedIn.value){
      Object.assign(params, { mineOnly: true })
    }else{
      Object.assign(params, { isPublic: true })
    }
    const { data } = await http.get('/notes', { params, suppress401Redirect: true })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    notes.value = (items || []).filter(n => hasTag(n, props.tag, true))
    // 每次重新加载时将页码重置为 1，并同步页码输入
    page.value = 1
    pageInput.value = 1
  }catch(e){ notes.value = [] }
  finally{ isLoading.value = false }
}

// 来源切换：未登录时强制回退为 public。
/**
 * 来源切换：仅登录时允许“我的”；未登录强制回退“公开”。
 */
function onSourceChange(){
  if (!isLoggedIn.value && source.value !== 'public') source.value = 'public'
  load()
}

// 初次挂载：设置默认来源与登录态，并加载列表。
onMounted(() => {
  source.value = 'public'
  refreshAuth()
  load()
})

// 登录态变化时重置来源为公开并重载。
watch(isLoggedIn, () => { source.value = 'public'; load() })

// 登录态刷新监听：确保退出登录后无需手动刷新即可隐藏控件并回退公开来源
// 1) hash 与可见性变化：在同页退出/登录后刷新 token
// 2) 兜底轻量轮询：每 1s 检查一次 token（避免某些场景下无事件触发）
// 3) 路由变化：导航时也刷新一次（更稳妥）
let authPoller = null
function setupAuthListeners(){
  const onHashChange = () => refreshAuth()
  const onVisibilityChange = () => { if (!document.hidden) refreshAuth() }
  window.addEventListener('hashchange', onHashChange)
  window.addEventListener('visibilitychange', onVisibilityChange)
  // 轮询兜底
  authPoller = setInterval(refreshAuth, 1000)
  // 路由变化时刷新
  try{
    const route = useRoute()
    watch(() => route.fullPath, () => refreshAuth())
  }catch{}
  // 存储事件（跨标签页退出登录可感知）；同页更新不触发，但作为补充
  const onStorage = (e) => { if (e.key === 'token') refreshAuth() }
  window.addEventListener('storage', onStorage)
  // 清理函数注册在 onUnmounted 中
  onUnmounted(() => {
    window.removeEventListener('hashchange', onHashChange)
    window.removeEventListener('visibilitychange', onVisibilityChange)
    window.removeEventListener('storage', onStorage)
    if (authPoller){ clearInterval(authPoller); authPoller = null }
  })
}
onMounted(setupAuthListeners)

// 客户端分页：对过滤后的完整列表进行切片
const total = computed(() => notes.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / props.pageSize)))
const hasPrev = computed(() => page.value > 1)
const hasNext = computed(() => page.value < totalPages.value)
const pagedNotes = computed(() => {
  const start = (page.value - 1) * props.pageSize
  return notes.value.slice(start, start + props.pageSize)
})

// 同步页码输入与当前页变化（用户翻页时更新输入框）
watch(page, (p) => { pageInput.value = p })

function goPage(p){
  if (p < 1 || p > totalPages.value) return
  page.value = p
}
function prevPage(){ if (hasPrev.value) page.value -= 1 }
function nextPage(){ if (hasNext.value) page.value += 1 }
function jumpPage(){
  let p = Number(pageInput.value)
  if (!Number.isFinite(p)) return
  p = Math.floor(p)
  if (p < 1) p = 1
  if (p > totalPages.value) p = totalPages.value
  goPage(p)
}
function loadMore(){ if (hasNext.value) nextPage() }

// 移动端：隐藏分页、仅保留“加载更多”，并支持到底部自动加载
const isMobile = ref(false)
const loadMoreSentinel = ref(null)
let io = null
let mq = null
const onMobileChange = () => { isMobile.value = mq?.matches ?? false }
function setupMobileMatch(){
  try{
    mq = window.matchMedia('(max-width: 640px)')
    onMobileChange()
    // 监听断点变化以动态切换移动端行为
    if (mq.addEventListener){ mq.addEventListener('change', onMobileChange) }
    else if (mq.addListener){ mq.addListener(onMobileChange) }
  }catch{}
}
function setupObserver(){
  // 如已有观察者，先断开
  if (io){ try{ io.disconnect() }catch{} io = null }
  const el = loadMoreSentinel.value
  if (!el || !isMobile.value) return
  try{
    io = new IntersectionObserver((entries) => {
      for (const entry of entries){
        if (entry.isIntersecting && isMobile.value && hasNext.value && !isLoading.value){
          // 小节：仅客户端分页，直接翻到下一页；避免持续触发造成翻页过快
          nextPage()
        }
      }
    })
    io.observe(el)
  }catch{}
}
onMounted(() => { setupMobileMatch(); setupObserver() })
// 当移动端状态、页数或哨兵元素发生变化时，重新建立观察者
watch([isMobile, page, () => loadMoreSentinel.value], () => setupObserver())
onUnmounted(() => {
  if (io){ try{ io.disconnect() }catch{} io = null }
  if (mq){
    try{ mq.removeEventListener?.('change', onMobileChange) }catch{}
    try{ mq.removeListener?.(onMobileChange) }catch{}
    mq = null
  }
})
</script>

<style scoped>
/* 列表基础样式：保持与页面统一的卡片风格与尺寸设置（与网站/Git 区一致） */
.note-list { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 8px; }
.note-item { background:#fff; border:1px solid #ebeef5; border-radius:12px; padding:10px; height:110px; box-shadow:0 3px 10px rgba(0,0,0,0.05); cursor:pointer; transition: transform .15s ease, box-shadow .15s ease; display:flex; flex-direction:column; justify-content:space-between; box-sizing:border-box; }
.note-item:hover { transform: translateY(-2px); box-shadow:0 8px 20px rgba(0,0,0,0.08); }
.note-item .title { color:#303133; font-size:14px; font-weight:600; line-height:1.5; display:-webkit-box; -webkit-line-clamp:1; -webkit-box-orient: vertical; overflow:hidden; word-break:break-word; overflow-wrap:anywhere; }
.note-item .content { color:#303133; font-size:12px; line-height:1.5; display:-webkit-box; -webkit-line-clamp:1; -webkit-box-orient: vertical; overflow:hidden; word-break:break-word; overflow-wrap:anywhere; }

/* 简化 tooltip：仅使用浏览器原生 title 属性显示完整简介内容 */
.content-with-tooltip { 
  cursor: default; 
}
.note-item .meta { display:flex; justify-content:space-between; align-items:center; margin-top:6px; color:#606266; font-size:11px; }
.note-item .meta .left { display:flex; align-items:center; gap:6px; min-width:0; flex:1; }
.note-item .meta .right { display:flex; align-items:center; gap:6px; color:#909399; flex:none; }
.note-item .meta .author { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; min-width:0; flex:1; }
.note-list .empty { color: #909399; background: #fff; border: 1px dashed #e5e7eb; }

/* 响应式断点：窄屏下减列以保证可读性 */
@media (max-width: 960px){ .note-list { grid-template-columns: repeat(2, minmax(160px, 1fr)); gap: 8px; } }
@media (max-width: 640px){ .note-list { grid-template-columns: 1fr; gap: 6px; } }

/* 骨架加载样式：用于加载占位，避免空白跳变与布局抖动 */
.note-item.skeleton { position: relative; overflow: hidden; }
.note-item.skeleton .skeleton-line { height: 14px; border-radius: 6px; background: #f2f3f5; }
.note-item.skeleton .skeleton-pill { display:inline-block; height: 12px; border-radius: 6px; background: #f2f3f5; }
.note-item.skeleton::after {
  content: '';
  position: absolute; left: -40%; top: 0; width: 40%; height: 100%;
  background: linear-gradient(90deg, rgba(255,255,255,0) 0%, rgba(255,255,255,.6) 50%, rgba(255,255,255,0) 100%);
  animation: shimmer 1.2s infinite;
}
@keyframes shimmer { 0% { left: -40%; } 100% { left: 100%; } }

/* 分页控件样式：轻量按钮与页数指示，保持页面风格一致 */
.pagination { display:flex; align-items:center; justify-content:flex-end; gap:8px; margin-top:10px; }
.pager-btn { padding:6px 10px; border-radius:6px; border:1px solid #dcdfe6; background:#fff; color:#303133; cursor:pointer; }
.pager-btn:disabled { cursor:not-allowed; color:#c0c4cc; background:#f5f7fa; border-color:#ebeef5; }
.pager-btn:hover:not(:disabled) { background:#f5f7ff; border-color:#e0e9ff; }
.pager-info { color:#606266; font-size:12px; }
.pager-input { width: 64px; padding:6px 8px; border:1px solid #dcdfe6; border-radius:6px; outline:none; }
.pager-input:focus { border-color:#409eff; }

/* 加载更多（移动端） */
.load-more { display:none; align-items:center; justify-content:center; gap:8px; margin-top:10px; }
.load-btn { padding:8px 12px; border-radius:6px; border:1px solid #dcdfe6; background:#fff; color:#303133; cursor:pointer; }
.load-btn:disabled { cursor:not-allowed; color:#c0c4cc; background:#f5f7fa; border-color:#ebeef5; }
.load-btn:hover:not(:disabled) { background:#f5f7ff; border-color:#e0e9ff; }
.load-sentinel { width: 100%; height: 1px; }

/* 移动端：隐藏分页，仅显示“加载更多” */
@media (max-width: 640px){
  .pagination { display: none; }
  .load-more { display: flex; }
}
</style>