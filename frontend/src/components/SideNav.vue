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
  <aside class="side-nav">
    <div class="nav-title">导航</div>
    <ul class="nav-list">
      <li v-for="s in sections" :key="s.id" :class="{ break: s.id === 'site', 'has-children': s.children && s.children.length }">
        <!-- 父项点击：有子项则切换展开并高亮父项；无子项则触发选择事件 -->
        <a href="javascript:;" :class="{ active: modelActiveId === s.id }" @click="onParentClick(s)">{{ s.label }}</a>
        <!-- 子导航：仅当存在 children 时渲染；默认折叠，展开后展示 -->
        <ul v-if="s.children && s.children.length" class="sub-nav-list" v-show="isExpanded(s.id)">
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
import { ref, computed } from 'vue'

const props = defineProps({
  // 导航配置：数组项包含 id/label/children，可选 aliasTargets 由父处理
  sections: { type: Array, default: () => [] },
  // v-model:activeId 当前高亮项 id
  activeId: { type: String, default: '' }
})
const emit = defineEmits(['update:activeId', 'select'])

// 本地展开状态：使用 Set 以获得 O(1) 插入/删除/查询性能
const expandedIds = ref(new Set())
function isExpanded(id){ return expandedIds.value.has(id) }
function toggleExpanded(id){ const set = expandedIds.value; set.has(id) ? set.delete(id) : set.add(id) }

// 代理 v-model:activeId（避免直接修改 props）
const modelActiveId = computed({
  get(){ return props.activeId },
  set(v){ emit('update:activeId', v) }
})

// 撤销：不再切换定位 class，统一使用默认样式

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
  width: 220px; 
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