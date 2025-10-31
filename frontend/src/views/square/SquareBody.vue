<template>
  <div class="container">
    <header class="square-header">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/notebook-outline.svg" alt="logo" width="28" height="28" />
        <h1>云便签 · 广场</h1>
      </div>
    </header>

    <section class="layout">
      <!-- 布局重构：侧边栏移至父组件（Square.vue）的左列，避免在正文内部嵌套侧栏影响整体结构。
           说明：
           - 本组件保留滚动逻辑 scrollTo 与滚动联动高亮（activeId）；
           - 通过 defineEmits 暴露 update:activeId 事件，使父组件可绑定到 SideNav 的 v-model；
           - 父组件通过 ref 调用 scrollTo(id) 保持原有滚动偏移与锚点映射逻辑。 -->
      <div class="content-scroll" ref="contentRef">
        <!-- 需求：隐藏顶部内容导航提示区域（红框圈住的横向标签文案）。
             实现：移除该 DOM 区块，避免冗余占位；保留滚动与锚点逻辑。
             兼容：scrollTo 中对 .content-head 的高度读取为 0（不存在），仍有额外安全间距；
             同时各卡片通过 CSS 的 scroll-margin-top 避免标题遮挡。 -->
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

        <!-- 云盘集：删除父导航卡片，改为渲染子卡片（云盘搜索 / 云盘工具）
             说明：
             - 用户需求：在导航栏“云盘集”下新增子导航“云盘搜索”“云盘工具”，并删除右侧无子导航的父卡片；
             - 本实现：侧边导航已配置 children（knowledge-search / knowledge-tool），点击父项滚动到首子项；
             - 右侧内容区删除父卡片 `<SiteNoteList id='knowledge' ...>`，仅渲染两个子卡片。
             - 标签过滤说明：为便于后续数据管理，子卡片分别严格过滤标签“云盘搜索”“云盘工具”（大小写不敏感）。 -->
        <!-- 子区块：云盘搜索（标签“云盘搜索”） -->
        <SiteNoteList id="knowledge-search" title="云盘 · 搜索" subtitle="云盘搜索工具与聚合入口" tag="云盘搜索" />
        <!-- 子区块：云盘工具（标签“云盘工具”） -->
        <SiteNoteList id="knowledge-tool" title="云盘 · 工具" subtitle="云盘上传、解析与转存相关工具" tag="云盘工具" />

        <!-- 撤销：移除 Git 父分组容器（div#git），恢复子卡片直接渲染
             目的：回退到"父导航不显示内容区，仅展示子卡片"的设计；
             实现：删除包裹容器，保留子卡片组件，使滚动与锚点仍按子项工作。 -->
        <!-- 子区块"git影音" → 严格过滤标签"git影音" -->
        <SiteNoteList id="git-media" title="Git · 影音" subtitle="与 Git 相关的影音资源" tag="git影音" />
        <!-- 子区块"git工具" → 严格过滤标签"git工具" -->
        <SiteNoteList id="git-tool" title="Git · 工具" subtitle="Git 配套工具与插件" tag="git工具" />
        <!-- 新增：git代理子区块 → 严格过滤标签"git代理" -->
        <SiteNoteList id="git-proxy" title="Git · 代理" subtitle="Git 代理配置与加速工具" tag="git代理" />

        <!-- 更新：站点类的"影视集"区（原"影视便签"）
             说明：
             - 仅更新展示文案为"影视集"，保持标签与数据筛选逻辑不变；
             - 标签约定仍为"影视"，复用通用组件 SiteNoteList 的严格过滤；
             - 来源切换、分页与移动端行为保持一致。 -->
        <!-- 撤销：移除"影视集"父卡片区（id="movie"）。父导航点击将滚动到首子项 movie-online。 -->

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

        <!-- 新增：图书集父区块与两个子区块
             说明：
             - 父区块“图书集”展示标签为“图书”的总览内容；
             - 子区块“在线图书 / 图书下载”分别严格过滤对应标签；
             - 锚点 id 与侧边导航子项一致（book-online / book-download），用于滚动定位与高亮。 -->
        <!-- 撤销：移除“图书集”父卡片区（id="book"）。父导航点击将滚动到首子项 book-online。 -->
        <!-- 子区块：在线图书（标签“在线图书”） -->
        <SiteNoteList id="book-online" title="图书 · 在线图书" subtitle="在线阅读与图书平台" tag="在线图书" />
        <!-- 子区块：图书下载（标签“图书下载”） -->
        <SiteNoteList id="book-download" title="图书 · 图书下载" subtitle="图书下载与资源" tag="图书下载" />

        <!-- 新增：图书搜索（标签“图书搜索”）
             说明：
             - 与侧边导航子项 id 对齐（book-search），用于滚动定位与高亮；
             - 严格过滤标签“图书搜索”，展示图书检索、聚合入口与相关工具；
             - 默认将父项点击滚动到首子项（book-online），如需变更可调整 aliasTargets。 -->
        <SiteNoteList id="book-search" title="图书 · 搜索" subtitle="图书搜索与聚合入口" tag="图书搜索" />

        <!-- 更新：工具区
             说明：
             - 父区块“工具集”用于展示“工具”标签的总览；
             - 子区块新增“文件工具 / 影音工具 / 其他工具”，分别严格过滤对应标签；
             - id 与侧边导航子项一致（tool-file / tool-media / tool-other），用于滚动定位与高亮。 -->
        <!-- 撤销：移除“工具集”父卡片区（id="tool"）。父导航点击将滚动到首子项 tool-file。 -->
        <!-- 子区块：文件工具（标签“文件工具”） -->
        <SiteNoteList id="tool-file" title="工具 · 文件工具" subtitle="文件处理与转换工具" tag="文件工具" />
        <!-- 子区块：影音工具（标签“影音工具”） -->
        <SiteNoteList id="tool-media" title="工具 · 影音工具" subtitle="音视频处理与播放相关工具" tag="影音工具" />
        <!-- 新增：磁力工具（标签“磁力工具”）
             说明：
             - 与侧边导航子项 id 对齐（tool-magnet），用于滚动定位与高亮；
             - 通过标签“磁力工具”严格过滤磁力链接解析、搜索与下载相关站点；
             - 放置在“影音工具”之后，便于用户在媒体相关工具中快速找到磁力相关功能。 -->
        <SiteNoteList id="tool-magnet" title="工具 · 磁力工具" subtitle="磁力链接解析与搜索" tag="磁力工具" />
        <!-- 子区块：其他工具（标签“其他工具”） -->
        <SiteNoteList id="tool-other" title="工具 · 其他工具" subtitle="其他常用站点工具" tag="其他工具" />

        <!-- 更新：子区块“AI绘图”（原文案“AI · 绘图”）
             说明：
             - 为站点类子便签添加独立卡片，锚点 id 对应侧边子导航（ai-draw）；
             - 数据来源标签改为“AI绘图”（原为“绘图”），确保与侧边导航文案一致；
             - 组件内部将以 props.tag 严格过滤，大小写不敏感（见 SiteNoteList 说明）。
             - 放置在 AI 便签之后，形成父子层级的顺序关系。 -->
        <!-- 父区块标题更新：将“AI便签”改为“AI集”，仅更新文案，id 不变 -->
        <!-- 撤销：移除“AI集”父卡片区（id="ai"）。父导航点击将滚动到首子项 ai-draw。 -->
        <!-- 子区块标题更新：移除中间点，改为“AI绘图”，仅更新文案，id 不变
             数据标签同步：将 tag 从“绘图”修改为“AI绘图”，以匹配后端与内容标签。 -->
        <SiteNoteList id="ai-draw" title="AI绘图" subtitle="AI 绘图工具与案例" tag="AI绘图" />
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
// 抽取：从公共配置导入侧边栏导航，保持与“添加便签”页一致
import { sideNavSections } from '@/config/navSections'
// 暴露事件：向父组件更新当前高亮项，以联动左侧 SideNav 的 v-model
const emit = defineEmits(['update:activeId'])

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
// 辅助函数：取得“最近的滚动父容器”（自适应 right-main 或 .content-scroll）
// 说明：
// - 在不同布局下，可能由 `.right-main` 或内部 `.content-scroll` 承担滚动；
// - 这里向上查找第一个 overflowY 为 auto/scroll 的祖先，作为滚动上下文；
// - 若未找到，则回退为 `.content-scroll` 本身，确保滚动行为可用。
function getScrollContainer(){
  const base = contentRef.value
  if (!base) return null
  let el = base
  while (el && el !== document.body){
    try{
      const s = getComputedStyle(el)
      const oy = String(s.overflowY || '').toLowerCase()
      if (oy === 'auto' || oy === 'scroll') return el
    }catch{ /* 忽略异常，继续向上查找 */ }
    el = el.parentElement
  }
  return base
}

