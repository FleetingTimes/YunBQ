<template>
  <!-- 两栏布局：左侧 SideNav（展示为统一结构），右侧顶栏与正文在一列 -->
  <TwoPaneLayout>
    <template #left>
      <!-- 引入通用侧栏以保持结构一致；本页不做锚点滚动，仅提供导航视觉 -->
      <SideNav :sections="sections" v-model:activeId="activeId" :alignCenter="true" />
    </template>
    <!-- 全宽顶栏：跨越左右两列并吸顶，顶栏内容全屏铺满 -->
    <template #topFull>
      <AppTopBar fluid @search="onSearch" />
    </template>
    <template #rightMain>
      <div class="container">
        <div class="page-header">
          <h2>我的收藏</h2>
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
  </TwoPaneLayout>
</template>

<script setup>
import { ref, onMounted, defineAsyncComponent, computed } from 'vue'
const TwoPaneLayout = defineAsyncComponent(() => import('@/components/TwoPaneLayout.vue'))
const SideNav = defineAsyncComponent(() => import('@/components/SideNav.vue'))
import { http } from '@/api/http'
import { ElMessage } from 'element-plus'

const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))
const DanmuWall = defineAsyncComponent(() => import('@/components/DanmuWall.vue'))
const NoteCard = defineAsyncComponent(() => import('@/components/NoteCard.vue'))
import { sideNavSections as sections } from '@/config/navSections'
// 侧栏当前高亮项（本页仅用于展示导航，不参与滚动）
const activeId = ref('')

const query = ref('')
const danmuItems = ref([])

function onSearch(q){ query.value = q || ''; load() }

onMounted(() => { load() })

function normalizeNote(it){
  // 在收藏页面中，所有便签都应该显示为已收藏状态
  const favorited = Boolean(it.favoritedByMe ?? it.favorited ?? it.bookmarked ?? it.starred ?? it.favored ?? it.isFavorite ?? it.favorite ?? true)
  return {
    id: it.id,
    content: String(it.content ?? it.text ?? ''),
    tags: Array.isArray(it.tags) ? it.tags : String(it.tags || '').split(',').filter(Boolean),
    color: String(it.color ?? '#ffd966'),
    authorName: String(it.authorName ?? it.author_name ?? ''),
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
    favorited,
    isPublic: Boolean(it.isPublic ?? it.is_public ?? false),
    createdAt: it.createdAt ?? it.created_at,
    updatedAt: it.updatedAt ?? it.updated_at,
  }
}

async function load(){
  try{
    const { data } = await http.get('/notes/favorites', { params: { q: query.value } })
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
    const mapped = (items || []).map(normalizeNote)
    danmuItems.value = mapped.length ? mapped : sampleDanmu()
  }catch(e){
    danmuItems.value = sampleDanmu()
    ElMessage.warning('加载收藏数据失败，已使用示例弹幕')
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
    // 若取消收藏，则从当前列表移除
    if (!n.favorited){
      danmuItems.value = danmuItems.value.filter(x => x.id !== n.id)
    }
  }catch(e){
    ElMessage.error('操作失败')
  }finally{
    n.favoriteLoading = false
  }
}

function sampleDanmu(){
  // 示例弹幕数据（在无接口或加载失败时展示）
  return [
    { id: 10, content: '收藏：效率技巧清单', tags:['收藏'], color:'#ffd966', likeCount: 18, liked: true },
    { id: 11, content: '收藏：旅行计划模板', tags:['模板'], color:'#b6d7a8', likeCount: 22, liked: false },
    { id: 12, content: '收藏：每日反思问题集', tags:['思考'], color:'#a4c2f4', likeCount: 9, liked: false },
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
/* 回退说明：
   - 移除了页面级顶栏包裹宽度限制（topbar-wrap），顶栏使用全宽插槽；
   - 保持原始页面样式与行为，不影响顶栏组件与其它页面。 */
</style>