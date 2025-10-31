<template>
  <!-- 通用两栏布局组件：左侧固定窄列，右侧上下两段（顶栏 + 正文） -->
  <!-- 使用说明：
       - 通过具名插槽组织页面：
         * left     → 左侧栏（建议放 SideNav）
         * rightTop → 右侧上部（建议放 AppTopBar）
         * rightMain→ 右侧下部（页面主体内容）
       - 支持顶栏吸顶：右侧顶栏区域具有 sticky 定位，确保滚动时固定在顶部；
       - 侧栏宽度与粘性偏移由 SideNav 组件的 CSS 变量控制（--side-nav-width/offset）。 -->
  <section class="two-pane" role="region" aria-label="Two Column Layout">
    <aside class="col-left">
      <!-- 左列：通常用于放置 SideNav，粘性与偏移在 SideNav 内部处理 -->
      <slot name="left" />
    </aside>
    <div class="col-right">
      <!-- 右列上部：顶栏区域（例如 AppTopBar），支持吸顶定位 -->
      <div class="right-top sticky-top">
        <slot name="rightTop" />
      </div>
      <!-- 右列下部：页面主体内容区，可滚动 -->
      <div class="right-main scrollable-content">
        <slot name="rightMain" />
      </div>
    </div>
  </section>
</template>

<script setup>
// 轻量布局组件：提供结构与吸顶支持。
// 顶栏吸顶通过 CSS sticky 实现，滚动检测由具体的顶栏组件处理。
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
  /* 确保布局容器占满视口高度，为吸顶提供正确的滚动上下文 */
  min-height: 100vh;
}

/* 左列容器：不强加额外定位，SideNav 内部使用 sticky 控制吸顶与偏移 */
.col-left { 
  min-width: 0; /* 防止子元素过长导致溢出 */
}

/* 右列容器：竖向排列上部顶栏与下部正文 */
.col-right {
  display: flex; 
  flex-direction: column; 
  min-width: 0; /* 避免内容溢出导致列宽异常 */
  /* 确保右列占满剩余高度，为内部滚动提供正确的容器 */
  min-height: 100vh;
}

/* 顶栏区：吸顶定位，确保滚动时固定在顶部 */
.right-top.sticky-top { 
  position: sticky;
  top: 0;
  z-index: 1000; /* 确保顶栏在其他内容之上 */
  /* 为毛玻璃效果预留背景透明度支持 */
  background: transparent;
}

/* 正文区：可滚动内容区域，占满剩余空间 */
.right-main.scrollable-content { 
  flex: 1; /* 占满右列剩余空间 */
  /* 为长内容提供滚动支持 */
  overflow-y: auto;
  /* 确保内容区域有足够的最小高度 */
  min-height: 0;
}

/* 响应式：窄屏时改为单列纵向布局，左列在上，右列在下 */
@media (max-width: 960px){
  .two-pane { 
    grid-template-columns: 1fr; 
    /* 窄屏时调整最小高度 */
    min-height: auto;
  }
  
  .col-right {
    /* 窄屏时右列不需要占满视口高度 */
    min-height: auto;
  }
}
</style>