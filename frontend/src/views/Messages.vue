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
                <!-- 左栏菜单项：显示标签 + 未读数量徽章（为保持简洁，仅显示正整数） -->
                <button class="menu-item" :class="{ active: activeId === item.id }" @click="setActive(item.id)">
                  <span class="label">{{ item.label }}</span>
                  <span v-if="badgeCount(item.id) > 0" class="menu-badge" aria-label="未读数量">{{ badgeCount(item.id) }}</span>
                </button>
              </li>
            </ul>
          </aside>

          <!-- 右栏：内容区，左侧添加明显的分隔线（与截图风格一致） -->
          <section class="card-right">
            <!-- 标题行：左侧为当前栏目名，右侧为批量操作（位于红框区域） -->
            <div class="title-row">
              <div class="section-title">{{ currentLabel }}</div>
              <div class="bulk-actions">
                <!--
                  一键已读：
                  - 对当前列表中未读消息逐条调用后端已读接口；
                  - 期间禁用按钮，完成后刷新未读徽章；
                  - 设计为“就地批量”而非一次性全库，避免后端缺少批量端点的限制。
                -->
                <el-button size="small" type="success" plain
                  :disabled="isBulkWorking || messages.length === 0"
                  @click="bulkMarkRead">
                  一键已读
                </el-button>
                <!--
                  一键删除：
                  - 对当前列表逐条调用删除接口；
                  - 出错不阻塞其他项，最终提示成功/失败数量；
                -->
                <el-button size="small" type="danger" plain
                  :disabled="isBulkWorking || messages.length === 0"
                  @click="bulkDelete">
                  一键删除
                </el-button>
              </div>
            </div>
            <!-- 空状态：当列表为空且非加载中时显示（图片留空使用 Element Plus 默认样式） -->
            <div v-if="messages.length === 0 && !isLoading" class="empty-wrap">
              <el-empty description="暂无消息" :image="emptyImage" />
            </div>
            <!-- 消息列表：当存在数据时渲染卡片列表 -->
            <div v-else class="msg-list">
              <transition-group name="list" tag="div">
                <div v-for="m in messages" :key="m.id" class="msg-card" :class="{ unread: !m.isRead }">
                  <!-- 头部：头像 + 昵称 + 行为文案 + 时间戳 -->
                  <div class="msg-header">
                    <img :src="avatarSrcFor(m.actor?.avatarUrl)" @error="onAvatarError" alt="avatar" class="avatar-sm" width="36" height="36" />
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
// 状态管理：本页不再跳转拾言详情，移除路由依赖
// 根据需求，直接在摘要中呈现拾言内容，无需路由跳转
import { ref, computed, onMounted, onUnmounted } from 'vue'
// 接口与头像地址拼接（与其它页面一致）
import { http, avatarFullUrl } from '@/api/http'
import { ElMessage } from 'element-plus'
// 默认头像占位图：用于头像为 null 或加载失败时的兜底显示
import defaultAvatar from '@/assets/default-avatar.svg'

// 已移除 useRouter；仅保留本页内的已读/删除操作

