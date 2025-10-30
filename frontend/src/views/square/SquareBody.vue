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
          <!-- 网站便签列表：撤销上一步的尺寸缩小，恢复默认列表样式 -->
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
          <div class="card-desc" style="display:flex; align-items:center; justify-content:space-between; gap:8px;">
            <span>推荐站点</span>
            <!-- 控件：登录后显示，用于切换“公开/我的”网站便签；未登录隐藏此控件。
                 行为：
                 - 未登录：默认显示公开网站便签，不渲染控件；
                 - 已登录：默认显示公开网站便签，可在此控件中切换为“我的网站便签”。 -->
            <template v-if="isLoggedIn">
              <el-radio-group v-model="siteSource" size="small" @change="onSiteSourceChange">
                <el-radio-button label="public">公开</el-radio-button>
                <el-radio-button label="mine">我的</el-radio-button>
              </el-radio-group>
            </template>
          </div>
          <ul class="note-list">
            <!-- 网站便签：仅展示“网站名”，点击跳转到对应链接。
                 注意：数据可能只有 content，其中包含网站名与链接地址。
                 为适配不同数据结构，显示名优先取 title；若没有 title，则从链接解析域名作为显示名。 -->
            <li class="note-item" v-for="it in siteNotes" :key="it.id" @click="goSite(it)" role="button">
              <div class="title">{{ siteName(it) }}</div>
              <!-- 显示网站介绍：若存在中间行内容则展示，否则为空白，以统一格式（首行名称、末行 URL、中间为介绍）。 -->
              <div class="content">{{ siteDesc(it) }}</div>
              <!-- 省略内容简介，仅保留网站名以满足需求 -->
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
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { http } from '@/api/http'
import { getToken } from '@/utils/auth'

const hotNotes = ref([])
const recentNotes = ref([])
// 网站便签数据源：只显示“我的便签”中标签为“网站”的便签
// 说明：
// - 数据来源统一通过后端接口 `/api/notes` 获取；
// - 请求参数包含 `mineOnly=true` 以确保仅返回“我的便签”；
// - 前端再次严格按标签过滤，仅保留标签集合中含“网站”的便签；
// - 便签的 `content` 解析遵循：首行名称 / 中间介绍 / 倒数第二行 URL / 最后一行标签（忽略）。
const siteNotes = ref([])
// 登录状态（响应式）：
// - 通过一个响应式 tokenRef 来驱动 isLoggedIn；
// - 监听 hash 路由变化与页面可见性变化，及时刷新登录状态；
// - 这样在退出登录后无需手动刷新也能隐藏控件与重载公开网站便签。
const tokenRef = ref('')
const isLoggedIn = computed(() => !!(tokenRef.value && tokenRef.value.trim()))
function refreshAuth(){
  try{ tokenRef.value = String(getToken() || '') }catch{ tokenRef.value = '' }
}
// 网站便签数据源：'public' 公开网站便签；'mine' 我的网站便签（仅登录时可选）
// 默认 'public'，未登录时强制为 'public' 并隐藏控件。
const siteSource = ref('public')
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
  // 跳转逻辑：统一按“末行 URL”为主；若未提供，则从内容中提取第一个 URL。
  const url = siteUrl(it)
  if (url) {
    window.open(url, '_blank', 'noopener')
  }
}

/**
 * 从文本中提取第一个 URL。
 * 支持 http/https 形式，简单鲁棒处理常见的链接分隔与尾随标点。
 */
function extractFirstUrl(text){
  const s = String(text || '')
  // 粗略 URL 正则：匹配 http(s):// 开头直到空白或右括号、中文标点前
  const re = /(https?:\/\/[^\s)\]\u3002\uFF1B\uFF0C]+)/i
  const m = s.match(re)
  return m ? m[1] : ''
}

/**
 * 统一解析网站便签内容，约定格式：
 *  - 第一行为网站名
 *  - 最后一行为 URL 地址
 *  - 中间的行为网站介绍（可选，多行将合并为一行展示）
 * 若未严格遵循，仍尽力解析并给出合理的 name/desc/url。
 */
