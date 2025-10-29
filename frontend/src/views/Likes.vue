<template>
  <div class="container">
    <AppTopBar @search="onSearch" />
    <div class="page-header">
      <h2>我喜欢的</h2>
      <el-button link @click="$router.push('/my-notes')">返回我的便签</el-button>
    </div>
    <DanmuWall :items="danmuItems" :rows="6" :speed-scale="1.35" />
    <div class="placeholder">此处展示你点赞过的便签列表。</div>
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
  return {
    id: it.id,
    content: String(it.content ?? it.text ?? ''),
    tags: Array.isArray(it.tags) ? it.tags : String(it.tags || '').split(',').filter(Boolean),
    color: String(it.color ?? '#ffd966'),
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
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
.placeholder { color:#606266; background:#fff; border-radius:12px; padding:16px; box-shadow:0 4px 12px rgba(0,0,0,0.06); }
</style>