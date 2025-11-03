<template>
  <!-- 接入统一布局：使用 TwoPaneLayout 提供“全宽吸顶顶栏 + 右侧正文滚动”
       改造要点：
       1) 将页面原本的本地顶栏移除，改为公共顶栏 AppTopBar；
       2) 顶栏放在 topFull 插槽中，保持全宽并吸顶；
       3) 页面主体（个人资料、过滤栏与列表）放在 rightMain 插槽中；
       4) 将回到顶部组件指定 target 为布局的滚动容器（.scrollable-content），确保滚动联动正常。 -->
  <TwoPaneLayout class="my-notes-layout">
    <!-- 公共顶栏：统一风格与交互；fluid 让中间区域（搜索）铺满宽度 -->
    <template #topFull>
      <AppTopBar fluid />
    </template>

    <!-- 右侧正文：保留原页面主体结构，仅移除了本地顶栏 -->
    <template #rightMain>
      <div class="container" :style="{ '--filtersH': filtersHeight + 'px' }">
        <!-- 个人资料摘要（显示在过滤栏上方） -->
        <div class="profile-summary">
          <img v-if="me.avatarUrl" :src="avatarUrl" alt="avatar" class="avatar-lg" width="260" height="260" loading="lazy" />
          <img v-else src="https://api.iconify.design/mdi/account-circle.svg" alt="avatar" class="avatar-lg" width="260" height="260" />
          <div class="text">
            <div class="nickname">{{ me.nickname || me.username || '未设置昵称' }}</div>
            <div
              class="signature"
              :class="[ signatureExpanded ? 'signature-full' : 'signature-ellipsis-3' ]"
              :title="me.signature || '未设置'"
              ref="signatureRef"
            >
              {{ me.signature || '未设置' }}
            </div>
            <a v-if="signatureOverflow" class="sig-toggle" @click="toggleSignature">{{ signatureExpanded ? '收起' : '展开' }}</a>
          </div>
        </div>

        <!-- 过滤与排序栏（sticky：在右侧滚动容器内粘顶） -->
        <div class="filters" :class="{ 'is-stuck': isStuck }" ref="filtersRef">
          <el-form :inline="true" label-width="80px" class="filters-form">
            <!-- 第一行：左侧分组（时间范围/标签/公开性） + 右侧搜索 -->
            <div class="top-row">
              <div class="top-left">
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="filters.range"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                size="small"
                style="width:180px"
              />
            </el-form-item>
            <el-form-item label="标签">
              <el-select v-model="filters.tags" multiple filterable allow-create default-first-option placeholder="选择或输入标签" size="small" style="width:140px">
                <el-option v-for="t in allTags" :key="t" :label="'#' + t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="公开性">
              <el-select v-model="filters.visibility" size="small" style="width:60px">
                <el-option label="全部" value="all" />
                <el-option label="公开" value="public" />
                <el-option label="私有" value="private" />
              </el-select>
            </el-form-item>
              </div>
              <div class="top-right">
                <el-form-item label="搜索">
                  <el-input
                    v-model="filters.query"
                    size="small"
                    clearable
                    placeholder="搜索我的便签..."
                    style="width:200px"
                    @keyup.enter="triggerSearchPulse"
                  >
                    <template #prefix>
                      <img src="https://api.iconify.design/mdi/magnify.svg" alt="search" width="16" height="16" />
                    </template>
                  </el-input>
                </el-form-item>
              </div>
            </div>
            <div class="flex-break" aria-hidden="true"></div>
            <!-- 第二行：排序在左侧；清空在右侧 -->
            <el-form-item label="排序">
              <span class="sort-inline" style="width:260px">
                <el-radio-group v-model="filters.sortBy" size="small">
                  <el-radio-button label="time">时间</el-radio-button>
                  <el-radio-button label="likes">点赞数</el-radio-button>
                </el-radio-group>
                <el-tooltip content="切换升/降序" placement="top">
                  <el-button size="small" class="order-toggle" @click="toggleOrder">
                    <img v-if="filters.sortOrder==='desc'" src="https://api.iconify.design/mdi/sort-descending.svg" alt="desc" width="18" height="18" />
                    <img v-else src="https://api.iconify.design/mdi/sort-ascending.svg" alt="asc" width="18" height="18" />
                  </el-button>
                </el-tooltip>
              </span>
            </el-form-item>
            <el-form-item class="pull-right">
              <el-button @click="resetFilters">清空</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 年份分组时间线 -->
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
              <transition name="overlay">
                <div
                  v-if="n.showActions"
                  class="actions-overlay"
                  @click="closeActions(n)"
                  @mousedown.stop
                  @mouseup.stop
                  @touchstart.stop
                  @touchend.stop
                >
                <div class="action-icon" :title="n.liked ? '取消喜欢' : '喜欢'" @click.stop="toggleLike(n)">
                  <img :src="n.liked ? 'https://api.iconify.design/mdi/heart.svg?color=%23e25555' : 'https://api.iconify.design/mdi/heart-outline.svg'" alt="like" width="20" height="20" />
                </div>
            <div class="action-icon" :title="n.favorited ? '取消收藏' : '收藏'" @click.stop="toggleFavorite(n)">
              <img :src="n.favorited ? 'https://api.iconify.design/mdi/bookmark.svg?color=%23409eff' : 'https://api.iconify.design/mdi/bookmark-outline.svg'" alt="favorite" width="20" height="20" />
            </div>
            <div class="action-icon" title="编辑" @click.stop="editNote(n)">
              <img src="https://api.iconify.design/mdi/pencil.svg" alt="edit" width="20" height="20" />
            </div>
            <div class="action-icon danger" title="删除" @click.stop="deleteNote(n)">
              <img src="https://api.iconify.design/mdi/delete.svg" alt="delete" width="20" height="20" />
            </div>
            </div>
          </transition>

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
      </transition-group>
            </el-timeline>
          </div>
        </div>
      </div>
      <!-- 加载更多区域：按钮 + 触底哨兵。桌面端可点击，移动端滚动到底自动触发 -->
      <div class="load-more-container">
        <button class="load-more-btn" :disabled="isLoading || !hasNext" @click="loadMore">
          {{ isLoading ? '加载中…' : (hasNext ? '加载更多' : '已无更多') }}
        </button>
        <!-- 触底哨兵：进入视口时自动加载下一页；在没有更多或正在加载时隐藏 -->
        <div v-show="hasNext && !isLoading" ref="loadMoreSentinel" class="load-more-sentinel" aria-hidden="true"></div>
      </div>
    </template>
  </TwoPaneLayout>
  <!-- 右下：回到顶部组件（可见高度 360px 后出现）
       指定 target 为 TwoPaneLayout 的滚动容器（.scrollable-content），
       以便在“右侧正文滚动”模式下仍能正确工作。 -->
  <el-backtop target=".scrollable-content" :right="80" :bottom="100" :visibility-height="360">
    <div class="backtop-btn" title="回到顶部">
      <img src="https://api.iconify.design/mdi/arrow-up.svg" alt="up" width="20" height="20" />
    </div>
  </el-backtop>
