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
        <div
          :class="['note-card', { editing: n.editing }]"
          :style="noteCardStyle(n)"
          :data-note-id="n.id"
          @mousedown="startPress(n, $event)"
          @mouseup="cancelPress"
          @mouseleave="cancelPress"
          @touchstart="startPress(n, $event)"
          @touchend="cancelPress"
        >
          <!-- 动作菜单：长按出现（图标版） -->
          <div
            v-if="n.showActions"
            class="actions-overlay"
            @click="closeActions(n)"
            @mousedown.stop
            @mouseup.stop
            @touchstart.stop
            @touchend.stop
          >
            <div class="action-icon" title="编辑" @click.stop="editNote(n)">
              <img src="https://api.iconify.design/mdi/pencil.svg" alt="edit" width="20" height="20" />
            </div>
            <div class="action-icon danger" title="删除" @click.stop="deleteNote(n)">
              <img src="https://api.iconify.design/mdi/delete.svg" alt="delete" width="20" height="20" />
            </div>
          </div>

          <!-- 非编辑态内容展示 -->
          <template v-if="!n.editing">
          <div class="note-tags top-right" v-if="parsedTags(n.tags).length">
            <el-tag v-for="t in parsedTags(n.tags)" :key="t" size="small" style="margin-left:6px;">#{{ t }}</el-tag>
          </div>
          <div class="note-content">{{ n.content }}</div>
          <div class="meta bottom-left">
            <el-tag size="small" :type="n.isPublic ? 'success' : 'info'">{{ n.isPublic ? '公开' : '私有' }}</el-tag>
          </div>
          <div class="meta bottom-right">
            <span class="time">更新：{{ formatTime(n.updatedAt || n.updated_at) }}</span>
          </div>
          </template>

          <!-- 编辑态：内容与公开/私有选择 -->
          <template v-else>
            <!-- 编辑态：右上始终展示源标签（有则显示） -->
            <div class="note-tags top-right" v-if="parsedTags(n.tags).length">
              <el-tag v-for="t in parsedTags(n.tags)" :key="t" size="small" style="margin-left:6px;">#{{ t }}</el-tag>
            </div>
            <div class="edit-form">
              <div class="edit-toolbar">
                <span class="label">内容</span>
              </div>
              <div class="textarea-highlight-wrapper" :data-note-id="n.id" ref="setWrapperRef(n)">
                <div class="highlight-layer" v-html="highlightHTML(n.contentEdit)"></div>
                <el-input
                  v-model="n.contentEdit"
                  type="textarea"
                  :rows="4"
                  placeholder="内容与标签一起输入；标签以#开头，逗号分隔。例如：今天完成了任务 #工作,#计划"
                />
              </div>
            </div>
            <div class="edit-footer">
              <div class="left">
                <el-switch
                  v-model="n.isPublicEdit"
                  active-text="公开"
                  inactive-text="私有"
                  inline-prompt
                  size="small"
                />
              </div>
              <div class="right edit-actions">
                <el-button size="small" @click="cancelEdit(n)">取消</el-button>
                <el-button size="small" type="primary" @click="saveEdit(n)">保存</el-button>
              </div>
            </div>
          </template>
        </div>
        
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import { http } from '@/api/http';
import { ElMessage, ElMessageBox } from 'element-plus';

const notes = ref([]);
const me = reactive({ username:'', nickname:'' });
const authorName = computed(() => me.nickname || me.username || '我');

