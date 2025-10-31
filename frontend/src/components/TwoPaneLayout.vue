<template>
  <!-- 通用两栏布局组件：左侧固定窄列，右侧上下两段（顶栏 + 正文） -->
  <!-- 使用说明：
       - 通过具名插槽组织页面：
         * left     → 左侧栏（建议放 SideNav）
         * rightTop → 右侧上部（建议放 AppTopBar）
         * rightMain→ 右侧下部（页面主体内容）
       - 不内置业务逻辑，仅提供语义化容器与响应式布局；
       - 侧栏宽度与粘性偏移由 SideNav 组件的 CSS 变量控制（--side-nav-width/offset）。 -->
  <section class="two-pane" role="region" aria-label="Two Column Layout">
    <aside class="col-left">
      <!-- 左列：通常用于放置 SideNav，粘性与偏移在 SideNav 内部处理 -->
      <slot name="left" />
    </aside>
    <div class="col-right">
      <!-- 右列上部：顶栏区域（例如 AppTopBar） -->
      <div class="right-top">
        <slot name="rightTop" />
      </div>
      <!-- 右列下部：页面主体内容区 -->
      <div class="right-main">
        <slot name="rightMain" />
      </div>
    </div>
  </section>
</template>

<script setup>
// 轻量布局组件：无逻辑，仅提供结构与样式。
// 如需扩展（例如列宽、间距、断点），建议通过 CSS 变量或父级样式覆盖。
</script>

<style scoped>
/* 两栏总体栅格布局
   - 左列为窄列，宽度由左列内容自身决定（通常由 SideNav 的 --side-nav-width 控制）
   - 右列自适应剩余空间，包含上下两段（顶栏 + 正文） */
.two-pane {
  display: grid;
  grid-template-columns: auto 1fr; /* 左列内容宽度（auto） + 右列（自适应） */
  gap: 12px; /* 列间距 */
  align-items: start; /* 顶对齐，避免不同高度导致错位 */
}

/* 左列容器：不强加额外定位，SideNav 内部使用 sticky 控制吸顶与偏移 */
.col-left { 
  min-width: 0; /* 防止子元素过长导致溢出 */
}

/* 右列容器：竖向排列上部顶栏与下部正文 */
.col-right {
  display: flex; 
  flex-direction: column; 
  gap: 8px; /* 顶栏与正文之间的竖向间距 */
  min-width: 0; /* 避免内容溢出导致列宽异常 */
}

/* 顶栏区：不做粘性处理，是否 sticky 交由具体顶栏组件控制 */
.right-top { 
  /* 保持默认文档流；若需页面级吸顶，请在顶栏组件或父容器处理 */
}

/* 正文区：自适应，占满右列剩余空间 */
.right-main { 
  /* 可根据需要增加内边距或背景；保持默认由页面具体内容控制 */
}

/* 响应式：窄屏时改为单列纵向布局，左列在上，右列在下 */
@media (max-width: 960px){
  .two-pane { grid-template-columns: 1fr; }
}
</style>