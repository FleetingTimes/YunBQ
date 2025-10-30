<template>
  <div>
    <DanmuWall
      :items="notes"
      :rows="danmuRows"
      :speed-scale="danmuSpeedScale"
      :highlight-id="danmuHighlightId"
      @itemClick="toggleLikeById"
    />

    <div class="grid" v-if="props.showComposer">
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
import DanmuWall from '@/components/DanmuWall.vue'

// Props 恢复为原始定义：仅保留 query 与 showComposer
// 说明：添加便签页侧边栏不再进行标签快捷填充，因此移除 quickTags。
const props = defineProps({ query: { type: String, default: '' }, showComposer: { type: Boolean, default: true } })

const router = useRouter()
const notes = ref([])
const justCreatedId = ref(null)
const justCreatedFirst = ref(false)
const danmuHighlightId = ref(null)

const danmuRows = 6
const danmuSpeedScale = 1.35

const draft = reactive({ content: '', isPublic: false, tags: '', color: '#ffd966' })

onMounted(() => { load() })
watch(() => props.query, () => { load() })

async function load(){
  try{
    const { data } = await http.get('/notes', { params: { q: props.query }, suppress401Redirect: true })
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
      danmuHighlightId.value = targetId
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
</style>