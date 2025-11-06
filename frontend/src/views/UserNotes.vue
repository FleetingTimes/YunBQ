<template>
  <!-- 他人拾言页：复用 TwoPaneLayout 与公共顶栏，右侧展示目标用户的公开拾言 -->
  <TwoPaneLayout class="user-notes-layout">
    <!-- 全宽吸顶顶栏：与站内其它页面一致，保持统一交互与视觉 -->
    <template #topFull>
      <AppTopBar fluid />
    </template>

    <!-- 右侧正文区域：顶部个人资料摘要 + 下方拾言时间线 -->
    <template #rightMain>
      <div class="container">
        <!-- 个人资料摘要：使用路由传参（username/nickname/avatar）渲染目标用户信息 -->
        <!-- 说明：
             - 初版不依赖后端“按用户名查询用户信息”的接口，直接使用消息页传来的昵称与头像；
             - 若未传递头像/昵称，使用 username 显示并采用默认头像；
             - 后续如提供 /account/user?username= 之类的接口，可在 onMounted 中补充拉取完善信息。 -->
        <div class="profile-summary">
          <img :src="avatarDisplay" alt="avatar" class="avatar-lg" width="260" height="260" @error="onAvatarError" />
          <div class="text">
            <!-- 展示昵称/用户名（优先昵称，其次用户名） -->
            <div class="nickname">{{ displayName }}</div>
            <!-- 展示对方签名：优先使用后端拉取的签名，其次使用路由 query 传来的签名；若均无，则显示“暂无签名！” -->
            <div class="signature" :title="signatureDisplay">{{ signatureDisplay }}</div>
          </div>
        </div>

        <!-- 年份分组时间线：仅展示目标用户的公开拾言（前端按作者过滤） -->
        <div class="year-groups">
          <div v-for="g in yearGroups" :key="g.year" class="year-group">
            <div class="year-header"><span class="year-title">{{ g.year }}</span></div>
            <el-timeline>
              <transition-group name="list" tag="div">
                <el-timeline-item
                  v-for="n in g.items"
                  :key="n.id"
                  :timestamp="formatMD(n.createdAt || n.created_at)"
                  placement="top">
                  <!-- 使用通用 NoteCard 展示拾言内容与交互（点赞/收藏） -->
                  <NoteCard :note="n" :enableLongPressActions="true" @toggle-like="toggleLike" @toggle-favorite="toggleFavorite" />
                </el-timeline-item>
              </transition-group>
            </el-timeline>
          </div>
        </div>

        <!-- 加载更多：服务端分页（公开数据），前端过滤后仍按时间线展示 -->
        <div class="load-more" v-if="hasNext || isLoading">
          <button class="load-btn" :disabled="!hasNext || isLoading" @click="loadMore">{{ isLoading ? '加载中…' : '加载更多' }}</button>
          <div ref="loadMoreSentinel" class="load-sentinel" aria-hidden="true"></div>
        </div>
        <!-- 空态：当过滤后无数据时提示 -->
        <div class="empty-wrap" v-if="!isLoading && filteredNotes.length === 0">
          <el-empty description="暂无公开拾言" />
        </div>
      </div>
    </template>
  </TwoPaneLayout>
</template>

<script setup>
// 视图说明：
// - 该页用于查看“他人拾言”（公开部分），不要求登录；
// - 数据来源：/shiyan 的公开数据（isPublic=true），前端按作者过滤；
// - 作者信息：由路由传参 username/nickname/avatar 提供，保持与消息页点击来源一致；
// - 后续如后端开放“按用户过滤”的端点，可将 fetchPage 的参数切换为按用户查询，提升效率。
import { ref, computed, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { http, avatarFullUrl } from '@/api/http'
import { ElMessage } from 'element-plus'
// 新增：引入本地登录态工具，用于在点赞/收藏前做未登录提醒而非跳转
import { getToken } from '@/utils/auth'

// 布局与通用组件
const TwoPaneLayout = defineAsyncComponent(() => import('@/components/TwoPaneLayout.vue'))
const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))
const NoteCard = defineAsyncComponent(() => import('@/components/NoteCard.vue'))

// 路由参数：用户名（必填）、昵称与头像（可选）、签名（可选）
const route = useRoute()
const targetUsername = computed(() => String(route.params.username || '').trim())
const targetNickname = computed(() => String(route.query.nickname || '').trim())
const targetAvatar = computed(() => String(route.query.avatar || '').trim())
// 可选：若消息页携带了签名，则优先显示（避免额外请求）
const querySignature = computed(() => String(route.query.signature || '').trim())
const targetUid = computed(() => {
  // 可选的用户 ID，若消息页传递则用于更可靠过滤
  const q = route.query.uid; const v = Number(q)
  return Number.isFinite(v) ? v : undefined
})

