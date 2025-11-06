<!--
  喜欢视图（Likes）
  说明：
  - 右侧正文：弹幕墙 + 喜欢的拾言列表；
  - 服务端分页：支持“加载更多”与触底自动加载，移动端与桌面统一样式；
  - 空状态与加载状态：提供统一提示与骨架；
  - 与顶栏联动：顶栏统一吸顶，全宽插槽布局；
-->
<template>
  <!-- 单列布局：去掉左侧区域，仅保留全宽顶栏和正文
       说明：继续复用 TwoPaneLayout 的顶栏吸顶与右侧唯一滚动容器逻辑。 -->
  <TwoPaneLayout>
    <!-- 全宽顶栏：跨越左右两列并吸顶，顶栏内容全屏铺满 -->
    <template #topFull>
      <!-- 固定透明顶栏：transparent=true 禁止滚动时毛玻璃切换，保持沉浸式背景 -->
      <AppTopBar fluid :transparent="true" @search="onSearch" />
    </template>
    <template #rightMain>
      <div class="container">
        <div class="page-header">
          <h2>我喜欢的</h2>
        </div>
        <!-- 空状态：当非加载中且没有任何“喜欢”的便签时，展示友好引导 -->
        <!-- 设计说明：
             - 使用 Element Plus 的 ElEmpty（<el-empty>）组件展示占位图片与文案；
             - 不强制使用自定义图片，默认内置图片即可；如需品牌化可将 emptyImage 替换为你的 URL；
             - 提供引导按钮跳转到“广场”页，鼓励用户去浏览并点赞。 -->
        <div class="empty-wrap" v-if="isEmpty">
  <!-- 文案重命名：将“便签”统一改为“拾言” -->
  <el-empty :image="emptyImage" description="喜欢列表为空，快去添加喜欢的拾言">
            <el-button type="primary" @click="goExplore">去广场看看</el-button>
          </el-empty>
        </div>

        <!-- 弹幕墙：仅在非空时展示，用于增强页面动效与氛围 -->
        <DanmuWall v-if="!isEmpty" :items="danmuItems" :rows="6" :speed-scale="1.35" />

        <!-- 年份分组时间线：在非空时按年分组展示喜欢的便签列表 -->
        <div class="year-groups" v-if="!isEmpty">
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
                  <NoteCard
                    :note="n"
                    :enableLongPressActions="true"
                    :showAuthorAvatar="true"
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
        <div class="load-more" v-if="!isEmpty && (hasNext || isLoading)">
          <button class="load-btn" :disabled="!hasNext || isLoading" @click="loadMore">{{ isLoading ? '加载中…' : '加载更多' }}</button>
          <div ref="loadMoreSentinel" class="load-sentinel" aria-hidden="true"></div>
        </div>
      </div>
    </template>
  </TwoPaneLayout>
</template>

<script setup>
import { ref, onMounted, onUnmounted, defineAsyncComponent, computed } from 'vue'
const TwoPaneLayout = defineAsyncComponent(() => import('@/components/TwoPaneLayout.vue'))
import { http } from '@/api/http'
import { ElMessage } from 'element-plus'

const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))
const DanmuWall = defineAsyncComponent(() => import('@/components/DanmuWall.vue'))
const NoteCard = defineAsyncComponent(() => import('@/components/NoteCard.vue'))

const query = ref('')
const danmuItems = ref([])
// 空状态计算：当“未在加载中且列表为空”时视为空
const isEmpty = computed(() => !isLoading.value && danmuItems.value.length === 0)
// 空状态图片：留空使用 Element Plus 默认图片；如需品牌化可设置为自定义图片 URL
const emptyImage = ''
// 服务端分页状态（响应式）
// - page/size 控制请求翻页；
// - total/hasNext 基于服务端 total 计算是否还有下一页；
// - isLoading 避免并发请求与重复触发；
const page = ref(1)
const size = ref(20)
const total = ref(0)
const hasNext = computed(() => danmuItems.value.length < total.value)
const isLoading = ref(false)
// 触底哨兵：进入视口时尝试加载下一页
const loadMoreSentinel = ref(null)
let io = null

function onSearch(q){
  // 搜索触发：重置分页为第 1 页并重新加载；
  query.value = q || ''
  reload()
}

onMounted(() => {
  // 首次加载：第 1 页
  reload()
  // 设置触底加载：IntersectionObserver 监听哨兵元素进入视口
  setupInfiniteScroll()
})
onUnmounted(() => { teardownInfiniteScroll() })