function scrollTo(id){
  const container = getScrollContainer()
  if (!container) return
  // 统一滚动容器：始终使用正文容器（.content-scroll）作为滚动上下文，避免回退到页面级滚动
  // 背景：两栏布局已将页面级滚动禁用（.two-pane { overflow: hidden }），因此 window.scrollTo 不再生效；
  // 方案：无论容器是否产生溢出，都使用容器 scrollTo，实现确定性的滚动行为。

  // 查找当前 section，若存在别名锚点，则优先使用第一个别名作为滚动目标
  const s = sections.find(x => x.id === id)
  const targetId = (s && Array.isArray(s.aliasTargets) && s.aliasTargets.length) ? s.aliasTargets[0] : id
  const el = (contentRef.value || container).querySelector('#' + targetId)
  if (!el) return

  // 动态计算滚动偏移量，避免标题与顶栏遮挡目标卡片
  // - 标题（.square-header）与全局顶栏（.topbar/.header.topbar）位于容器之外，但会遮挡视线；
  // - 读取实际高度并叠加容器上内边距与安全间距，保证滚动后舒适的可视留白。
  // 兼容两种滚动容器：页面右栏(.right-main) 与正文内部(.content-scroll)
  // - 当容器是 .right-main 时，顶部不存在覆盖性的固定顶栏，不需要额外减去顶栏高度；
  // - 当容器是 .content-scroll（较旧布局或嵌套场景），才考虑标题区 .square-header 的高度。
  const isRightMain = container.classList?.contains('right-main')
  const titleEl = !isRightMain ? document.querySelector('.square-header') : null
  const titleH = titleEl ? titleEl.offsetHeight : 0
  const containerStyles = getComputedStyle(container)
  const containerPadTop = parseFloat(containerStyles.paddingTop || '0')
  const extra = isRightMain ? 12 : 24 // 右栏滚动仅保留轻量安全间距；内部容器稍多一些

  // 更稳健的滚动距离计算（推荐）：基于 getBoundingClientRect
  // 说明：
  // - offsetTop 受 offsetParent 影响，在多层嵌套或定位上下文复杂时可能产生误差；
  // - 使用元素与容器的可见位置差值 + 当前 scrollTop，可得到“容器坐标系”下的准确目标位置；
  // - 再减去顶栏/标题/容器内边距等偏移，确保目标卡片顶部不会被遮挡。
  const elRect = el.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  const visibleDelta = elRect.top - containerRect.top
  const offset = titleH + containerPadTop + extra
  const targetTop = Math.max(0, container.scrollTop + visibleDelta - offset)
  container.scrollTo({ top: targetTop, behavior: 'smooth' })

  // 更新活跃项并通知父组件以联动侧栏高亮
  activeId.value = id
  try{ emit('update:activeId', id) }catch{ /* 忽略异常以保证滚动稳定 */ }
}

