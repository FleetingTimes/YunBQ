<template>
  <div>
    <!-- 顶部弹幕流：统一速度 + 数量上限，避免搜索首屏过于拥挤 -->
    <DanmuWall
      :items="notes"
      :rows="danmuRows"
      :speed-scale="danmuSpeedScale"
      :highlight-id="danmuHighlightId"
      :same-speed="true"
      :uniform-duration="16"
      :max-visible="danmuRows * 3"
      @itemClick="toggleLikeById"
    />

    <div class="grid" v-if="props.showComposer">
      <div class="sticky composer p-2 rot-2">
        <!-- 文案重命名：将“便签”统一改为“拾言” -->
        <div class="title">新建拾言</div>
        <el-input v-model="draft.tags" placeholder="标签（用逗号分隔）" style="margin-bottom:6px;" />
        <el-input
          v-model="draft.content"
          type="textarea"
          :rows="4"
          placeholder="内容"
          @focus="onComposerFocus"
          @blur="onComposerBlur"
        />
        <div style="display:flex; align-items:center; justify-content:space-between; margin-top:6px; gap:8px;">
          <el-switch v-model="draft.isPublic" active-text="公开" inactive-text="私有" />
          <div style="display:flex; align-items:center; gap:6px;">
            <span style="font-size:12px;color:#606266;">颜色</span>
            <el-color-picker v-model="draft.color" size="small" />
          </div>
          <div class="auth-actions" style="justify-content:flex-end;">
            <el-button type="primary" @click="create">添加</el-button>
          </div>
        </div>
      </div>
    </div>

    <!--
      计数标签说明：
      - 此处显示的“条数”仅代表本组件（顶部弹幕区域）当前展示的条目数量，
        并非搜索结果的总条数。完整的分页结果由下方时间线列表负责加载与展示。
      - 为避免在搜索页产生“总数仅 20”之类的误解，这里提供开关与可定制前缀文案：
        props.showCountTag（默认 true）与 props.countLabel（默认“共”）。
    -->
    <div class="footer" v-if="props.showCountTag">
      <el-tag type="info">{{ props.countLabel }} {{ notes.length }} 条</el-tag>
    </div>
  </div>
</template>

<!--
  NotesBody 组件（添加拾言主体）
  职责：
  - 顶部草稿编辑器：内容输入、标签、可见性与颜色选择；
  - 弹幕流展示：rows/speed-scale 控制，支持新发布高亮；
  - 列表区域：按年份分组与时间线展示，卡片复用 NoteCard。
  数据与接口：
  - 创建：POST /shiyan；查询：GET /shiyan?q&page&size；
  - 更新/删除：PUT/DELETE /shiyan/{id}；
  - 喜欢/收藏：POST /shiyan/{id}/like|unlike 与 /favorite|unfavorite；
  - 字段兼容与映射：normalize 统一后端不同命名（content/text、like_count/liked 等）。
  细节与修复：
  - 表情粘贴修复：将聊天应用的图片表情映射为 Unicode Emoji（emojiMap）；
  - 触底加载与“加载更多”按钮并存，防止并发请求；
  可访问性与安全：
  - 公私有切换通过 isPublic 字段；
  - 接口错误统一提示；编辑器聚焦状态明确（onComposerFocus/Blur）。
