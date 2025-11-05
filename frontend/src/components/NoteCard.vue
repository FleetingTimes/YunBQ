<template>
  <!--
    通用笔记卡片 NoteCard
    功能：展示内容、标签、时间与交互；支持长按弹出操作层；
    新增：可选的作者头像区（showAuthorAvatar），用于在“我喜欢的拾言”等页面显示作者头像。
    设计说明：
    - 头像区仅在 showAuthorAvatar=true 时渲染；
    - 头像地址支持相对路径，由 avatarFullUrl 拼接成完整 URL；
    - 加载失败时使用默认占位图，避免破图；
    - 为避免重复信息，当显示头像区时，底部作者名标签不再重复显示。
  -->
  <div ref="rootRef" class="note-card" :style="noteCardStyle(note)" :data-note-id="note.id"
    @mousedown="enableLongPressActions && startPress($event)"
    @mouseup="enableLongPressActions && cancelPress()"
    @mouseleave="enableLongPressActions && cancelPress()"
    @touchstart="enableLongPressActions && startPress($event)"
    @touchend="enableLongPressActions && cancelPress()"
  >
    <transition name="overlay">
      <div v-if="showActions" class="actions-overlay" @click="closeActions" @mousedown.stop @mouseup.stop @touchstart.stop @touchend.stop>
        <div class="action-icon" :title="note.liked ? '取消喜欢' : '喜欢'" @click.stop="$emit('toggle-like', note)">
          <img :src="note.liked ? 'https://api.iconify.design/mdi/heart.svg?color=%23e25555' : 'https://api.iconify.design/mdi/heart-outline.svg'" alt="like" width="20" height="20" />
        </div>
        <div class="action-icon" :title="note.favorited ? '取消收藏' : '收藏'" @click.stop="$emit('toggle-favorite', note)">
          <img :src="note.favorited ? 'https://api.iconify.design/mdi/bookmark.svg?color=%23409eff' : 'https://api.iconify.design/mdi/bookmark-outline.svg'" alt="favorite" width="20" height="20" />
        </div>
        <div v-if="enableEditDelete" class="action-icon" title="编辑" @click.stop="$emit('edit', note)">
          <img src="https://api.iconify.design/mdi/pencil.svg" alt="edit" width="20" height="20" />
        </div>
        <div v-if="enableEditDelete" class="action-icon danger" title="删除" @click.stop="$emit('delete', note)">
          <img src="https://api.iconify.design/mdi/delete.svg" alt="delete" width="20" height="20" />
        </div>
      </div>
    </transition>

    <!-- 作者头像区：仅在需要显示作者头像的页面启用 -->
    <div class="author-head" v-if="showAuthorAvatar">
      <img
        v-if="note.authorAvatarUrl"
        class="avatar"
        :src="avatarFullUrl(note.authorAvatarUrl)"
        alt="avatar"
        loading="lazy"
        @error="onAvatarError"
      />
      <img v-else class="avatar" :src="defaultAvatar" alt="avatar" loading="lazy" />
      <div class="author-meta">
        <div class="name" :title="note.authorName">{{ note.authorName || '匿名' }}</div>
      </div>
    </div>

    <div class="note-tags top-right" v-if="parsedTags(note.tags).length">
      <el-tag v-for="t in parsedTags(note.tags)" :key="t" size="small" style="margin-left:6px;">#{{ t }}</el-tag>
    </div>
    <div class="note-content">{{ note.content }}</div>
    <div class="meta bottom-left">
      <!-- 当顶部头像区启用时，为避免信息重复，这里不再显示作者名标签 -->
      <el-tag v-if="showAuthorName && note.authorName && !showAuthorAvatar" size="small" type="warning">作者：{{ note.authorName }}</el-tag>
      <el-tag size="small" :type="(note.isPublic ?? false) ? 'success' : 'info'">{{ (note.isPublic ?? false) ? '公开' : '私有' }}</el-tag>
    </div>
    <div class="meta bottom-right">
      <span class="time">更新：{{ formatTime(note.updatedAt || note.updated_at || note.createdAt || note.created_at) }}</span>
    </div>
  </div>
</template>

<script setup>
// 说明：
// - 引入拼接函数 avatarFullUrl，将后端返回的相对路径（如 /uploads/avatars/...）拼接为完整 URL；
// - 引入默认头像占位图，以防网络或路径异常导致破图；
import { ref, onBeforeUnmount } from 'vue'
import { avatarFullUrl } from '@/api/http'
import defaultAvatar from '@/assets/default-avatar.svg'

