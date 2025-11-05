<template>
  <!-- 拾言小镇页面：两栏布局（顶栏 + 正文）
       说明：
       - 顶栏使用站点公共顶栏组件 AppTopBar；
       - 启用 fluid（铺满）与 transparent（固定透明），保持统一沉浸式体验；
       - 正文展示标题“拾言”和介绍“一句话：拾心之所言”。 -->
  <TwoPaneLayout>
    <!-- 顶栏：跨越左右两列并吸顶。fluid 让中间搜索区域拉伸，transparent 固定透明不随滚动变更毛玻璃态。 -->
    <template #topFull>
      <AppTopBar fluid :transparent="true" @search="onSearch" />
    </template>

    <!-- 正文：右下主区。此页为静态介绍，后续可扩展“镇首页公告/拾言动态”等模块。 -->
    <template #rightMain>
      <div class="town-container">
        <section class="hero-card" aria-label="拾言小镇简介">
          <div class="brand">
            <!-- 标题：严格按需求显示“拾言”（不加中点） -->
            <img src="https://api.iconify.design/mdi/home-city-outline.svg" alt="town" width="28" height="28" />
            <h1>拾言</h1>
          </div>
          <p class="subtitle">拾心之所言</p>
        </section>

        <!-- 内容区：拾言列表
             说明：
             - 分页加载 + 无限滚动：避免一次性加载大量数据；
             - 卡片展示作者头像、名字、内容、标签、时间；
             - 底部交互控件：喜欢与收藏（预留后端接口，先做乐观 UI）；
             - 性能考量：懒加载头像、批量分页、节流滚动、避免 N+1 请求。 -->
        <section class="feed" aria-label="拾言列表">
          <!-- 首屏骨架：提升感知性能 -->
          <template v-if="initialLoading">
            <div class="note-card skeleton" v-for="n in 4" :key="'skeleton-' + n" aria-hidden="true">
              <div class="head">
                <div class="avatar sk"></div>
                <div class="meta">
                  <div class="line sk" style="width: 120px"></div>
                  <div class="line sk" style="width: 80px"></div>
                </div>
              </div>
              <div class="content sk" style="height: 56px"></div>
              <div class="tags">
                <span class="tag sk" style="width: 60px"></span>
                <span class="tag sk" style="width: 48px"></span>
              </div>
              <div class="actions">
                <div class="btn sk" style="width: 80px"></div>
                <div class="btn sk" style="width: 80px"></div>
              </div>
            </div>
          </template>

          <!-- 列表内容：真实数据渲染 -->
          <template v-else>
            <div
              v-for="it in items"
              :key="it.id"
              class="note-card"
              :aria-label="'拾言卡片 ' + (it.authorName || '匿名')"
            >
              <div class="head">
                <!-- 头像：懒加载，缺省使用占位图标；避免 N+1 请求，优先使用后端返回字段 -->
                <img
                  v-if="it.authorAvatarUrl"
                  class="avatar"
                  :src="avatarFullUrl(it.authorAvatarUrl)"
                  alt="avatar"
                  loading="lazy"
                  @error="onAvatarError"
                />
                <img v-else class="avatar" :src="defaultAvatar" alt="avatar" loading="lazy" />

                <div class="meta">
                  <div class="name" :title="it.authorName">{{ it.authorName || '匿名' }}</div>
                  <div class="time">{{ formatTime(it.createdAt || it.updatedAt) }}</div>
                </div>
              </div>

              <!-- 内容：支持长文，做多行裁剪与换行优化 -->
              <div class="content" v-text="it.content || ''"></div>

              <!-- 标签：数组或逗号分隔字符串，统一渲染为胶囊 -->
              <div class="tags" v-if="(it.tags || '').length">
                <span class="tag" v-for="tg in normalizeTags(it.tags)" :key="tg">#{{ tg }}</span>
              </div>

              <!-- 底部交互：喜欢/收藏（乐观 UI，后端接口可接入） -->
              <div class="actions">
                <el-button link class="icon-act" :class="{ on: it.liked }" @click="toggleLike(it)" aria-label="喜欢">
                  <img :src="it.liked ? likeIconOn : likeIconOff" alt="like" width="18" height="18" />
                  <span>{{ it.likeCount ?? 0 }}</span>
                </el-button>
                <el-button link class="icon-act" :class="{ on: it.favorited }" @click="toggleFav(it)" aria-label="收藏">
                  <img :src="it.favorited ? favIconOn : favIconOff" alt="fav" width="18" height="18" />
                  <span>{{ it.favoriteCount ?? 0 }}</span>
                </el-button>
              </div>
            </div>

            <!-- 加载更多状态区 -->
            <div class="load-more" v-if="loading">正在加载更多…</div>
            <div class="end-tip" v-if="!loading && done && items.length>0">已到达尽头</div>
            <div class="empty" v-if="!loading && items.length===0">暂无拾言</div>
          </template>

          <!-- 无限滚动锚点：进入视窗触发下一页加载 -->
          <div ref="moreSentinel" class="sentinel" aria-hidden="true"></div>
        </section>
      </div>
    </template>
  </TwoPaneLayout>
