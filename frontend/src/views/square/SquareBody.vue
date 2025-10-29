<template>
  <div class="container">
    <header class="square-header">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="28" height="28" />
        <h1>云便签 · 广场</h1>
      </div>
    </header>

    <section class="layout">
      <aside class="side-nav">
        <div class="nav-title">导航</div>
        <ul class="nav-list">
          <li v-for="s in sections" :key="s.id" :class="{ break: s.id === 'site' }">
            <a href="javascript:;" :class="{ active: activeId === s.id }" @click="scrollTo(s.id)">{{ s.label }}</a>
          </li>
        </ul>
      </aside>
      <div class="content-scroll" ref="contentRef">
        <div class="content-head"><span>热门</span><span class="slash">/</span><span>最近</span><span class="slash">/</span><span>网站便签</span><span class="slash">/</span><span>Git便签</span></div>
        <div class="grid-two">
        <div class="card" id="hot">
          <div class="card-title">热门便签</div>
          <div class="card-desc">基于收藏、点赞与时效综合排序</div>
          <ul class="note-list">
            <li class="note-item" v-for="it in hotPageItems" :key="it.id" @click="goNote(it)" role="button">
              <div class="title">{{ tagTitle(it) }}</div>
              <div class="content">{{ snippet(it.content || it.title) }}</div>
              <div class="meta">
                <div class="left">
                  <span class="author">{{ displayAuthor(it) }}</span>
                </div>
                <div class="right">
                  <span class="time">{{ formatYMD(it.updatedAt || it.updated_at) }}</span>
                </div>
              </div>
            </li>
            <li v-if="!hotNotes.length" class="empty">暂无热门便签</li>
          </ul>
          <el-pagination
            v-if="hotNotes.length"
            background
            layout="prev, pager, next"
            :total="hotNotes.length"
            :page-size="pageSize"
            v-model:current-page="pageHot"
            style="margin-top:10px; display:flex; justify-content:center;"
          />
        </div>

        <div class="card" id="recent">
          <div class="card-title">最近便签</div>
          <div class="card-desc">最新公开更新</div>
          <ul class="note-list">
            <li class="note-item" v-for="it in recentPageItems" :key="it.id" @click="goNote(it)" role="button">
              <div class="title">{{ tagTitle(it) }}</div>
              <div class="content">{{ snippet(it.content || it.title) }}</div>
              <div class="meta">
                <div class="left">
                  <span class="author">{{ displayAuthor(it) }}</span>
                </div>
                <div class="right">
                  <span class="time">{{ formatYMD(it.updatedAt || it.updated_at) }}</span>
                </div>
              </div>
            </li>
            <li v-if="!recentNotes.length" class="empty">暂无最近便签</li>
          </ul>
          <el-pagination
            v-if="recentNotes.length"
            background
            layout="prev, pager, next"
            :total="recentNotes.length"
            :page-size="pageSize"
            v-model:current-page="pageRecent"
            style="margin-top:10px; display:flex; justify-content:center;"
          />
        </div>
        </div>

        <div class="card" id="site">
          <div class="card-title">网站便签</div>
          <div class="card-desc">推荐站点</div>
          <ul class="note-list">
            <li class="note-item" v-for="it in siteNotes" :key="it.id" @click="goSite(it)" role="button">
              <div class="title">{{ it.title }}</div>
              <div class="content">{{ snippet(it.content || '') }}</div>
              <div class="meta">
                <div class="left">
                  <span class="author">站点</span>
                </div>
                <div class="right">
                  <span class="time"></span>
                </div>
              </div>
            </li>
            <li v-if="!siteNotes.length" class="empty">暂无网站便签</li>
          </ul>
        </div>

        <div class="card" id="git">
          <div class="card-title">Git便签</div>
          <div class="card-desc">常用 Git 命令与参考</div>
          <ul class="note-list">
            <li class="note-item" v-for="it in gitNotes" :key="it.id" @click="goGit(it)" role="button">
              <div class="title">{{ it.title }}</div>
              <div class="content">{{ snippet(it.content || '') }}</div>
              <div class="meta">
                <div class="left">
                  <span class="author">命令</span>
                </div>
                <div class="right">
                  <span class="time"></span>
                </div>
              </div>
            </li>
            <li v-if="!gitNotes.length" class="empty">暂无Git便签</li>
          </ul>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { http } from '@/api/http'

const hotNotes = ref([])
const recentNotes = ref([])
const siteNotes = ref([
  { id: 'site-emby', title: 'emby.wiki', content: 'Emby 指南与更新', url: 'https://emby.wiki' }
])
const sections = [
  { id: 'hot', label: '热门' },
  { id: 'recent', label: '最近' },
  { id: 'site', label: '网站便签' },
  { id: 'git', label: 'git便签' },
]
const activeId = ref('hot')
const contentRef = ref(null)
const pageSize = 4
const pageHot = ref(1)
const pageRecent = ref(1)
const hotPageItems = computed(() => {
  const start = (pageHot.value - 1) * pageSize
  return hotNotes.value.slice(start, start + pageSize)
})
const recentPageItems = computed(() => {
  const start = (pageRecent.value - 1) * pageSize
  return recentNotes.value.slice(start, start + pageSize)
})