/**
 * 跳转到广场（首页）进行浏览与点赞
 * 说明：广场路径为 '/'（参见 router/index.js），使用 hash 路由进行跳转。
 */
function goExplore(){
  try{ window.location.hash = '#/' }catch{}
}

function normalizeNote(it){
  return {
    id: it.id,
    content: String(it.content ?? it.text ?? ''),
    tags: Array.isArray(it.tags) ? it.tags : String(it.tags || '').split(',').filter(Boolean),
    color: String(it.color ?? '#ffd966'),
    authorName: String(it.authorName ?? it.author_name ?? (it.user?.nickname ?? it.user?.username ?? '')),
    // 作者头像字段：兼容多命名与嵌套结构；为空时组件内回退默认头像
    authorAvatarUrl: (
      it.authorAvatarUrl ?? it.author_avatar_url ??
      it.userAvatarUrl ?? it.user_avatar_url ??
      it.avatarUrl ?? it.avatar_url ??
      (it.author ? (it.author.avatarUrl ?? it.author.avatar_url) : '') ??
      (it.user ? (it.user.avatarUrl ?? it.user.avatar_url) : '')
    ) || '',
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    favorited: Boolean(it.favoritedByMe ?? it.favorited ?? false),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
    isPublic: Boolean(it.isPublic ?? it.is_public ?? false),
    createdAt: it.createdAt ?? it.created_at,
    updatedAt: it.updatedAt ?? it.updated_at,
  }
}

/**
 * 加载一页数据（服务端分页）
 * - 请求参数：page/size + q（关键词）；
 * - 兼容返回结构：数组 或 PageResult（items/records + total）。
 * - 追加模式：将新页数据 append 到 danmuItems。
 */
async function fetchPage(p = 1){
  if (isLoading.value) return
  isLoading.value = true
  try{
    // 路径切换：统一使用 /shiyan/liked
    const { data } = await http.get('/shiyan/liked', {
      params: { q: query.value, page: p, size: size.value },
      // 关键：抑制 401 的全局重定向，由本页自行提示与跳转
      suppress401Redirect: true,
    })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    const mapped = (items || []).map(normalizeNote)
    // 更新总数（若服务端返回 total），否则按累加近似估算
    const t = (data?.total ?? data?.count ?? null)
    if (typeof t === 'number') total.value = t
    // 追加到现有列表
    if (p <= 1) danmuItems.value = mapped
    else danmuItems.value = danmuItems.value.concat(mapped)
    // 页码前进
    page.value = p
  }catch(e){
    const status = e?.response?.status
    if (status === 401) {
      try{ ElMessage.warning('登录状态失效，请重新登录') }catch{}
      const redirect = encodeURIComponent(window.location.hash ? window.location.hash.slice(1) : '/likes')
      window.location.hash = `#/login?redirect=${redirect}`
      return
    } else {
      ElMessage.error('加载点赞数据失败')
    }
  }finally{
    isLoading.value = false
    // 若服务端未返回 total，则根据当页长度与 size 粗略判断是否还有下一页
    if (!total.value){
      const lastCount = danmuItems.value.length % size.value
      if (lastCount === 0) {
        // 可能仍有下一页（不确定，总数未知）
      } else {
        total.value = danmuItems.value.length
      }
    }
  }
}

/** 重置并加载第 1 页 */
function reload(){
  page.value = 1
  total.value = 0
  danmuItems.value = []
  fetchPage(1)
}

/** 手动加载下一页 */
function loadMore(){ if (hasNext.value && !isLoading.value) fetchPage(page.value + 1) }

/** 设置触底加载（IntersectionObserver） */
function setupInfiniteScroll(){
  try{
    if (!('IntersectionObserver' in window)) return
    io = new IntersectionObserver((entries) => {
      for (const e of entries){
        if (e.isIntersecting){
          loadMore()
        }
      }
    })
    if (loadMoreSentinel.value) io.observe(loadMoreSentinel.value)
  }catch{}
}
function teardownInfiniteScroll(){
  try{
    if (io) io.disconnect()
    io = null
  }catch{}
}

function pad(n){ return String(n).padStart(2, '0') }
function formatMD(t){
  if (!t) return ''
  try{
    const d = new Date(t)
    if (isNaN(d.getTime())) return ''
    const M = pad(d.getMonth()+1)
    const D = pad(d.getDate())
    const h = pad(d.getHours())
    const m = pad(d.getMinutes())
    return `${M}月${D}日 ${h}:${m}`
  }catch{ return '' }
}

