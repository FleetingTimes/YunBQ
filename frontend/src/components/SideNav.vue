<template>
  <!-- 通用侧边栏组件：支持父子导航、默认折叠、点击父项展开
       使用说明：
       - 通过 props 传入 sections（数组），每项结构：{ id, label, children?, aliasTargets? }
       - 使用 v-model:activeId 双向绑定当前高亮项 id；
       - 组件内部管理展开状态（expandedIds），默认全部折叠；
       - 事件：
         * select(id) 当点击子项或无子项的父项时触发，由父组件决定滚动或其他行为；
       - 样式：本组件包含自身样式，不依赖父组件 scoped 样式。 -->
  <!-- 撤销：恢复默认粘性布局，不再固定到页面左侧 -->
  <!-- 增加可选居中样式：当传入 alignCenter=true 时，侧边导航内容在自身宽度内水平居中 -->
  <aside class="side-nav" :class="{ centered: props.alignCenter }" :style="asideStyle">
    <div class="nav-title">导航</div>
    <ul class="nav-list">
      <li v-for="s in sections" :key="s.id" :class="{ break: s.id === 'site', 'has-children': s.children && s.children.length }">
        <!-- 父项点击：有子项则切换展开并高亮父项；无子项则触发选择事件 -->
        <!-- 父项高亮规则（折叠场景优化）：
             说明：
             - 当父项自身被选中（modelActiveId === s.id）时高亮；
             - 当其子项被选中（modelActiveId 命中某个子项 id）时也高亮父项，
               以便在“收起状态”下仍能看到导航的反馈。 -->
        <a href="javascript:;" :class="{ active: modelActiveId === s.id || hasActiveChild(s) }" @click="onParentClick(s)">{{ s.label }}</a>
        <!-- 子导航：仅当存在 children 时渲染；默认折叠，展开后展示 -->
        <!-- 子导航显示规则（折叠场景优化）：
             说明：
             - 原逻辑仅在 isExpanded(s.id) 为真时显示子导航；
             - 增强：当某个子项处于活跃状态（滚动联动选中）时，即使父项未手动展开，
               也临时显示子导航，避免“右侧滚动到子卡片但左侧无显示反馈”的问题。 -->
        <ul v-if="s.children && s.children.length" class="sub-nav-list" v-show="isExpanded(s.id) || hasActiveChild(s)">
          <li v-for="c in s.children" :key="c.id">
            <a href="javascript:;" :class="{ active: modelActiveId === c.id }" @click="onChildClick(c.id)">{{ c.label }}</a>
          </li>
        </ul>
      </li>
    </ul>
  </aside>
</template>

<script setup>
// 通用侧边栏组件逻辑
// 说明：
// - 通过 v-model:activeId 实现父组件对当前高亮项的双向绑定；
// - 内部维护 expandedIds（Set）以管理父项的展开/折叠状态；
// - 点击子项或无子项的父项时，向父组件发出 select 事件；
// - 父组件可根据 id 决定滚动、过滤或其他行为（例如 aliasTargets 映射在父组件处理）。
import { ref, computed, watch } from 'vue'

const props = defineProps({
  // 导航配置：数组项包含 id/label/children，可选 aliasTargets 由父处理
  sections: { type: Array, default: () => [] },
  // v-model:activeId 当前高亮项 id
  activeId: { type: String, default: '' },
  // 居中对齐开关：当为 true 时，侧栏中的标题与导航项在自身列内居中显示
  // 说明：
  // - 顶层导航（父项）与子导航均采用文本居中；
  // - 子导航的树状连接线将被隐藏，以避免与居中布局冲突；
  // - 不改变侧栏自身宽度与粘性定位，仅是内容对齐方式的调整。
  alignCenter: { type: Boolean, default: false },
  // 导航容器内部宽度：集中到组件控制，支持数字或带单位字符串；默认 160px
  innerWidth: { type: [Number, String], default: 160 }
  ,
  /**
   * 组件外部宽度（aside.side-nav 的整体宽度）。
   * - 用于“缩小侧边栏组件本身的宽度”，不影响内部文本对齐。
   * - 支持数字（按 px 处理）或字符串（例如 '180px'/'12rem'/'80%'）。
   * - 默认值：200（即 200px）。
   */
  width: { type: [Number, String], default: 200 }
})
const emit = defineEmits(['update:activeId', 'select'])

