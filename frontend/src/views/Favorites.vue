<template>
  <div class="container">
    <AppTopBar @search="onSearch" />
    <div class="page-header">
      <h2>我的收藏</h2>
      <el-button link @click="$router.push('/my-notes')">返回我的便签</el-button>
    </div>
    <DanmuWall :items="danmuItems" :rows="6" :speed-scale="1.35" />
    <div class="placeholder">此处展示你收藏的便签列表。</div>
  </div>
</template>

<script setup>
import { ref, onMounted, defineAsyncComponent } from 'vue'
import { http } from '@/api/http'
import { ElMessage } from 'element-plus'

const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))
const DanmuWall = defineAsyncComponent(() => import('@/components/DanmuWall.vue'))

const query = ref('')
const danmuItems = ref([])

function onSearch(q){ query.value = q || ''; load() }

onMounted(() => { load() })

function normalizeNote(it){
  const favorited = Boolean(it.favoritedByMe ?? it.favorited ?? it.bookmarked ?? it.starred ?? it.favored ?? it.isFavorite ?? it.favorite ?? false)
  return {
    id: it.id,
    content: String(it.content ?? it.text ?? ''),
    tags: Array.isArray(it.tags) ? it.tags : String(it.tags || '').split(',').filter(Boolean),
    color: String(it.color ?? '#ffd966'),
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
    favorited,
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
.placeholder { color:#606266; background:#fff; border-radius:12px; padding:16px; box-shadow:0 4px 12px rgba(0,0,0,0.06); }
</style>