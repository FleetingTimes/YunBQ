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
        <!-- 添加拾言：替换原“标题栏”，以玻璃卡片风格呈现新增表单 -->
        <section class="composer-card" aria-label="添加拾言">
          <div class="composer-brand">
            <!-- 标题：改为“添加拾言”，图标与站点风格一致 -->
            <img src="https://api.iconify.design/mdi/square-edit-outline.svg?color=%23303133" alt="edit" width="22" height="22" />
            <h2>添加拾言</h2>
          </div>
          <div class="composer-body">
            <!-- 内容输入：多行文本，保持与站点整体字体与间距一致 -->
            <el-input
              v-model="composer.content"
              type="textarea"
              :rows="4"
              placeholder="写下一句触动心灵的话…（最后一行用 #标签1 #标签2 标注标签）"
              maxlength="500"
              show-word-limit
              @focus="onComposerFocus"
              @blur="onComposerBlur"
            />
            <!-- 操作行：右对齐；公开/私有下拉置于发布按钮之前 -->
            <div class="composer-actions">
              <div class="visibility-select">
                <span class="label">可见性</span>
                <!-- 下拉选择：公开 / 私有；发布前设置，提交时转换为布尔 isPublic -->
                <el-select v-model="composer.visibility" size="small" style="width: 120px">
                  <el-option label="公开" value="public" />
                  <el-option label="私有" value="private" />
                </el-select>
              </div>
              <el-button type="primary" :loading="composer.loading" @click="createShiyan" aria-label="发布拾言">发布</el-button>
              <el-button type="default" @click="resetComposer" aria-label="清空草稿">清空</el-button>
            </div>
          </div>
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
                <!-- 头像：可点击跳转到该作者的拾言页；懒加载与失败兜底均保留 -->
                <img
                  v-if="it.authorAvatarUrl"
                  class="avatar clickable"
                  :src="avatarFullUrl(it.authorAvatarUrl)"
                  alt="avatar"
                  loading="lazy"
                  @error="onAvatarError"
                  @click="goToUserNotes(it)"
                />
                <img
                  v-else
                  class="avatar clickable"
                  :src="defaultAvatar"
                  alt="avatar"
                  loading="lazy"
                  @click="goToUserNotes(it)"
                />

              <div class="meta">
                <div class="name" :title="it.authorName">{{ it.authorName || '匿名' }}</div>
                <div class="time">{{ formatTime(it.createdAt || it.updatedAt) }}</div>
              </div>
              <!-- 我的拾言操作入口：若该拾言属于当前登录用户，则显示右侧“v”按钮，点击弹出编辑/删除菜单 -->
              <div class="owner-ops" v-if="isMyNote(it)">
                <el-dropdown trigger="click" @command="onOwnerCommand(it, $event)">
                  <!-- 更换为三点菜单图标，低干扰但可点击 -->
                  <img class="owner-menu" src="https://api.iconify.design/mdi/dots-vertical.svg?color=%23606366" alt="menu" title="我的拾言菜单" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="delete">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>

              <!-- 内容：支持长文，做多行裁剪与换行优化 -->
              <!-- 非编辑态：直接展示拾言内容；编辑态：展示可编辑文本域与保存/取消按钮 -->
              <template v-if="!isEditing(it.id)">
                <div class="content" v-text="it.content || ''"></div>
              </template>
              <template v-else>
                <div class="content edit-mode">
                  <!-- 文本编辑框：绑定当前卡片的草稿内容，限制长度以避免超长提交。
                       标签规则与“添加拾言”一致：最后一行以“#”开头视为标签行（#标签1 #标签2）。 -->
                  <el-input
                    v-model="editDraft[it.id]"
                    type="textarea"
                    :rows="4"
                    maxlength="500"
                    show-word-limit
                    placeholder="修改拾言内容…（最后一行用 #标签1 #标签2 标注标签）"
                    @focus="onEditFocus(it)"
                    @blur="onEditBlur(it)"
                  />
                  <!-- 底部控制行：左侧可见性选择，右侧保存/取消按钮 -->
                  <div class="edit-controls">
                    <div class="visibility-select">
                      <span class="label">可见性</span>
                      <el-select v-model="editVisibility[it.id]" size="small" style="width: 120px">
                        <el-option label="公开" value="public" />
                        <el-option label="私有" value="private" />
                      </el-select>
                    </div>
                    <div class="edit-actions">
                      <el-button type="primary" size="small" :loading="saveLoading[it.id]" @click="saveEdit(it)">保存</el-button>
                      <el-button size="small" @click="cancelEdit(it)">取消</el-button>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 标签与操作行：同一行展示，标签在左，喜欢/收藏在右侧。
                   修复：当没有标签时，仅有“actions”一个子元素，flex 容器的默认 space-between 会使其靠左。
                   方案：根据是否存在标签动态追加类名 no-tags，当无标签时将容器的对齐改为 flex-end，使操作按钮保持右侧对齐。 -->
              <div class="tags-actions-row" :class="{ 'no-tags': !hasTags(it) }">
                <div class="tags" v-if="hasTags(it)">
                  <span class="tag" v-for="tg in normalizeTags(it.tags)" :key="tg">#{{ tg }}</span>
                </div>
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
            </div>

            <!-- 加载更多状态区 -->
            <div class="load-more" v-if="loading">正在加载更多…</div>
            <div class="end-tip" v-if="!loading && done && items.length>0">已到达尽头</div>
            <div class="empty" v-if="!loading && items.length===0">暂无拾言</div>
          </template>

          <!-- 无限滚动锚点：进入视窗触发下一页加载 -->
          <div ref="moreSentinel" class="sentinel" aria-hidden="true"></div>
        </section>
        <!-- 回到顶部：绑定右侧滚动容器，采用玻璃拟态风格与平滑滚动；
             当滚动超过设定偏移后自动出现，点击平滑返回容器顶部。 -->
        <BackToTop :target="scrollRootEl" />
      </div>
    </template>
  </TwoPaneLayout>