function snippet(text){
  const s = String(text || '').replace(/\s+/g, ' ').trim()
  return s.length > 80 ? s.slice(0, 80) + '…' : s
}
function snippetTitle(text){
  const s = String(text || '').replace(/\s+/g, ' ').trim()
  return s.length > 36 ? s.slice(0, 36) + '…' : s
}
function displayAuthor(n){
  // 优先昵称，其次兼容后端 authorName/author_name（后端已优先昵称），最后回退用户名
  const candidates = [
    n.nickname,
    n.nickName,
    n.userNickname,
    n.user_nickname,
    n.authorNickname,
    n.author_nickname,
    (n.user && n.user.nickname),
    n.authorName,
    n.author_name,
    n.username
  ]
  const v = candidates.find(x => typeof x === 'string' && x.trim())
  return v ? String(v) : '未知'
}
function normalizeTags(tags){
  if (Array.isArray(tags)) return tags.map(t => String(t || '').replace(/^#+/, '').trim()).filter(Boolean)
  const s = String(tags || '').trim()
  if (!s) return []
  return s.split(/[\s,，、;；]+/).map(x => String(x).replace(/^#+/, '').trim()).filter(Boolean)
}
function extractTagsFromContent(text){
  const out = []
  const s = String(text || '')
  const re = /#([\p{L}\w-]+)/gu
  for (const m of s.matchAll(re)){
    const t = (m[1] || '').trim()
    if (t) out.push(t)
  }
  return out
}
function tagTitle(n){
  const fromField = normalizeTags(n.tags)
  const fromContent = extractTagsFromContent(n.content || n.title)
  const uniq = []
  const seen = new Set()
  for (const t of [...fromField, ...fromContent]){
    const key = t.toLowerCase()
    if (t && !seen.has(key)) { seen.add(key); uniq.push(t) }
  }
  const joined = uniq.slice(0,3).map(t => `#${t}`).join(' ')
  return joined || '无标签'
}
function fmtTime(t){
  if (!t) return ''
  try{
    const d = new Date(String(t))
    if (isNaN(d.getTime())) return String(t)
    return d.toLocaleString()
  }catch{ return String(t || '') }
}
function formatYMD(t){
  if (!t) return ''
  try{
    const d = new Date(String(t))
    if (isNaN(d.getTime())) return ''
    const y = d.getFullYear()
    const m = String(d.getMonth()+1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${day}`
  }catch{ return '' }
}
function goNote(it){
  // 跳到搜索页，使用标题/内容片段作为查询词
  const base = snippet(it.title || it.content || '')
  const q = base.slice(0, 30)
  window.location.hash = `#/search?q=${encodeURIComponent(q)}`
}

function goSite(it){
  if (it && it.url) {
    const url = String(it.url)
    window.open(url, '_blank', 'noopener')
  }
}

const gitNotes = ref([
  { id: 'git-init', title: 'git init', content: '初始化仓库，创建 .git 目录', url: 'https://git-scm.com/docs/git-init' },
  { id: 'git-clone', title: 'git clone <url>', content: '克隆远程仓库到本地', url: 'https://git-scm.com/docs/git-clone' },
  { id: 'git-status', title: 'git status', content: '查看工作区与暂存区的状态', url: 'https://git-scm.com/docs/git-status' },
  { id: 'git-add', title: 'git add .', content: '添加所有更改到暂存区', url: 'https://git-scm.com/docs/git-add' },
  { id: 'git-commit', title: 'git commit -m "msg"', content: '提交暂存内容并附加说明', url: 'https://git-scm.com/docs/git-commit' },
  { id: 'git-pull', title: 'git pull', content: '拉取远程更新并合并', url: 'https://git-scm.com/docs/git-pull' },
  { id: 'git-push', title: 'git push', content: '推送本地提交到远程仓库', url: 'https://git-scm.com/docs/git-push' }
])

function goGit(it){
  if (it && it.url){
    const url = String(it.url)
    window.open(url, '_blank', 'noopener')
  }
}

async function loadHot(){
  try{
    const { data } = await http.get('/notes/hot', { params: { size: 16 }, suppress401Redirect: true })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    hotNotes.value = items || []
    pageHot.value = 1
  }catch(e){ hotNotes.value = [] }
}
async function loadRecent(){
  try{
    const { data } = await http.get('/notes/recent', { params: { size: 16 }, suppress401Redirect: true })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    recentNotes.value = items || []
    pageRecent.value = 1
  }catch(e){ recentNotes.value = [] }
}

onMounted(() => { loadHot(); loadRecent(); })

// 滚动控制与激活态
function scrollTo(id){
  const container = contentRef.value
  if (!container) return
  const el = container.querySelector('#' + id)
  if (!el) return
  const top = el.offsetTop
  container.scrollTo({ top, behavior: 'smooth' })
  activeId.value = id
}

function handleScroll(){
  const container = contentRef.value
  if (!container) return
  const scrollTop = container.scrollTop
  const nodes = sections.map(s => ({ id: s.id, el: container.querySelector('#' + s.id) })).filter(x => x.el)
  // 选取当前滚动位置最近的 section
  let current = 'hot'
  let minDelta = Infinity
  for (const n of nodes){
    const delta = Math.abs(n.el.offsetTop - scrollTop)
    if (delta < minDelta){ minDelta = delta; current = n.id }
  }
  activeId.value = current
}

onMounted(() => {
  const container = contentRef.value
  if (container){ container.addEventListener('scroll', handleScroll, { passive: true }) }
})
</script>

<style scoped>
.square-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.brand { display: flex; align-items: center; gap: 8px; }
.brand h1 { font-size: 20px; margin: 0; }
.actions { display: flex; gap: 8px; }
.grid-two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; }
.layout { display:flex; gap:12px; align-items:flex-start; }
.side-nav { width:180px; border:none; border-radius:12px; background: transparent; padding:12px; position:fixed; left:12px; top:12px; z-index:10; }
.side-nav .nav-title { font-weight:600; margin-bottom:8px; }
.side-nav .nav-list { list-style:none; margin:0; padding:0; display:flex; flex-direction:row; flex-wrap:wrap; gap:8px; align-items:center; }
.side-nav .nav-list a { display:block; padding:8px 10px; border-radius:8px; color:#303133; text-decoration:none; transition: background-color .15s ease; }
.side-nav .nav-list a:hover { background:#f5f7ff; }
.side-nav .nav-list a.active { background:#ecf5ff; color:#409eff; }
.side-nav .nav-list li { display:flex; align-items:center; }
.side-nav .nav-list li + li::before { content:'/'; color:#909399; margin:0 4px; }
.side-nav .nav-list li.break { flex-basis:100%; margin-top:6px; }
.side-nav .nav-list li.break::before { content: none; }
.content-scroll { flex:1; max-height:70vh; overflow:auto; scroll-behavior:smooth; display:flex; flex-direction:column; gap:12px; }
.content-scroll .card { scroll-margin-top:8px; }
.content-head { display:flex; align-items:center; gap:6px; font-weight:600; color:#303133; margin: 4px 0 4px; }
.content-head .slash { color:#909399; }
.card { border: 1px solid #e5e7eb; border-radius: 12px; padding: 16px; background: #fff; }
.card-title { font-weight: 600; margin-bottom: 6px; }
.card-desc { color: #606266; margin-bottom: 8px; }
 .note-list { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
 .note-item { background:#fff; border:1px solid #ebeef5; border-radius:12px; padding:14px; height:140px; box-shadow:0 4px 12px rgba(0,0,0,0.06); cursor:pointer; transition: transform .15s ease, box-shadow .15s ease; display:flex; flex-direction:column; justify-content:space-between; box-sizing:border-box; }
 .note-item:hover { transform: translateY(-2px); box-shadow:0 8px 20px rgba(0,0,0,0.08); }
 .note-item .title { color:#303133; font-size:15px; font-weight:600; line-height:1.6; display:-webkit-box; -webkit-line-clamp:1; -webkit-box-orient: vertical; overflow:hidden; word-break:break-word; overflow-wrap:anywhere; }
 .note-item .content { color:#303133; font-size:14px; line-height:1.7; display:-webkit-box; -webkit-line-clamp:1; -webkit-box-orient: vertical; overflow:hidden; word-break:break-word; overflow-wrap:anywhere; }
 .note-item .meta { display:flex; justify-content:space-between; align-items:center; margin-top:8px; color:#606266; font-size:12px; }
 .note-item .meta .left { display:flex; align-items:center; gap:6px; min-width:0; flex:1; }
 .note-item .meta .right { display:flex; align-items:center; gap:6px; color:#909399; flex:none; }
 .note-item .meta .author { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; min-width:0; flex:1; }
 .note-item .meta .dot { color:#c0c4cc; }
.note-list li { cursor: pointer; padding: 10px; border-radius: 8px; border: 1px solid #f0f0f0; background: #fafafa; }
.note-list li:hover { background: #f5f7ff; border-color: #e0e9ff; }
.note-list .content { color: #303133; margin-bottom: 6px; }
.note-list .meta { color: #606266; font-size: 12px; display: flex; gap: 10px; }
.note-list .empty { color: #909399; background: #fff; border: 1px dashed #e5e7eb; }
@media (max-width: 720px){ .grid-two { grid-template-columns: 1fr; } }
@media (max-width: 960px){
  .layout { flex-direction: column; }
  .side-nav { width: 100%; position: static; left: auto; top: auto; }
  .content-scroll { max-height: none; margin-left: 0; }
}
</style>
