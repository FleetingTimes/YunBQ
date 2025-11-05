<template>
  <!-- 布局说明：
       - 顶栏保持全宽吸顶（AppTopBar）；
       - 右侧正文内部新增“内容区”，宽度占正文区的 85%，并居中；
       - 内容区再分为左右两栏：左栏为“消息中心”导航，右栏为消息列表；
       - 移除所有过滤与批量工具，保留轻量操作。 -->
  <TwoPaneLayout>
    <!-- 顶栏：复用全站顶栏（品牌一致、可搜索），不额外添加过滤控件。 -->
    <template #topFull>
      <AppTopBar />
    </template>

    <!-- 右侧正文：内部居中内容区（宽度 85%）再分左右两栏 -->
    <template #rightMain>
      <!-- 页面主内容：先渲染顶端“胶囊标题”，再渲染居中卡片（左右两栏） -->
      <div class="center-area">
        <!-- 顶端胶囊标题：与截图一致的圆角条形，居中显示“消息中心” -->
        <div class="pill-title">消息中心</div>

        <!-- 居中卡片容器：浅色背景 + 圆角 + 阴影；内部左右两栏 -->
        <div class="content-card">
          <!-- 左栏：大字号的三级菜单（我的消息 / 收到的赞 / 系统消息） -->
          <aside class="card-left">
            <!-- 说明：根据用户最新需求，取消外侧导航与分组标题，直接放置三项。 -->
            <ul class="menu-list">
              <li v-for="item in navChildren" :key="item.id">
                <button class="menu-item" :class="{ active: activeId === item.id }" @click="setActive(item.id)">
                  {{ item.label }}
                </button>
              </li>
            </ul>
          </aside>

          <!-- 右栏：内容区，左侧添加明显的分隔线（与截图风格一致） -->
          <section class="card-right">
            <div class="section-title">{{ currentLabel }}</div>
            <div class="msg-list">
              <transition-group name="list" tag="div">
                <div v-for="m in messages" :key="m.id" class="msg-card" :class="{ unread: !m.isRead }">
                  <!-- 头部：头像 + 昵称 + 行为文案 + 时间戳 -->
                  <div class="msg-header">
                    <img :src="avatarFullUrl(m.actor?.avatarUrl)" alt="avatar" class="avatar-sm" width="36" height="36" />
                    <div class="title">
                      <span class="nickname">{{ m.actor?.nickname || m.actor?.username || '系统' }}</span>
                      <span class="action">{{ renderAction(m) }}</span>
                    </div>
                    <div class="time">{{ formatTime(m.createdAt) }}</div>
                  </div>
                  <!-- 内容：摘要 + 轻量操作 -->
                  <div class="msg-content">
                    <div class="note-snippet">“{{ m.note?.contentSnippet || m.message || '（无摘要）' }}”</div>
                    <div class="actions">
                      <el-button size="small" @click="openNote(m)">查看拾言</el-button>
                      <el-button size="small" type="success" plain @click="markRead(m)" :disabled="m.isRead">已读</el-button>
                      <el-button size="small" type="danger" plain @click="deleteMsg(m)">删除</el-button>
                    </div>
                  </div>
                </div>
              </transition-group>
            </div>
            <div class="load-more-container">
              <button class="load-more-btn" :disabled="isLoading || !hasNext" @click="loadMore">
                {{ isLoading ? '加载中…' : (hasNext ? '加载更多' : '已无更多') }}
              </button>
              <div v-show="hasNext && !isLoading" ref="loadMoreSentinel" class="load-more-sentinel" aria-hidden="true"></div>
            </div>
          </section>
        </div>
      </div>
    </template>
  </TwoPaneLayout>
</template>

<script setup>
// 布局与通用组件：顶栏、两栏布局、侧边导航
import TwoPaneLayout from '@/components/TwoPaneLayout.vue'
import AppTopBar from '@/components/AppTopBar.vue'
// 状态与路由
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
// 接口与头像地址拼接（与其它页面一致）
import { http, avatarFullUrl } from '@/api/http'
import { ElMessage } from 'element-plus'

const router = useRouter()