</template>

<script setup>
// 组件加载策略：按需异步加载以优化首屏资源体积。
// 保留 query 状态与 onSearch 处理，便于后续扩展在本页接入搜索联动模块。
// 新增：分页加载与无限滚动，实现性能友好的列表渲染。
import { ref, defineAsyncComponent, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, avatarFullUrl } from '@/api/http'
import defaultAvatar from '@/assets/default-avatar.svg'
import { getToken } from '@/utils/auth'
const TwoPaneLayout = defineAsyncComponent(() => import('@/components/TwoPaneLayout.vue'))
const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))
// 回到顶部组件：按需异步加载，保持首屏资源体积友好
const BackToTop = defineAsyncComponent(() => import('@/components/BackToTop.vue'))
import { useRouter } from 'vue-router'

// 路由：用于从头像点击跳转到用户拾言页
const router = useRouter()

const query = ref('')
function onSearch(q){ query.value = q || '' }
// —— 粘贴修复：将来源于聊天应用的“图片表情/贴纸”转换为 Unicode Emoji ——
// 场景说明：部分聊天应用（微信/QQ/社交平台）在消息中使用 <img> 标签承载表情，复制到剪贴板时携带 HTML。
// 默认粘贴到 textarea 仅保留纯文本，<img> 会丢失，导致“表情不可复制”。
// 方案：在捕获阶段监听 document 的 paste 事件，若目标位于本页输入框，解析剪贴板中的 HTML，
//       将 <img ... alt="🌷"> 或 data-emoji/title/aria-label 等属性转换为可插入的 Unicode 字符；
//       同时兼容中文表情别名（如 [玫瑰] → 🌹、[鼓掌] → 👏）。
const focusedComposer = ref(false)        // 是否当前聚焦“添加拾言”输入框
const focusedEditingId = ref(null)        // 若处于编辑态，指向当前编辑的拾言 id
function onComposerFocus(){ focusedComposer.value = true }
function onComposerBlur(){ focusedComposer.value = false }
function onEditFocus(it){ focusedEditingId.value = it?.id ?? null }
function onEditBlur(){ focusedEditingId.value = null }