// 头像与展示名：优先昵称，其次用户名；头像用完整地址兜底
const avatarDisplay = computed(() => {
  const full = avatarFullUrl(targetAvatar.value)
  return full || 'https://api.iconify.design/mdi/account-circle.svg'
})
const displayName = computed(() => targetNickname.value || targetUsername.value || '未知用户')
function onAvatarError(e){ try{ const img = e?.target; if (img) img.src = 'https://api.iconify.design/mdi/account-circle.svg' }catch{} }

// —— 对方签名展示 ——
// 实现策略：
// 1) 首选路由 query 中的 `signature`（若消息来源包含则直接展示，减少一次网络请求）；
// 2) 次选调用后端公开资料接口（约定：GET /account/user?username=xxx 返回 { signature, avatarUrl, nickname, ... }）；
// 3) 若以上均不可用，则展示“暂无签名！”。
const profile = ref({ signature: '' })
const signatureDisplay = computed(() => (profile.value.signature?.trim()) || querySignature.value || '暂无签名！')

// 服务端分页（公开数据）
const page = ref(1)
const size = ref(20)
const total = ref(0)
const isLoading = ref(false)
const hasNext = computed(() => notes.value.length < total.value)
const loadMoreSentinel = ref(null)
let io = null

// 全量公开拾言（按页追加）；过滤由前端进行
const notes = ref([])

// 统一映射拾言结构，便于 NoteCard 展示
function normalizeNote(it){
  return {
    id: it.id,
    content: String(it.content ?? it.text ?? ''),
    tags: Array.isArray(it.tags) ? it.tags : String(it.tags || '').split(',').filter(Boolean),
    color: String(it.color ?? '#ffd966'),
    authorName: String(it.authorName ?? it.author_name ?? ''),
    userId: it.userId ?? it.user_id ?? undefined,
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
    favorited: Boolean(it.favoritedByMe ?? it.favorited ?? false),
    isPublic: Boolean(it.isPublic ?? it.is_public ?? false),
    createdAt: it.createdAt ?? it.created_at,
    updatedAt: it.updatedAt ?? it.updated_at,
  }
}

// 判定是否属于目标作者：优先使用 uid，其次比对昵称与用户名（大小写不敏感）
const targetNickLower = computed(() => targetNickname.value.toLowerCase())
const targetUserLower = computed(() => targetUsername.value.toLowerCase())
function isFromTarget(n){
  try{
    if (typeof targetUid.value !== 'undefined' && Number(n.userId) === Number(targetUid.value)) return true
    const name = String(n.authorName || '').trim().toLowerCase()
    if (!name) return false
    return (name === targetNickLower.value) || (name === targetUserLower.value)
  }catch{ return false }
}

// 过滤后的结果（保持原有时间排序）
const filteredNotes = computed(() => notes.value.filter(isFromTarget))

// 年份分组（按过滤结果）
const yearGroups = computed(() => {
  const map = new Map()
  for (const n of filteredNotes.value){
    const t = new Date(n.createdAt || n.created_at || 0)
    const year = isNaN(t.getTime()) ? '未知' : t.getFullYear()
    if (!map.has(year)) map.set(year, [])
    map.get(year).push(n)
  }
  const groups = []
  for (const [year, items] of map.entries()) groups.push({ year, items })
  return groups
})

// 拉取公开数据（服务端分页）；初版不传作者过滤，前端筛选
async function fetchPage(p = 1){
  if (isLoading.value) return
  isLoading.value = true
  try{
    // 请求公开拾言并排除归档项；说明：本页仅呈现目标用户的公开内容，服务端按公开分页，前端再按作者过滤。
    const { data } = await http.get('/shiyan', { params: { page: p, size: size.value, isPublic: true, archived: false }, suppress401Redirect: true })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    const mapped = (items || []).map(normalizeNote)
    if (p <= 1) notes.value = mapped; else notes.value = notes.value.concat(mapped)
    const t = data?.total ?? data?.count
    total.value = Number.isFinite(t) ? Number(t) : (notes.value.length + (items?.length || 0))
    page.value = p
  }catch(e){ ElMessage.error('加载公开拾言失败') }
  finally{ isLoading.value = false }
}

function loadMore(){ if (hasNext.value && !isLoading.value) fetchPage(page.value + 1) }

// 工具函数：获取右侧滚动容器（TwoPaneLayout 的 rightMain）以正确触发触底
function getScrollParent(el){
  // 详细注释：TwoPaneLayout 将右侧主区域设置为独立滚动容器（overflow:auto）。
  // 若 IO 的 root 未指向该容器，则在页面滚动不到视口底部时，哨兵不会进入视口，导致“只加载第一页”。
  // 这里沿父链查找最近的可滚动容器，并作为 IO 的 root。
  let node = el?.parentElement
  while (node){
    const style = window.getComputedStyle(node)
    const overflowY = style.overflowY
    if (overflowY === 'auto' || overflowY === 'scroll') return node
    node = node.parentElement
  }
  return null
}
function setupInfiniteScroll(){
  try{
    if (!('IntersectionObserver' in window)) return
    const root = getScrollParent(loadMoreSentinel.value)
    io = new IntersectionObserver((entries) => { for (const e of entries){ if (e.isIntersecting) loadMore() } }, { root, rootMargin: '200px', threshold: 0 })
    if (loadMoreSentinel.value) io.observe(loadMoreSentinel.value)
  }catch{}
}
function teardownInfiniteScroll(){ try{ if (io) io.disconnect(); io = null }catch{} }