-->
<script setup>
import { reactive, ref, onMounted, computed, watch, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/http'
import { ElMessage } from 'element-plus'
import DanmuWall from '@/components/DanmuWall.vue'

// Props 恢复为原始定义：仅保留 query 与 showComposer
// 说明：添加便签页侧边栏不再进行标签快捷填充，因此移除 quickTags。
// 组件入参：
// - query：搜索关键词
// - showComposer：是否显示顶部创建入口
// - showCountTag：是否显示底部计数标签（默认 true）。在搜索页为了避免误导可设为 false。
// - countLabel：计数标签前缀文案（默认“共”），也可改为“首屏展示”等。
const props = defineProps({
  query: { type: String, default: '' },
  showComposer: { type: Boolean, default: true },
  showCountTag: { type: Boolean, default: true },
  countLabel: { type: String, default: '共' }
})

const router = useRouter()
const notes = ref([])
const justCreatedId = ref(null)
const justCreatedFirst = ref(false)
const danmuHighlightId = ref(null)

/**
 * 移动端断点检测（≤640px）与弹幕参数响应式
 * 说明：手机屏幕较窄，减少弹幕行数与同时可见总数，避免过于拥挤影响输入与阅读。
 */
const isMobile = ref(false)
function updateIsMobile(){
  try{ isMobile.value = (window.innerWidth || 0) <= 640 }catch{ isMobile.value = false }
}
onMounted(() => { updateIsMobile(); window.addEventListener('resize', updateIsMobile) })
onUnmounted(() => { try{ window.removeEventListener('resize', updateIsMobile) }catch{} })
const danmuRows = computed(() => isMobile.value ? 3 : 6)
const danmuSpeedScale = 1.35

const draft = reactive({ content: '', isPublic: false, tags: '', color: '#ffd966' })
const composerRef = ref(null)

// —— 粘贴修复：聊天应用的图片表情转换为 Unicode Emoji ——
const focusedComposer = ref(false)
function onComposerFocus(){ focusedComposer.value = true }
function onComposerBlur(){ focusedComposer.value = false }

// 常见/热门映射：英文数据名与中文别名到 Unicode Emoji
const emojiMap = {
  // 经典笑脸
  smile: '😊', happy: '😄', grin: '😁', laugh: '😆', joy: '😂', wink: '😉', blush: '😊', smirk: '😏',
  neutral_face: '😐', expressionless: '😑', unamused: '😒', relieved: '😌',
  surprised: '😮', astonished: '😲', scream: '😱',
  sad: '☹️', crying: '😢', sob: '😭', weary: '😩', tired: '😫', disappointed: '😞',
  angry: '😠', rage: '🤬', confounded: '😖',
  thinking: '🤔', facepalm: '🤦', shushing_face: '🤫', lying_face: '🤥', zipper_mouth: '🤐',
  // 爱心/庆祝
  heart: '❤️', hearts: '💕', heart_eyes: '😍', kiss: '😘', kissing_heart: '😘',
  broken_heart: '💔', two_hearts: '💕', sparkling_heart: '💖',
  sparkles: '✨', star: '⭐', stars: '🌟', party_popper: '🎉', tada: '🎉', gift: '🎁', balloon: '🎈', ribbon: '🎀', confetti_ball: '🎊',
  // 手势
  thumbs_up: '👍', thumbsup: '👍', like: '👍', thumbs_down: '👎', clap: '👏', pray: '🙏',
  ok_hand: '👌', victory_hand: '✌️', v: '✌️', wave: '👋', raised_hand: '✋', fist: '✊', rock: '🤘', handshake: '🤝',
  // 自然/植物
  tulip: '🌷', rose: '🌹', cherry_blossom: '🌸', sunflower: '🌻', hibiscus: '🌺', bouquet: '💐',
  sun: '☀️', moon: '🌙', cloud: '☁️', fire: '🔥', rainbow: '🌈', leaf: '🍃', butterfly: '🦋',
  // 其它常用图标
  dog: '🐶', cat: '🐱', coffee: '☕', cake: '🍰', beer: '🍺', camera: '📷', music: '🎵', book: '📚', pencil: '✏️', check: '✔️', cross: '❌', warning: '⚠️', info: 'ℹ️', question: '❓', exclamation: '❗', rocket: '🚀',
  // 中文别名（微信/QQ/贴吧等常见）
  '微笑': '😊', '开心': '😊', '大笑': '😄', '坏笑': '😏', '笑哭': '😂', '眨眼': '😉', '捂脸': '🤦', '尴尬': '😬', '害羞': '☺️',
  '可爱': '😊', '酷': '😎', '思考': '🤔', '惊讶': '😲', '震惊': '😱', '难过': '☹️', '大哭': '😭', '委屈': '😢', '无语': '😑', '闭嘴': '🤐',
  '心': '❤️', '爱心': '❤️', '红心': '❤️', '心碎': '💔', '比心': '💕', '星星': '⭐', '闪耀': '✨',
  '点赞': '👍', '赞': '👍', '不赞': '👎', '鼓掌': '👏', '祈祷': '🙏', '握手': '🤝', '再见': '👋', '耶': '✌️', 'ok': '👌',
  '礼物': '🎁', '庆祝': '🎉', '气球': '🎈', '太阳': '☀️', '月亮': '🌙', '彩虹': '🌈', '叶子': '🍃', '蝴蝶': '🦋',
  // 网络常见别名
  'doge': '🐶', '泪目': '😭', '摸鱼': '🐟', '燃': '🔥', '真棒': '👍', '牛': '🐮'
}

function htmlToTextWithEmoji(html){
  try{
    const div = document.createElement('div')
    div.innerHTML = html
    div.querySelectorAll('img').forEach(img => {
      const alt = img.getAttribute('alt') || ''
      const title = img.getAttribute('title') || ''
      const aria = img.getAttribute('aria-label') || ''
      const dataEmoji = img.getAttribute('data-emoji') || img.getAttribute('data-name') || ''
      let rep = ''
      const cand = [alt, title, aria, dataEmoji].map(s => String(s).replace(/[\[\]]/g,'').trim()).filter(Boolean)
      for (const c of cand){
        if (/[\u2600-\u27BF\uD83C-\uDBFF\uDC00-\uDFFF]/.test(c)) { rep = c; break }
        if (emojiMap[c]) { rep = emojiMap[c]; break }
      }
      const span = document.createElement('span')
      span.textContent = rep || ''
      img.replaceWith(span)
    })
    div.querySelectorAll('script,style').forEach(el => el.remove())
    return div.textContent || div.innerText || ''
  }catch{ return '' }
}

function handlePaste(e){
  try{
    if (!focusedComposer.value) return
    const target = e.target
    const root = composerRef.value || document.querySelector('.composer')
    if (!root || !root.contains(target)) return
    const cb = e.clipboardData || window.clipboardData
    if (!cb) return
    const html = cb.getData?.('text/html') || ''
    if (!html) return
    const converted = htmlToTextWithEmoji(html)
    if (!converted) return
    e.preventDefault()
    const ta = root.querySelector('textarea')
    if (!ta) return
    const start = ta.selectionStart ?? ta.value.length
    const end = ta.selectionEnd ?? start
    const before = ta.value.slice(0, start)
    const after = ta.value.slice(end)
    const ins = converted
    ta.value = `${before}${ins}${after}`
    const pos = before.length + ins.length
    ta.setSelectionRange(pos, pos)
    ta.dispatchEvent(new Event('input', { bubbles: true }))
    draft.content = ta.value
  }catch{}
}

onMounted(async () => {
  document.addEventListener('paste', handlePaste, true)
  await nextTick();
  composerRef.value = document.querySelector('.composer')
})
onUnmounted(() => {
  document.removeEventListener('paste', handlePaste, true)
})

onMounted(() => { load() })
watch(() => props.query, () => { load() })

async function load(){
  try{
    // 路径切换：统一使用 /shiyan 搜索拾言（参数语义保持一致）
    // 修复：默认仅返回 10 条（后端默认 size=10），这里显式传入 size=20，并排除归档项以提升结果质量。
    // 说明：顶栏搜索结果页顶部弹幕区域仅做“首屏展示”，因此不做分页；
    //       若需要更多数据，页面下方的“时间线列表”具备服务端分页与无限滚动能力。
    const { data } = await http.get('/shiyan', { params: { q: props.query, page: 1, size: 20, archived: false }, suppress401Redirect: true })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    notes.value = (items || []).map(it => ({
      ...it,
      isPublic: it.isPublic ?? it.is_public ?? false,
      likeCount: Number(it.likeCount ?? it.like_count ?? 0),
      liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    }))
    if (justCreatedId.value != null || justCreatedFirst.value) {
      let targetId = justCreatedId.value
      if (!targetId && notes.value.length > 0) targetId = notes.value[0].id
      danmuHighlightId.value = targetId
      justCreatedId.value = null
      justCreatedFirst.value = false
    }
  }catch(e){
  // 文案重命名：将“便签”统一改为“拾言”
  ElMessage.error('加载拾言失败')
  }
}

async function toggleLike(n){
  if (n.likeLoading) return
  n.likeLoading = true
  try{
    // 路径切换：统一使用 /shiyan/{id}/like|unlike
    const url = n.liked ? `/shiyan/${n.id}/unlike` : `/shiyan/${n.id}/like`
    const { data } = await http.post(url)
    n.likeCount = Number(data?.count ?? data?.like_count ?? (n.likeCount || 0))
    n.liked = Boolean((data?.likedByMe ?? data?.liked_by_me ?? n.liked))
  }catch(e){
    ElMessage.error('操作失败')
  }finally{
    n.likeLoading = false
  }
}

async function archive(n){
  try{
    // 路径切换：统一使用 /shiyan/{id}/archive
    await http.post(`/shiyan/${n.id}/archive`, { archived: !n.archived })
    ElMessage.success('已更新归档状态')
    load()
  }catch(e){
    ElMessage.error('更新归档失败')
  }
}

async function remove(n){
  try{
    // 路径切换：统一使用 /shiyan/{id}
    await http.delete(`/shiyan/${n.id}`)
    ElMessage.success('已删除')
    load()
  }catch(e){
    ElMessage.error('删除失败')
  }
}

async function create(){
  if (!draft.content) { ElMessage.warning('请填写内容'); return }
  try{
    // 说明：后端 DTO（NoteRequest.java）字段为 camelCase 的 isPublic，
    // 若使用 is_public（snake_case）将无法被 Jackson 默认命名策略绑定，导致后端取值为 null，
    // 进而在服务层 Boolean.TRUE.equals(req.getIsPublic()) 为 false，最终保存为“私有”。
    // 因此此处改为 isPublic，确保后端正确接收“公开/私有”选择。
    const payload = {
      content: draft.content,
      isPublic: draft.isPublic,
      tags: (draft.tags || '').trim(),
      color: (draft.color || '').trim()
    }
    // 路径切换：创建统一使用 /shiyan
    const { data } = await http.post('/shiyan', payload)
    const createdId = data?.id ?? data?.note?.id ?? data?.data?.id ?? null
    if (createdId) justCreatedId.value = createdId; else justCreatedFirst.value = true
    ElMessage.success('已添加')
    draft.content = ''; draft.tags = ''; draft.color = '#ffd966'
    draft.isPublic = false
    load()
  }catch(e){
    const status = e?.response?.status
    if (status === 401){
      ElMessage.error('未登录，请先登录')
      router.replace('/')
    } else if (status === 403){
      ElMessage.error('无权限，请检查登录状态或稍后重试')
    } else {
      ElMessage.error(e?.response?.data?.message || e?.message || '添加失败')
    }
  }
}

async function togglePublic(n){
  try{
    const tagsStr = Array.isArray(n.tags) ? n.tags.join(',') : (n.tags || '')
    const currentPublic = (n.isPublic ?? n.is_public ?? false)
    // 说明：更新请求同样使用 isPublic（camelCase）与后端 DTO 保持一致，避免因 is_public 未绑定导致始终保存为“私有”。
    const payload = {
      content: n.content,
      tags: tagsStr,
      archived: n.archived,
      isPublic: !currentPublic,
      color: (n.color || '').trim()
    }
    // 路径切换：更新统一使用 /shiyan/{id}
    await http.put(`/shiyan/${n.id}`, payload)
    ElMessage.success('已更新可见性')
    load()
  }catch(e){
    ElMessage.error('更新可见性失败')
  }
}

function parsedTags(tags){
  if (Array.isArray(tags)) return tags
  if (typeof tags === 'string') return tags.split(',').map(t => t.trim()).filter(Boolean)
  return []
}
function toggleLikeById(id){
  const n = notes.value.find(x => x.id === id)
  if (!n) return
  if (n.likeLoading === undefined) n.likeLoading = false
  toggleLike(n)
}
</script>

<style scoped>
</style>