// 常见映射：data-emoji 或中文别名到 Unicode Emoji；根据需要可继续扩充
// 扩充热门表情覆盖：笑脸、手势、爱心、庆祝、自然、网络常见别名等。
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

// 从剪贴板 HTML中提取文本并将 <img> 表情转换为 Unicode
function htmlToTextWithEmoji(html){
  try{
    const div = document.createElement('div')
    div.innerHTML = html
    // 将所有 <img> 替换为其 alt/title/aria-label 或 data-emoji 的映射字符
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
    // 处理可能的 <span class="emoji">直接文本
    div.querySelectorAll('span.emoji, i.emoji').forEach(el => {
      const t = el.textContent || el.getAttribute('title') || el.getAttribute('aria-label') || ''
      const clean = String(t).trim()
      el.textContent = emojiMap[clean] || clean
    })
    return div.textContent || ''
  }catch{ return '' }
}

// 统一粘贴处理：将 HTML 表情转换后插入到当前输入框的光标位置
function handlePaste(e){
  try{
    const target = e?.target
    const isTextarea = target && target.classList && target.classList.contains('el-textarea__inner')
    if (!isTextarea) return
    const html = e.clipboardData?.getData('text/html') || ''
    const plain = e.clipboardData?.getData('text/plain') || ''
    const enriched = html && html.includes('<img') ? htmlToTextWithEmoji(html) : plain
    if (!enriched) return
    e.preventDefault()
    const start = target.selectionStart ?? 0
    const end = target.selectionEnd ?? start
    if (focusedComposer.value){
      const old = String(composer.value.content || '')
      composer.value.content = old.slice(0, start) + enriched + old.slice(end)
      setTimeout(() => { try{ target.selectionStart = target.selectionEnd = start + enriched.length }catch{} }, 0)
      return
    }
    if (focusedEditingId.value != null){
      const id = focusedEditingId.value
      const old = String(editDraft.value[id] || '')
      editDraft.value[id] = old.slice(0, start) + enriched + old.slice(end)
      setTimeout(() => { try{ target.selectionStart = target.selectionEnd = start + enriched.length }catch{} }, 0)
      return
    }
  }catch{}
}
onMounted(() => { try{ document.addEventListener('paste', handlePaste, true) }catch{} })
onUnmounted(() => { try{ document.removeEventListener('paste', handlePaste, true) }catch{} })

// —— 添加拾言草稿与发布逻辑 ——
// 说明：与 NotesBody.vue 的创建保持一致，后端 DTO 使用 camelCase 的 isPublic；
// 若误用 is_public（snake_case）将导致服务端默认保存为“私有”。因此此处严格使用 isPublic。
// 变更后的草稿状态：移除 tags 与 color，改为 visibility 下拉（public/private）
const composer = ref({ content: '', visibility: 'public', loading: false })

