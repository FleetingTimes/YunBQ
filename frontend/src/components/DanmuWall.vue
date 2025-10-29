<template>
  <div class="danmu-section">
    <div class="danmu-track" v-for="row in danmuRowList" :key="row" :style="trackStyle(row)">
      <div
        class="danmu-item"
        v-for="item in danmuItemsForRow(row)"
        :key="item.id"
        :style="danmuStyle(item)"
        @click="onItemClick(item.id)"
      >
        <span class="danmu-text">{{ item.content }}</span>
        <span class="like-badge">{{ item.liked ? '♥' : '♡' }} {{ item.likeCount || 0 }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  rows: { type: Number, default: 6 },
  speedScale: { type: Number, default: 1.35 },
  highlightId: { type: [Number, String, null], default: null },
})
const emit = defineEmits(['itemClick'])

const danmuRowList = computed(() => Array.from({ length: props.rows }, (_, i) => i + 1))

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
function luminance({r,g,b}){
  return 0.2126*(r/255) + 0.7152*(g/255) + 0.0722*(b/255)
}
function hueFromNote(n, idx) {
  const tagsStr = Array.isArray(n.tags) ? n.tags.join(',') : (n.tags || '')
  const s = (n.content || '') + tagsStr + String(n.id ?? idx)
  let hash = 0
  for (let i = 0; i < s.length; i++) { hash = (hash * 31 + s.charCodeAt(i)) >>> 0 }
  return hash % 360
}

const danmuCache = ref({})

const danmuItems = computed(() => (props.items || []).map((n, idx) => {
  const rgb = parseHexColor(n.color)
  const h = rgb ? null : hueFromNote(n, idx)
  const cached = danmuCache.value[n.id] || {
    row: (idx % props.rows) + 1,
    // 初始延迟与时长随机，避免后续循环同步导致弹幕密度下降
    duration: 12 + Math.random() * 8, // 12s ~ 20s
    delay: 0,
  }
  if (!cached.delay || cached.delay <= 0) {
    cached.delay = Math.random() * cached.duration
  }
  danmuCache.value[n.id] = cached
  const base = {
    id: n.id,
    content: n.content,
    row: cached.row,
    delay: cached.delay,
    duration: cached.duration,
    h,
    bg: '',
    fg: '',
    liked: !!(n.liked ?? n.likedByMe ?? n.liked_by_me),
    likeCount: Number(n.likeCount ?? n.like_count ?? 0),
  }
  if (rgb){
    const lum = luminance(rgb)
    base.bg = `rgba(${rgb.r},${rgb.g},${rgb.b},0.18)`
    base.fg = lum > 0.6 ? '#303133' : '#ffffff'
    base.border = `1px solid rgba(${rgb.r},${rgb.g},${rgb.b},0.6)`
  } else {
    base.bg = `hsla(${h}, 85%, 88%, 0.95)`
    base.fg = `hsl(${h}, 35%, 22%)`
    base.border = `1px solid hsla(${h}, 70%, 80%, 1)`
  }
  return base
}))

function danmuItemsForRow(row) { return danmuItems.value.filter(i => i.row === row) }
function danmuStyle(it) {
  return {
    animationDuration: (it.duration * props.speedScale) + 's',
    animationDelay: (-it.delay) + 's',
    background: it.bg,
    color: it.fg,
    border: it.border,
  }
}
function trackStyle(row) {
  const h = 100 / props.rows
  return { top: ((row - 1) * h) + '%', height: h + '%' }
}

// 高亮（新创建）项：把其延迟设为0，并按索引分配行
watch(() => props.highlightId, (id) => {
  if (!id) return
  const idx = (props.items || []).findIndex(n => n.id === id)
  const row = (idx >= 0 ? (idx % props.rows) + 1 : 1)
  danmuCache.value[id] = { row, delay: 0, duration: 15 }
}, { immediate: true })

function onItemClick(id){ emit('itemClick', id) }
</script>

<style scoped>
.danmu-section {
  position: relative;
  height: 33vh;
  overflow: hidden;
  background: transparent;
  border-bottom: 1px solid #e5e7eb;
}
.danmu-track { position: absolute; left: 0; width: 100%; }
.danmu-item {
  position: absolute;
  right: -200px;
  white-space: nowrap;
  color: #333;
  background: rgba(255,255,255,0.85);
  border-radius: 16px;
  padding: 6px 12px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.08);
  animation-name: danmu-move;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  animation-fill-mode: backwards;
  will-change: transform;
}
@keyframes danmu-move { 0% { transform: translateX(100vw); } 100% { transform: translateX(-120vw); } }
.danmu-item:hover { animation-play-state: paused; cursor: pointer; }
.like-badge { display: inline-block; margin-left: 8px; font-weight: 600; color: currentColor; background: rgba(255,255,255,0.6); border-radius: 12px; padding: 2px 6px; }
.danmu-text { display: inline-block; }
</style>