</template>

<script setup>
// 组件加载策略：按需异步加载以优化首屏资源体积。
// 保留 query 状态与 onSearch 处理，便于后续扩展在本页接入搜索联动模块。
// 新增：分页加载与无限滚动，实现性能友好的列表渲染。
import { ref, defineAsyncComponent, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { http, avatarFullUrl } from '@/api/http'
import defaultAvatar from '@/assets/default-avatar.svg'
import { getToken } from '@/utils/auth'
const TwoPaneLayout = defineAsyncComponent(() => import('@/components/TwoPaneLayout.vue'))
const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))

const query = ref('')
function onSearch(q){ query.value = q || '' }

// —— 列表与分页状态 ——
const items = ref([])               // 已加载的拾言数据（累积）
const page = ref(1)                 // 当前页（从 1 开始）
const size = ref(20)                // 每页数量（适度控制 DOM 数量）
const loading = ref(false)          // 是否正在加载（用于“加载更多”状态与节流）
const initialLoading = ref(true)    // 首屏骨架占位控制
const done = ref(false)             // 是否已加载完所有数据
const moreSentinel = ref(null)      // 无限滚动锚点引用
let io = null                       // IntersectionObserver 实例

// —— 图标资源（本地常量，避免多处硬编码） ——
const likeIconOn = 'https://api.iconify.design/mdi/heart.svg?color=%23ff4d4f'
const likeIconOff = 'https://api.iconify.design/mdi/heart-outline.svg?color=%23606366'
const favIconOn = 'https://api.iconify.design/mdi/bookmark.svg?color=%23e6a23c'
const favIconOff = 'https://api.iconify.design/mdi/bookmark-outline.svg?color=%23606366'

// —— 工具函数：时间格式化与标签标准化 ——
function formatTime(ts){
  // 容错：后端可能返回 ISO 字符串或时间戳
  try{
    if (!ts) return ''
    const d = typeof ts === 'number' ? new Date(ts) : new Date(String(ts))
    const yyyy = d.getFullYear(); const mm = String(d.getMonth()+1).padStart(2,'0'); const dd = String(d.getDate()).padStart(2,'0')
    const hh = String(d.getHours()).padStart(2,'0'); const mi = String(d.getMinutes()).padStart(2,'0')
    return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
  }catch{ return String(ts) }
}
function normalizeTags(tags){
  // tags 可能是数组或字符串（逗号分隔）；统一返回字符串数组，去除空项与两端空格
  if (Array.isArray(tags)) return tags.map(t => String(t).trim()).filter(Boolean)
  if (typeof tags === 'string') return tags.split(',').map(t => t.trim()).filter(Boolean)
  return []
}

