<template>
  <div class="note-card" :style="noteCardStyle(note)" :data-note-id="note.id"
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

    <div class="note-tags top-right" v-if="parsedTags(note.tags).length">
      <el-tag v-for="t in parsedTags(note.tags)" :key="t" size="small" style="margin-left:6px;">#{{ t }}</el-tag>
    </div>
    <div class="note-content">{{ note.content }}</div>
    <div class="meta bottom-left">
      <el-tag v-if="showAuthorName && note.authorName" size="small" type="warning">作者：{{ note.authorName }}</el-tag>
      <el-tag size="small" :type="(note.isPublic ?? false) ? 'success' : 'info'">{{ (note.isPublic ?? false) ? '公开' : '私有' }}</el-tag>
    </div>
    <div class="meta bottom-right">
      <span class="time">更新：{{ formatTime(note.updatedAt || note.updated_at || note.createdAt || note.created_at) }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  note: { type: Object, required: true },
  enableLongPressActions: { type: Boolean, default: false },
  enableEditDelete: { type: Boolean, default: false },
  showAuthorName: { type: Boolean, default: true },
})

const showActions = ref(false)
const pressTimer = ref(null)

function startPress(){
  cancelPress()
  pressTimer.value = setTimeout(() => { showActions.value = true }, 600)
}
function cancelPress(){ if (pressTimer.value){ clearTimeout(pressTimer.value); pressTimer.value = null } }
function closeActions(){ showActions.value = false }

function parsedTags(tags){
  if (Array.isArray(tags)) return tags
  if (typeof tags === 'string') return tags.split(',').map(t => t.trim().replace(/^#/, '')).filter(Boolean)
  return []
}

function formatTime(t){
  if (!t) return ''
  try { return new Date(t).toLocaleString() } catch { return String(t) }
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
</style>