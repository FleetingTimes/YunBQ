<template>
  <div class="container">
    <header class="square-header">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="28" height="28" />
        <h1>云便签 · 广场</h1>
      </div>
    </header>

    <section class="layout">
      <!-- 抽取：使用通用侧边栏组件 SideNav（父子导航、默认折叠、选择事件）
           说明：
           - 通过 v-model:activeId 绑定当前高亮项；
           - 监听 select 事件并调用 scrollTo，实现滚动到内容区块；
           - sections 数据结构保持不变，aliasTargets 的滚动映射由父组件 scrollTo 处理。 -->
      <SideNav :sections="sections" v-model:activeId="activeId" @select="scrollTo" />
      <div class="content-scroll" ref="contentRef">
        <!-- 内容导航提示：同步展示区块名称，新增“知识/影视/工具/AI 便签” -->
        <div class="content-head">
          <!-- 文案合并：顶部内容导航合并为“热门·最近”以与侧边导航一致 -->
          <span>热门·最近</span><span class="slash">/</span>
          <!-- 更新：网站便签改为聚合便签，数据来源为标签“聚合” -->
          <span>聚合便签</span><span class="slash">/</span>
          <!-- 撤销：顶部内容导航不再展示父项“Git便签”，仅保留其子项文案提示
               原因：父导航内容区已取消分组容器，回退为直接显示子卡片；
               因此顶部文案与侧边导航保持“仅子项”一致，避免层级冗余。 -->
          <span>git影音</span><span class="slash">/</span><span>git工具</span><span class="slash">/</span>
          <span>知识便签</span><span class="slash">/</span>
          <!-- 更新：顶部内容导航显示影视便签的五个子项文案
               说明：与侧边导航子项保持一致，便于用户快速了解影视便签的内容分类 -->
          <span>在线影视</span><span class="slash">/</span><span>影视软件</span><span class="slash">/</span><span>短视频</span><span class="slash">/</span><span>短视频下载</span><span class="slash">/</span><span>在线动漫</span><span class="slash">/</span>
          <!-- 撤销：顶部内容导航不再展示父项“音乐便签”，仅保留其子项文案提示
               原因：父导航内容区已取消分组容器，回退为直接显示子卡片；
               因此顶部文案与侧边导航保持“仅子项”一致。 -->
          <span>在线音乐</span><span class="slash">/</span><span>音乐下载</span><span class="slash">/</span>
          <span>工具便签</span><span class="slash">/</span>
          <!-- 顶部内容导航：在 AI 便签后追加子类“AI·绘图”用于位置提示（不影响侧边导航与滚动逻辑） -->
          <span>AI便签</span><span class="slash">/</span><span>AI·绘图</span>
        </div>
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

        <!-- 使用通用站点便签组件：抽象样式与数据逻辑，传入标签为“网站”
             启用来源切换模式为“公开/聚合”（聚合=公开+我的，登录后可用） -->
        <!-- 改造：站点类“聚合便签”（仅标签含“聚合”的便签）
             说明：
             - 使用通用组件 SiteNoteList，并将标签过滤改为“聚合”；
             - 保持来源切换为“公开/我的”，未登录时仅显示公开；
             - 仅更换数据过滤标签与区块标题，不改变样式与交互。 -->
        <SiteNoteList id="site" title="聚合便签" subtitle="推荐站点" tag="聚合" />

        <!-- 撤销：移除 Git 父分组容器（div#git），恢复子卡片直接渲染
             目的：回退到“父导航不显示内容区，仅展示子卡片”的设计；
             实现：删除包裹容器，保留子卡片组件，使滚动与锚点仍按子项工作。 -->
        <!-- 子区块“git影音” → 严格过滤标签“git影音” -->
        <SiteNoteList id="git-media" title="Git · 影音" subtitle="与 Git 相关的影音资源" tag="git影音" />
        <!-- 子区块“git工具” → 严格过滤标签“git工具” -->
        <SiteNoteList id="git-tool" title="Git · 工具" subtitle="Git 配套工具与插件" tag="git工具" />

        <!-- 新增：站点类的“知识便签”区
             说明：
             - 复用通用组件 SiteNoteList；
             - 通过标签严格过滤（大小写不敏感），此处约定标签为“知识”；
             - 列表样式与网站/Git 区一致，保持统一视觉。 -->
        <SiteNoteList id="knowledge" title="知识便签" subtitle="站点知识精选" tag="知识" />

        <!-- 新增：站点类的"影视便签"区
             说明：
             - 标签约定为"影视"；
             - 复用通用组件 SiteNoteList，统一来源切换、分页与移动端行为。 -->
        <SiteNoteList id="movie" title="影视便签" subtitle="影视站点资源" tag="影视" />

        <!-- 新增：影视便签子卡片区域
             说明：
             - 为"影视便签"添加四个子卡片：在线影视、影视软件、短视频、短视频下载；
             - 每个子卡片使用 SiteNoteList 组件，通过不同标签过滤对应内容；
             - 锚点 id 与侧边导航子项对应，支持滚动定位与高亮；
             - 样式继承影视便签区的统一视觉效果。 -->
        <!-- 子区块：在线影视（标签"在线影视"） -->
        <SiteNoteList id="movie-online" title="影视 · 在线影视" subtitle="在线影视平台与资源" tag="在线影视" />
        <!-- 子区块：影视软件（标签"影视软件"） -->
        <SiteNoteList id="movie-software" title="影视 · 影视软件" subtitle="影视播放与编辑软件" tag="影视软件" />
        <!-- 子区块：短视频（标签"短视频"） -->
        <SiteNoteList id="movie-short" title="影视 · 短视频" subtitle="短视频平台与工具" tag="短视频" />
        <!-- 子区块：短视频下载（标签"短视频下载"） -->
        <SiteNoteList id="movie-download" title="影视 · 短视频下载" subtitle="短视频下载工具与方法" tag="短视频下载" />
        <!-- 新增：在线动漫子卡片
             说明：
             - 为影视便签添加在线动漫分类卡片，锚点 id 为 movie-anime；
             - 通过标签"在线动漫"过滤对应内容；
             - 样式继承影视便签区的统一视觉效果。 -->
        <SiteNoteList id="movie-anime" title="影视 · 在线动漫" subtitle="在线动漫平台与资源" tag="在线动漫" />

        <!-- 新增：站点类的“音乐便签”区
             说明：
             - 父区块用于展示标签为“音乐”的总览；
             - 子区块“在线音乐/音乐下载”分别严格过滤对应标签；
             - 与侧边导航的树状结构保持锚点一致（music / music-online / music-download）。 -->
        <!-- 撤销：移除 音乐 父分组容器（div#music），恢复子卡片直接渲染
             目的：回退到“父导航不显示内容区，仅展示子卡片”的设计；
             实现：删除包裹容器，保留子卡片组件，使滚动与锚点仍按子项工作。 -->
        <!-- 子区块：在线音乐（标签“在线音乐”） -->
        <SiteNoteList id="music-online" title="音乐 · 在线音乐" subtitle="在线音乐平台与工具" tag="在线音乐" />
        <!-- 子区块：音乐下载（标签“音乐下载”） -->
        <SiteNoteList id="music-download" title="音乐 · 音乐下载" subtitle="音乐下载与资源" tag="音乐下载" />

        <!-- 新增：站点类的“工具便签”区
             说明：
             - 标签约定为“工具”；
             - 使用与网站/Git/知识一致的视觉与交互。 -->
        <SiteNoteList id="tool" title="工具便签" subtitle="常用工具站点" tag="工具" />

        <!-- 新增：子区块“AI · 绘图”
             说明：
             - 为站点类子便签添加独立卡片，锚点 id 对应侧边子导航（ai-draw）；
             - 通过标签严格过滤（约定标签为“绘图”）；
             - 放置在 AI 便签之后，形成父子层级的顺序关系。 -->
        <SiteNoteList id="ai" title="AI便签" subtitle="AI 工具与教程" tag="AI" />
        <SiteNoteList id="ai-draw" title="AI · 绘图" subtitle="AI 绘图工具与案例" tag="绘图" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { http } from '@/api/http'