// —— 数据映射：统一字段，容错后端命名差异 ——
function mapNoteItem(it){
  return {
    id: it.id,
    userId: it.userId ?? it.user_id ?? '',
    authorName: it.authorName ?? it.author_name ?? (it.user?.nickname ?? it.user?.username) ?? '匿名',
    // 头像字段适配：
    // - 兼容后端多种命名：authorAvatarUrl / author_avatar_url / avatarUrl / avatar_url；
    // - 同时兼容嵌套结构：author{ avatarUrl | avatar_url }、user{ avatarUrl | avatar_url }；
    // - 若均不存在则使用空串，模板层将回退为默认头像（见 onAvatarError 与 v-else）。
    authorAvatarUrl: (
      it.authorAvatarUrl ??
      it.author_avatar_url ??
      it.userAvatarUrl ??
      it.user_avatar_url ??
      it.avatarUrl ??
      it.avatar_url ??
      (it.author ? (it.author.avatarUrl ?? it.author.avatar_url) : '') ??
      (it.user ? (it.user.avatarUrl ?? it.user.avatar_url) : '')
    ) || '',
    content: it.content ?? '',
    tags: Array.isArray(it.tags) ? it.tags : (it.tags ?? ''),
    createdAt: it.createdAt ?? it.created_at ?? '',
    updatedAt: it.updatedAt ?? it.updated_at ?? '',
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
    // 公开性：前端过滤使用（fetchPage 中的 list.filter），兼容 isPublic / is_public；默认视为公开。
    isPublic: Boolean(it.isPublic ?? it.is_public ?? true),
    // 前端状态：交互后的乐观 UI 控制位
    liked: Boolean(it.liked ?? false),
    favorited: Boolean(it.favorited ?? false),
  }
}

// —— 拉取一页数据（性能：节流与错误容忍） ——
async function fetchPage(){
  if (loading.value || done.value) return
  loading.value = true
  try{
    // 仅展示公开拾言：尽量通过参数告知后端；若后端无此参数也会在前端过滤
    const { data } = await http.get('/shiyan', { params: { page: page.value, size: size.value, publicOnly: true } })
    const raw = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    let list = Array.isArray(raw) ? raw.map(mapNoteItem) : []
    // 客户端过滤：确保仅保留公开项（isPublic/is_public 为真）
    list = list.filter(it => Boolean(it.isPublic ?? true))
    if (list.length === 0){ done.value = true }
    items.value.push(...list)
    // 下一页
    if (list.length > 0) page.value += 1
  }catch(e){
    // 后端未启动或网络错误时：显示提示但不阻塞页面
    const msg = e?.response?.data?.message || '加载失败，请稍后重试'
    ElMessage.error(msg)
  }finally{
    initialLoading.value = false
    loading.value = false
  }
}

// —— 无限滚动：锚点进入视窗触发加载 ——
function setupInfiniteScroll(){
  if (!moreSentinel.value) return
  // IntersectionObserver：低成本且稳定；阈值设为 1% 提前触发
  io = new IntersectionObserver((entries) => {
    for (const e of entries){
      if (e.isIntersecting) fetchPage()
    }
  }, { root: null, rootMargin: '120px', threshold: 0.01 })
  io.observe(moreSentinel.value)
}
function teardownInfiniteScroll(){
  try{ if (io){ io.disconnect(); io = null } }catch{}
}

// 头像加载失败兜底：将破图替换为默认头像，避免出现坏链路
function onAvatarError(e){
  try{
    const img = e?.target
    if (img && img.src !== defaultAvatar){ img.src = defaultAvatar }
  }catch{}
}

// —— 交互：喜欢/收藏（乐观更新，占位实现） ——
async function toggleLike(it){
  // 登录校验：点赞需要登录
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  if (it.likeLoading) return
  it.likeLoading = true
  try{
    // 与 Favorites.vue 保持一致：/shiyan/{id}/like|unlike
    const url = it.liked ? `/shiyan/${it.id}/unlike` : `/shiyan/${it.id}/like`
    const { data } = await http.post(url)
    it.likeCount = Number(data?.count ?? data?.like_count ?? (it.likeCount || 0))
    it.liked = Boolean((data?.likedByMe ?? data?.liked_by_me ?? !it.liked))
  }catch(e){
    ElMessage.error('操作失败')
  }finally{
    it.likeLoading = false
  }
}
async function toggleFav(it){
  // 登录校验：收藏需要登录
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  if (it.favoriteLoading) return
  it.favoriteLoading = true
  try{
    // 与 Favorites.vue 保持一致：/shiyan/{id}/favorite|unfavorite
    const url = it.favorited ? `/shiyan/${it.id}/unfavorite` : `/shiyan/${it.id}/favorite`
    const { data } = await http.post(url)
    it.favoriteCount = Number(data?.count ?? data?.favorite_count ?? (it.favoriteCount || 0))
    it.favorited = Boolean((data?.favoritedByMe ?? data?.favorited_by_me ?? !it.favorited))
  }catch(e){
    ElMessage.error('操作失败')
  }finally{
    it.favoriteLoading = false
  }
}