// 组件根元素样式绑定：通过 CSS 变量传递“内部容器宽度”与“组件外宽”
// 说明：
// - innerWidth → 绑定到 --side-nav-inner-width，用于 .nav-list/.sub-nav-list 的居中容器宽度；
// - width → 绑定到 --side-nav-width，用于 aside.side-nav 的整体宽度；
// - 两者都支持数字（自动转 px）与长度字符串（px/rem/% 等），包含详尽容错注释。
const asideStyle = computed(() => {
  // 解析内部宽度（默认 160px）
  let inner = props.innerWidth
  if (typeof inner === 'number') {
    inner = `${inner}px`
  } else if (typeof inner === 'string') {
    const t = inner.trim()
    inner = /^\d+$/.test(t) ? `${t}px` : t
  } else {
    inner = '160px'
  }

  // 解析组件外宽（默认 200px）
  let outer = props.width
  if (typeof outer === 'number') {
    outer = `${outer}px`
  } else if (typeof outer === 'string') {
    const t = outer.trim()
    outer = /^\d+$/.test(t) ? `${t}px` : t
  } else {
    outer = '200px'
  }

  return {
    '--side-nav-inner-width': inner,
    '--side-nav-width': outer
  }
})

// 本地展开状态：使用 Set 以获得 O(1) 插入/删除/查询性能
const expandedIds = ref(new Set())
function isExpanded(id){ return expandedIds.value.has(id) }
// 展开/折叠（互斥）
// 说明：
// - 修改为“始终保持只展开一个父项”的互斥策略；
// - 若点击的父项已处于展开状态，则折叠为“全部收起”（无任何父项展开）；
// - 若点击其他父项，则仅展开该父项并折叠其他父项。
function toggleExpanded(id){
  const set = expandedIds.value
  if (set.has(id)){
    // 已展开 → 折叠为“全部收起”
    expandedIds.value = new Set()
  }else{
    // 互斥展开：仅保留当前父项为展开状态
    expandedIds.value = new Set([id])
  }
}

// 代理 v-model:activeId（避免直接修改 props）
const modelActiveId = computed({
  get(){ return props.activeId },
  set(v){ emit('update:activeId', v) }
})

// 撤销：不再切换定位 class，统一使用默认样式

// 折叠场景增强：判断某个父项是否包含当前活跃子项
// 说明：
// - 当 modelActiveId 命中某个子项 id 时，返回 true；
// - 用于在模板中高亮父项与临时显示子导航，解决“收起时无反馈”。
function hasActiveChild(section){
  try{
    return Array.isArray(section?.children) && section.children.some(c => c.id === modelActiveId.value)
  }catch{ return false }
}

// 折叠场景增强：当活跃项切换为某父项的子项时，自动展开该父项
// 说明：
// - 结合上面的临时显示规则，自动展开可保留更一致的交互体验；
// - 展开仅针对包含当前活跃子项的父；不主动折叠其它父项，避免频繁抖动。
watch(modelActiveId, (val) => {
  try{
    const sec = props.sections.find(s => Array.isArray(s.children) && s.children.some(c => c.id === val))
    if (sec){
      // 自动展开包含当前活跃子项的父项，同时保持互斥（只展开这一个）
      expandedIds.value = new Set([sec.id])
    }
  }catch{ /* 忽略异常以保证渲染稳定 */ }
})

// 父项点击：有子项 → 展开/折叠并高亮父项；无子项 → 触发选择事件
function onParentClick(s){
  if (s && Array.isArray(s.children) && s.children.length){
    toggleExpanded(s.id)
    modelActiveId.value = s.id
  }else{
    // 无子项：交由父组件处理（滚动/高亮等），此处不直接改变 activeId
    emit('select', s?.id)
  }
}

// 子项点击：触发选择事件并更新高亮项 id
function onChildClick(id){
  emit('select', id)
  modelActiveId.value = id
}
</script>