// 从草稿内容中解析标签与正文：
// 规则：若最后一个非空行以“#”开头，则该行为标签行；以“#”分隔多个标签（允许使用空格/逗号/中文逗号分隔）。
// 返回清理后的正文 contentClean 与逗号分隔的标签 tagsStr。
function extractTagsAndContentFromDraft(raw){
  try{
    const text = String(raw || '')
    const lines = text.split(/\r?\n/)
    // 找到最后一个非空行
    let lastIdx = lines.length - 1
    while (lastIdx >= 0 && !String(lines[lastIdx]).trim()) lastIdx--
    let tagsStr = ''
    let contentClean = text
    if (lastIdx >= 0){
      const lastLine = String(lines[lastIdx]).trim()
      if (lastLine.startsWith('#')){
        // 解析标签：支持 “#标签1 #标签2” 或 “#标签1,标签2” 等形式
        const tagLine = lastLine.replace(/^#+\s*/, '')
        const parts = tagLine.split(/[#\s,，、]+/).map(s => s.trim()).filter(Boolean)
        // 去重并拼接为逗号分隔字符串
        const uniq = Array.from(new Set(parts))
        tagsStr = uniq.join(',')
        // 从正文中移除该标签行
        const before = lines.slice(0, lastIdx).join('\n')
        const after = lines.slice(lastIdx + 1).join('\n')
        contentClean = (before + (after ? ('\n' + after) : '')).trim()
      }
    }
    return { contentClean, tagsStr }
  }catch{ return { contentClean: String(raw || ''), tagsStr: '' } }
}
async function createShiyan(){
  // 登录校验：添加拾言需要登录
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  if (!composer.value.content || !composer.value.content.trim()) { ElMessage.warning('请填写内容'); return }
  if (composer.value.loading) return
  composer.value.loading = true
  try{
    // 从内容解析标签（最后一行以“#”区分），并转换下拉选择为布尔 isPublic
    const { contentClean, tagsStr } = extractTagsAndContentFromDraft(composer.value.content)
    const payload = {
      content: contentClean,
      isPublic: composer.value.visibility === 'public',
      tags: tagsStr
    }
    const { data } = await http.post('/shiyan', payload)
    // 成功后提示，并重置草稿；刷新列表（优先重置分页并拉取最新公开拾言）
    ElMessage.success('已添加')
    resetComposer()
    resetFeedAndReload()
  }catch(e){
    const status = e?.response?.status
    if (status === 401){ ElMessage.error('未登录，请先登录') }
    else if (status === 403){ ElMessage.error('无权限，请检查登录状态或稍后重试') }
    else { ElMessage.error(e?.response?.data?.message || e?.message || '添加失败') }
  }finally{ composer.value.loading = false }
}
function resetComposer(){ composer.value.content = ''; composer.value.visibility = 'public' }
function resetFeedAndReload(){ items.value = []; page.value = 1; done.value = false; initialLoading.value = true; fetchPage() }

// —— 列表与分页状态 ——
const items = ref([])               // 已加载的拾言数据（累积）
const page = ref(1)                 // 当前页（从 1 开始）
const size = ref(20)                // 每页数量（适度控制 DOM 数量）
const loading = ref(false)          // 是否正在加载（用于“加载更多”状态与节流）
const initialLoading = ref(true)    // 首屏骨架占位控制
const done = ref(false)             // 是否已加载完所有数据
const moreSentinel = ref(null)      // 无限滚动锚点引用
let io = null                       // IntersectionObserver 实例
// 右侧滚动容器引用：用于 BackToTop 的定位与滚动绑定（相对该容器出现/隐藏并滚动至顶部）
const scrollRootEl = ref(null)

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

// 是否存在有效标签：供模板层动态控制布局（无标签时将操作区右对齐）
function hasTags(it){
  try{
    const arr = normalizeTags(it?.tags ?? '')
    return Array.isArray(arr) && arr.length > 0
  }catch{ return false }
}

// —— 当前登录用户与“我的拾言”判定 ——
// 说明：前端通过本地存储的 JWT token 解码获取 uid；
//       仅用于前端 UI 判定（不做服务端身份校验），服务端会在更新/删除接口再次校验权限。
const myUid = ref(undefined)
function tryGetUidFromToken(){
  try{
    const t = getToken()
    if (!t) return undefined
    const parts = String(t).split('.')
    if (parts.length < 2) return undefined
    // Base64URL 解码 JWT payload，读取 uid 字段（服务端在生成 token 时写入）
    const b64 = parts[1].replace(/-/g,'+').replace(/_/g,'/')
    const json = atob(b64)
    const payload = JSON.parse(json)
    const uid = Number(payload?.uid)
    return Number.isFinite(uid) ? uid : undefined
  }catch{ return undefined }
}
function isMyNote(it){
  try{
    const uid = myUid.value
    const noteUid = Number(it?.userId ?? it?.user_id)
    return Number.isFinite(uid) && Number.isFinite(noteUid) && uid === noteUid
  }catch{ return false }
}

// —— 内联编辑与删除：状态与事件 ——
// 说明：为每条拾言维护独立的编辑状态与草稿内容，避免多个卡片互相干扰。
const editing = ref({})     // { [id]: boolean }
const editDraft = ref({})   // { [id]: string }
const saveLoading = ref({}) // { [id]: boolean }
const editVisibility = ref({}) // { [id]: 'public' | 'private' }
function isEditing(id){ return Boolean(editing.value[id]) }
function onOwnerCommand(it, cmd){ if (cmd === 'edit') startEdit(it); else if (cmd === 'delete') confirmDelete(it) }
function startEdit(it){
  // 进入编辑态：初始化草稿为当前内容，并设置可见性选择（与“添加拾言”的下拉一致）
  editing.value[it.id] = true
  // 草稿预填充：将现有标签行拼接为最后一行（格式：#标签1 #标签2），便于用户直接编辑
  const base = String(it.content || '')
  const arr = normalizeTags(it?.tags ?? '')
  const tagLine = arr.length ? ('\n#' + arr.join(' #')) : ''
  editDraft.value[it.id] = (base + tagLine)
  editVisibility.value[it.id] = Boolean(it.isPublic) ? 'public' : 'private'
}
function cancelEdit(it){ editing.value[it.id] = false; editDraft.value[it.id] = '' }
async function saveEdit(it){
  // 登录校验：更新拾言需要登录
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  if (saveLoading.value[it.id]) return
  const newText = String(editDraft.value[it.id] || '').trim()
  if (!newText){ ElMessage.warning('内容不能为空'); return }
  saveLoading.value[it.id] = true
  try{
    // 与“添加拾言”保持一致的标签解析：最后一行以“#”开头视为标签行
    const { contentClean, tagsStr } = extractTagsAndContentFromDraft(newText)
    const payload = {
      content: contentClean,
      tags: tagsStr,
      isPublic: (editVisibility.value[it.id] === 'public')
    }
    await http.put(`/shiyan/${it.id}`, payload)
    // 乐观更新本地数据并提示
    it.content = contentClean
    it.tags = tagsStr
    it.isPublic = (editVisibility.value[it.id] === 'public')
    it.updatedAt = Date.now()
    ElMessage.success('已更新')
    cancelEdit(it)
  }catch(e){
    const status = e?.response?.status
    if (status === 401){ ElMessage.error('未登录，请先登录') }
    else if (status === 403){ ElMessage.error('无权限编辑该拾言') }
    else { ElMessage.error(e?.response?.data?.message || e?.message || '更新失败') }
  }finally{ saveLoading.value[it.id] = false }
}
async function confirmDelete(it){
  // 登录校验：删除拾言需要登录
  if (!getToken()) { ElMessage.warning('请先登录'); return }
  try{
    await ElMessageBox.confirm('确定删除这条拾言吗？删除后不可恢复。', '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  }catch{ return }
  try{
    await http.delete(`/shiyan/${it.id}`)
    items.value = items.value.filter(x => x.id !== it.id)
    ElMessage.success('已删除')
  }catch(e){
    const status = e?.response?.status
    if (status === 401){ ElMessage.error('未登录，请先登录') }
    else if (status === 403){ ElMessage.error('无权限删除该拾言') }
    else { ElMessage.error(e?.response?.data?.message || e?.message || '删除失败') }
  }
}

// —— 数据映射：统一字段，容错后端命名差异 ——
function mapNoteItem(it){
  return {
    id: it.id,
    userId: it.userId ?? it.user_id ?? '',
    authorName: it.authorName ?? it.author_name ?? (it.user?.nickname ?? it.user?.username) ?? '匿名',
    // 新增：作者用户名（用于路由跳转），增强兼容性
    authorUsername: it.authorUsername ?? it.author_username ?? (it.user?.username ?? it.author?.username ?? ''),
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
    liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    favorited: Boolean(it.favorited ?? it.favoritedByMe ?? it.favorited_by_me ?? false),
  }
}

// —— 拉取一页数据（性能：节流与错误容忍） ——
async function fetchPage(){
  if (loading.value || done.value) return
  loading.value = true
  try{
    // 仅展示公开拾言：尽量通过参数告知后端；若后端无此参数也会在前端过滤
    // 修正参数：后端识别 isPublic，而非 publicOnly；同时排除归档项
    const { data } = await http.get('/shiyan', { params: { page: page.value, size: size.value, isPublic: true, archived: false } })
    const raw = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    let list = Array.isArray(raw) ? raw.map(mapNoteItem) : []
    // 注意：后端已按 isPublic=true 过滤，这里不再二次过滤，避免出现“第二页仅含私有而被前端过滤为空”的问题。
    if (raw.length === 0){ done.value = true }
    items.value.push(...list)
    // 下一页推进基于服务端返回记录数判断，防止过滤造成的误判
    if (raw.length > 0) page.value += 1
  }catch(e){
    // 后端未启动或网络错误时：显示提示但不阻塞页面
    const msg = e?.response?.data?.message || '加载失败，请稍后重试'
    ElMessage.error(msg)
  }finally{
    initialLoading.value = false
    loading.value = false
    // 兜底填充：若列表高度不足以使哨兵进入滚动容器视窗，则主动尝试继续拉取下一页
    // 说明：在某些布局或浏览器环境下，IntersectionObserver 可能因 root 绑定异常而未及时触发；
    //       该逻辑将检查哨兵是否“近似可见”（加入 200px 提前量），若仍未到达底部且未完成，则继续拉取。
    try { setTimeout(() => { autoFillIfShort(5) }, 0) } catch{}
  }
}

// —— 无限滚动：锚点进入视窗触发加载 ——
async function setupInfiniteScroll(){
  // 详细修复说明：
  // - 问题：当在 onMounted 早期调用本函数时，moreSentinel 可能尚未挂载，导致观察器未绑定，从而只能加载首屏 1 页。
  // - 方案：等待下一次渲染帧（nextTick）确保节点已存在，再绑定 IntersectionObserver。
  await nextTick()
  if (!moreSentinel.value) return
  // 找到滚动容器：TwoPaneLayout 将右侧正文设为唯一滚动容器（.right-main.scrollable-content）
  // 为保证在该容器滚动时也能正确触发观察，将 IntersectionObserver 的 root 设置为该容器。
  const rootEl = getScrollParent(moreSentinel.value)

  // IntersectionObserver：在滚动容器内触发，阈值 1% + 提前 200px 触发加载下一页
  io = new IntersectionObserver((entries) => {
    for (const e of entries){
      if (e.isIntersecting) fetchPage()
    }
  }, { root: rootEl || null, rootMargin: '200px', threshold: 0.01 })
  io.observe(moreSentinel.value)
}
function teardownInfiniteScroll(){
  try{ if (io){ io.disconnect(); io = null } }catch{}
}

// —— 滚动容器查找（上移为顶层，便于兜底填充复用） ——
// 说明：TwoPaneLayout 将右侧正文设置为唯一滚动容器（.right-main.scrollable-content），
//       但不同页面嵌套层级可能有所差异，此函数沿父链查找最近的可滚动容器。
function getScrollParent(el){
  let p = el?.parentElement
  try{
    while(p){
      const s = getComputedStyle(p)
      if (/(auto|scroll)/.test(s.overflowY)) return p
      p = p.parentElement
    }
  }catch{}
  return null
}

// —— 兜底：首屏不足自动填充 ——
// 场景：当列表高度尚不足一屏，或观察器未能及时触发时，主动拉取后续页直至填满或达到循环上限。
function isSentinelVisible(rootEl){
  try{
    const el = moreSentinel.value
    if (!el) return false
    const rect = el.getBoundingClientRect()
    if (rootEl && rootEl.getBoundingClientRect){
      const rootRect = rootEl.getBoundingClientRect()
      // 近似“可见”判定：哨兵的顶部进入 root 底部阈值（提前 200px）
      return rect.top <= (rootRect.bottom + 200)
    }
    // 回退到窗口视口判定（在极端布局下 root 未找到）
    return rect.top <= (window.innerHeight + 200)
  }catch{ return false }
}
async function autoFillIfShort(maxLoops = 3){
  let loops = 0
  const rootEl = getScrollParent(moreSentinel.value)
  while (loops < maxLoops && !loading.value && !done.value && isSentinelVisible(rootEl)){
    await fetchPage()
    await nextTick()
    loops++
  }
}

// 头像加载失败兜底：将破图替换为默认头像，避免出现坏链路
function onAvatarError(e){
  try{
    const img = e?.target
    if (img && img.src !== defaultAvatar){ img.src = defaultAvatar }
  }catch{}
}

// 点击作者头像：跳转到该用户的拾言页（/user/:username/shiyan），并携带昵称/头像/uid 作为查询参数
function goToUserNotes(it){
  try{
    const username = String(it.authorUsername || it.authorName || '').trim()
    const query = {}
    if (it.authorName) query.nickname = it.authorName
    if (it.authorAvatarUrl) query.avatar = it.authorAvatarUrl
    if (it.userId) query.uid = it.userId
    if (username) router.push({ path: `/user/${encodeURIComponent(username)}/shiyan`, query })
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
onMounted(async () => {
  // 解析当前登录用户 uid（用于前端判断“我的拾言”显示操作入口）
  myUid.value = tryGetUidFromToken()
  // 首屏拉取一页
  await fetchPage()
  // 绑定观察器
  await setupInfiniteScroll()
  // 兜底：若首屏高度不足一屏，尝试继续拉取填满视窗
  await autoFillIfShort(5)
  // 获取滚动容器（供“回到顶部”组件使用）：在节点挂载后定位到最近的可滚动父容器
  await nextTick()
  scrollRootEl.value = getScrollParent(moreSentinel.value)
})
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

/* 顶部添加卡片：玻璃卡片风格，与站点整体风格一致 */
.composer-card {
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: saturate(180%) blur(12px);
  -webkit-backdrop-filter: saturate(180%) blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}
.composer-brand { display:flex; align-items:center; gap:8px; }
.composer-brand h2 { margin:0; font-size:18px; color:#303133; }
.composer-body { margin-top: 10px; display:flex; flex-direction:column; gap: 12px; }
.composer-actions { display:flex; align-items:center; gap: 10px; justify-content:flex-end; }
.visibility-select { display:flex; align-items:center; gap: 8px; color:#606266; }

/* 禁用文本域拖拽缩放：保持固定输入高度（由 :rows 控制）
   说明：Element Plus 的 textarea 内层选择器为 .el-textarea__inner；
   在 scoped 样式下使用 :deep 选择到子组件内部元素；
   作用域仅限“添加拾言”卡片，避免影响其他页面的输入框。 */
.composer-card :deep(.el-textarea__inner) { resize: none; }

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
/* 我的拾言操作入口样式：右侧对齐，低干扰按钮 */
.note-card .owner-ops { margin-left: auto; }
.owner-menu { width:20px; height:20px; border-radius:50%; background: rgba(0,0,0,0.06); cursor:pointer; user-select:none; }
.owner-menu:hover { background: rgba(0,0,0,0.08); }
/* 编辑态样式：输入框禁用拖拽，操作按钮右对齐 */
.content.edit-mode { margin-top: 10px; }
.content.edit-mode :deep(.el-textarea__inner) { resize: none; }
.edit-controls { margin-top: 8px; display:flex; align-items:center; justify-content:space-between; gap:8px; }
.edit-actions { display:flex; gap:8px; justify-content:flex-end; }
.edit-controls .visibility-select { display:flex; align-items:center; gap:8px; color:#606266; }
/* 标签与动作同一行，标签左对齐，动作右对齐 */
.tags-actions-row { margin-top: 8px; display:flex; align-items:center; justify-content:space-between; gap:12px; }
.tags-actions-row.no-tags { justify-content: flex-end; }
.tags-actions-row .actions { display:flex; gap: 12px; }
.icon-act { display:inline-flex; align-items:center; gap:6px; color:#606266; }
.icon-act.on { color: var(--el-color-primary); }
/* 可点击样式：用于头像 */
.clickable { cursor: pointer; }

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
  .composer-card { border-radius: 12px; padding: 16px 18px; }
  .note-card { padding: 12px; }
  .note-card .avatar { width:32px; height:32px; }
}
</style>