const yearGroups = computed(() => {
  const map = new Map()
  for (const n of danmuItems.value){
    const t = new Date(n.createdAt || n.created_at || 0)
    const year = isNaN(t.getTime()) ? '未知' : t.getFullYear()
    if (!map.has(year)) map.set(year, [])
    map.get(year).push(n)
  }
  const groups = []
  for (const [year, items] of map.entries()) groups.push({ year, items })
  return groups
})

async function toggleLike(n){
  if (n.likeLoading) return
  n.likeLoading = true
  try{
    // 路径切换：统一使用 /shiyan/{id}/like|unlike
    const url = n.liked ? `/shiyan/${n.id}/unlike` : `/shiyan/${n.id}/like`
    const { data } = await http.post(url)
    n.likeCount = Number(data?.count ?? data?.like_count ?? (n.likeCount || 0))
    n.liked = Boolean((data?.likedByMe ?? data?.liked_by_me ?? n.liked))
    // 若取消喜欢，则从当前列表移除
    if (!n.liked){
      danmuItems.value = danmuItems.value.filter(x => x.id !== n.id)
    }
  }catch(e){
    ElMessage.error('操作失败')
  }finally{
    n.likeLoading = false
  }
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
  }catch(e){
    ElMessage.error('操作失败')
  }finally{
    n.favoriteLoading = false
  }
}

function sampleDanmu(){
  // 示例弹幕数据（在无接口或加载失败时展示）
  return [
    { id: 1, content: '喜欢：清晨的第一缕阳光', tags:['生活'], color:'#ffd966', likeCount: 12, liked: true },
    { id: 2, content: '好句子：山高路远，勇者不惧', tags:['语录'], color:'#b6d7a8', likeCount: 30, liked: true },
    { id: 3, content: '打卡：今日阅读《小王子》', tags:['阅读'], color:'#a4c2f4', likeCount: 7, liked: true },
  ]
}
</script>

<style scoped>
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.year-group { margin-bottom: 16px; }
.year-header { display:flex; align-items:center; padding:10px 12px; border-radius:12px; background:#ffffff; box-shadow: 0 6px 20px rgba(0,0,0,0.06); position: sticky; top: 6px; z-index: 10; }
.year-title { font-size:22px; font-weight:700; color:#303133; letter-spacing:0.5px; }
/* 空状态容器：居中布局，适度留白 */
.empty-wrap { display:flex; align-items:center; justify-content:center; padding: 48px 12px; }
/* 调整空状态图片尺寸与自适应 */
:deep(.el-empty__image) { width: 160px; height: auto; }
/* 空状态文案颜色与间距 */
:deep(.el-empty__description) { color:#909399; margin-top: 8px; }
/* 隐藏便签卡片上的公开/私有标签，仅保留作者标签 */
:deep(.meta.bottom-left .el-tag--success),
:deep(.meta.bottom-left .el-tag--info) {
  display: none !important;
}
/* 布局覆盖：在喜欢页隐藏左侧列并取消列间距，使页面成为真正的单列
   说明：
   - TwoPaneLayout 默认两列（auto 1fr）并设置 column-gap: 12px；
   - 若不渲染 #left 插槽，左列宽度为 0，但仍残留 12px 列间距；
   - 覆盖 .bottom-row 与 .col-left，实现完全单列展示，并保留顶栏吸顶与右侧滚动。 */
:deep(.bottom-row){ grid-template-columns: 1fr; column-gap: 0; }
:deep(.col-left){ display: none !important; }
/* 回退说明：
   - 移除了页面级顶栏包裹宽度限制（topbar-wrap），顶栏使用全宽插槽；
   - 保持原有样式与滚动行为，避免对顶栏组件造成间接影响。 */
/* 加载更多（移动端与桌面统一样式） */
.load-more { display:flex; align-items:center; justify-content:center; gap:8px; margin:16px 0; }
.load-btn { padding:8px 12px; border-radius:6px; border:1px solid #dcdfe6; background:#fff; color:#303133; cursor:pointer; }
.load-btn:disabled { cursor:not-allowed; color:#c0c4cc; background:#f5f7fa; border-color:#ebeef5; }
.load-btn:hover:not(:disabled) { background:#f5f7ff; border-color:#e0e9ff; }
.load-sentinel { width:100%; height: 1px; }
</style>