<style scoped>
/* 侧边栏总体布局：窄列、粘性定位、竖向导航，支持滚动 */
.side-nav { 
  /* 组件整体宽度：集中由 CSS 变量控制，默认 200px，可通过 prop 覆盖 */
  width: var(--side-nav-width, 200px); 
  flex: none; 
  position: sticky; 
  /* 垂直偏移（向下移动）
     说明：当侧边栏采用粘性定位 position: sticky 时，
     通过 top 控制它在滚动容器顶部的贴附位置。
     将该值改为更大可使侧栏整体“向下”移动。
     可通过父容器声明 CSS 变量 --side-nav-offset-y 自定义此值。 */
  top: var(--side-nav-offset-y, 16px); 
  /* 水平偏移（向右移动）
     说明：sticky 元素相对其所在的布局列对齐；
     使用 margin-left 可以为侧栏在该列中增加向右的空隙。
     可通过父容器声明 CSS 变量 --side-nav-offset-x 自定义此值。 */
  margin-left: var(--side-nav-offset-x, 0px);
  align-self: flex-start; 
  /* 设置最大高度为视窗高度减去顶部偏移和底栏高度，确保不被底栏遮挡 */
  max-height: calc(100vh - 32px - 48px); 
  /* 当内容超出最大高度时启用滚动，但隐藏滚动条 */
  overflow-y: auto; 
  /* 隐藏滚动条 - Firefox */
  scrollbar-width: none;
  /* 隐藏滚动条 - IE/Edge */
  -ms-overflow-style: none;
}
/* 撤销：移除 fixed-left 固定定位样式，保持粘性定位 */
.nav-title { font-weight: 600; color: #303133; margin-bottom: 8px; }
.nav-list { list-style: none; margin: 0; padding: 0; }
.nav-list li { display: block; margin: 6px 0; }
.nav-list li.has-children > a { display: inline-block; margin-bottom: 6px; }
.nav-list a { display: inline-block; padding: 6px 8px; border-radius: 6px; color: #606266; text-decoration: none; transition: background-color .15s ease; }
.nav-list a:hover { background: #f6f8fe; }
.nav-list a.active { background: #eef5ff; color: #409eff; }

/* 子导航：树状缩进与连接线，增强层级感 */
.sub-nav-list { list-style: none; margin: 4px 0 0 16px; padding: 0; display: block; position: relative; }
.sub-nav-list::before { content: ""; position: absolute; left: 6px; top: 4px; bottom: 4px; width: 0; border-left: 1px dashed #dcdfe6; }
.sub-nav-list li { display: block; margin: 4px 0; padding-left: 12px; position: relative; }
.sub-nav-list li::before { content: ""; position: absolute; left: 0; top: 50%; width: 8px; border-top: 1px solid #dcdfe6; transform: translateY(-50%); }
.sub-nav-list a { display: block; padding: 6px 8px; border-radius: 6px; color: #606266; text-decoration: none; transition: background-color .15s ease; font-size: 13px; }
.sub-nav-list a:hover { background: #f6f8fe; }
.sub-nav-list a.active { background: #eef5ff; color: #409eff; }

/* 隐藏WebKit浏览器（Chrome/Safari）的滚动条 */
.side-nav::-webkit-scrollbar {
  display: none;
}

/* 居中变体（仅控件居中）：
   说明：
   - 当启用 centered 时，仅让侧边栏“控件本身”（aside.side-nav 区块）在其所在列内居中；
   - 不改变导航标题与父/子导航项的文本对齐方式（仍保持默认左对齐）；
   - 适用于希望整体控件居中而内容仍左对齐的场景。 */
.side-nav.centered { 
  /* 使用自动左右外边距将侧栏控件在列内居中（宽度 220px 保持不变） */
  margin-left: auto; 
  margin-right: auto; 
}
/* 仅导航容器居中：
   说明：
   - 在保持侧栏控件（aside）居中的基础上，将内部的导航容器（.nav-list）作为一个“居中块”，
     通过固定较窄的内容宽度并设置左右自动外边距，使其在侧栏中居中显示；
   - 导航项文本与层级线保持默认左对齐，不改变交互与树状视觉；
   - 可通过 CSS 变量 --side-nav-inner-width 调整导航容器的宽度（默认 180px）。 */
.side-nav.centered .nav-list {
  width: var(--side-nav-inner-width, 160px);
  margin-left: auto;
  margin-right: auto;
  /* 明确文本左对齐，避免继承导致的意外居中 */
  text-align: left;
}
/* 子导航容器也应用同样的居中宽度，以保证父子层级宽度一致 */
.side-nav.centered .sub-nav-list {
  width: var(--side-nav-inner-width, 160px);
  margin-left: auto;
  margin-right: auto;
}
/* 子导航项宽度优化（居中模式）：
   说明：
   - 默认 sub-nav 的 <a> 为 block，会占满容器宽度，看起来“胶囊过长”；
   - 这里改为 inline-block/自适应宽度，让胶囊长度贴合文本；
   - 仍保持左对齐与树状连接线（::before）不变。 */
.side-nav.centered .sub-nav-list a {
  display: inline-block; /* 自适应文本宽度，避免占满容器 */
  width: auto;          /* 明确不拉伸至容器宽度 */
}

@media (max-width: 960px){ 
  .side-nav { 
    width: 100%; 
    position: static; 
    /* 移动端取消最大高度限制 */
    max-height: none; 
    overflow-y: visible; 
  } 
  /* 移动端：普通文档流布局，无需特殊处理 */
}
</style>