function parseSiteInfoFromContent(text){
  const s = String(text || '').replace(/`/g, '').trim()
  if (!s) return { name: '', desc: '', url: '' }
  const lines = s.split(/\r?\n+/).map(l => l.trim()).filter(Boolean)
  // 识别“标签行”：常见为多个 #tag 或以“标签/Tags”开头的行，用于在便签末行标注，不参与简介
  const isTagsLine = (line) => {
    const t = String(line || '').trim()
    if (!t) return false
    if (/^(标签|tags?):?/i.test(t)) return true
    // 形如 “#前端 #Vue #教程” 的情况
    if (/^#\S+(?:\s+#\S+)*$/i.test(t)) return true
    return false
  }
  // 查找最后一个包含 URL 的行
  const urlRe = /https?:\/\/\S+/i
  let urlIdx = -1
  let url = ''
  for (let i = lines.length - 1; i >= 0; i--){
    const m = lines[i].match(urlRe)
    if (m){ urlIdx = i; url = m[0]; break }
  }
  // 网站名：优先第一行
  let name = lines.length ? lines[0] : ''
  name = String(name).replace(/["'“”‘’《》「」\[\]\(\)]+/g, '').trim()
  // 介绍：第一行与末行之间的所有行合并为一行
  let desc = ''
  if (urlIdx > 0){
    const descLines = lines.slice(1, urlIdx)
    desc = descLines.join(' ').trim()
  }else if (lines.length > 1){
    // 无 URL 行：若末行是“标签行”，介绍取除首行与末行外的中间行；否则取除首行外全部
    const lastIsTags = isTagsLine(lines[lines.length - 1])
    const descLines = lastIsTags ? lines.slice(1, lines.length - 1) : lines.slice(1)
    desc = descLines.join(' ').trim()
  }
  return { name, desc, url }
}

/**
 * 取得站点 URL：优先 it.url；否则从 it.content 中提取。
 */
function siteUrl(it){
  if (!it) return ''
  const direct = String(it.url || '').trim()
  if (direct) return direct
  // 优先末行 URL，其次回退为全文第一个 URL
  const info = parseSiteInfoFromContent(it.content)
  if (info.url) return info.url
  return extractFirstUrl(it.content)
}

/**
 * 取得用于展示的站点名：
 * 1) 优先使用 title
 * 2) 若无 title，则尝试从 URL 的域名解析（如 example.com）
 * 3) 仍不可得则回退为 content 的片段
 */
function siteName(it){
  if (!it) return '未知站点'
  // 优先使用 title 字段作为网站名（当数据本身已提供网站名时，更可靠）
  const t = String(it.title || '').trim()
  if (t) return t
  // 次级：从 content 中解析“首行网站名”（适配名称+介绍+URL 的统一格式）
  const info = parseSiteInfoFromContent(it?.content)
  if (info.name) return info.name
  // 再回退：域名/URL
  const url = siteUrl(it)
  if (url){
    try{ const u = new URL(url); return u.hostname || url }catch{ return url }
  }
  // 最后回退：内容片段
  return snippetTitle(it?.content)
}

/**
 * 从 content 文本中解析“网站名”。
 * 优先识别 Markdown 链接格式：[名称](https://example.com)
 * 若为“名称 + URL”的顺序（如：掘金 https://juejin.cn 或 掘金 `https://juejin.cn`），
 * 将 URL 之前的文本作为网站名，并进行清理（去除引号、反引号、分隔符等）。
 */
function parseSiteNameFromContent(text){
  const s = String(text || '').trim()
  if (!s) return ''
  // 1) Markdown 链接格式：[名称](url)
  const md = s.match(/\[([^\]]+)\]\(\s*https?:\/\/[^\s)]+\s*\)/i)
  if (md && md[1]){
    const name = String(md[1]).trim()
    if (name) return name
  }
  // 2) 支持“网站名 + 换行 + 链接地址”以及同一行的“名称 + URL”（允许反引号包裹 URL）
  //    思路：
  //    - 先按行拆分，找到第一行 URL；
  //    - 若存在，则选择其上一行（或最近的非 URL 行）作为网站名；
  //    - 若行内未找到 URL，则回退到全文检索第一个 URL，并取其前面的文本作为名称。
  const cleaned = s.replace(/`/g, '')
  const lines = cleaned.split(/\r?\n+/).map(l => l.trim()).filter(Boolean)
  const isUrlLine = (line) => /https?:\/\/\S+/i.test(line)
  let firstUrlLineIndex = -1
  for (let i = 0; i < lines.length; i++){
    if (isUrlLine(lines[i])){ firstUrlLineIndex = i; break }
  }
  if (firstUrlLineIndex >= 0){
    // 在 URL 之前寻找最近的非 URL 行作为名称（典型：第 0 行是名称，第 1 行是 URL）
    for (let j = firstUrlLineIndex - 1; j >= 0; j--){
      if (!isUrlLine(lines[j])){
        let name = lines[j]
        name = name
          .replace(/["'“”‘’《》「」\[\]\(\)`]+/g, '')
          .replace(/[|｜:：\-—–·•]+$/g, '')
          .trim()
        if (name) return name
      }
    }
    // 如果所有前序行都不可用，尝试用域名/URL
    const urlLine = lines[firstUrlLineIndex]
    const urlFromLineMatch = urlLine.match(/https?:\/\/\S+/i)
    const urlFromLine = urlFromLineMatch ? urlFromLineMatch[0] : ''
    if (urlFromLine){
      try{ const u = new URL(urlFromLine); return u.hostname || urlFromLine }catch{ return urlFromLine }
    }
  }
  // 3) 回退：文本 + URL 在同一行（或未按行拆分找到 URL）
  const url = extractFirstUrl(cleaned)
  if (url){
    const idx = cleaned.indexOf(url)
    if (idx > 0){
      let name = cleaned.slice(0, idx).trim()
      name = name
        .replace(/["'“”‘’《》「」\[\]\(\)`]+/g, '')
        .replace(/[|｜:：\-—–·•]+$/g, '')
        .trim()
      if (name) return name
    }
    try{ const u = new URL(url); return u.hostname || url }catch{ return url }
  }
  // 3) 没有 URL：清除可能的 URL 内容并截断一段文本
  const noUrlText = s.replace(/https?:\/\/\S+/g, '').trim()
  return snippetTitle(noUrlText)
}

/**
 * 取得网站介绍：
 *  - 若内容遵循“首行名称、末行链接”，则取中间行合并为简介；
 *  - 否则回退为空字符串，实现“无介绍则空白”的展示；
 *    如需更强回退可改为片段截断（但本需求指向空白）。
 */
function siteDesc(it){
  if (!it) return ''
  // 解析中间行作为简介
  const info = parseSiteInfoFromContent(it.content)
  let desc = String(info.desc || '').trim()
  // 兼容场景：数据提供了 title 作为网站名，而 content 只有一行介绍，且 URL 不在 content 中
  // 在这种情况下，解析不出中间行简介（因为没有 URL 行），这里将 content 作为简介显示
  if (!desc){
    const s = String(it.content || '').trim()
    const hasUrl = /https?:\/\/\S+/i.test(s)
    const isTagsLine = /^(标签|tags?):?/i.test(s) || /^#\S+(?:\s+#\S+)*$/i.test(s)
    if (s && !hasUrl && !isTagsLine){ desc = s }
  }
  return desc
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

/**
 * 判断便签是否包含“网站”标签（严格按标签匹配，不仅是内容包含）。
 * - 同时检查 `tags` 字段与 `content` 内的内联 #标签；
 * - 标签统一规范化，去除开头的 `#` 与多余分隔符；
 */
function hasWebsiteTag(n){
  const fieldTags = normalizeTags(n.tags)
  const contentTags = extractTagsFromContent(n.content || '')
  const all = [...fieldTags, ...contentTags].map(t => String(t || '').trim()).filter(Boolean)
  return all.some(t => t === '网站')
}

/**
 * 加载网站便签：依据来源（公开 / 我的）请求后端，并在前端再次严格过滤“网站”标签。
 * - source='public'：获取公开便签（`isPublic=true`，未登录时仅公开）；
 * - source='mine'：仅获取我的便签（`mineOnly=true`，登录后可用）。
 * - 同时传 `q=网站` 进行粗筛，降低传输体量；最终仍以前端严格标签过滤为准。
 */
async function loadSites(source = 'public'){
  try{
    const params = { page: 1, size: 100, q: '网站' }
    if (source === 'mine' && isLoggedIn.value){
      // 我的便签：仅作者本人，公开与私有均可；如需仅公开可加 isPublic=true
      Object.assign(params, { mineOnly: true })
    }else{
      // 公开网站便签：任何人可见
      Object.assign(params, { isPublic: true })
    }
    const { data } = await http.get('/notes', { params, suppress401Redirect: true })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    // 前端严格过滤标签为“网站”
    siteNotes.value = (items || []).filter(hasWebsiteTag)
  }catch(e){
    // 失败时置空，避免残留旧数据
    siteNotes.value = []
  }
}

/**
 * 控件切换回调：根据选择加载来源（公开/我的）。
 * - 未登录时强制回退为 'public' 并隐藏控件（由模板层处理）。
 */
function onSiteSourceChange(){
  const src = isLoggedIn.value ? siteSource.value : 'public'
  loadSites(src)
}

// 页面挂载：加载热门/最近与网站便签（网站便签仅来源于“我的便签”且标签为“网站”）
onMounted(() => {
  loadHot();
  loadRecent();
  // 初始：未登录/已登录均默认显示公开网站便签
  siteSource.value = 'public'
  loadSites('public')
  // 初始化登录状态并添加监听，确保退出登录后无需手动刷新也能更新控件显示
  refreshAuth()
  const onHashChange = () => refreshAuth()
  const onVisibilityChange = () => { if (!document.hidden) refreshAuth() }
  window.addEventListener('hashchange', onHashChange)
  window.addEventListener('visibilitychange', onVisibilityChange)
  // 兜底：轻量轮询，确保在同路由退出登录也能及时更新
  const authPoller = setInterval(refreshAuth, 1000)
  // 路由变化也刷新一次（更稳妥）
  const route = useRoute()
  watch(() => route.fullPath, () => refreshAuth())
  // 清理监听
  onUnmounted(() => {
    window.removeEventListener('hashchange', onHashChange)
    window.removeEventListener('visibilitychange', onVisibilityChange)
    clearInterval(authPoller)
  })
})

// 当登录状态发生变化时（例如登录/退出），重载网站便签来源：保持默认公开
// 当登录状态变化时，重置来源为公开并重新加载
watch(isLoggedIn, () => { siteSource.value = 'public'; loadSites('public') })

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

 /* 仅缩小“网站便签”区卡片尺寸（不影响热门/最近/Git）
    说明：
    - 使用区块 id 选择器 #site 限定作用范围，避免污染其他列表；
    - 调整列表列数、卡片内边距与高度、字体大小与间距；
    - 响应式在不同断点下保持合理密度与可读性。 */
 #site .note-list { grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 8px; }
 #site .note-item { padding: 10px; height: 110px; box-shadow: 0 3px 10px rgba(0,0,0,0.05); }
 #site .note-item .title { font-size: 14px; line-height: 1.5; }
 #site .note-item .content { font-size: 12px; line-height: 1.5; }
 #site .note-item .meta { margin-top: 6px; font-size: 11px; }

 /* 响应式断点：窄屏下减列以保证可读性 */
 @media (max-width: 960px){ #site .note-list { grid-template-columns: repeat(2, minmax(160px, 1fr)); gap: 8px; } }
 @media (max-width: 640px){ #site .note-list { grid-template-columns: 1fr; gap: 6px; } }
@media (max-width: 720px){ .grid-two { grid-template-columns: 1fr; } }
@media (max-width: 960px){
  .layout { flex-direction: column; }
  .side-nav { width: 100%; position: static; left: auto; top: auto; }
  .content-scroll { max-height: none; margin-left: 0; }
}
</style>