import { getToken } from '@/utils/auth'
import SiteNoteList from '@/components/SiteNoteList.vue'
import SideNav from '@/components/SideNav.vue'
// 抽取：从公共配置导入侧边栏导航，保持与“添加便签”页一致
import { sideNavSections } from '@/config/navSections'

const hotNotes = ref([])
const recentNotes = ref([])
// 登录状态（响应式）：
// - 通过一个响应式 tokenRef 来驱动 isLoggedIn；
// - 监听 hash 路由变化与页面可见性变化，及时刷新登录状态；
// - 这样在退出登录后无需手动刷新也能隐藏控件与重载公开网站便签。
const tokenRef = ref('')
const isLoggedIn = computed(() => !!(tokenRef.value && tokenRef.value.trim()))
function refreshAuth(){
  try{ tokenRef.value = String(getToken() || '') }catch{ tokenRef.value = '' }
}
// 导航配置改为公共导入，确保与添加便签页一致、便于维护
const sections = sideNavSections
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

// 网站区跳转逻辑已内聚到 SiteNoteList（通过 openSite 实现），此处不再保留。

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

// Git便签数据源：来自后端 `/api/notes`，严格过滤标签为“git”（大小写不敏感）
// 与网站便签区一致，默认只拉取公开便签（isPublic=true），后续可扩展“我的Git便签”。