// 生命周期：首屏拉取 + 启用无限滚动；卸载时清理观察器
onMounted(() => { fetchPage(); setupInfiniteScroll() })
onUnmounted(() => { teardownInfiniteScroll() })
</script>

<style scoped>
/* 页面容器：占满右列宽度，居中内容区并设置安全边距 */
.town-container {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: 16px;
}

/* 顶部品牌卡片：玻璃卡片风格，与顶栏整体风格一致 */
.hero-card {
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: saturate(180%) blur(12px);
  -webkit-backdrop-filter: saturate(180%) blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 14px;
  padding: 20px 24px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}

.hero-card .brand { display:flex; align-items:center; gap:10px; }
.hero-card .brand h1 { margin:0; font-size:22px; color:#303133; }
.subtitle { margin: 8px 0 0; color:#606266; font-size:14px; }

/* 列表容器：与 hero-card 间保持舒适间距 */
.feed { margin-top: 16px; display:flex; flex-direction:column; gap: 12px; }
.sentinel { height: 1px; }

/* 拾言卡片：玻璃风格与阴影一致，内容分区清晰 */
.note-card {
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: saturate(180%) blur(12px);
  -webkit-backdrop-filter: saturate(180%) blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 12px;
  padding: 14px;
  box-shadow: 0 6px 18px rgba(0,0,0,0.08);
}
.note-card .head { display:flex; align-items:center; gap: 10px; }
.note-card .avatar { width:36px; height:36px; border-radius:50%; object-fit:cover; background:#fff; border:2px solid #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.12); }
.note-card .meta { display:flex; flex-direction:column; }
.note-card .name { font-weight:600; color:#303133; }
.note-card .time { color:#909399; font-size:12px; }
.note-card .content { margin-top: 10px; color:#303133; font-size:14px; line-height:1.7; white-space:pre-wrap; word-break:break-word; }
.note-card .tags { margin-top: 10px; display:flex; flex-wrap:wrap; gap:6px; }
.note-card .tag { font-size:12px; color:#606266; background: rgba(0,0,0,0.04); border:1px solid rgba(0,0,0,0.06); padding:4px 8px; border-radius:999px; }
.note-card .actions { margin-top: 8px; display:flex; gap: 12px; }
.icon-act { display:inline-flex; align-items:center; gap:6px; color:#606266; }
.icon-act.on { color: var(--el-color-primary); }

/* 加载状态与空白提示 */
.load-more { text-align:center; color:#909399; font-size:13px; padding:6px; }
.end-tip { text-align:center; color:#909399; font-size:12px; padding:6px; }
.empty { text-align:center; color:#909399; font-size:13px; padding:10px; }

/* 骨架屏样式（简化版） */
.skeleton { position: relative; overflow: hidden; }
.sk { background: linear-gradient(90deg, rgba(0,0,0,0.06), rgba(0,0,0,0.04), rgba(0,0,0,0.06)); border-radius: 6px; }
.skeleton::after {
  content: '';
  position: absolute;
  left: -40%; top: 0; bottom: 0;
  width: 40%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.5), transparent);
  animation: shine 1.6s infinite;
}
@keyframes shine { 0% { left: -40%; } 100% { left: 120%; } }

/* 响应式：在小屏设备上减少外边距并拉伸内容区 */
@media (max-width: 640px){
  .town-container { max-width: none; padding: 12px; }
  .hero-card { border-radius: 12px; padding: 16px 18px; }
  .note-card { padding: 12px; }
  .note-card .avatar { width:32px; height:32px; }
}
</style>