<template>
  <div>
    <div class="danmu-section">
      <div class="danmu-track" v-for="row in danmuRowList" :key="row" :style="trackStyle(row)">
        <div class="danmu-item" v-for="item in danmuItemsForRow(row)" :key="item.id" :style="danmuStyle(item)" @click="toggleLikeById(item.id)">
           <span class="danmu-text">{{ item.content }}</span>
           <span class="like-badge">{{ item.liked ? '♥' : '♡' }} {{ item.likeCount || 0 }}</span>
         </div>
      </div>
    </div>

    <div class="grid">
      <div class="sticky composer p-2 rot-2">
        <div class="title">新建便签</div>
        <el-input v-model="draft.tags" placeholder="标签（用逗号分隔）" style="margin-bottom:6px;" />
        <el-input v-model="draft.content" type="textarea" :rows="4" placeholder="内容" />
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

    <div class="footer">
      <el-tag type="info">共 {{ notes.length }} 条</el-tag>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/http'
import { ElMessage } from 'element-plus'

const props = defineProps({ query: { type: String, default: '' } })

const router = useRouter()
const notes = ref([])
const justCreatedId = ref(null)
const justCreatedFirst = ref(false)

const danmuRows = 6
const danmuRowList = computed(() => Array.from({ length: danmuRows }, (_, i) => i + 1))
const danmuSpeedScale = 1.35

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
const danmuItems = computed(() => notes.value.map((n, idx) => {
  const rgb = parseHexColor(n.color)
  const h = rgb ? null : hueFromNote(n, idx)
  const cached = danmuCache.value[n.id] || {
    row: (idx % danmuRows) + 1,
    delay: Math.random() * 8,
    duration: 15,
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
    liked: !!n.liked,
    likeCount: n.likeCount || 0,
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
    animationDuration: (it.duration * danmuSpeedScale) + 's',
    animationDelay: it.delay + 's',
    background: it.bg,
    color: it.fg,
    border: it.border
  }
}
function trackStyle(row) { const h = 100 / danmuRows; return { top: ((row - 1) * h) + '%', height: h + '%' } }

const draft = reactive({ content: '', isPublic: false, tags: '', color: '#ffd966' })

onMounted(() => { load() })
watch(() => props.query, () => { load() })

async function load(){
  try{
    const { data } = await http.get('/notes', { params: { q: props.query } })
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
      const idx = notes.value.findIndex(n => n.id === targetId)
      const row = (idx >= 0 ? (idx % danmuRows) + 1 : 1)
      danmuCache.value[targetId] = { row, delay: 0, duration: 15 }
      justCreatedId.value = null
      justCreatedFirst.value = false
    }
  }catch(e){
    ElMessage.error('加载便签失败')
  }
}

async function toggleLike(n){
  if (n.likeLoading) return
  n.likeLoading = true
  try{
    const url = n.liked ? `/notes/${n.id}/unlike` : `/notes/${n.id}/like`
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
    await http.post(`/notes/${n.id}/archive`, { archived: !n.archived })
    ElMessage.success('已更新归档状态')
    load()
  }catch(e){
    ElMessage.error('更新归档失败')
  }
}

async function remove(n){
  try{
    await http.delete(`/notes/${n.id}`)
    ElMessage.success('已删除')
    load()
  }catch(e){
    ElMessage.error('删除失败')
  }
}

async function create(){
  if (!draft.content) { ElMessage.warning('请填写内容'); return }
  try{
    const payload = { content: draft.content, is_public: draft.isPublic, tags: (draft.tags || '').trim(), color: (draft.color || '').trim() }
    const { data } = await http.post('/notes', payload)
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
    const payload = { content: n.content, tags: tagsStr, archived: n.archived, is_public: !currentPublic, color: (n.color || '').trim() }
    await http.put(`/notes/${n.id}`, payload)
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