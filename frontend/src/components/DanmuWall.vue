<template>
  <!--
    弹幕墙（DanmuWall）
    设计目标：
    - 保持“所有弹幕相同速度”，避免快慢不一造成视觉干扰；
    - 控制“屏幕同时显示的弹幕数量”，防止过多造成拥挤与遮挡；
    实现要点：
    - 速度统一：使用统一的 `uniformDuration`（动画时长），配合线性动画与固定位移（100vw → -120vw）；
      这样所有条目在相同时长内完成相同位移，等价为“相同速度”。
    - 数量上限：通过 `maxVisible` 按行均匀限制渲染的条目数量（行均分）。
    - 随机延迟：保留每条初次进入的随机起始延迟，避免所有弹幕同相位导致密集重叠。
  -->
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
  // 弹幕数据列表：内容与点赞信息
  items: { type: Array, default: () => [] },
  // 行数（轨道数）：决定垂直方向的分层数量
  rows: { type: Number, default: 6 },
  // 速度缩放：在统一时长基础上可整体加速或减速
  speedScale: { type: Number, default: 1.35 },
  // 高亮/新创建条目的 id：用于设置其初始延迟为 0，使其立即进入视野
  highlightId: { type: [Number, String, null], default: null },
  // 是否启用统一速度：true 时所有条目使用相同动画时长
  sameSpeed: { type: Boolean, default: true },
  // 统一动画时长（秒）：sameSpeed=true 生效；建议 14~20s
  uniformDuration: { type: Number, default: 16 },
  // 屏幕同时可见的最大弹幕数量（总数上限；0/负数表示不限制）
  // 当设置上限时，会按行“均分”限制每行渲染的条目数量。
  maxVisible: { type: Number, default: 0 },
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

// 说明：
// - 为避免所有弹幕在同一时间进入视野而重叠，这里仍保留“随机初始延迟”（delay）。
// - 当 sameSpeed=true 时，duration 将被统一设置为 props.uniformDuration。
const danmuItems = computed(() => (props.items || []).map((n, idx) => {
  const rgb = parseHexColor(n.color)
  const h = rgb ? null : hueFromNote(n, idx)
  const cached = danmuCache.value[n.id] || {
    row: (idx % props.rows) + 1,
    // 初始延迟随机：避免循环相位一致导致密集重叠
    duration: 12 + Math.random() * 8, // 默认 12s ~ 20s（仅在 sameSpeed=false 时使用）
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
    // 当开启统一速度时，强制使用统一动画时长；否则保留每条的随机时长
    duration: props.sameSpeed ? props.uniformDuration : cached.duration,
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

// 行内条目选择：在有“总可见上限”时，按行均分限制每行数量（不足向下取整，至少为 1）
function danmuItemsForRow(row) {
  const all = danmuItems.value.filter(i => i.row === row)
  const limitTotal = Number(props.maxVisible || 0)
  if (limitTotal > 0) {
    const perRow = Math.max(1, Math.floor(limitTotal / Math.max(1, props.rows)))
    return all.slice(0, perRow)
  }
  return all
}
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

// 高亮（新创建）项：把其延迟设为 0，并按索引分配行；
// 若启用统一速度，则其时长也使用统一值，保证速度一致。
watch(() => props.highlightId, (id) => {
  if (!id) return
  const idx = (props.items || []).findIndex(n => n.id === id)
  const row = (idx >= 0 ? (idx % props.rows) + 1 : 1)
  danmuCache.value[id] = { row, delay: 0, duration: (props.sameSpeed ? props.uniformDuration : 15) }
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