</template>

<script setup>
// 引入统一布局与公共顶栏组件：
// - TwoPaneLayout：提供“全宽吸顶顶栏 + 右侧正文滚动”的通用布局结构；
// - AppTopBar：公共顶栏，统一品牌与快捷入口，支持透明/毛玻璃切换与搜索。
import TwoPaneLayout from '@/components/TwoPaneLayout.vue';
import AppTopBar from '@/components/AppTopBar.vue';
import { ref, reactive, onMounted, onUnmounted, computed, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { http, avatarFullUrl } from '@/api/http';
import { ElMessage, ElMessageBox } from 'element-plus';

const notes = ref([]);
const me = reactive({ username:'', nickname:'', avatarUrl:'', signature:'' });
const avatarUrl = computed(() => avatarFullUrl(me.avatarUrl));
const authorName = computed(() => me.nickname || me.username || '我');
const router = useRouter();

// —— 服务端分页状态（我的便签页）——
// 当前页码（从 1 开始）
const page = ref(1);
// 每页条数（建议 20，避免一次性加载过多）
const size = ref(20);
// 总条数（由后端返回，用于判断是否还有下一页）
const total = ref(0);
// 是否正在加载（防止并发）
const isLoading = ref(false);
// 是否还有下一页：当已加载数量小于总数时继续加载
const hasNext = computed(() => notes.value.length < total.value);
// 触底加载哨兵
const loadMoreSentinel = ref(null);
let sentinelObserver = null;
// 签名展开/收起
const signatureExpanded = ref(false);
const signatureOverflow = ref(false);
const signatureRef = ref(null);
function toggleSignature(){ signatureExpanded.value = !signatureExpanded.value; }
function checkSignatureOverflow(){
  const el = signatureRef.value;
  if (!el) { signatureOverflow.value = false; return; }
  nextTick(() => {
    const wasExpanded = signatureExpanded.value;
    // 强制切到折叠状态测量可见高度
    el.classList.add('signature-ellipsis-3');
    el.classList.remove('signature-full');
    const collapsedH = el.getBoundingClientRect().height;
    // 强制切到展开状态测量完整高度
    el.classList.add('signature-full');
    el.classList.remove('signature-ellipsis-3');
    const expandedH = el.getBoundingClientRect().height;
    // 还原原始状态
    if (!wasExpanded){
      el.classList.add('signature-ellipsis-3');
      el.classList.remove('signature-full');
    }
    signatureOverflow.value = expandedH > collapsedH + 1;
  });
}
watch(() => me.signature, () => { signatureExpanded.value = false; checkSignatureOverflow(); });
onMounted(() => { checkSignatureOverflow(); window.addEventListener('resize', checkSignatureOverflow); });
onUnmounted(() => { window.removeEventListener('resize', checkSignatureOverflow); });
watch(signatureExpanded, () => { nextTick(checkSignatureOverflow); });

// 过滤与排序状态
const filters = reactive({
  visibility: 'all', // all | public | private
  range: null,       // [startDate, endDate]
  tags: [],          // array of tag strings
  query: '',         // content search query
  sortBy: 'time',    // time | likes
  sortOrder: 'desc', // desc | asc
});
function resetFilters(){
  filters.visibility = 'all';
  filters.range = null;
  filters.tags = [];
  filters.query = '';
  filters.sortBy = 'time';
  filters.sortOrder = 'desc';
}

// 搜索框 Enter 动画反馈状态与触发方法
const searchPulse = ref(false);
function triggerSearchPulse(){
  // 通过切换类名来触发一次性动画
  searchPulse.value = false;
  requestAnimationFrame(() => {
    searchPulse.value = true;
    setTimeout(() => { searchPulse.value = false; }, 400);
  });
}

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

// 月-日 时:分 格式（中文样式）
function pad(n){ return String(n).padStart(2, '0'); }
function formatMD(t){
  if (!t) return '';
  try{
    const d = new Date(t);
    if (isNaN(d.getTime())) return '';
    const M = pad(d.getMonth()+1);
    const D = pad(d.getDate());
    const h = pad(d.getHours());
    const m = pad(d.getMinutes());
    return `${M}月${D}日 ${h}:${m}`;
  }catch{ return ''; }
}

function goMessages(){ router.push('/messages'); }
function goLikes(){ router.push('/likes'); }
function goFavorites(){ router.push('/favorites'); }
function goHistory(){ router.push('/history'); }

async function loadMe(){
  try{
  // 说明：我的便签页在初始化时获取用户信息。
  // 若在未登录或后端短暂校验失败返回 401，这里采用静默处理，避免触发全局 401 重定向和干扰导航。
  const { data } = await http.get('/account/me', { suppress401Redirect: true });
    Object.assign(me, data);
  }catch(e){ /* 忽略错误 */ }
}

// 将后端返回的便签项映射为页面内部结构，并累加到列表
function appendMappedItems(items){
  if (!Array.isArray(items) || items.length === 0) return;
  const mapped = items.map(it => ({
    ...it,
    isPublic: it.isPublic ?? it.is_public ?? false,
    likeCount: Number(it.likeCount ?? it.like_count ?? 0),
    liked: Boolean(it.liked ?? it.likedByMe ?? it.liked_by_me ?? false),
    favoriteCount: Number(it.favoriteCount ?? it.favorite_count ?? 0),
    favorited: Boolean(it.favoritedByMe ?? it.favorited_by_me ?? it.favorited ?? false),
    showActions: false,
    editing: false,
    contentEdit: it.content,
    isPublicEdit: it.isPublic ?? it.is_public ?? false,
  }));
  notes.value = notes.value.concat(mapped);
}

// 拉取指定页的数据，并累加到列表
async function fetchPage(targetPage){
  if (isLoading.value) return;
  isLoading.value = true;
  try{
    const { data } = await http.get('/notes', {
      params: { size: size.value, page: targetPage, mineOnly: true },
      suppress401Redirect: true,
    });
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? []);
    const t = data?.total ?? data?.count ?? 0;
    total.value = Number.isFinite(t) ? Number(t) : (notes.value.length + (items?.length || 0));
    appendMappedItems(items || []);
    page.value = targetPage;
  }catch(e){
    ElMessage.error('加载我的便签失败');
  }finally{
    isLoading.value = false;
  }
}