// —— 内容区左栏导航（父分组：系统通知 → 子项：我的消息/收到的赞/系统消息）——
// 说明：
// - 外侧不需要导航栏，使用内容区内的分组式导航；
// - “系统通知”为父分组标题，不可选；其下三个子项用于切换右侧列表数据；
// - 默认选择“系统消息”（id=system）。
// 左栏菜单：新增“收藏消息”，位于“收到的赞”下方
const navChildren = [
  { id: 'all', label: '我的消息' },
  { id: 'like', label: '收到的赞' },
  { id: 'favorite', label: '收藏消息' },
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

// —— 未读数量（用于左栏徽章与总未读计数）——
// 说明：
// - 为了减少跨组件通信依赖，这里独立拉取后端未读计数接口；
// - 结构与后端约定：{ counts: { like, favorite, system }, total, hasNew }；
// - 消息页当前仅使用 like 与 system 的未读徽章；favorite 徽章预留。
const unreadCounts = ref({ like: 0, favorite: 0, system: 0, total: 0 })
// 计算菜单项的徽章值（只显示大于 0 的数字）
// 未读徽章：为“收藏消息”增加计数显示
function badgeCount(id){
  if (id === 'all') return Math.max(0, Number(unreadCounts.value.total || 0))
  if (id === 'like') return Math.max(0, Number(unreadCounts.value.like || 0))
  if (id === 'favorite') return Math.max(0, Number(unreadCounts.value.favorite || 0))
  if (id === 'system') return Math.max(0, Number(unreadCounts.value.system || 0))
  return 0
}

// —— 行为文案渲染（按类型给出自然语句）——
function renderAction(m){
  const t = m?.type || activeId.value
  if (t === 'like') return '赞了你的拾言'
  if (t === 'favorite') return '收藏了你的拾言'
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
// 数据映射：兼容后端拍平与嵌套两种返回结构
// 拍平字段示例：actorUsername/actorNickname/actorAvatarUrl、noteId、contentSnippet
// 嵌套字段示例：actor{username,nickname,avatarUrl}、note{id,contentSnippet}
function mapItems(items){
  return (items || []).map(m => {
    const actorUsername = m.actor?.username ?? m.actorUsername ?? m.username
    const actorNickname = m.actor?.nickname ?? m.actorNickname ?? m.nickname
    const actorAvatarUrl = m.actor?.avatarUrl ?? m.actorAvatarUrl ?? m.avatarUrl
    const noteId = m.note?.id ?? m.noteId
    const noteSnippet = m.note?.contentSnippet ?? m.contentSnippet ?? m.note?.content ?? undefined
    return {
      id: m.id,
      type: m.type,
      isRead: !!(m.isRead ?? m.read),
      actor: { username: actorUsername, nickname: actorNickname, avatarUrl: actorAvatarUrl },
      note: { id: noteId, contentSnippet: noteSnippet },
      message: m.message,
      createdAt: m.createdAt ?? m.created_at,
    }
  })
}

// —— 头像地址兜底逻辑 ——
// 说明：
// - avatarFullUrl(null) 会返回空字符串，直接用于 <img> 会显示破图；
// - 这里提供一个安全函数，当头像缺失或无效时返回默认头像资源；
// - 并通过 onerror 在网络加载异常（如 404）时将图片替换为默认头像。
function avatarSrcFor(url){
  const full = avatarFullUrl(url)
  return full || defaultAvatar
}
function onAvatarError(e){
  try{
    const img = e?.target
    if (img && img.src !== defaultAvatar) img.src = defaultAvatar
  }catch{}
}

// —— 服务端分页加载（根据 activeId 传参）——
function typeParam(){
  // 后端可能只支持部分类型；未匹配时不传 type，以便获取“全部/默认”。
  const t = activeId.value
  if (['like','favorite','reply','at','system'].includes(t)) return t
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

// —— 加载未读计数（用于左栏徽章与顶端提示）——
async function loadUnread(){
  try{
    // 路径修正：后端控制器为 /api/messages/counts（不是 unread-counts）
    const { data } = await http.get('/messages/counts', { suppress401Redirect: true })
    const counts = data?.counts || {}
    unreadCounts.value = {
      like: Number(counts.like || 0),
      favorite: Number(counts.favorite || 0),
      system: Number(counts.system || 0),
      total: Number(data?.total || (counts.like || 0) + (counts.favorite || 0) + (counts.system || 0))
    }
  }catch(e){ /* 未登录或接口异常不影响页面基础功能 */ }
}

// —— 触底自动加载：移动端进入视口时自动拉取下一页 ——
function setupInfiniteScroll(){
  try{
    if (!('IntersectionObserver' in window)) return
    io = new IntersectionObserver((entries) => { for (const e of entries){ if (e.isIntersecting) loadMore() } })
    if (loadMoreSentinel.value) io.observe(loadMoreSentinel.value)
  }catch{}
}
function teardownInfiniteScroll(){ try{ if (io) io.disconnect(); io = null }catch{} }

// —— 操作：单条已读、删除 ——
// 说明：移除“查看拾言”，摘要中直接展示内容片段
// 单条已读：更新当前消息状态，并刷新左侧未读徽章
// 设计说明：
// 1) 即刻将本条消息的 isRead 置为 true，避免用户二次点击；
// 2) 本地对未读计数做“保守减一”，让徽章立即下降，提升反馈速度；
//    然后后台再调用 loadUnread() 与服务端对齐，防止并发情况下的误差；
// 3) 与批量已读保持一致的刷新策略，从而修复“单条已读不更新徽章”的问题。
async function markRead(m){
  try{
    await http.post(`/messages/${m.id}/read`)
    m.isRead = true
    // 本地未读计数立即更新（按消息类型归类）
    try {
      const t = m?.type || activeId.value
      if (t === 'like' && unreadCounts.value.like > 0) unreadCounts.value.like -= 1
      else if (t === 'favorite' && unreadCounts.value.favorite > 0) unreadCounts.value.favorite -= 1
      else if (t === 'system' && unreadCounts.value.system > 0) unreadCounts.value.system -= 1
      // 重新汇总总未读数（仅使用当前三类的和）
      unreadCounts.value.total = Math.max(0,
        Number(unreadCounts.value.like || 0) +
        Number(unreadCounts.value.favorite || 0) +
        Number(unreadCounts.value.system || 0)
      )
    } catch {}
    // 后台与服务端对齐，确保最终计数准确
    loadUnread()
    // 通知顶栏刷新 NEW 徽章与计数（浏览器事件总线）
    notifyTopBar('read', [m.id])
  }
  catch{ ElMessage.error('标记已读失败') }
}
async function deleteMsg(m){
  try{ await http.delete(`/messages/${m.id}`); messages.value = messages.value.filter(x => x.id !== m.id) }
  catch{ ElMessage.error('删除失败') }
}

// —— 批量操作：一键已读 / 一键删除 ——
// 说明：
// - 后端当前提供的是单条接口，这里采用“逐条顺序执行”的安全方式；
// - 顺序执行便于控制失败继续、避免热点同时打爆后端；
// - 完成后刷新未读计数；
const isBulkWorking = ref(false)
async function bulkMarkRead(){
  if (isBulkWorking.value) return
  isBulkWorking.value = true
  let ok = 0, fail = 0
  try{
    for (const m of messages.value){
      if (m.isRead) continue
      try{ await http.post(`/messages/${m.id}/read`); m.isRead = true; ok++ }
      catch{ fail++ }
    }
    if (fail === 0) ElMessage.success(`已标记 ${ok} 条为已读`)
    else ElMessage.warning(`已读成功 ${ok} 条，失败 ${fail} 条`)
  } finally {
    isBulkWorking.value = false
    // 批量完成后刷新未读徽章
    loadUnread()
    // 通知顶栏刷新 NEW 徽章与计数
    try { const ids = messages.value.filter(x => x.isRead).map(x => x.id); notifyTopBar('bulk-read', ids) } catch {}
  }
}
async function bulkDelete(){
  if (isBulkWorking.value) return
  isBulkWorking.value = true
  let ok = 0, fail = 0
  try{
    // 复制当前列表，避免边删边遍历导致索引错乱
    const list = [...messages.value]
    for (const m of list){
      try{ await http.delete(`/messages/${m.id}`); ok++ } catch { fail++ }
    }
    // 本地列表清空（仅清除当前已加载页的内容）
    messages.value = []
    total.value = 0
    page.value = 1
    if (fail === 0) ElMessage.success(`已删除 ${ok} 条消息`)
    else ElMessage.warning(`删除成功 ${ok} 条，失败 ${fail} 条`)
  } finally {
    isBulkWorking.value = false
    // 刷新未读徽章
    loadUnread()
    // 通知顶栏刷新 NEW 徽章与计数
    try { const ids = [] /* 删除不需要传具体 id */; notifyTopBar('bulk-delete', ids) } catch {}
  }
}

// —— 首次挂载：按默认栏目加载第 1 页并设置触底加载 ——
// 首次挂载：加载列表、设置触底加载，并拉取未读计数用于左栏徽章
const emptyImage = '' // 留空使用 Element Plus 的默认空状态图片
let unreadPoller = null
onMounted(() => {
  fetchPage(1)
  setupInfiniteScroll()
  loadUnread()
  // 监听顶栏派发的未读计数事件：用于实时联动左侧徽章
  // 说明：当顶栏通过轮询或可见性刷新获取到新计数时，消息页无需额外请求即可更新徽章
  window.addEventListener('messages-counts', onTopBarCounts)
  // 轻量轮询兜底：每 10 秒刷新一次，防止事件错过或顶栏未挂载
  unreadPoller = setInterval(() => { loadUnread() }, 10000)
  // 标签重新可见时刷新一次（在当前页查看过程中得到最新计数）
  document.addEventListener('visibilitychange', onVisibilityRefresh)
})

onUnmounted(() => {
  try { window.removeEventListener('messages-counts', onTopBarCounts) } catch {}
  try { document.removeEventListener('visibilitychange', onVisibilityRefresh) } catch {}
  try { if (unreadPoller) { clearInterval(unreadPoller); unreadPoller = null } } catch {}
})

function onTopBarCounts(e){
  try{
    const d = e?.detail || {}
    const c = d?.counts || {}
    unreadCounts.value = {
      like: Number(c.like || 0),
      favorite: Number(c.favorite || 0),
      system: Number(c.system || 0),
      total: Number(d?.total || (c.like || 0) + (c.favorite || 0) + (c.system || 0))
    }
  }catch{}
}
function onVisibilityRefresh(){ if (document.visibilityState === 'visible') loadUnread() }

// —— 与顶栏通信：消息状态变更后触发顶栏未读刷新 ——
function notifyTopBar(action, ids){
  try {
    window.dispatchEvent(new CustomEvent('messages-updated', { detail: { source: 'messages', action, ids } }))
  } catch {}
}
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
/* 菜单徽章：靠右、圆角小红点样式，显示未读数量 */
.menu-item { position: relative; }
.menu-item .label { display:inline-block; }
/* 左侧菜单未读徽章：缩小尺寸，并略微靠近菜单栏右缘 */
.menu-item .menu-badge {
  /* 未读徽章：与标签同行显示，不靠右对齐 */
  position: absolute;
  right: 36px; /* 原 8px → 4px，更靠近菜单栏 */
  top: 60%; /**  badges 与 label 垂直居中对齐，与标签同行显示 */
  transform: translateY(-50%); /* 垂直居中对齐，与标签同行显示 */
  background: #ff4d4f;
  color: #fff;
  min-width: 16px; /* 原 20px → 16px，尺寸更小 */
  height: 16px;     /* 原 20px → 16px */
  padding: 0 4px;   /* 原 6px → 4px，减少横向占用 */
  border-radius: 8px; /* 原 10px → 8px，跟随尺寸缩小 */
  font-size: 10px;  /* 原 12px → 10px */
  line-height: 16px;
  text-align: center; /* 居中对齐，与标签同行显示 */
  box-shadow: 0 0 0 2px #fff;
}

/* 右栏内容区：左侧竖线分隔，让布局更贴近截图 */
.card-right { padding: 12px 16px 16px; border-left: 2px solid rgba(0, 0, 0, 0.35); height: 100%; overflow: auto; }
/* 内容区标题：大字号、加粗、与菜单对齐 */
.section-title { font-size: 18px; color: #303133; font-weight: 700; margin-bottom: 8px; }

/* 标题行：左右对齐，将批量操作放置在右上角（红框区域） */
.title-row { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.bulk-actions { display:flex; align-items:center; gap:8px; }

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
/* 空状态容器：与消息列表保持一致的左右内边距 */
.empty-wrap { padding: 12px 0; }
</style>