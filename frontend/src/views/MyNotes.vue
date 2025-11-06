<!--
  我的拾言视图（MyNotes）
  职责与结构：
  - 两栏统一布局：复用 TwoPaneLayout 的“全宽吸顶顶栏 + 右侧正文滚动容器”；
  - 顶栏：AppTopBar 统一品牌与快捷入口；正文含个人资料摘要、过滤栏与拾言列表；
  - 提供“回到顶部”与触底加载，兼容移动端与桌面端的滚动体验。
  数据与接口：
  - 服务端分页：通过 /shiyan?q&page&size 拉取数据，使用 total 判断是否还有下一页；
  - 创建/更新/删除：POST/PUT/DELETE /shiyan 及 /shiyan/{id}；
  - 点赞/收藏：POST /shiyan/{id}/like|unlike 与 /favorite|unfavorite；字段统一映射与兼容处理。
  细节与修复：
  - Emoji 粘贴修复：将聊天应用中的图片表情映射为 Unicode Emoji（emojiMap）；
  - 编辑态聚焦管理与防并发加载；过滤栏与作者信息展示对齐。
  安全与权限：
  - 页面需要登录（路由 meta.requiresAuth）；
  - 写操作仅允许作者本人或具备权限的用户；错误统一提示，避免信息泄露。
-->
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
                    placeholder="搜索我的拾言..."
                    style="width:200px"
                    @keyup.enter="triggerSearchPulse"
                  >
                    <template #prefix>
                      <img src="https://api.iconify.design/mdi/magnify.svg" alt="search" width="16" height="16" />
                    </template>
                  </el-input>
                </el-form-item>
                <!-- 选择按钮移出至右侧“清空”前面，此处不再渲染 -->
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
              <!-- 将“选择”控件放在“清空”控件前面：便于批量操作入口更靠近右侧工具 -->
              <!-- 样式优化：使用默认按钮风格以与“清空”一致，提升协调性 -->
              <el-button size="small" @click="toggleSelectionMode" style="margin-right:8px;" class="select-btn">
                {{ selectionMode ? '退出选择' : '选择' }}
              </el-button>
              <span v-if="selectedIds.length" class="selected-count" title="已选数量" style="margin-right:8px;">已选 {{ selectedIds.length }} 条</span>
              <!-- 统一按钮大小：与“选择”保持相同的小尺寸，视觉更协调 -->
              <el-button size="small" @click="resetFilters">清空</el-button>
            </el-form-item>
          </el-form>
          <!-- 批量操作工具栏：仅在选择模式或有已选项时显示；支持全选本页/取消全选/批量设为公开/私有/删除 -->
          <div v-if="selectionMode || selectedIds.length" class="bulk-toolbar">
            <div class="bulk-left">
              <el-button size="small" @click="selectAllOnPage">全选本页</el-button>
              <el-button size="small" @click="clearSelection">取消全选</el-button>
            </div>
            <div class="spacer"></div>
            <div class="bulk-right">
              <el-button size="small" type="primary" :disabled="!selectedIds.length || bulkLoading" @click="bulkSetVisibility(true)">批量设为公开</el-button>
              <el-button size="small" type="info" :disabled="!selectedIds.length || bulkLoading" @click="bulkSetVisibility(false)">批量设为私有</el-button>
              <el-button size="small" type="danger" :disabled="!selectedIds.length || bulkLoading" @click="bulkDeleteSelected">批量删除</el-button>
            </div>
          </div>
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
          <!-- 选择框：放在“标签”下方；保持靠右对齐。阻止事件冒泡避免触发长按动作层或编辑态切换。 -->
          <div v-if="selectionMode" class="select-box below-tags" @click.stop @mousedown.stop @mouseup.stop @touchstart.stop @touchend.stop>
            <el-checkbox :checked="isSelected(n)" @change="onSelectChange(n, $event)" />
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
            <!-- 选择框：编辑态同样置于“标签”下方，便于统一交互 -->
            <div v-if="selectionMode" class="select-box below-tags" @click.stop @mousedown.stop @mouseup.stop @touchstart.stop @touchend.stop>
              <el-checkbox :checked="isSelected(n)" @change="onSelectChange(n, $event)" />
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
                  @focus="onEditFocus(n)"
                  @blur="onEditBlur(n)"
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
  <!-- 右下：统一“回到顶部”按钮（玻璃拟态风格，平滑滚动）
       说明：
       - 替换原 Element Plus 的 el-backtop，改用站内统一 BackToTop 组件；
       - 通过 target 绑定 TwoPaneLayout 的右侧滚动容器，确保在“右侧正文滚动”模式下正常显示与滚动；
       - threshold 控制出现阈值（像素），right/bottom 控制按钮位置。 -->
  <BackToTop :target="scrollRootEl" :right="80" :bottom="100" :threshold="360" />