// —— 内容区左栏导航（父分组：系统通知 → 子项：我的消息/收到的赞/系统消息）——
// 说明：
// - 外侧不需要导航栏，使用内容区内的分组式导航；
// - “系统通知”为父分组标题，不可选；其下三个子项用于切换右侧列表数据；
// - 默认选择“系统消息”（id=system）。
const navChildren = [
  { id: 'all', label: '我的消息' },
  { id: 'like', label: '收到的赞' },
  { id: 'system', label: '系统消息' }
]
const activeId = ref('system')
function setActive(id){ activeId.value = id; reload() }
const currentLabel = computed(() => navChildren.find(c => c.id === activeId.value)?.label || '消息列表')

// —— 列表与分页状态 ——
const messages = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const isLoading = ref(false)
const hasNext = computed(() => messages.value.length < total.value)
const loadMoreSentinel = ref(null)
let io = null

// —— 行为文案渲染（按类型给出自然语句）——
function renderAction(m){
  const t = m?.type || activeId.value
  if (t === 'like') return '赞了你的拾言'
  if (t === 'reply') return '回复了你'
  if (t === 'at') return '提到了你'
  if (t === 'system') return '系统通知'
  return '与您相关的动态'
}

// —— 时间格式化（轻量实现，确保本页独立可用）——
function pad(n){ return String(n).padStart(2, '0') }
function formatTime(ts){
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return '时间未知'
  const Y = d.getFullYear(); const M = pad(d.getMonth() + 1); const D = pad(d.getDate())
  const h = pad(d.getHours()); const m = pad(d.getMinutes())
  return `${Y}-${M}-${D} ${h}:${m}`
}

// —— 数据映射：归一化接口返回 ——
function mapItems(items){
  return (items || []).map(m => ({
    id: m.id,
    type: m.type,
    isRead: !!(m.isRead ?? m.read),
    actor: { username: m.actor?.username, nickname: m.actor?.nickname, avatarUrl: m.actor?.avatarUrl },
    note: { id: m.note?.id, contentSnippet: m.note?.contentSnippet ?? m.note?.content },
    message: m.message,
    createdAt: m.createdAt ?? m.created_at,
  }))
}

// —— 服务端分页加载（根据 activeId 传参）——
function typeParam(){
  // 后端可能只支持部分类型；未匹配时不传 type，以便获取“全部/默认”。
  const t = activeId.value
  if (['like','reply','at','system'].includes(t)) return t
  return undefined
}
async function fetchPage(targetPage){
  if (isLoading.value) return
  isLoading.value = true
  try{
    const params = { page: targetPage, size: size.value }
    const t = typeParam(); if (t) params.type = t
    const { data } = await http.get('/messages', { params })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    const totalFromApi = data?.total ?? data?.count
    total.value = Number.isFinite(totalFromApi) ? Number(totalFromApi) : (messages.value.length + (items?.length || 0))
    const mapped = mapItems(items)
    messages.value = targetPage <= 1 ? mapped : messages.value.concat(mapped)
    page.value = targetPage
  }catch(e){
    const status = e?.response?.status
    if (status === 401) { /* 交由全局拦截器处理（重定向登录） */ return }
    ElMessage.error('加载消息失败')
  }finally{ isLoading.value = false }
}
function reload(){ total.value = 0; page.value = 1; messages.value = []; fetchPage(1) }
function loadMore(){ if (hasNext.value && !isLoading.value) fetchPage(page.value + 1) }

// —— 触底自动加载：移动端进入视口时自动拉取下一页 ——
function setupInfiniteScroll(){
  try{
    if (!('IntersectionObserver' in window)) return
    io = new IntersectionObserver((entries) => { for (const e of entries){ if (e.isIntersecting) loadMore() } })
    if (loadMoreSentinel.value) io.observe(loadMoreSentinel.value)
  }catch{}
}
function teardownInfiniteScroll(){ try{ if (io) io.disconnect(); io = null }catch{} }

// —— 操作：查看拾言、单条已读、删除 ——
function openNote(m){ if (m?.note?.id) router.push('/shiyan') }
async function markRead(m){
  try{ await http.post(`/messages/${m.id}/read`); m.isRead = true }
  catch{ ElMessage.error('标记已读失败') }
}
async function deleteMsg(m){
  try{ await http.delete(`/messages/${m.id}`); messages.value = messages.value.filter(x => x.id !== m.id) }
  catch{ ElMessage.error('删除失败') }
}

// —— 首次挂载：按默认栏目加载第 1 页并设置触底加载 ——
onMounted(() => { fetchPage(1); setupInfiniteScroll() })
</script>

<style scoped>
/* 页面整体内容区：
   - 宽度占右侧正文 85%，在 TwoPaneLayout 的 rightMain 内水平居中；
   - 采用“上方胶囊标题 + 下方内容卡片”的纵向布局，匹配截图视觉。 */
