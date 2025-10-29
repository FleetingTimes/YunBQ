<template>
  <div class="container">
    <AppTopBar @search="onSearch" />
    <div class="page-header">
      <h2>我喜欢的</h2>
      <el-button link @click="$router.push('/my-notes')">返回我的便签</el-button>
    </div>
    <DanmuWall :items="danmuItems" :rows="6" :speed-scale="1.35" />
    <div class="year-groups">
      <div v-for="g in yearGroups" :key="g.year" class="year-group">
        <div class="year-header">
          <span class="year-title">{{ g.year }}</span>
        </div>
        <el-timeline>
          <transition-group name="list" tag="div">
            <el-timeline-item
              v-for="n in g.items"
              :key="n.id"
              :timestamp="formatMD(n.createdAt || n.created_at)"
              placement="top"
            >
              <NoteCard
                :note="n"
                :enableLongPressActions="true"
                @toggle-like="toggleLike"
                @toggle-favorite="toggleFavorite"
              />
            </el-timeline-item>
          </transition-group>
        </el-timeline>
      </div>
    </div>
  </div>
  
</template>

<script setup>
import { ref, onMounted, defineAsyncComponent, computed } from 'vue'
import { http } from '@/api/http'
import { ElMessage } from 'element-plus'

const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))
const DanmuWall = defineAsyncComponent(() => import('@/components/DanmuWall.vue'))
const NoteCard = defineAsyncComponent(() => import('@/components/NoteCard.vue'))

const query = ref('')
const danmuItems = ref([])

function onSearch(q){ query.value = q || ''; load() }

onMounted(() => { load() })

function normalizeNote(it){
  return {
    id: it.id,
    content: String(it.content ?? it.text ?? ''),
    tags: Array.isArray(it.tags) ? it.tags : String(it.tags || '').split(',').filter(Boolean),
    color: String(it.color ?? '#ffd966'),
    authorName: String(it.authorName ?? it.author_name ?? ''),
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    favorited: Boolean(it.favoritedByMe ?? it.favorited ?? false),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
    isPublic: Boolean(it.isPublic ?? it.is_public ?? false),
    createdAt: it.createdAt ?? it.created_at,
    updatedAt: it.updatedAt ?? it.updated_at,
  }
}

async function load(){
  try{
    const { data } = await http.get('/notes', { params: { q: query.value } })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    const mapped = (items || []).map(normalizeNote).filter(n => n.liked)
    danmuItems.value = mapped.length ? mapped : sampleDanmu()
  }catch(e){
    danmuItems.value = sampleDanmu()
    ElMessage.warning('加载点赞数据失败，已使用示例弹幕')
  }
}

function pad(n){ return String(n).padStart(2, '0') }
function formatMD(t){
  if (!t) return ''
  try{
    const d = new Date(t)
    if (isNaN(d.getTime())) return ''
    const M = pad(d.getMonth()+1)
    const D = pad(d.getDate())
    const h = pad(d.getHours())
    const m = pad(d.getMinutes())
    return `${M}月${D}日 ${h}:${m}`
  }catch{ return '' }
}

const yearGroups = computed(() => {
  const map = new Map()
  for (const n of danmuItems.value){
    const t = new Date(n.createdAt || n.created_at || 0)
    const year = isNaN(t.getTime()) ? '未知' : t.getFullYear()
    if (!map.has(year)) map.set(year, [])
    map.get(year).push(n)
  }
  const groups = []
  for (const [year, items] of map.entries()) groups.push({ year, items })
  return groups
})

async function toggleLike(n){
  if (n.likeLoading) return
  n.likeLoading = true
  try{
    const url = n.liked ? `/notes/${n.id}/unlike` : `/notes/${n.id}/like`
    const { data } = await http.post(url)
    n.likeCount = Number(data?.count ?? data?.like_count ?? (n.likeCount || 0))
    n.liked = Boolean((data?.likedByMe ?? data?.liked_by_me ?? n.liked))
    // 若取消喜欢，则从当前列表移除
    if (!n.liked){
      danmuItems.value = danmuItems.value.filter(x => x.id !== n.id)
    }
  }catch(e){
    ElMessage.error('操作失败')
  }finally{
    n.likeLoading = false
  }
}

async function toggleFavorite(n){
  if (n.favoriteLoading) return
  n.favoriteLoading = true
  try{
    const url = n.favorited ? `/notes/${n.id}/unfavorite` : `/notes/${n.id}/favorite`
    const { data } = await http.post(url)
    n.favoriteCount = Number(data?.count ?? data?.favorite_count ?? (n.favoriteCount || 0))
    n.favorited = Boolean((data?.favoritedByMe ?? data?.favorited_by_me ?? n.favorited))
  }catch(e){
    ElMessage.error('操作失败')
  }finally{
    n.favoriteLoading = false
  }
}

function sampleDanmu(){
  // 示例弹幕数据（在无接口或加载失败时展示）
  return [
    { id: 1, content: '喜欢：清晨的第一缕阳光', tags:['生活'], color:'#ffd966', likeCount: 12, liked: true },
    { id: 2, content: '好句子：山高路远，勇者不惧', tags:['语录'], color:'#b6d7a8', likeCount: 30, liked: true },
    { id: 3, content: '打卡：今日阅读《小王子》', tags:['阅读'], color:'#a4c2f4', likeCount: 7, liked: true },
  ]
}
</script>

<style scoped>
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.year-group { margin-bottom: 16px; }
.year-header { display:flex; align-items:center; padding:10px 12px; border-radius:12px; background:#ffffff; box-shadow: 0 6px 20px rgba(0,0,0,0.06); position: sticky; top: 6px; z-index: 10; }
.year-title { font-size:22px; font-weight:700; color:#303133; letter-spacing:0.5px; }
/* 隐藏便签卡片上的公开/私有标签，仅保留作者标签 */
:deep(.meta.bottom-left .el-tag--success),
:deep(.meta.bottom-left .el-tag--info) {
  display: none !important;
}
</style>