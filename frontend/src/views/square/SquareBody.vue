<template>
  <div>
    <header class="square-header">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="28" height="28" />
        <h1>云便签 · 广场</h1>
      </div>
    </header>

    <section class="hero">
      <h2>记录灵感，分享片段</h2>
      <p>在广场探索大家公开的便签，或登录后开始创作。</p>
      <div class="hero-actions">
        <el-button type="primary" @click="$router.push('/notes')">进入便签</el-button>
        <el-button @click="$router.push('/my-notes')">我的便签</el-button>
      </div>
    </section>

    <section class="grid-two">
      <div class="card">
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
      <div class="card">
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
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { http } from '@/api/http'

const hotNotes = ref([])
const recentNotes = ref([])
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
</script>

<style scoped>
.square-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.brand { display: flex; align-items: center; gap: 8px; }
.brand h1 { font-size: 20px; margin: 0; }
.actions { display: flex; gap: 8px; }
.hero { text-align: center; padding: 48px 12px; background: linear-gradient(135deg, #f0f7ff, #ffffff); border: 1px solid #e5e7eb; border-radius: 12px; margin-bottom: 20px; }
.hero h2 { font-size: 24px; margin: 0 0 8px; }
.hero p { color: #606266; margin: 0 0 16px; }
.hero-actions { display: flex; gap: 12px; justify-content: center; }
.grid-two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.card { border: 1px solid #e5e7eb; border-radius: 12px; padding: 16px; background: #fff; }
.card-title { font-weight: 600; margin-bottom: 6px; }
.card-desc { color: #606266; margin-bottom: 8px; }
 .note-list { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
 .note-item { background:#fff; border:1px solid #ebeef5; border-radius:12px; padding:14px; height:140px; box-shadow:0 4px 12px rgba(0,0,0,0.06); cursor:pointer; transition: transform .15s ease, box-shadow .15s ease; display:flex; flex-direction:column; justify-content:space-between; box-sizing:border-box; }
 .note-item:hover { transform: translateY(-2px); box-shadow:0 8px 20px rgba(0,0,0,0.08); }
 .note-item .title { color:#303133; font-size:15px; font-weight:600; line-height:1.6; display:-webkit-box; -webkit-line-clamp:1; -webkit-box-orient: vertical; overflow:hidden; word-break:break-word; overflow-wrap:anywhere; }
 .note-item .content { color:#303133; font-size:14px; line-height:1.7; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient: vertical; overflow:hidden; word-break:break-word; overflow-wrap:anywhere; }
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
</style>