</template>

<script setup>
// 引入统一布局与公共顶栏组件：
// - TwoPaneLayout：提供“全宽吸顶顶栏 + 右侧正文滚动”的通用布局结构；
// - AppTopBar：公共顶栏，统一品牌与快捷入口，支持透明/毛玻璃切换与搜索。
import TwoPaneLayout from '@/components/TwoPaneLayout.vue';
import AppTopBar from '@/components/AppTopBar.vue';
// 统一的“回到顶部”组件：玻璃拟态风格 + 平滑滚动
import BackToTop from '@/components/BackToTop.vue';
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
// 右侧滚动容器引用：供 BackToTop 组件绑定滚动目标
const scrollRootEl = ref(null);
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

// ==================== 多选与批量操作（删除 / 公开 / 私有） ====================
// 设计说明：
// - 通过 selectionMode 开关进入选择模式，在每条便签左上角显示复选框；
// - 使用 selectedIds 数组保存已选便签 ID；
// - 提供“全选本页/取消全选/批量设为公开/私有/批量删除”等操作；
// - 批量操作基于现有单条接口（PUT /shiyan/{id}、DELETE /shiyan/{id}），通过 Promise.allSettled 并发执行；
// - 完成后给出成功/失败统计并更新本地列表状态。

// 是否处于选择模式（显示选择框与批量工具栏）
const selectionMode = ref(false);
// 已选便签 ID 列表（数组）
const selectedIds = ref([]);
// 批量操作加载状态（防止重复点击触发并发）
const bulkLoading = ref(false);

/** 切换选择模式：进入或退出。退出时清空已选项。 */
function toggleSelectionMode(){
  selectionMode.value = !selectionMode.value;
  if (!selectionMode.value) selectedIds.value = [];
}

/** 当前便签是否选中（用于复选框勾选状态） */
function isSelected(n){
  return selectedIds.value.includes(n.id);
}

/** 勾选状态改变（复选框 change）：添加或移除选中项。 */
function onSelectChange(n, checked){
  const id = n.id;
  if (checked){
    if (!selectedIds.value.includes(id)) selectedIds.value = selectedIds.value.concat(id);
  }else{
    selectedIds.value = selectedIds.value.filter(x => x !== id);
  }
}

/** 全选当前页（当前已加载的 notes 列表）；重复 ID 自动去重。 */
function selectAllOnPage(){
  const ids = notes.value.map(n => n.id);
  const set = new Set([...selectedIds.value, ...ids]);
  selectedIds.value = Array.from(set);
}

/** 取消全选（清空选中）并保留选择模式状态。 */
function clearSelection(){ selectedIds.value = []; }

/**
 * 批量删除已选拾言：二次确认 + 并发删除 + 本地移除 + 统计提示
 */