// Git便签打开链接：优先从内容中解析（与网站逻辑一致），若解析不到则回退到字段 it.url
function goGit(it){
  if (!it) return
  const u1 = siteUrl(it)
  const u2 = it.url ? String(it.url) : ''
  const url = u1 || u2
  if (url){ window.open(url, '_blank', 'noopener') }
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

// 网站区的标签过滤由通用组件内部完成（hasTag），无需在父组件保留。

// Git 区也已改用通用组件 SiteNoteList，父组件不再保留标签过滤。

// 网站区数据加载由通用组件内部完成，父组件不再维护。

/**
 * 加载 Git 便签：来源与网站区一致，默认拉取公开便签并在前端严格过滤 “git” 标签。
 * - 通过 `q=git` 做粗筛；最终严格以标签为准，大小写不敏感。
 */

/**
 * 控件切换回调：根据选择加载来源（公开/我的）。
 * - 未登录时强制回退为 'public' 并隐藏控件（由模板层处理）。
 */
// 网站来源切换由通用组件内部完成，父组件不再维护。


// 页面挂载：加载热门/最近与网站便签（网站便签仅来源于“我的便签”且标签为“网站”）
onMounted(() => {
  loadHot();
  loadRecent();
  // 网站区的初始加载与来源切换交由 SiteNoteList 组件处理
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

// 网站区使用通用组件，父组件无需重置网站来源或数据。

// 滚动控制与激活态
// 滚动控制：
// - 支持“别名锚点”（aliasTargets）：当点击合并项（如热门·最近）时，默认滚动到其第一个锚点（hot）；
// - 其余项按原逻辑滚动到自身 id 对应的内容区块。
function scrollTo(id){
  const container = contentRef.value
  if (!container) return
  // 查找当前 section，若存在别名锚点，则优先使用第一个别名作为滚动目标
  const s = sections.find(x => x.id === id)
  const targetId = (s && Array.isArray(s.aliasTargets) && s.aliasTargets.length) ? s.aliasTargets[0] : id
  const el = container.querySelector('#' + targetId)
  if (!el) return
  const top = el.offsetTop
  container.scrollTo({ top, behavior: 'smooth' })
  activeId.value = id
}

// 滚动高亮：
// - 普通项：直接以自身 id 对应的内容锚点参与计算；
// - 合并项（有 aliasTargets）：其别名锚点（如 hot/recent）都映射为该项的 id，
//   因此在热门或最近附近滚动时，都会高亮“热门·最近”。
function handleScroll(){
  const container = contentRef.value
  if (!container) return
  const scrollTop = container.scrollTop
  const nodes = []
  for (const s of sections){
    // 自身锚点（若有对应内容区块 id）
    const elTop = container.querySelector('#' + s.id)
    if (elTop) nodes.push({ id: s.id, el: elTop })
    // 子项锚点（保持既有子导航支持）
    if (s.children && s.children.length){
      for (const c of s.children){
        const elChild = container.querySelector('#' + c.id)
        if (elChild) nodes.push({ id: c.id, el: elChild })
      }
    }
    // 别名锚点：将别名的内容锚点映射到当前 section 的 id
    if (Array.isArray(s.aliasTargets)){
      for (const a of s.aliasTargets){
        const elAlias = container.querySelector('#' + a)
        if (elAlias) nodes.push({ id: s.id, el: elAlias })
      }
    }
  }
  const validNodes = nodes.filter(x => x.el)
  let current = sections[0]?.id || 'hot-recent'
  let minDelta = Infinity
  for (const n of validNodes){
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
/* 统一侧边导航为树状纵向布局
   说明：
   - 改为块级纵向排列，移除斜杠分隔；
   - 在列表左侧绘制竖虚线作为树干；
   - 每个项前绘制短横线与树干连接，形成父子层级的统一视觉。 */
.side-nav .nav-list { list-style:none; margin:0; padding:0 0 0 16px; display:block; position:relative; }
.side-nav .nav-list::before { content:""; position:absolute; left:8px; top:0; bottom:0; width:0; border-left:1px dashed #dcdfe6; }
.side-nav .nav-list a { display:block; padding:8px 10px; border-radius:8px; color:#303133; text-decoration:none; transition: background-color .15s ease; }
.side-nav .nav-list a:hover { background:#f5f7ff; }
.side-nav .nav-list a.active { background:#ecf5ff; color:#409eff; }
.side-nav .nav-list li { display:block; align-items:unset; position:relative; padding-left:12px; margin:4px 0; }
/* 列表项连接树干：替代原有斜杠分隔符 */
.side-nav .nav-list li::before { content:""; position:absolute; left:0; top:50%; width:8px; border-top:1px solid #dcdfe6; transform: translateY(-50%); }
/* 分组断点：扩大间距以区分（原先 break 用于换行，现在用于加间距） */
.side-nav .nav-list li.break { margin-top:10px; }
.side-nav .nav-list li.break::before { content: ""; }
/* 恢复父级 has-children 行为：
   - 将父级 li 改为块级，使子导航纵向呈现并排版在父项下方；
   - 移除父级项前的斜杠分隔，避免视觉干扰。 */
.side-nav .nav-list li.has-children { display:block; }
.side-nav .nav-list li.has-children::before { content: none; }
/* 撤销样式：移除 has-children 的纵向布局与分隔符调整，恢复默认导航排列 */
/* 子导航样式：缩进显示，分隔符更轻
   说明：
   - 子导航与父导航区分开，采用更小的字体与内边距；
   - 去除父级的斜杠分隔，改用更轻的分隔符（或不显示分隔）。 */
.sub-nav-list { list-style:none; margin:4px 0 0 16px; padding:0; display:block; position:relative; }
/* 树状结构：在子导航左侧绘制一条竖线，增强父子层级感 */
.sub-nav-list::before { content:""; position:absolute; left:6px; top:4px; bottom:4px; width:0; border-left:1px dashed #dcdfe6; }
.sub-nav-list li { display:block; margin:4px 0; padding-left:12px; position:relative; }
/* 每个子项前绘制短横线与竖线连接，模拟树状分支 */
.sub-nav-list li::before { content:""; position:absolute; left:0; top:50%; width:8px; border-top:1px solid #dcdfe6; transform: translateY(-50%); }
.sub-nav-list a { display:block; padding:6px 8px; border-radius:6px; color:#606266; text-decoration:none; transition: background-color .15s ease; font-size: 13px; }
.sub-nav-list a:hover { background:#f6f8fe; }
.sub-nav-list a.active { background:#eef5ff; color:#409eff; }
.content-scroll { flex:1; max-height:70vh; overflow:auto; scroll-behavior:smooth; display:flex; flex-direction:column; gap:12px; }
.content-scroll .card { scroll-margin-top:8px; }
.content-head { display:flex; align-items:center; gap:6px; font-weight:600; color:#303133; margin: 4px 0 4px; }
.content-head .slash { color:#909399; }
.card { border: 1px solid #e5e7eb; border-radius: 12px; padding: 16px; background: #fff; }
.card-title { font-weight: 600; margin-bottom: 6px; }
.card-desc { color: #606266; margin-bottom: 8px; }
/* 撤销分组样式：删除 ai-group 的分组视觉，恢复为两个独立卡片 */
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

 /* 骨架加载样式：用于 Git 加载占位，避免空白跳变与布局抖动 */
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

 /* Git 便签区样式对齐网站便签区（仅作用于 #git 区块）
    说明：
    - 保持与网站便签一致的列数、间距、卡片尺寸与字体大小；
    - 通过 #git 作为作用域，避免影响其他区块；
    - 这样两者视觉统一，同时不改变热门/最近便签。 */
 #git .note-list { grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 8px; }
 #git .note-item { padding: 10px; height: 110px; box-shadow: 0 3px 10px rgba(0,0,0,0.05); }
 #git .note-item .title { font-size: 14px; line-height: 1.5; }
 #git .note-item .content { font-size: 12px; line-height: 1.5; }
 #git .note-item .meta { margin-top: 6px; font-size: 11px; }
 @media (max-width: 960px){ #git .note-list { grid-template-columns: repeat(2, minmax(160px, 1fr)); gap: 8px; } }
 @media (max-width: 640px){ #git .note-list { grid-template-columns: 1fr; gap: 6px; } }

 /* 知识便签区样式对齐网站/Git 便签区（仅作用于 #knowledge 区块）
    说明：
    - 统一列数、间距与卡片尺寸，保证一致的阅读体验；
    - 使用区块 id #knowledge 限定作用范围，避免影响其他列表。 */
 #knowledge .note-list { grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 8px; }
 #knowledge .note-item { padding: 10px; height: 110px; box-shadow: 0 3px 10px rgba(0,0,0,0.05); }
 #knowledge .note-item .title { font-size: 14px; line-height: 1.5; }
 #knowledge .note-item .content { font-size: 12px; line-height: 1.5; }
 #knowledge .note-item .meta { margin-top: 6px; font-size: 11px; }
 @media (max-width: 960px){ #knowledge .note-list { grid-template-columns: repeat(2, minmax(160px, 1fr)); gap: 8px; } }
 @media (max-width: 640px){ #knowledge .note-list { grid-template-columns: 1fr; gap: 6px; } }

 /* 影视便签区样式：与网站/Git/知识一致（仅作用于 #movie 区块） */
 #movie .note-list { grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 8px; }
 #movie .note-item { padding: 10px; height: 110px; box-shadow: 0 3px 10px rgba(0,0,0,0.05); }
 #movie .note-item .title { font-size: 14px; line-height: 1.5; }
 #movie .note-item .content { font-size: 12px; line-height: 1.5; }
 #movie .note-item .meta { margin-top: 6px; font-size: 11px; }
 @media (max-width: 960px){ #movie .note-list { grid-template-columns: repeat(2, minmax(160px, 1fr)); gap: 8px; } }
 @media (max-width: 640px){ #movie .note-list { grid-template-columns: 1fr; gap: 6px; } }

 /* 工具便签区样式：与网站/Git/知识一致（仅作用于 #tool 区块） */
 #tool .note-list { grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 8px; }
 #tool .note-item { padding: 10px; height: 110px; box-shadow: 0 3px 10px rgba(0,0,0,0.05); }
 #tool .note-item .title { font-size: 14px; line-height: 1.5; }
 #tool .note-item .content { font-size: 12px; line-height: 1.5; }
 #tool .note-item .meta { margin-top: 6px; font-size: 11px; }
 @media (max-width: 960px){ #tool .note-list { grid-template-columns: repeat(2, minmax(160px, 1fr)); gap: 8px; } }
 @media (max-width: 640px){ #tool .note-list { grid-template-columns: 1fr; gap: 6px; } }

 /* AI 便签区样式：与网站/Git/知识一致（仅作用于 #ai 区块） */
 #ai .note-list { grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 8px; }
 #ai .note-item { padding: 10px; height: 110px; box-shadow: 0 3px 10px rgba(0,0,0,0.05); }
 #ai .note-item .title { font-size: 14px; line-height: 1.5; }
 #ai .note-item .content { font-size: 12px; line-height: 1.5; }
 #ai .note-item .meta { margin-top: 6px; font-size: 11px; }
 @media (max-width: 960px){ #ai .note-list { grid-template-columns: repeat(2, minmax(160px, 1fr)); gap: 8px; } }
 @media (max-width: 640px){ #ai .note-list { grid-template-columns: 1fr; gap: 6px; } }
@media (max-width: 720px){ .grid-two { grid-template-columns: 1fr; } }
@media (max-width: 960px){
  .layout { flex-direction: column; }
  .side-nav { width: 100%; position: static; left: auto; top: auto; }
  .content-scroll { max-height: none; margin-left: 0; }
}
</style>