// 滚动高亮：
// - 普通项：直接以自身 id 对应的内容锚点参与计算；
// - 合并项（有 aliasTargets）：其别名锚点（如 hot/recent）都映射为该项的 id，
//   因此在热门或最近附近滚动时，都会高亮“热门·最近”。
function handleScroll(){
  const container = getScrollContainer()
  if (!container) return

  // 计算滚动偏移（与 scrollTo 保持一致）：
  // - 当滚动容器为右栏 .right-main 时，仅保留轻量安全间距；
  // - 当为内部 .content-scroll 时，考虑标题区高度与容器内边距。
  const isRightMain = container.classList?.contains('right-main')
  const titleEl = !isRightMain ? document.querySelector('.square-header') : null
  const titleH = titleEl ? titleEl.offsetHeight : 0
  const containerStyles = getComputedStyle(container)
  const containerPadTop = parseFloat(containerStyles.paddingTop || '0')
  const offset = titleH + containerPadTop + (isRightMain ? 12 : 24)

  // 收集所有参与高亮判断的锚点：自身、子项与别名
  const nodes = []
  for (const s of sections){
    const elTop = (contentRef.value || container).querySelector('#' + s.id)
    if (elTop) nodes.push({ id: s.id, el: elTop })
    if (s.children && s.children.length){
      for (const c of s.children){
        const elChild = (contentRef.value || container).querySelector('#' + c.id)
        if (elChild) nodes.push({ id: c.id, el: elChild })
      }
    }
    if (Array.isArray(s.aliasTargets)){
      for (const a of s.aliasTargets){
        const elAlias = (contentRef.value || container).querySelector('#' + a)
        if (elAlias) nodes.push({ id: s.id, el: elAlias })
      }
    }
  }
  const validNodes = nodes.filter(x => x.el)

  // 基于可见位置差值（rect.top 差）计算距离，更鲁棒：
  // - offsetTop 受 offsetParent 影响，嵌套/定位复杂时容易不准；
  // - 使用 getBoundingClientRect，并与容器 rect.top 取差，再减去偏移，得到“贴顶程度”。
  const containerRect = container.getBoundingClientRect()
  let current = sections[0]?.id || 'hot-recent'
  let minDelta = Infinity
  for (const n of validNodes){
    const elRect = n.el.getBoundingClientRect()
    const visibleDelta = elRect.top - containerRect.top
    const delta = Math.abs(visibleDelta - offset)
    if (delta < minDelta){ minDelta = delta; current = n.id }
  }

  // 更新本组件状态并同步到父组件，以驱动左侧导航高亮
  activeId.value = current
  try { emit('update:activeId', current) } catch { /* 忽略异常，确保滚动流畅 */ }
}

