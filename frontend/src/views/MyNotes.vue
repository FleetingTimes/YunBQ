<template>
  <div class="container">
    <div class="header">
      <div class="brand">
        <img src="https://api.iconify.design/mdi/timeline-text.svg" alt="timeline" width="24" height="24" />
        <h1>我的便签时间线</h1>
      </div>
      <div class="search">
        <el-button @click="$router.push('/notes')">返回便签主页</el-button>
      </div>
    </div>

    <el-timeline>
      <el-timeline-item
        v-for="n in notes"
        :key="n.id"
        :timestamp="formatTime(n.createdAt || n.created_at)"
        placement="top">
        <div class="note-card">
          <div class="note-head">
            <el-tag size="small" :type="n.isPublic ? 'success' : 'info'">{{ n.isPublic ? '公开' : '私有' }}</el-tag>
            <span class="author">作者：{{ authorName }}</span>
            <span class="time">更新：{{ formatTime(n.updatedAt || n.updated_at) }}</span>
          </div>
          <div class="note-content">{{ n.content }}</div>
          <div class="note-tags" v-if="parsedTags(n.tags).length">
            <el-tag v-for="t in parsedTags(n.tags)" :key="t" size="small" style="margin-right:6px;">{{ t }}</el-tag>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import { http } from '@/api/http';
import { ElMessage } from 'element-plus';

const notes = ref([]);
const me = reactive({ username:'', nickname:'' });
const authorName = computed(() => me.nickname || me.username || '我');

function parsedTags(tags){
  if (Array.isArray(tags)) return tags;
  if (typeof tags === 'string') return tags.split(',').map(t => t.trim()).filter(Boolean);
  return [];
}

function formatTime(t){
  if (!t) return '';
  // 兼容后端返回的 LocalDateTime 字符串
  try { return new Date(t).toLocaleString(); } catch { return String(t); }
}

async function loadMe(){
  try{
    const { data } = await http.get('/account/me');
    Object.assign(me, data);
  }catch(e){ /* 忽略错误 */ }
}

async function loadNotes(){
  try{
    const { data } = await http.get('/notes', { params: { size: 100, page: 1 } });
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? []);
    notes.value = (items || []).map(it => ({
      ...it,
      isPublic: it.isPublic ?? it.is_public ?? false,
    }));
  }catch(e){
    ElMessage.error('加载我的便签失败');
  }
}

onMounted(() => { loadMe(); loadNotes(); });
</script>

<style scoped>
.note-card { background:#fff; border-radius:12px; padding:12px; box-shadow:0 4px 12px rgba(0,0,0,0.08); }
.note-head { display:flex; gap:10px; align-items:center; color:#606266; margin-bottom:6px; }
.note-head .author { margin-left:auto; }
.note-head .time { color:#909399; font-size:12px; margin-left:8px; }
.note-content { white-space:pre-wrap; line-height:1.7; color:#303133; margin:4px 0 6px; }
.note-tags { display:flex; flex-wrap:wrap; gap:6px; }
</style>