async function bulkDeleteSelected(){
  if (!selectedIds.value.length || bulkLoading.value) return;
  try{
    // 文案重命名：提示中“便签”统一改为“拾言”
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条拾言吗？不可恢复。`, '批量删除确认', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    });
  }catch{ return; }
  bulkLoading.value = true;
  try{
    const ids = [...selectedIds.value];
    // 路径切换：批量删除统一使用 /shiyan/{id}
    const tasks = ids.map(id => http.delete(`/shiyan/${id}`));
    const results = await Promise.allSettled(tasks);
    const successIds = [];
    const failed = [];
    results.forEach((r, i) => {
      const id = ids[i];
      if (r.status === 'fulfilled') successIds.push(id); else failed.push(id);
    });
    if (successIds.length) notes.value = notes.value.filter(n => !successIds.includes(n.id));
    selectedIds.value = selectedIds.value.filter(id => !successIds.includes(id));
    ElMessage.success(`删除成功 ${successIds.length} 条，失败 ${failed.length} 条`);
  }catch(e){
    ElMessage.error('批量删除失败，请稍后再试');
  }finally{
    bulkLoading.value = false;
  }
}

/**
 * 批量设置公开/私有：并发更新 isPublic 保留其它字段，完成后更新本地状态并提示统计
 */
async function bulkSetVisibility(isPublic){
  if (!selectedIds.value.length || bulkLoading.value) return;
  bulkLoading.value = true;
  try{
    const idToNote = new Map(notes.value.map(n => [n.id, n]));
    const ids = [...selectedIds.value];
    const tasks = ids.map(id => {
      const n = idToNote.get(id);
      const payload = {
        content: n?.content ?? '',
        tags: (Array.isArray(n?.tags) ? n.tags.join(',') : (n?.tags ?? '')),
        archived: n?.archived ?? false,
        isPublic: isPublic,
        color: (typeof n?.color === 'string' ? n.color.trim() : '')
      };
      // 路径切换：批量更新统一使用 /shiyan/{id}
      return http.put(`/shiyan/${id}`, payload);
    });
    const results = await Promise.allSettled(tasks);
    let success = 0, failed = 0;
    results.forEach((r, i) => {
      const id = ids[i];
      const n = idToNote.get(id);
      if (r.status === 'fulfilled'){
        success++;
        const data = r.value?.data;
        const newVal = (data?.isPublic ?? data?.is_public);
        n.isPublic = (typeof newVal === 'boolean') ? newVal : isPublic;
      }else{
        failed++;
      }
    });
    ElMessage.success(`批量更新完成：成功 ${success} 条，失败 ${failed} 条`);
  }catch(e){
    ElMessage.error('批量更新失败，请稍后再试');
  }finally{
    bulkLoading.value = false;
  }
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
    // 接口路径重命名：统一改为 /shiyan；后端保留 /notes 兼容
    const { data } = await http.get('/shiyan', {
      params: { size: size.value, page: targetPage, mineOnly: true },
      suppress401Redirect: true,
    });
    const items = Array.isArray(data) ? data : (data?.items ?? data?.records ?? []);
    const t = data?.total ?? data?.count ?? 0;
    total.value = Number.isFinite(t) ? Number(t) : (notes.value.length + (items?.length || 0));
    appendMappedItems(items || []);
    page.value = targetPage;
  }catch(e){
    // 文案重命名：将“便签”统一改为“拾言”
    ElMessage.error('加载我的拾言失败');
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
  // 修复说明：TwoPaneLayout 的右侧主区域是独立滚动容器（overflow:auto）。
  // 若 IO 的 root 绑定为浏览器视口（root=null），则当页面本身不滚动时，哨兵不会进入视口，导致无法触发下一页加载。
  // 这里动态查找最近的可滚动父容器并作为 IO 的 root，配合较大的 rootMargin 提前触发加载，提升体验。
  function getScrollParent(el){
    let node = el?.parentElement;
    while (node){
      const style = window.getComputedStyle(node);
      const overflowY = style.overflowY;
      if (overflowY === 'auto' || overflowY === 'scroll') return node;
      node = node.parentElement;
    }
    return null;
  }
  const root = getScrollParent(loadMoreSentinel.value);
  sentinelObserver = new IntersectionObserver((entries) => {
    const entry = entries[0];
    if (entry?.isIntersecting){ loadMore(); }
  }, { root, rootMargin: '200px', threshold: 0 });
  sentinelObserver.observe(loadMoreSentinel.value);
}

// 获取 TwoPaneLayout 的右侧滚动容器（沿父链查找最近的 overflow:auto/scroll）
// 说明：模板中的 loadMoreSentinel 位于列表底部，能够用于定位其最近的滚动父元素。
function findScrollParent(el){
  let node = el?.parentElement;
  while (node){
    const style = window.getComputedStyle(node);
    const overflowY = style.overflowY;
    if (overflowY === 'auto' || overflowY === 'scroll') return node;
    node = node.parentElement;
  }
  return null;
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
  // 绑定回到顶部滚动容器：在下一渲染帧获取并赋值，确保元素已挂载
  await nextTick();
  scrollRootEl.value = findScrollParent(loadMoreSentinel.value);
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
    // 路径切换：统一使用 /shiyan/{id}
    const { data } = await http.put(`/shiyan/${n.id}`, payload);
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
    // 文案重命名：将“便签”统一改为“拾言”
    await ElMessageBox.confirm('确定要删除这条拾言吗？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    });
    // 路径切换：统一使用 /shiyan/{id}
    await http.delete(`/shiyan/${n.id}`);
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
  // 兼容性修复说明：
  // 原正则使用了 Unicode 属性转义 \p{L}（匹配所有字母类），虽然现代浏览器支持，但在部分环境下会导致语法错误（SyntaxError: Invalid or unexpected token）。
  // 为保证在所有常见浏览器中可用，这里改用显式的字符范围：
  // - 英文与数字：A-Za-z0-9
  // - 下划线与连字符：_-
  // - 中文（基本汉字）：\u4e00-\u9fff
  // 如果后续需要更广覆盖（如日文、韩文、更多 Unicode 平面），可在此范围上扩展。
  const re = /#([A-Za-z0-9_\u4e00-\u9fff-]+)/g;
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
  // 兼容性修复：移除 \p{L} 属性转义，改为显式字符范围，避免在部分浏览器解析时报语法错误。
  return s
    .replace(/\s*#([A-Za-z0-9_\u4e00-\u9fff-]+)\s*(,\s*)?/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

// 喜欢与收藏
async function toggleLike(n){
  if (n.likeLoading) return;
  n.likeLoading = true;
  try{
    // 路径切换：统一使用 /shiyan/{id}/like|unlike
    const url = n.liked ? `/shiyan/${n.id}/unlike` : `/shiyan/${n.id}/like`;
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
    // 路径切换：统一使用 /shiyan/{id}/favorite|unfavorite
    const url = n.favorited ? `/shiyan/${n.id}/unfavorite` : `/shiyan/${n.id}/favorite`;
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
  // 兼容性修复：同上，移除 \p{L}，采用更广但显式的字符范围以提升跨浏览器稳定性。
  const re = /#([A-Za-z0-9_\u4e00-\u9fff-]+)/g;
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

// —— 粘贴修复：将来源于聊天应用的“图片表情/贴纸”转换为 Unicode Emoji ——
// 说明：部分聊天应用复制到剪贴板时携带 HTML，<img> 承载表情图片；直接粘贴到 textarea 会丢失图片。
// 方案：在捕获阶段监听 document 的 paste 事件，若目标位于本页编辑输入框，则解析剪贴板 HTML，
//       将 <img ... alt|title|aria-label|data-emoji> 转换为对应的 Unicode Emoji 字符，并兼容中文别名。
const focusedEditingId = ref(null);
function onEditFocus(it){ focusedEditingId.value = it?.id ?? null; }
function onEditBlur(){ focusedEditingId.value = null; }

// 常见/热门映射：英文数据名与中文别名到 Unicode Emoji
const emojiMap = {
  // 经典笑脸
  smile: '😊', happy: '😄', grin: '😁', laugh: '😆', joy: '😂', wink: '😉', blush: '😊', smirk: '😏',
  neutral_face: '😐', expressionless: '😑', unamused: '😒', relieved: '😌',
  surprised: '😮', astonished: '😲', scream: '😱',
  sad: '☹️', crying: '😢', sob: '😭', weary: '😩', tired: '😫', disappointed: '😞',
  angry: '😠', rage: '🤬', confounded: '😖',
  thinking: '🤔', facepalm: '🤦', shushing_face: '🤫', lying_face: '🤥', zipper_mouth: '🤐',
  // 爱心/庆祝
  heart: '❤️', hearts: '💕', heart_eyes: '😍', kiss: '😘', kissing_heart: '😘',
  broken_heart: '💔', two_hearts: '💕', sparkling_heart: '💖',
  sparkles: '✨', star: '⭐', stars: '🌟', party_popper: '🎉', tada: '🎉', gift: '🎁', balloon: '🎈', ribbon: '🎀', confetti_ball: '🎊',
  // 手势
  thumbs_up: '👍', thumbsup: '👍', like: '👍', thumbs_down: '👎', clap: '👏', pray: '🙏',
  ok_hand: '👌', victory_hand: '✌️', v: '✌️', wave: '👋', raised_hand: '✋', fist: '✊', rock: '🤘', handshake: '🤝',
  // 自然/植物
  tulip: '🌷', rose: '🌹', cherry_blossom: '🌸', sunflower: '🌻', hibiscus: '🌺', bouquet: '💐',
  sun: '☀️', moon: '🌙', cloud: '☁️', fire: '🔥', rainbow: '🌈', leaf: '🍃', butterfly: '🦋',
  // 其它常用图标
  dog: '🐶', cat: '🐱', coffee: '☕', cake: '🍰', beer: '🍺', camera: '📷', music: '🎵', book: '📚', pencil: '✏️', check: '✔️', cross: '❌', warning: '⚠️', info: 'ℹ️', question: '❓', exclamation: '❗', rocket: '🚀',
  // 中文别名（微信/QQ/贴吧等常见）
  '微笑': '😊', '开心': '😊', '大笑': '😄', '坏笑': '😏', '笑哭': '😂', '眨眼': '😉', '捂脸': '🤦', '尴尬': '😬', '害羞': '☺️',
  '可爱': '😊', '酷': '😎', '思考': '🤔', '惊讶': '😲', '震惊': '😱', '难过': '☹️', '大哭': '😭', '委屈': '😢', '无语': '😑', '闭嘴': '🤐',
  '心': '❤️', '爱心': '❤️', '红心': '❤️', '心碎': '💔', '比心': '💕', '星星': '⭐', '闪耀': '✨',
  '点赞': '👍', '赞': '👍', '不赞': '👎', '鼓掌': '👏', '祈祷': '🙏', '握手': '🤝', '再见': '👋', '耶': '✌️', 'ok': '👌',
  '礼物': '🎁', '庆祝': '🎉', '气球': '🎈', '太阳': '☀️', '月亮': '🌙', '彩虹': '🌈', '叶子': '🍃', '蝴蝶': '🦋',
  // 网络常见别名
  'doge': '🐶', '泪目': '😭', '摸鱼': '🐟', '燃': '🔥', '真棒': '👍', '牛': '🐮'
};

function htmlToTextWithEmoji(html){
  try{
    const div = document.createElement('div');
    div.innerHTML = html;
    div.querySelectorAll('img').forEach(img => {
      const alt = img.getAttribute('alt') || '';
      const title = img.getAttribute('title') || '';
      const aria = img.getAttribute('aria-label') || '';
      const dataEmoji = img.getAttribute('data-emoji') || img.getAttribute('data-name') || '';
      let rep = '';
      const cand = [alt, title, aria, dataEmoji].map(s => String(s).replace(/[\[\]]/g,'').trim()).filter(Boolean);
      for (const c of cand){
        if (/[\u2600-\u27BF\uD83C-\uDBFF\uDC00-\uDFFF]/.test(c)) { rep = c; break; }
        if (emojiMap[c]) { rep = emojiMap[c]; break; }
      }
      const span = document.createElement('span');
      span.textContent = rep || '';
      img.replaceWith(span);
    });
    // 移除可能的脚本并获取纯文本
    div.querySelectorAll('script,style').forEach(el => el.remove());
    return div.textContent || div.innerText || '';
  }catch{ return ''; }
}

function handlePaste(e){
  try{
    // 仅在当前页面编辑态输入框内处理
    if (!focusedEditingId.value) return;
    const target = e.target;
    const wrapper = document.querySelector(`[data-note-id="${focusedEditingId.value}"]`);
    if (!wrapper || !wrapper.contains(target)) return;
    const cb = e.clipboardData || window.clipboardData;
    if (!cb) return;
    const html = cb.getData?.('text/html') || '';
    if (!html) return; // 无 HTML 内容则交由默认粘贴处理
    const converted = htmlToTextWithEmoji(html);
    if (!converted) return;
    e.preventDefault();
    const ta = wrapper.querySelector('textarea');
    if (!ta) return;
    const start = ta.selectionStart ?? ta.value.length;
    const end = ta.selectionEnd ?? start;
    const before = ta.value.slice(0, start);
    const after = ta.value.slice(end);
    const ins = converted;
    ta.value = `${before}${ins}${after}`;
    const newVal = ta.value;
    // 同步 v-model
    const note = notes.value.find(x => x.id === focusedEditingId.value);
    if (note) note.contentEdit = newVal;
    // 恢复光标到插入末尾
    const pos = before.length + ins.length;
    ta.setSelectionRange(pos, pos);
    ta.dispatchEvent(new Event('input', { bubbles: true }));
  }catch{}
}

onMounted(() => {
  document.addEventListener('paste', handlePaste, true);
});
onUnmounted(() => {
  document.removeEventListener('paste', handlePaste, true);
});
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

/* 批量工具栏样式：左右分布，跟随过滤栏粘顶，避免遮挡 */
.bulk-toolbar { display:flex; align-items:center; gap:8px; padding:8px 0 0; }
.bulk-toolbar .bulk-left { display:flex; gap:8px; }
.bulk-toolbar .bulk-right { display:flex; gap:8px; }
.bulk-toolbar .spacer { flex:1; }
.selected-count { margin-left:8px; font-size:12px; color:#606266; }

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

/* 选择框（左上角）样式：保持与卡片一致的圆角与层级 */
.select-box { position:absolute; right:12px; z-index: 5; background: rgba(255,255,255,0.92); border-radius: 8px; padding: 2px 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
/* 放在标签下方：标签高度约为 24~28px，考虑内边距与间距，取 38px 作为偏移量，避免重叠 */
.select-box.below-tags { top: 38px; }
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
<!--
  我的拾言视图（MyNotes）
  说明：
  - 复用 TwoPaneLayout 与 AppTopBar，右侧为个人资料、过滤栏与列表；
  - 需要登录：由路由 `meta.requiresAuth` 控制，进入前校验本地 token；
  - 服务端分页：使用 page/size/total 控制翻页与“是否还有下一页”；
  - 交互：支持批量操作、回到顶部、触底加载，避免并发与重复加载。
-->