onMounted(() => {
  const container = getScrollContainer()
  if (container){ 
    // 使用 requestAnimationFrame 节流滚动处理，降低频率，避免加载数据期间卡顿
    let ticking = false
    const onScroll = () => {
      if (ticking) return
      ticking = true
      window.requestAnimationFrame(() => {
        handleScroll()
        ticking = false
      })
    }
    // 始终监听容器滚动（页面级滚动已被两栏布局禁用）
    container.addEventListener('scroll', onScroll, { passive: true })

    // 初始化并监听窗口尺寸变化（如需）：顶部提示已移除，仅通过 scrollTo 精确控制偏移
    const updateAnchorOffset = () => {}
    updateAnchorOffset()
    // 刷新锚点缓存，避免滚动时频繁查询
    const refreshAnchors = () => { window.__squareAnchors = [] }
    // 初次渲染后以及数据可能到达后的时刻刷新一次
    setTimeout(refreshAnchors, 600)
    setTimeout(refreshAnchors, 1600)
    window.addEventListener('resize', updateAnchorOffset)
  }
})

// 暴露滚动方法给父组件（Square.vue）
// 说明：通过 defineExpose 暴露 scrollTo，父级可通过 ref 调用，
// 以确保侧栏点击后使用本组件的滚动定位逻辑（含别名锚点与偏移计算）。
defineExpose({ scrollTo })
</script>

<style scoped>
/* 导航容器宽度改由共享组件的 prop 控制（SideNav.innerWidth），此处不再覆盖 */
/* 顶部标题区（云便签 · 广场）
   说明：
   - 将高度改为“可配置”，通过 CSS 变量控制，便于按需微调；
   - 默认提供较为稳妥的最小高度 64px 与上下内边距 8px；
   - 滚动定位逻辑会动态读取实际高度（offsetHeight），不需额外修改。 */
.square-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  /* 可配置的最小高度：将 --square-header-height 设置为你希望的数值（如 72px/80px） */
  min-height: var(--square-header-height, 64px);
  /* 可配置的上下内边距：增加纵向空间时可以更柔和，不仅靠高度撑开 */
  padding: var(--square-header-padding-block, 8px) 0;
}
.brand { display: flex; align-items: center; gap: 8px; }
.brand h1 { font-size: 20px; margin: 0; }
.actions { display: flex; gap: 8px; }
.grid-two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; }
.layout { display:flex; gap:12px; align-items:flex-start; }
/* 让布局承载高度，保证内部 content-scroll 能成为唯一滚动容器
   说明：
   - 父容器（右侧正文）高度为 100%，需要子层级也按 100% 传递；
   - 设置 .container 为 100% 高度的纵向 Flex，.layout 占满剩余空间；
   - 解开最小高度约束（min-height: 0），避免子滚动容器因网格/父 Flex 默认最小高度导致无法滚动。 */
.container { display:flex; flex-direction: column; height: 100%; }
.layout { flex: 1; min-height: 0; }
/* 移除本组件内的侧栏样式：侧栏已抽离至父组件左列，避免样式重复或干扰 */
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
/* 右侧内容容器：移除 max-height 限制，允许随页面自然延展
   说明：
   - 之前设置为 max-height: 70vh，导致底部留出 30% 视口的空白；
   - 按“方案B”，移除该限制，使页面使用整页滚动，消除底栏上方的大间隙；
   - 保留 overflow:auto 以兼容某些宽高布局情况下容器仍可能产生内部滚动。 */
/* 右侧滚动切换到父容器 right-main：正文内部不再作为滚动容器 */
.content-scroll { flex:1; /* max-height: none */ overflow:visible; scroll-behavior:smooth; display:flex; flex-direction:column; gap:12px; }
/*
  隐藏右侧内容区滚动条（跨浏览器），但仍保留滚动功能。
  说明：
  - Firefox 与旧版 Edge/IE 通过设置 `scrollbar-width: none` 与 `-ms-overflow-style: none` 来隐藏滚动条；
  - WebKit 浏览器（Chrome/Safari）通过伪元素 `::-webkit-scrollbar` 隐藏滚动条；
  - 不影响鼠标滚轮、触摸板与触屏手势的滚动体验。
*/
/* 滚动条样式移除：由于滚动发生在父容器 right-main，内部容器无需滚动条样式 */
/* 移除卡片的 scroll-margin-top，避免 scrollIntoView 留白；
   现使用容器 scrollTo 精确偏移，确保目标卡片贴近顶部。 */
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
