<template>
  <!-- 单列布局：去除左侧侧边栏，仅保留全宽顶栏与正文区域 -->
  <!-- 说明：继续复用 TwoPaneLayout 的顶栏吸顶与右侧滚动容器逻辑。 -->
  <TwoPaneLayout>
    <!-- 全宽顶栏：跨越左右两列并吸顶，顶栏内容全屏铺满 -->
    <template #topFull>
      <!-- 固定透明顶栏：transparent=true 禁止滚动时毛玻璃切换，保持沉浸式背景 -->
      <AppTopBar fluid :transparent="true" @search="onSearch" />
    </template>
    <template #rightMain>
      <div class="container">
        <div class="page-header">
          <h2>搜索结果</h2>
        </div>
        <!-- 顶部：弹幕流（复用 NotesBody，便于保留原搜索联动与草稿逻辑）
             说明：此区域仅展示首屏数据，不代表搜索总数；为避免“总数仅 20 条”的误解，关闭底部计数标签。 -->
        <NotesBody :query="query" :showComposer="false" :showCountTag="false" />

        <!-- 下方：与收藏页一致的便签展示（按年份分组 + 时间线 + NoteCard） -->
<!-- 数据来源：服务端分页 /api/shiyan?q=...&page=...&size=...，滚动到底自动加载下一页 -->
        <div class="year-groups">
          <div v-for="g in yearGroups" :key="g.year" class="year-group">
            <div class="year-header">
              <span class="year-title">{{ g.year }}</span>
            </div>
            <el-timeline>
              <transition-group name="list" tag="div">
                <el-timeline-item
                  v-for="n in g.items"
                  :key="n.id"
                  :timestamp="formatMD(n.createdAt || n.created_at)"
                  placement="top"
                >
                  <!-- 复用 NoteCard：支持长按动作（喜欢/收藏/删除等） -->
                  <NoteCard
                    :note="n"
                    :enableLongPressActions="true"
                    @toggle-like="toggleLike"
                    @toggle-favorite="toggleFavorite"
                  />
                </el-timeline-item>
              </transition-group>
            </el-timeline>
          </div>
        </div>

        <!-- 服务端分页 + 触底加载（无限滚动）
             说明：
             - 当列表靠近底部时，自动请求下一页并追加到现有数组；
             - 同时提供按钮手动触发，便于桌面端调试与回退；
             - 当 hasNext=false 或正在加载时禁用按钮。 -->
        <div class="load-more" v-if="hasNext || isLoading">
          <!-- 触底自动加载：默认隐藏按钮，仅在不支持 IntersectionObserver 时显示回退按钮 -->
          <button v-if="!supportsIO" class="load-btn" :disabled="!hasNext || isLoading" @click="loadMore">{{ isLoading ? '加载中…' : '加载更多' }}</button>
          <!-- 触底哨兵：进入视口尝试自动加载下一页 -->
          <div ref="loadMoreSentinel" class="load-sentinel" aria-hidden="true"></div>
        </div>
      </div>
    </template>
  </TwoPaneLayout>
  
</template>

<script setup>
// 搜索结果页：
// - 去除左侧侧边栏，仅保留全宽顶栏与正文；
// - 弹幕流由 NotesBody 展示；在其下方增加与收藏页一致的“按年份分组 + 时间线 + NoteCard”的列表；
// - 数据加载方式为服务端分页（/api/shiyan），支持 q + page + size，并提供触底自动加载与按钮手动加载。
// 修复说明：
// 1) 延后 IntersectionObserver 绑定到下一渲染帧（nextTick），确保哨兵已挂载；
// 2) 指定 IO 的 root 为右侧滚动容器，避免窗口滚动与容器滚动不一致导致不触发；
// 3) 新增“首屏不足自动填充”兜底逻辑，在内容不足一屏或 IO 未触发时主动拉取后续页。
import { ref, watch, defineAsyncComponent, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
const TwoPaneLayout = defineAsyncComponent(() => import('@/components/TwoPaneLayout.vue'))
const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))
const NotesBody = defineAsyncComponent(() => import('./notes/NotesBody.vue'))
const NoteCard = defineAsyncComponent(() => import('@/components/NoteCard.vue'))
import { http } from '@/api/http'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

// 搜索关键词：跟随顶栏输入与路由 query 同步
const query = ref(String(route.query.q || ''))
watch(() => route.query.q, (nv) => { query.value = String(nv || '') })