const props = defineProps({
  note: { type: Object, required: true },
  enableLongPressActions: { type: Boolean, default: false },
  enableEditDelete: { type: Boolean, default: false },
  showAuthorName: { type: Boolean, default: true },
  // 新增：是否显示作者头像区（默认不显示，避免影响现有页面布局）
  showAuthorAvatar: { type: Boolean, default: false },
})

const showActions = ref(false)
const pressTimer = ref(null)
const rootRef = ref(null)

function onDocClick(e){
  // 点击卡片外部时关闭动作层
  if (showActions.value){
    const el = rootRef.value
    if (el && !el.contains(e.target)){
      closeActions()
    }
  }
}

function startPress(){
  cancelPress()
  pressTimer.value = setTimeout(() => {
    showActions.value = true
    // 在动作层打开后监听文档点击，支持点击外部关闭
    document.addEventListener('click', onDocClick, true)
  }, 600)
}
function cancelPress(){ if (pressTimer.value){ clearTimeout(pressTimer.value); pressTimer.value = null } }
function closeActions(){
  showActions.value = false
  document.removeEventListener('click', onDocClick, true)
}

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick, true)
  cancelPress()
})

function parsedTags(tags){
  if (Array.isArray(tags)) return tags
  if (typeof tags === 'string') return tags.split(',').map(t => t.trim().replace(/^#/, '')).filter(Boolean)
  return []
}

function formatTime(t){
  if (!t) return ''
  try { return new Date(t).toLocaleString() } catch { return String(t) }
}

// 头像加载失败兜底：将破图替换为默认头像，避免出现坏链路
function onAvatarError(e){
  try{
    const img = e?.target
    if (img && img.src !== defaultAvatar){ img.src = defaultAvatar }
  }catch{}
}

function parseHexColor(hex){
  if (!hex || typeof hex !== 'string') return null
  const m = hex.trim().match(/^#?([0-9a-fA-F]{6})$/)
  if (!m) return null
  const v = m[1]
  const r = parseInt(v.slice(0,2), 16)
  const g = parseInt(v.slice(2,4), 16)
  const b = parseInt(v.slice(4,6), 16)
  return { r, g, b }
}
function luminance({r,g,b}){ return 0.2126*(r/255) + 0.7152*(g/255) + 0.0722*(b/255) }
function noteCardStyle(n){
  const rgb = parseHexColor(n.color)
  if (!rgb) return {}
  const fg = luminance(rgb) > 0.6 ? '#303133' : '#ffffff'
  return {
    borderLeft: `6px solid rgba(${rgb.r},${rgb.g},${rgb.b},0.6)`,
    background: (typeof n.color === 'string' ? n.color.trim() : `rgba(${rgb.r},${rgb.g},${rgb.b},0.18)`),
    '--fgColor': fg
  }
}
</script>

<style scoped>
.note-card { background:#fff; border-radius:12px; padding:12px 12px 32px; box-shadow:0 4px 12px rgba(0,0,0,0.08); position:relative; }
.note-content { white-space:pre-wrap; line-height:1.7; color:#303133; margin:4px 0 6px; }
.note-tags { display:flex; flex-wrap:wrap; gap:6px; }
.note-tags.top-right { position:absolute; top:8px; right:12px; }
.meta.bottom-left { position:absolute; left:12px; bottom:10px; }
.meta.bottom-right { position:absolute; right:12px; bottom:10px; color:#606266; font-size:12px; }

.actions-overlay { position:absolute; inset:0; background: rgba(0,0,0,0.08); display:flex; align-items:center; justify-content:center; gap:12px; border-radius:12px; }
.action-icon { width:40px; height:40px; border-radius:50%; background:#fff; box-shadow:0 6px 16px rgba(0,0,0,0.12); display:flex; align-items:center; justify-content:center; cursor:pointer; }
.action-icon.danger { background:#fff0f0; }
.action-icon:hover { transform: translateY(-1px); transition: transform 0.12s ease; }

.overlay-enter-active, .overlay-leave-active { transition: opacity .18s ease, transform .18s ease; }
.overlay-enter-from, .overlay-leave-to { opacity: 0; transform: scale(0.98); }

/* 作者头像区样式：与 ShiyanTown 页的头部风格保持一致 */
.author-head { display:flex; align-items:center; gap:10px; margin-bottom:8px; }
.author-head .avatar { width:32px; height:32px; border-radius:50%; object-fit:cover; background:#fff; border:2px solid #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.12); }
.author-head .author-meta { display:flex; flex-direction:column; }
.author-head .name { font-weight:600; color:#303133; }
</style>