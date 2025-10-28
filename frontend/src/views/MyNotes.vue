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
        <div class="author-above">作者：{{ authorName }}</div>
        <div class="note-card" :style="noteCardStyle(n)">
          <div class="note-tags top-right" v-if="parsedTags(n.tags).length">
            <el-tag v-for="t in parsedTags(n.tags)" :key="t" size="small" style="margin-left:6px;">{{ t }}</el-tag>
          </div>
          <div class="note-content">{{ n.content }}</div>
          <div class="meta bottom-left">
            <el-tag size="small" :type="n.isPublic ? 'success' : 'info'">{{ n.isPublic ? '公开' : '私有' }}</el-tag>
          </div>
          <div class="meta bottom-right">
            <span class="time">更新：{{ formatTime(n.updatedAt || n.updated_at) }}</span>
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

function parseHexColor(hex){
  if (!hex || typeof hex !== 'string') return null;
  const m = hex.trim().match(/^#?([0-9a-fA-F]{6})$/);
  if (!m) return null;
  const v = m[1];
  const r = parseInt(v.slice(0,2), 16);
  const g = parseInt(v.slice(2,4), 16);
  const b = parseInt(v.slice(4,6), 16);
  return { r, g, b };
}
function noteCardStyle(n){
  const rgb = parseHexColor(n.color);
  if (!rgb) return {};
  return { borderLeft: `6px solid rgba(${rgb.r},${rgb.g},${rgb.b},0.6)` };
}

onMounted(() => { loadMe(); loadNotes(); });
</script>

<style scoped>
.note-card { background:#fff; border-radius:12px; padding:12px 12px 32px; box-shadow:0 4px 12px rgba(0,0,0,0.08); position:relative; }
.note-content { white-space:pre-wrap; line-height:1.7; color:#303133; margin:4px 0 6px; }
.note-tags { display:flex; flex-wrap:wrap; gap:6px; }
.note-tags.top-right { position:absolute; top:8px; right:12px; }
.meta.bottom-left { position:absolute; left:12px; bottom:10px; }
.meta.bottom-right { position:absolute; right:12px; bottom:10px; color:#606266; font-size:12px; }
.author-above { color:#606266; font-size:12px; margin: 0 0 6px 0; }
</style>