function parsedTags(tags){
  if (Array.isArray(tags)) return tags;
  if (typeof tags === 'string') return tags.split(',').map(t => t.trim().replace(/^#/, '')).filter(Boolean);
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
      showActions: false,
      editing: false,
      contentEdit: it.content,
      isPublicEdit: it.isPublic ?? it.is_public ?? false,
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
function luminance({r,g,b}){
  return 0.2126*(r/255) + 0.7152*(g/255) + 0.0722*(b/255);
}
function noteCardStyle(n){
  const rgb = parseHexColor(n.color);
  if (!rgb) return {};
  const fg = luminance(rgb) > 0.6 ? '#303133' : '#ffffff';
  return {
    borderLeft: `6px solid rgba(${rgb.r},${rgb.g},${rgb.b},0.6)`,
    background: (typeof n.color === 'string' ? n.color.trim() : `rgba(${rgb.r},${rgb.g},${rgb.b},0.18)`),
    '--fgColor': fg
  };
}

onMounted(() => { loadMe(); loadNotes(); });

// 长按动作菜单
const pressTimer = ref(null);
const activeNoteId = ref(null);
function onDocClick(e){
  if (!activeNoteId.value) return;
  const card = document.querySelector(`[data-note-id="${activeNoteId.value}"]`);
  if (!card || !card.contains(e.target)) {
    const note = notes.value.find(x => x.id === activeNoteId.value);
    if (note) note.showActions = false;
    activeNoteId.value = null;
    document.removeEventListener('click', onDocClick, true);
  }
}
function startPress(n){
  // 若已显示动作菜单，避免重复触发并隐藏
  if (n.showActions) return;
  // 编辑态下不显示长按菜单
  if (n.editing) return;
  cancelPress();
  pressTimer.value = setTimeout(() => {
    n.showActions = true;
    activeNoteId.value = n.id;
    document.addEventListener('click', onDocClick, true);
  }, 600);
}
function cancelPress(){
  if (pressTimer.value){
    clearTimeout(pressTimer.value);
    pressTimer.value = null;
  }
}

function editNote(n){
  n.showActions = false;
  n.editing = true;
  n.contentEdit = n.content;
  n.isPublicEdit = n.isPublic;
  // 将缺失的标签拼入内容末尾，便于直接在内容中编辑
  const existingArr = parsedTags(n.tags);
  const inContentArr = parseTagsFromText(n.contentEdit);
  const missing = existingArr.filter(t => !inContentArr.includes(t));
  if (missing.length){
    const suffix = missing.map(t => `#${t}`).join(',');
    n.contentEdit = (n.contentEdit || '').trim();
    // 将缺失的标签拼接到内容的下一行，便于视觉区分
    n.contentEdit = n.contentEdit ? `${n.contentEdit}\n${suffix}` : suffix;
  }
}
function cancelEdit(n){
  n.editing = false;
}
async function saveEdit(n){
  try{
    const parsedArr = parseTagsFromText(n.contentEdit);
    const existingArr = parsedTags(n.tags);
    const useParsed = parsedArr.length > 0;
    const finalTagsArr = useParsed ? parsedArr : existingArr;
    const contentClean = useParsed ? stripTagsFromText(n.contentEdit) : n.contentEdit;
    const payload = {
      content: contentClean,
      tags: finalTagsArr.join(','),
      archived: n.archived ?? false,
      is_public: n.isPublicEdit,
      color: (typeof n.color === 'string' ? n.color.trim() : '')
    };
    const { data } = await http.put(`/notes/${n.id}`, payload);
    // 后端可能返回更新后的便签，若无则使用编辑值回填
    const updated = data || payload;
    n.content = updated.content ?? contentClean;
    n.isPublic = (updated.isPublic ?? updated.is_public) ?? n.isPublicEdit;
    n.tags = updated.tags ?? finalTagsArr.join(',');
    n.editing = false;
    ElMessage.success('已保存修改');
  }catch(e){
    ElMessage.error('保存失败，请稍后再试');
  }
}

async function deleteNote(n){
  try{
    await ElMessageBox.confirm('确定要删除这条便签吗？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    });
    await http.delete(`/notes/${n.id}`);
    notes.value = notes.value.filter(m => m.id !== n.id);
    ElMessage.success('已删除');
  }catch(e){
    // 取消或错误时不提示错误信息
  }finally{
    n.showActions = false;
    if (activeNoteId.value === n.id) {
      activeNoteId.value = null;
      document.removeEventListener('click', onDocClick, true);
    }
  }
}

function closeActions(n){
  n.showActions = false;
  if (activeNoteId.value === n.id) {
    activeNoteId.value = null;
    document.removeEventListener('click', onDocClick, true);
  }
}

function parseTagsFromText(s){
  if (!s || typeof s !== 'string') return [];
  const re = /#([\p{L}\w-]+)/gu;
  const set = new Set();
  let m;
  while ((m = re.exec(s))){
    const tag = (m[1] || '').trim();
    if (tag) set.add(tag);
  }
  return Array.from(set);
}
function stripTagsFromText(s){
  if (!s || typeof s !== 'string') return '';
  // 移除以#开头的标签以及其后的逗号（若有），并规范空白
  return s.replace(/\s*#([\p{L}\w-]+)\s*(,\s*)?/gu, ' ').replace(/\s{2,}/g, ' ').trim();
}

// 轻量高亮：在编辑态对正文中的 #标签 做背景高亮
const wrapperRefs = new Map();
function setWrapperRef(n){
  return (el) => {
    if (el) wrapperRefs.set(n.id, el); else wrapperRefs.delete(n.id);
  };
}
function escapeHtml(str){
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}
function highlightHTML(s){
  const text = typeof s === 'string' ? s : '';
  const re = /#([\p{L}\w-]+)/gu;
  let out = '';
  let last = 0;
  for (const m of text.matchAll(re)){
    const i = m.index ?? 0;
    const full = m[0] ?? '';
    out += escapeHtml(text.slice(last, i));
    out += `<span class="hl-tag">${escapeHtml(full)}</span>`;
    last = i + full.length;
  }
  out += escapeHtml(text.slice(last));
  return out;
}
</script>

<style scoped>
.note-card { background:#fff; border-radius:12px; padding:12px 12px 32px; box-shadow:0 4px 12px rgba(0,0,0,0.08); position:relative; }
.note-card.editing { box-shadow:0 0 0 3px rgba(64,158,255,0.14), 0 4px 12px rgba(0,0,0,0.08); }
.note-content { white-space:pre-wrap; line-height:1.7; color:#303133; margin:4px 0 6px; }
.note-card.editing .note-content { color: var(--fgColor, #303133); }
.note-tags { display:flex; flex-wrap:wrap; gap:6px; }
.note-tags.top-right { position:absolute; top:8px; right:12px; }
.meta.bottom-left { position:absolute; left:12px; bottom:10px; }
.meta.bottom-right { position:absolute; right:12px; bottom:10px; color:#606266; font-size:12px; }
.author-above { color:#606266; font-size:12px; margin: 0 0 6px 0; }

/* 长按动作菜单覆盖层 */
.actions-overlay {
  position:absolute;
  inset:0;
  background: rgba(0,0,0,0.08);
  display:flex;
  align-items:center;
  justify-content:center;
  gap:12px;
  border-radius:12px;
}

.edit-actions { display:flex; gap:8px; align-items:center; }

/* 图标动作按钮 */
.action-icon {
  width:40px; height:40px; border-radius:50%; background:#fff;
  box-shadow:0 6px 16px rgba(0,0,0,0.12);
  display:flex; align-items:center; justify-content:center;
  cursor:pointer;
}
.action-icon.danger { background:#fff0f0; }
.action-icon:hover { transform: translateY(-1px); transition: transform 0.12s ease; }

/* 编辑态布局优化 */
.edit-form { display:flex; flex-direction:column; gap:10px; padding-bottom:38px; }
.edit-toolbar { display:flex; align-items:center; gap:8px; }
.edit-toolbar .spacer { flex:1; }
.edit-toolbar .label { font-size:12px; color:#606266; }
.edit-row { display:flex; align-items:center; gap:8px; }
.edit-row .label { font-size:12px; color:#606266; }
.tags-preview { display:flex; flex-wrap:wrap; gap:6px; }
.edit-footer { position:absolute; left:12px; right:12px; bottom:10px; display:flex; align-items:center; justify-content:space-between; }
.edit-footer .left { display:flex; align-items:center; }
.edit-footer .edit-actions { gap:8px; }

/* 编辑态：#标签轻微高亮（不占额外空间） */
.textarea-highlight-wrapper { position: relative; }
.textarea-highlight-wrapper .highlight-layer {
  position: absolute; inset: 0;
  padding: 6px 12px; /* 对齐 textarea 内边距 */
  white-space: pre-wrap; word-break: break-word;
  pointer-events: none; /* 不拦截输入 */
  color: transparent; /* 普通文本透明，仅显示高亮片段 */
}
.textarea-highlight-wrapper .highlight-layer .hl-tag {
  background: rgba(64,158,255,0.15);
  border-radius: 4px;
  padding: 0 2px;
  color: #409eff;
}
.edit-form :deep(.el-textarea__inner) {
  background: transparent !important;
  color: var(--fgColor, #303133) !important;
  caret-color: var(--fgColor, #303133);
}
</style>