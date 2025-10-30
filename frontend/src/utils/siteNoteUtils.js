// 站点便签工具函数集合：用于解析内容结构、提取链接与名称、严格标签过滤等。
// 说明：
// - 便签内容通常遵循统一格式：首行名称 / 中间介绍 / 倒数第二行 URL / 最后一行标签（忽略）；
// - 数据可能包含 `title`/`content`/`url`/`tags` 等字段；
// - 解析逻辑在“网站便签 / Git便签 / 其他同类站点便签”之间通用，可直接复用。

/**
 * 文本片段截断：用于简介回退场景，避免过长文本影响布局。
 */
export function snippet(text){
  const s = String(text || '').replace(/\s+/g, ' ').trim()
  return s.length > 80 ? s.slice(0, 80) + '…' : s
}

/**
 * 标题片段截断：用于从 content 提取回退标题时限制长度。
 */
export function snippetTitle(text){
  const s = String(text || '').replace(/\s+/g, ' ').trim()
  return s.length > 36 ? s.slice(0, 36) + '…' : s
}

/**
 * 从文本中提取第一个 URL。
 * 支持 http/https，简化处理常见分隔与尾随标点。
 */
export function extractFirstUrl(text){
  const s = String(text || '')
  const re = /(https?:\/\/[^\s)\]\u3002\uFF1B\uFF0C]+)/i
  const m = s.match(re)
  return m ? m[1] : ''
}

/**
 * 解析站点信息：从 content 按约定结构提取 name/desc/url。
 * - 第一行为网站名；
 * - 最后一行为 URL；
 * - 中间行为介绍（合并为一行）。
 * 若不完全遵循约定，仍尽力解析出合理信息。
 */
export function parseSiteInfoFromContent(text){
  const s = String(text || '').replace(/`/g, '').trim()
  if (!s) return { name: '', desc: '', url: '' }
  const lines = s.split(/\r?\n+/).map(l => l.trim()).filter(Boolean)
  const isTagsLine = (line) => {
    const t = String(line || '').trim()
    if (!t) return false
    if (/^(标签|tags?):?/i.test(t)) return true
    if (/^#\S+(?:\s+#\S+)*$/i.test(t)) return true
    return false
  }
  const urlRe = /https?:\/\/\S+/i
  let urlIdx = -1
  let url = ''
  for (let i = lines.length - 1; i >= 0; i--){
    const m = lines[i].match(urlRe)
    if (m){ urlIdx = i; url = m[0]; break }
  }
  let name = lines.length ? lines[0] : ''
  name = String(name).replace(/["'“”‘’《》「」\[\]\(\)]+/g, '').trim()
  let desc = ''
  if (urlIdx > 0){
    const descLines = lines.slice(1, urlIdx)
    desc = descLines.join(' ').trim()
  }else if (lines.length > 1){
    const lastIsTags = isTagsLine(lines[lines.length - 1])
    const descLines = lastIsTags ? lines.slice(1, lines.length - 1) : lines.slice(1)
    desc = descLines.join(' ').trim()
  }
  return { name, desc, url }
}

/**
 * 取得站点 URL：优先 it.url；否则从 it.content 中提取（优先末行 URL）。
 */
export function siteUrl(it){
  if (!it) return ''
  const direct = String(it.url || '').trim()
  if (direct) return direct
  const info = parseSiteInfoFromContent(it.content)
  if (info.url) return info.url
  return extractFirstUrl(it.content)
}

/**
 * 取得用于展示的站点名：
 * 1) 优先 title；
 * 2) 次级从 content 解析 name；
 * 3) 回退为 URL 的域名；
 * 4) 最后回退为内容片段。
 */
export function siteName(it){
  if (!it) return '未知站点'
  const t = String(it.title || '').trim()
  if (t) return t
  const info = parseSiteInfoFromContent(it?.content)
  if (info.name) return info.name
  const url = siteUrl(it)
  if (url){
    try{ const u = new URL(url); return u.hostname || url }catch{ return url }
  }
  return snippetTitle(it?.content)
}

/**
 * 取得网站介绍：优先 content 中间行合并；若无法解析，回退为空或内容全文（当无 URL 且非“标签行”）。
 */
export function siteDesc(it){
  if (!it) return ''
  const info = parseSiteInfoFromContent(it.content)
  let desc = String(info.desc || '').trim()
  if (!desc){
    const s = String(it.content || '').trim()
    const hasUrl = /https?:\/\/\S+/i.test(s)
    const isTagsLine = /^(标签|tags?):?/i.test(s) || /^#\S+(?:\s+#\S+)*$/i.test(s)
    if (s && !hasUrl && !isTagsLine){ desc = s }
  }
  return desc
}

/**
 * 归一化标签字段：统一移除开头 `#`，按常见分隔符拆分。
 */
export function normalizeTags(tags){
  if (Array.isArray(tags)) return tags.map(t => String(t || '').replace(/^#+/, '').trim()).filter(Boolean)
  const s = String(tags || '').trim()
  if (!s) return []
  return s.split(/[\s,，、;；]+/).map(x => String(x).replace(/^#+/, '').trim()).filter(Boolean)
}

/**
 * 从内容内联提取 #标签。
 */
export function extractTagsFromContent(text){
  const out = []
  const s = String(text || '')
  const re = /#([\p{L}\w-]+)/gu
  for (const m of s.matchAll(re)){
    const t = (m[1] || '').trim()
    if (t) out.push(t)
  }
  return out
}

/**
 * 严格标签匹配：判断便签是否包含指定标签（大小写敏感/不敏感可选）。
 * - 同时检查 `tags` 字段与 `content` 内联 #标签；
 * - `caseInsensitive=true` 时统一转小写后匹配。
 */
export function hasTag(note, tag, caseInsensitive = false){
  const fieldTags = normalizeTags(note?.tags)
  const contentTags = extractTagsFromContent(note?.content || '')
  let all = [...fieldTags, ...contentTags].map(t => String(t || '').trim()).filter(Boolean)
  let target = String(tag || '').trim()
  if (caseInsensitive){
    all = all.map(t => t.toLowerCase())
    target = target.toLowerCase()
  }
  return all.some(t => t === target)
}

/**
 * 列表项点击：统一站点跳转逻辑（优先解析末行 URL，回退 it.url）。
 */
export function openSite(note){
  const url = siteUrl(note)
  if (url){ window.open(url, '_blank', 'noopener') }
}