.center-area { width: 75%; margin: 0 auto; display: flex; flex-direction: column; gap: 12px; }

/* 顶端胶囊标题：居中、浅灰背景、圆角、轻阴影（与截图一致） */
.pill-title { align-self: left; padding: 10px 24px; background: #e9edf3; color: #4b5563; font-weight: 700; font-size: 22px; border-radius: 999px; box-shadow: 0 2px 6px rgba(0,0,0,0.05); }

/* 居中卡片容器：浅色背景、圆角与阴影；内部左右两栏 */
/* 居中卡片容器：
   - 通过 CSS 变量控制左右栏宽度：
     --messages-left-width  左栏宽度（默认 280px）
     --messages-right-width 右栏宽度（默认 1fr，自动占满剩余）
   - 示例：在父级设置 `--messages-left-width: 300px; --messages-right-width: 1fr;` 即可调整。 */
.content-card { align-self: center; width: 90%; background: rgba(245, 247, 252, 0.9); border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.08); padding: 16px; display: grid; grid-template-columns: var(--messages-left-width, 180px) var(--messages-right-width, 1fr); column-gap: 0; }
/* 固定高度：
   - 通过 CSS 变量 --messages-card-height 可灵活调整卡片高度；
   - 默认高度为 560px；
   - 当内部内容超出高度时，使用右侧内容区滚动，避免整页滚动影响背景视觉。 */
.content-card { height: var(--messages-card-height, 700px); overflow: hidden; }

/* 左栏菜单（大字号、简洁、当前项高亮） */
.card-left { padding: 12px; /* 左侧菜单较短，保留可滚动以防未来菜单增多 */ overflow: auto; }
/* 菜单列表：无编号点、等间距垂直排列 */
.menu-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 16px; }
/* 菜单项：左对齐、等宽、悬停高亮 */
.menu-item { width: 100%; text-align: left; background: transparent; border: none; outline: none; cursor: pointer; font-size: 20px; font-weight: 600; color: #2c2c2c; padding: 6px 8px; border-radius: 8px; transition: background-color .15s ease, color .15s ease; }
.menu-item:hover { background: #eef3ff; }
.menu-item.active { background: #e6f0ff; color: #3a7bd5; }

/* 右栏内容区：左侧竖线分隔，让布局更贴近截图 */
.card-right { padding: 12px 16px 16px; border-left: 2px solid rgba(0, 0, 0, 0.35); height: 100%; overflow: auto; }
/* 内容区标题：大字号、加粗、与菜单对齐 */
.section-title { font-size: 18px; color: #303133; font-weight: 700; margin-bottom: 8px; }

.msg-list { display:flex; flex-direction:column; gap:10px; }
.msg-card { background:#fff; border-radius:12px; padding:12px; box-shadow:0 4px 12px rgba(0,0,0,0.06); }
.msg-card.unread { box-shadow:0 0 0 2px rgba(64,158,255,0.18), 0 4px 12px rgba(0,0,0,0.06); }
.msg-header { display:flex; align-items:center; gap:10px; margin-bottom:8px; }
.avatar-sm { border-radius:50%; object-fit:cover; overflow:hidden; }
.title { display:flex; align-items:center; gap:6px; font-size:14px; color:#303133; }
.title .nickname { font-weight:600; }
.title .action { color:#606266; }
.time { margin-left:auto; color:#909399; font-size:12px; }
.msg-content { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.note-snippet { color:#606266; font-style:italic; opacity:0.9; }
.actions { display:flex; align-items:center; gap:8px; }

/* 列表过渡动画（重排/进出） */
.list-enter-active, .list-leave-active { transition: all .20s ease; will-change: transform, opacity; }
.list-enter-from, .list-leave-to { opacity: 0; transform: translateY(6px) scale(0.99); }
.list-move { transition: transform .20s ease; }

/* 加载更多样式（统一视觉） */
.load-more-container { display:flex; flex-direction:column; align-items:center; gap:8px; margin: 12px 0 24px; }
.load-more-btn { padding:8px 16px; border-radius:6px; border:1px solid #dcdfe6; background:#f5f7ff; color:#409eff; cursor:pointer; }
.load-more-btn:disabled { opacity:0.6; cursor:not-allowed; }
.load-more-sentinel { width:100%; max-width:640px; height:1px; }
</style>