// —— 点赞/收藏交互 ——
// 说明：
// - 与“我的拾言/收藏/搜索”等页面保持一致的端点与行为；
// - 点赞：POST /shiyan/{id}/like 或 /shiyan/{id}/unlike；返回最新计数与是否已点赞；
// - 收藏：POST /shiyan/{id}/favorite 或 /shiyan/{id}/unfavorite；返回最新计数与是否已收藏；
// - 未登录提示：在交互开始前检查本地 token，无则提示“请先登录”，不再触发全局 401 跳转，提升用户体验。
async function toggleLike(n){
  // 未登录直接提示并终止交互（不跳转登录）
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  try{
    if (n.likeLoading) return
    n.likeLoading = true
    const url = n.liked ? `/shiyan/${n.id}/unlike` : `/shiyan/${n.id}/like`
    const { data } = await http.post(url)
    // 同步最新状态与计数（兼容后端不同字段命名）
    n.likeCount = Number(data?.count ?? data?.like_count ?? (n.likeCount || 0))
    n.liked = Boolean((data?.likedByMe ?? data?.liked_by_me ?? n.liked))
  } catch (e) {
    ElMessage.error('点赞操作失败')
  } finally {
    n.likeLoading = false
  }
}
async function toggleFavorite(n){
  // 未登录直接提示并终止交互（不跳转登录）
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  try{
    if (n.favoriteLoading) return
    n.favoriteLoading = true
    const url = n.favorited ? `/shiyan/${n.id}/unfavorite` : `/shiyan/${n.id}/favorite`
    const { data } = await http.post(url)
    // 同步最新状态与计数（兼容后端不同字段命名）
    n.favoriteCount = Number(data?.count ?? data?.favorite_count ?? (n.favoriteCount || 0))
    n.favorited = Boolean((data?.favoritedByMe ?? data?.favorited_by_me ?? n.favorited))
  } catch (e) {
    ElMessage.error('收藏操作失败')
  } finally {
    n.favoriteLoading = false
  }
}

onMounted(async () => {
  // 拉取公开拾言数据与触底加载
  fetchPage(1)
  setupInfiniteScroll()
  // 尝试拉取对方公开资料（若后端提供该端点），用于显示签名
  try {
    if (targetUsername.value) {
      // 后端已开放匿名访问 /api/account/user，此处无需 suppress401Redirect
      const { data } = await http.get('/account/user', { params: { username: targetUsername.value } })
      // 兼容不同字段命名
      profile.value.signature = String(data?.signature ?? data?.sig ?? '').trim()
    }
  } catch (_) {
    // 忽略错误：若端点不存在或返回异常，保留“暂无签名！”兜底
  }
})
onUnmounted(() => { teardownInfiniteScroll() })

// 工具：时间格式化（中文样式）
function pad(n){ return String(n).padStart(2, '0') }
function formatMD(t){
  if (!t) return ''
  try{
    const d = new Date(t)
    if (isNaN(d.getTime())) return ''
    const M = pad(d.getMonth()+1); const D = pad(d.getDate()); const h = pad(d.getHours()); const m = pad(d.getMinutes())
    return `${M}月${D}日 ${h}:${m}`
  }catch{ return '' }
}
</script>

<style scoped>
.user-notes-layout .container { max-width: 1080px; margin: 0 auto; padding: 0 16px; }
.profile-summary { display:flex; align-items:center; gap:16px; margin: 12px 0 12px; }
.avatar-lg { border-radius:12px; object-fit:cover; }
.profile-summary .text .nickname { font-size:22px; font-weight:700; color:#303133; }
.profile-summary .text .signature { font-size:13px; color:#606266; }
.year-groups { display:flex; flex-direction:column; gap:12px; }
.year-header { display:flex; align-items:center; justify-content:flex-start; }
.year-title { font-weight:700; color:#606266; }
.load-more { display:flex; flex-direction:column; align-items:center; gap:8px; margin: 12px 0 24px; }
.load-btn { padding:8px 16px; border-radius:6px; border:1px solid #dcdfe6; background:#f5f7ff; color:#409eff; cursor:pointer; }
.load-btn:disabled { opacity:0.6; cursor:not-allowed; }
.load-sentinel { width:100%; max-width:640px; height:1px; }
.empty-wrap { padding: 12px 0; }
</style>