// 重新加载（重置列表并拉取第 1 页）
async function reload(){
  total.value = 0;
  page.value = 1;
  notes.value = [];
  await fetchPage(1);
}

// 加载下一页
async function loadMore(){
  if (!hasNext.value || isLoading.value) return;
  await fetchPage(page.value + 1);
}

// 初始化触底加载：当哨兵进入视口时自动触发下一页加载
async function setupInfiniteScroll(){
  await nextTick();
  if (!loadMoreSentinel.value) return;
  if (sentinelObserver){ try{ sentinelObserver.disconnect(); }catch{} }
  sentinelObserver = new IntersectionObserver((entries) => {
    const entry = entries[0];
    if (entry?.isIntersecting){ loadMore(); }
  }, { root: null, rootMargin: '0px', threshold: 1.0 });
  sentinelObserver.observe(loadMoreSentinel.value);
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

onMounted(async () => {
  loadMe();
  await reload();
  await setupInfiniteScroll();
});

// 吸顶状态检测（用于视觉强调）
const filtersRef = ref(null);
const isStuck = ref(false);
const filtersHeight = ref(0);
function updateStickyState(){
  const el = filtersRef.value;
  if (!el) return;
  const top = el.getBoundingClientRect().top;
  isStuck.value = top <= 0;
  // 同步过滤栏当前高度，用于年份吸顶偏移
  filtersHeight.value = el.offsetHeight || 0;
}
onMounted(() => {
  window.addEventListener('scroll', updateStickyState, { passive: true });
  updateStickyState();
  // 监听过滤栏尺寸变化，动态更新高度变量
  const el = filtersRef.value;
  if (el && 'ResizeObserver' in window){
    const ro = new ResizeObserver(() => {
      filtersHeight.value = el.offsetHeight || 0;
    });
    ro.observe(el);
  }
});
onUnmounted(() => {
  window.removeEventListener('scroll', updateStickyState);
});

// 组件卸载时清理触底观察器，避免泄漏
onUnmounted(() => {
  if (sentinelObserver){ try{ sentinelObserver.disconnect(); }catch{} }
  sentinelObserver = null;
});

// 所有标签集合（去重）
const allTags = computed(() => {
  const set = new Set();
  for (const n of notes.value){
    for (const t of parsedTags(n.tags)) set.add(t);
  }
  return Array.from(set);
});

// 过滤与排序后的结果
const filteredNotes = computed(() => {
  let arr = notes.value.slice();
  // 过滤：公开性
  if (filters.visibility !== 'all'){
    const target = filters.visibility === 'public';
    arr = arr.filter(n => Boolean(n.isPublic) === target);
  }
  // 过滤：时间范围（按更新时间，有则用，否则用创建时间）
  if (Array.isArray(filters.range) && filters.range.length === 2 && filters.range[0] && filters.range[1]){
    const start = new Date(filters.range[0]).getTime();
    const end = new Date(filters.range[1]).getTime();
    arr = arr.filter(n => {
      const t = new Date(n.createdAt || n.created_at || 0).getTime();
      return t >= start && t <= end;
    });
  }
  // 过滤：标签（包含任意一个所选标签）
  if (Array.isArray(filters.tags) && filters.tags.length > 0){
    arr = arr.filter(n => {
      const tags = parsedTags(n.tags);
      return filters.tags.some(t => tags.includes(t));
    });
  }
  // 过滤：内容关键词（大小写不敏感）
  if (filters.query && typeof filters.query === 'string' && filters.query.trim()){
    const q = filters.query.trim().toLowerCase();
    arr = arr.filter(n => String(n.content || '').toLowerCase().includes(q));
  }
  // 排序
  const by = filters.sortBy;
  const dir = filters.sortOrder === 'asc' ? 1 : -1;
  arr.sort((a,b) => {
    let av, bv;
    if (by === 'likes'){
      av = Number(a.likeCount || 0);
      bv = Number(b.likeCount || 0);
    }else{
      av = new Date(a.createdAt || a.created_at || 0).getTime();
      bv = new Date(b.createdAt || b.created_at || 0).getTime();
    }
    return (av - bv) * dir;
  });
  return arr;
});

// 按年份分组（保持 filteredNotes 的排序顺序）
const yearGroups = computed(() => {
  const map = new Map();
  for (const n of filteredNotes.value){
    const t = new Date(n.createdAt || n.created_at || 0);
    const year = isNaN(t.getTime()) ? '未知' : t.getFullYear();
    if (!map.has(year)) map.set(year, []);
    map.get(year).push(n);
  }
  // 保持出现顺序
  const groups = [];
  for (const [year, items] of map.entries()) groups.push({ year, items });
  return groups;
});

function toggleOrder(){
  filters.sortOrder = (filters.sortOrder === 'desc' ? 'asc' : 'desc');
}

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
      // 说明：后端 NoteRequest.java 使用 camelCase 字段 isPublic，
      // 之前发送 is_public（snake_case）未被绑定，导致公开状态丢失并按默认“私有”保存。
      // 这里改为 isPublic，确保后端正确持久化用户的公开选择。
      isPublic: n.isPublicEdit,
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

// 喜欢与收藏
async function toggleLike(n){
  if (n.likeLoading) return;
  n.likeLoading = true;
  try{
    const url = n.liked ? `/notes/${n.id}/unlike` : `/notes/${n.id}/like`;
    const { data } = await http.post(url);
    n.likeCount = Number(data?.count ?? data?.like_count ?? (n.likeCount || 0));
    n.liked = Boolean((data?.likedByMe ?? data?.liked_by_me ?? n.liked));
  }catch(e){
    ElMessage.error('操作失败');
  }finally{
    n.likeLoading = false;
  }
}

async function toggleFavorite(n){
  if (n.favoriteLoading) return;
  n.favoriteLoading = true;
  try{
    const url = n.favorited ? `/notes/${n.id}/unfavorite` : `/notes/${n.id}/favorite`;
    const { data } = await http.post(url);
    n.favoriteCount = Number(data?.count ?? data?.favorite_count ?? (n.favoriteCount || 0));
    n.favorited = Boolean((data?.favoritedByMe ?? data?.favorited_by_me ?? n.favorited));
  }catch(e){
    ElMessage.error('操作失败');
  }finally{
    n.favoriteLoading = false;
  }
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
 /* 移除本地顶栏相关样式（使用公共顶栏 AppTopBar） */
 /* 其它样式保持不变，正文仍在右侧滚动容器中进行滚动与粘顶 */
.note-card { background:#fff; border-radius:12px; padding:12px 12px 32px; box-shadow:0 4px 12px rgba(0,0,0,0.08); position:relative; }
.note-card.editing { box-shadow:0 0 0 3px rgba(64,158,255,0.14), 0 4px 12px rgba(0,0,0,0.08); }
.note-content { white-space:pre-wrap; line-height:1.7; color:#303133; margin:4px 0 6px; }
.note-card.editing .note-content { color: var(--fgColor, #303133); }
.note-tags { display:flex; flex-wrap:wrap; gap:6px; }
.note-tags.top-right { position:absolute; top:8px; right:12px; }
.meta.bottom-left { position:absolute; left:12px; bottom:10px; }
.meta.bottom-right { position:absolute; right:12px; bottom:10px; color:#606266; font-size:12px; }
.author-above { color:#606266; font-size:12px; margin: 0 0 6px 0; }

/* 年份分组样式（层次更明显） */
.year-group { margin-bottom: 16px; }
.year-header { display:flex; align-items:center; padding:10px 12px; border-radius:12px; background:#ffffff; box-shadow: 0 6px 20px rgba(0,0,0,0.06); position: sticky; top: calc(var(--filtersH, 48px) + 6px); z-index: 10; }
.year-title { font-size:22px; font-weight:700; color:#303133; letter-spacing:0.5px; }
.year-header::before { content:''; display:block; width:6px; height:24px; border-radius:6px; background:#409eff; margin-right:10px; opacity:0.85; }

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

/* 过滤栏样式优化 */
.filters { background:#fff; border-radius:12px; padding:10px 12px; box-shadow:0 4px 12px rgba(0,0,0,0.06); margin-bottom:12px; }
.filters-form :deep(.el-form-item) { margin-bottom: 0; margin-right: 26px; }
.order-toggle { padding:4px 8px; }
.sort-inline { display:inline-flex; align-items:center; gap:8px; }
.filters-form { display:flex; flex-wrap:wrap; align-items:center; }
.top-row { display:flex; align-items:center; justify-content:space-between; width:100%; flex: 1 1 100%; }
.top-left { display:flex; flex-wrap:wrap; align-items:center; flex: 1 1 auto; min-width: 0; }
.top-right { display:flex; align-items:center; flex: 0 0 auto; }
.filters-form .pull-right { margin-left:auto; margin-right:0; }
.filters-form .flex-break { flex-basis: 100%; height: 0; }
/* 搜索项按内容自适应宽度（消除右侧空白占位），仍保留右对齐 */
.filters-form .aligned-340 { flex: 0 0 auto; width: auto; min-width: 240px; margin-right:0; }
@media (max-width: 480px){
  .filters-form .aligned-340 { min-width: 200px; }
}
/* 强制统一标签宽度，避免因样式覆盖导致偏差 */
.filters-form :deep(.el-form-item__label){ width: 80px !important; }

/* 吸顶效果 */
.filters { position: sticky; top: 0; z-index: 20; }
.filters.is-stuck { backdrop-filter: saturate(180%) blur(8px); background: rgba(255,255,255,0.85); box-shadow: 0 6px 20px rgba(0,0,0,0.12); border: 1px solid rgba(0,0,0,0.06); }

/* 列表过渡动画（重排/进出） */
.list-enter-active, .list-leave-active { transition: all .25s ease; will-change: transform, opacity; }
.list-enter-from, .list-leave-to { opacity: 0; transform: translateY(8px) scale(0.98); }
.list-move { transition: transform .25s ease; }

/* 长按动作菜单过渡 */
.overlay-enter-active, .overlay-leave-active { transition: opacity .18s ease, transform .18s ease; }
.overlay-enter-from, .overlay-leave-to { opacity: 0; transform: scale(0.98); }

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
/* 搜索框美化 */
.search-input :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: #f5f7fa;
  box-shadow: none;
  transition: box-shadow .15s ease, background-color .15s ease;
}
.search-input :deep(.el-input__wrapper:hover) {
  background: #f4f6f9;
}
.search-input :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 2px rgba(64,158,255,0.25), 0 4px 10px rgba(0,0,0,0.06);
}
.search-input :deep(.el-input__prefix) {
  margin-right: 4px;
  opacity: 0.7;
}
.search-input :deep(input::placeholder) {
  color: #909399;
}
/* Enter 轻微动画反馈（柔和扩散阴影） */
.search-input.pulse :deep(.el-input__wrapper){
  animation: pulseRing 400ms ease;
}
@keyframes pulseRing{
  0%{ box-shadow: 0 0 0 0 rgba(64,158,255,0.35); }
  100%{ box-shadow: 0 0 0 8px rgba(64,158,255,0); }
}

/* 右下回到顶部按钮美化 */
.container :deep(.el-backtop){ z-index: 120; background: transparent; box-shadow: none; }
.backtop-btn{
  width: 44px; height: 44px; border-radius: 999px;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  display:flex; align-items:center; justify-content:center; color:#fff;
  box-shadow: 0 8px 24px rgba(64,158,255,0.30), 0 2px 6px rgba(0,0,0,0.12);
  transition: transform .15s ease, box-shadow .15s ease, filter .15s ease;
}
.backtop-btn:hover{ transform: translateY(-2px); box-shadow: 0 12px 28px rgba(64,158,255,0.38), 0 4px 10px rgba(0,0,0,0.14); }
.backtop-btn:active{ transform: translateY(0); filter: brightness(0.96); }
/* 个人资料摘要（头像 + 文本）样式 */
.profile-summary { display:flex; flex-direction:column; align-items:center; justify-content:center; gap:8px; background: transparent; border-radius:12px; padding:12px; box-shadow:none; margin-bottom:12px; }
.profile-summary .avatar-lg { width:260px; height:260px; max-width:260px; max-height:260px; display:block; border-radius:50%; object-fit:cover; overflow:hidden; flex-shrink:0; border:3px solid #fff; box-shadow:0 4px 12px rgba(0,0,0,0.12); background:#fff; }
.profile-summary .text { display:flex; flex-direction:column; align-items:center; text-align:center; min-width:0; max-width:360px; gap:6px; }
.profile-summary .nickname { font-weight:700; color:#303133; font-size:20px; letter-spacing:0.3px; line-height:1.2; }
.profile-summary .nickname::after { content:''; display:block; width:28px; height:3px; border-radius:3px; background:#409eff; opacity:0.85; margin:6px auto 0; }
.profile-summary .signature { color:#606266; font-size:14px; font-style:italic; line-height:1.6; opacity:0.9; }
.signature-ellipsis-3 { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; word-break: break-word; white-space: pre-line; }
.signature-full { -webkit-line-clamp: unset; display: block; overflow: visible; white-space: pre-line; }
.sig-toggle { color: var(--el-color-primary); font-size:13px; cursor:pointer; user-select:none; margin-top:4px; }
.signature-ellipsis { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; word-break: break-word; white-space: pre-line; }

/* 加载更多区域样式（与 Likes/Favorites 页面保持一致体验） */
.load-more-container { display:flex; flex-direction:column; align-items:center; gap:8px; margin: 16px 0 32px; }
.load-more-btn { padding:8px 16px; border-radius:6px; border:1px solid #dcdfe6; background:#f5f7ff; color:#409eff; cursor:pointer; }
.load-more-btn:disabled { opacity:0.6; cursor:not-allowed; }
.load-more-sentinel { width:100%; max-width:640px; height:1px; }
</style>