function onSearch(q){
  // 顶栏搜索：更新关键词与路由，并重置列表为第 1 页重新加载
  query.value = q || ''
  router.replace({ path: '/search', query: { q: query.value } })
  reload()
}

// 下方列表数据（与收藏页一致的展示效果）
const listItems = ref([])
// 服务端分页状态
const page = ref(1)
const size = ref(20)
const total = ref(0)
const isLoading = ref(false)
const hasNext = computed(() => listItems.value.length < total.value)
const loadMoreSentinel = ref(null)
// 浏览器能力检测：是否支持 IntersectionObserver，用于决定是否显示手动按钮
const supportsIO = ref(true)
let io = null

function normalizeNote(it){
  // 统一字段命名与类型，便于 NoteCard 使用
  return {
    id: it.id,
    content: String(it.content ?? it.text ?? ''),
    tags: Array.isArray(it.tags) ? it.tags : String(it.tags || '').split(',').filter(Boolean),
    color: String(it.color ?? '#ffd966'),
    authorName: String(it.authorName ?? it.author_name ?? ''),
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
    favorited: Boolean(it.favoritedByMe ?? it.favorited ?? false),
    isPublic: Boolean(it.isPublic ?? it.is_public ?? false),
    createdAt: it.createdAt ?? it.created_at,
    updatedAt: it.updatedAt ?? it.updated_at,
  }
}

/** 加载一页搜索结果（服务端分页） */
async function fetchPage(p = 1){
  if (isLoading.value) return
  isLoading.value = true
  try{
    // 使用 /shiyan 接口：传递 q + page + size；不强制 mineOnly，以保持“公开 + 我的”的联合搜索
    // 非登录态：服务端会自动限制为公开；登录态：默认返回“公开 + 我的全部”，此处排除归档项。
    const { data } = await http.get('/shiyan', { params: { q: query.value, page: p, size: size.value, archived: false }, suppress401Redirect: true })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    const mapped = (items || []).map(normalizeNote)
    const t = (data?.total ?? data?.count ?? null)
    if (typeof t === 'number') total.value = t
    if (p <= 1) listItems.value = mapped
    else listItems.value = listItems.value.concat(mapped)
    page.value = p
  }catch(e){ ElMessage.error('加载搜索结果失败') }
  finally{
    isLoading.value = false
    // 若服务端未返回 total，则根据实际数量粗略估计，以便在小数据集下给出“已无更多”的提示
    if (!total.value){
      const lastCount = listItems.value.length % size.value
      if (lastCount !== 0) total.value = listItems.value.length
    }
  }
}
function reload(){ page.value = 1; total.value = 0; listItems.value = []; return fetchPage(1) }
function loadMore(){ if (hasNext.value && !isLoading.value) fetchPage(page.value + 1) }
// 工具函数：查找右侧滚动容器（TwoPaneLayout 的 rightMain）并设置 IO 的 root
function getScrollParent(el){
  // 详细注释：Search 页右侧主区域为独立滚动容器，需将 IO 的 root 指向该容器。
  let node = el?.parentElement
  while (node){
    const style = window.getComputedStyle(node)
    const overflowY = style.overflowY
    if (overflowY === 'auto' || overflowY === 'scroll') return node
    node = node.parentElement
  }
  return null
}
async function setupInfiniteScroll(){
  try{
    if (!('IntersectionObserver' in window)) return
    // 关键：等待下一渲染帧，确保哨兵节点已挂载，否则初次绑定会失败，后续也不会自动生效
    await nextTick()
    const root = getScrollParent(loadMoreSentinel.value)
    io = new IntersectionObserver((entries) => { for (const e of entries){ if (e.isIntersecting) loadMore() } }, { root, rootMargin: '200px', threshold: 0.01 })
    if (loadMoreSentinel.value) io.observe(loadMoreSentinel.value)
  }catch{}
}
function teardownInfiniteScroll(){ try{ if (io) io.disconnect(); io = null }catch{} }

// —— 兜底：首屏不足自动填充 ——
// 场景：当列表高度不足一屏，或观察器未能及时触发时，主动拉取后续页直至填满或达到循环上限。
function isSentinelVisible(root){
  try{
    const el = loadMoreSentinel.value
    if (!el) return false
    const rect = el.getBoundingClientRect()
    if (root && root.getBoundingClientRect){
      const rootRect = root.getBoundingClientRect()
      return rect.top <= (rootRect.bottom + 200)
    }
    return rect.top <= (window.innerHeight + 200)
  }catch{ return false }
}
async function autoFillIfShort(maxLoops = 5){
  let loops = 0
  const root = getScrollParent(loadMoreSentinel.value)
  while (loops < maxLoops && hasNext.value && !isLoading.value && isSentinelVisible(root)){
    await fetchPage(page.value + 1)
    await nextTick()
    loops++
  }
}

