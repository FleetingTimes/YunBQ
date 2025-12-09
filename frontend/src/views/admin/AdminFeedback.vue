<template>
  <div class="admin-feedback">
    <div class="controls">
      <el-segmented v-model="type" :options="typeOptions" />
      <el-select v-model="status" placeholder="状态" style="width: 140px; margin-left: 12px;">
        <el-option label="全部" value="" />
        <el-option label="未处理" value="open" />
        <el-option label="处理中" value="processing" />
        <el-option label="已解决" value="resolved" />
        <el-option label="已拒绝" value="rejected" />
      </el-select>
      <el-input v-model="q" placeholder="搜索标题/内容" clearable style="width: 280px; margin-left: 12px;" @keyup.enter="reload" />
      <el-button type="primary" style="margin-left: 12px;" :loading="loading" @click="reload">刷新</el-button>
    </div>

    <el-table :data="items" stripe v-loading="loading" @row-click="openDetail">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column v-if="type==='issue'" prop="module" label="模块" width="120" />
      <el-table-column v-if="type==='issue'" prop="pagePath" label="路径" width="220" />
      <el-table-column v-if="type==='suggest'" prop="category" label="分类" width="140" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="contactEmail" label="邮箱" width="200" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="300">
        <template #default="{ row }">
          <el-button size="small" @click.stop="updateStatus(row, 'open')">未处理</el-button>
          <el-button size="small" type="warning" @click.stop="updateStatus(row, 'processing')">处理中</el-button>
          <el-button size="small" type="success" @click.stop="updateStatus(row, 'resolved')">已解决</el-button>
          <el-button size="small" type="danger" @click.stop="updateStatus(row, 'rejected')">已拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination background layout="prev, pager, next" :total="total" :page-size="size" :current-page="page" @current-change="onPage" />
    </div>

    <el-drawer v-model="detailVisible" title="反馈详情" size="560px">
      <div v-if="detail">
        <div class="kv"><span>类型</span><b>{{ detail.type }}</b></div>
        <div class="kv"><span>状态</span><b>{{ detail.status }}</b></div>
        <div class="kv" v-if="detail.module"><span>模块</span><b>{{ detail.module }}</b></div>
        <div class="kv" v-if="detail.pagePath"><span>路径</span><b>{{ detail.pagePath }}</b></div>
        <div class="kv" v-if="detail.category"><span>分类</span><b>{{ detail.category }}</b></div>
        <div class="kv" v-if="detail.title"><span>标题</span><b>{{ detail.title }}</b></div>
        <div class="kv"><span>描述</span><pre class="mono">{{ detail.description }}</pre></div>
        <!-- 根据需求：移除步骤/期望/实际/预期收益字段 -->
        <div class="kv" v-if="detail.contactEmail"><span>邮箱</span><b>{{ detail.contactEmail }}</b></div>
        <div class="kv" v-if="detail.contactQq"><span>QQ</span><b>{{ detail.contactQq }}</b></div>
        <!-- 根据需求：移除 GitHub 联系方式展示，仅保留邮箱/QQ -->
        <div class="kv"><span>创建时间</span><b>{{ detail.createdAt }}</b></div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { http } from '@/api/http'
import { ElMessage } from 'element-plus'

const props = defineProps({ updateSummary: Function })
const typeOptions = [
  { label: '问题反馈', value: 'issue' },
  { label: '站点建议', value: 'suggest' }
]
const type = ref('issue')
const status = ref('')
const q = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const items = ref([])
const loading = ref(false)

async function reload(){
  loading.value = true
  try{
    const { data } = await http.get('/admin/feedback', { params: { type: type.value, status: status.value, q: q.value, page: page.value, size: size.value } })
    items.value = data?.items || []
    total.value = Number(data?.total || 0)
    props.updateSummary?.({ total: total.value })
  }catch{ ElMessage.error('加载失败') }
  finally{ loading.value = false }
}

function onPage(p){ page.value = p; reload() }

const detailVisible = ref(false)
const detail = ref(null)
function openDetail(row){ detail.value = row; detailVisible.value = true }

async function updateStatus(row, s){
  try{
    await http.put(`/admin/feedback/${row.id}/status`, { status: s })
    ElMessage.success('状态已更新')
    reload()
  }catch{ ElMessage.error('更新失败') }
}

onMounted(reload)
watch(type, () => { page.value = 1; reload() })
watch(status, () => { page.value = 1; reload() })
</script>

<style scoped>
.controls{ display:flex; align-items:center; gap:8px; margin-bottom:12px; }
.pager{ display:flex; justify-content:flex-end; padding: 12px 0; }
.kv{ display:grid; grid-template-columns: 120px 1fr; gap:8px; padding:6px 0; }
.kv span{ color:#6b7280; }
.mono{ font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; white-space: pre-wrap; margin:0; }
</style>
