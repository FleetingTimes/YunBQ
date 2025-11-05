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
        <!-- 文案重命名：将“便签”统一改为“拾言” -->
        <div class="title">新建拾言</div>
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

    <!--
      计数标签说明：
      - 此处显示的“条数”仅代表本组件（顶部弹幕区域）当前展示的条目数量，
        并非搜索结果的总条数。完整的分页结果由下方时间线列表负责加载与展示。
      - 为避免在搜索页产生“总数仅 20”之类的误解，这里提供开关与可定制前缀文案：
        props.showCountTag（默认 true）与 props.countLabel（默认“共”）。
    -->
    <div class="footer" v-if="props.showCountTag">
      <el-tag type="info">{{ props.countLabel }} {{ notes.length }} 条</el-tag>
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
// 组件入参：
// - query：搜索关键词
// - showComposer：是否显示顶部创建入口
// - showCountTag：是否显示底部计数标签（默认 true）。在搜索页为了避免误导可设为 false。
// - countLabel：计数标签前缀文案（默认“共”），也可改为“首屏展示”等。
const props = defineProps({
  query: { type: String, default: '' },
  showComposer: { type: Boolean, default: true },
  showCountTag: { type: Boolean, default: true },
  countLabel: { type: String, default: '共' }
})

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
    // 路径切换：统一使用 /shiyan 搜索拾言（参数语义保持一致）
    // 修复：默认仅返回 10 条（后端默认 size=10），这里显式传入 size=20，并排除归档项以提升结果质量。
    // 说明：顶栏搜索结果页顶部弹幕区域仅做“首屏展示”，因此不做分页；
    //       若需要更多数据，页面下方的“时间线列表”具备服务端分页与无限滚动能力。
    const { data } = await http.get('/shiyan', { params: { q: props.query, page: 1, size: 20, archived: false }, suppress401Redirect: true })
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
  // 文案重命名：将“便签”统一改为“拾言”
  ElMessage.error('加载拾言失败')
  }
}

async function toggleLike(n){
  if (n.likeLoading) return
  n.likeLoading = true
  try{
    // 路径切换：统一使用 /shiyan/{id}/like|unlike
    const url = n.liked ? `/shiyan/${n.id}/unlike` : `/shiyan/${n.id}/like`
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
    // 路径切换：统一使用 /shiyan/{id}/archive
    await http.post(`/shiyan/${n.id}/archive`, { archived: !n.archived })
    ElMessage.success('已更新归档状态')
    load()
  }catch(e){
    ElMessage.error('更新归档失败')
  }
}

async function remove(n){
  try{
    // 路径切换：统一使用 /shiyan/{id}
    await http.delete(`/shiyan/${n.id}`)
    ElMessage.success('已删除')
    load()
  }catch(e){
    ElMessage.error('删除失败')
  }
}

async function create(){
  if (!draft.content) { ElMessage.warning('请填写内容'); return }
  try{
    // 说明：后端 DTO（NoteRequest.java）字段为 camelCase 的 isPublic，
    // 若使用 is_public（snake_case）将无法被 Jackson 默认命名策略绑定，导致后端取值为 null，
    // 进而在服务层 Boolean.TRUE.equals(req.getIsPublic()) 为 false，最终保存为“私有”。
    // 因此此处改为 isPublic，确保后端正确接收“公开/私有”选择。
    const payload = {
      content: draft.content,
      isPublic: draft.isPublic,
      tags: (draft.tags || '').trim(),
      color: (draft.color || '').trim()
    }
    // 路径切换：创建统一使用 /shiyan
    const { data } = await http.post('/shiyan', payload)
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
    // 说明：更新请求同样使用 isPublic（camelCase）与后端 DTO 保持一致，避免因 is_public 未绑定导致始终保存为“私有”。
    const payload = {
      content: n.content,
      tags: tagsStr,
      archived: n.archived,
      isPublic: !currentPublic,
      color: (n.color || '').trim()
    }
    // 路径切换：更新统一使用 /shiyan/{id}
    await http.put(`/shiyan/${n.id}`, payload)
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