onMounted(async () => {
  // 初始化支持能力：默认 true，实际根据 window 能力检测更新
  try { supportsIO.value = 'IntersectionObserver' in window } catch { supportsIO.value = false }
  // 首屏拉取并绑定观察器
  await reload();
  await setupInfiniteScroll();
  // 兜底：若首屏不足一屏，尝试填充至可滚动
  await autoFillIfShort(5)
})
onUnmounted(() => { teardownInfiniteScroll() })

// 工具：月-日 时:分 格式（中文样式）与年份分组
function pad(n){ return String(n).padStart(2, '0') }
function formatMD(t){
  if (!t) return ''
  try{
    const d = new Date(t)
    if (isNaN(d.getTime())) return ''
    const M = pad(d.getMonth()+1), D = pad(d.getDate()), h = pad(d.getHours()), m = pad(d.getMinutes())
    return `${M}月${D}日 ${h}:${m}`
  }catch{ return '' }
}
const yearGroups = computed(() => {
  const map = new Map()
  for (const n of listItems.value){
    const t = new Date(n.createdAt || n.created_at || 0)
    const year = isNaN(t.getTime()) ? '未知' : t.getFullYear()
    if (!map.has(year)) map.set(year, [])
    map.get(year).push(n)
  }
  const groups = []
  for (const [year, items] of map.entries()) groups.push({ year, items })
  return groups
})

// 喜欢与收藏动作：复用收藏页的交互逻辑
async function toggleLike(n){
  if (n.likeLoading) return
  n.likeLoading = true
  try{
    // 路径切换：统一使用 /shiyan/{id}/like|unlike
    const url = n.liked ? `/shiyan/${n.id}/unlike` : `/shiyan/${n.id}/like`
    const { data } = await http.post(url)
    n.likeCount = Number(data?.count ?? data?.like_count ?? (n.likeCount || 0))
    n.liked = Boolean((data?.likedByMe ?? data?.liked_by_me ?? n.liked))
  }catch{ ElMessage.error('操作失败') }
  finally{ n.likeLoading = false }
}
async function toggleFavorite(n){
  if (n.favoriteLoading) return
  n.favoriteLoading = true
  try{
    // 路径切换：统一使用 /shiyan/{id}/favorite|unfavorite
    const url = n.favorited ? `/shiyan/${n.id}/unfavorite` : `/shiyan/${n.id}/favorite`
    const { data } = await http.post(url)
    n.favoriteCount = Number(data?.count ?? data?.favorite_count ?? (n.favoriteCount || 0))
    n.favorited = Boolean((data?.favoritedByMe ?? data?.favorited_by_me ?? n.favorited))
  }catch{ ElMessage.error('操作失败') }
  finally{ n.favoriteLoading = false }
}
</script>

<style scoped>
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
/* 年份分组时间线样式（与收藏页保持一致体验） */
.year-groups { margin-top: 12px; }
.year-group { margin-bottom: 16px; }
.year-header { display:flex; align-items:center; padding:10px 12px; border-radius:12px; background:#ffffff; box-shadow: 0 6px 20px rgba(0,0,0,0.06); position: sticky; top: 6px; z-index: 10; }
.year-title { font-size:22px; font-weight:700; color:#303133; letter-spacing:0.5px; }
.year-header::before { content:''; display:block; width:6px; height:24px; border-radius:6px; background:#409eff; margin-right:10px; opacity:0.85; }

/* 分页与触底加载区域 */
.load-more { display:flex; align-items:center; justify-content:center; gap:8px; margin-top:10px; }
.load-btn { padding:8px 12px; border-radius:6px; border:1px solid #dcdfe6; background:#fff; color:#303133; cursor:pointer; }
.load-btn:disabled { cursor:not-allowed; color:#c0c4cc; background:#f5f7fa; border-color:#ebeef5; }
.load-btn:hover:not(:disabled) { background:#f5f7ff; border-color:#e0e9ff; }
.load-sentinel { width: 100%; height: 1px; }
</style>