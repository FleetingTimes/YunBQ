<template>
  <!-- 通用侧边栏组件：支持父子导航、默认折叠、点击父项展开
       使用说明：
       - 通过 props 传入 sections（数组），每项结构：{ id, label, children?, aliasTargets? }
       - 使用 v-model:activeId 双向绑定当前高亮项 id；
       - 组件内部管理展开状态（expandedIds），默认全部折叠；
       - 事件：
         * select(id) 当点击子项或无子项的父项时触发，由父组件决定滚动或其他行为；
       - 样式：本组件包含自身样式，不依赖父组件 scoped 样式。 -->
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
/* 侧边栏总体布局：窄列、粘性定位、竖向导航 */
.side-nav { width: 220px; flex: none; position: sticky; top: 16px; align-self: flex-start; }
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

@media (max-width: 960px){ .side-nav { width: 100%; position: static; } }
</style>