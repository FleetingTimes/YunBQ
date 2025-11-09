<template>
  <!-- 通用两栏布局组件：左侧固定窄列，右侧上下两段（顶栏 + 正文） -->
  <!-- 使用说明：
       - 通过具名插槽组织页面：
         * left     → 左侧栏（建议放 SideNav）
         * rightTop → 右侧上部（建议放 AppTopBar）
         * rightMain→ 右侧下部（页面主体内容）
       - 支持顶栏吸顶：右侧顶栏区域具有 sticky 定位，确保滚动时固定在顶部；
       - 侧栏宽度与粘性偏移由 SideNav 组件的 CSS 变量控制（--side-nav-width/offset）。 -->
  <!-- 统一上下两段布局：上为顶栏、下为左右分栏（侧边栏 + 正文）
       用法：
       - #topFull     → 顶栏组件（建议放 AppTopBar），跨整行并吸顶；
       - #left        → 左边侧栏（建议放 SideNav），宽度由其自身决定；
       - #rightMain   → 右侧正文区，占满剩余空间并可滚动；
       兼容性：
       - 保留原有粘性定位语义；
       - 移除了旧的 #rightTop（右列顶栏），改为统一的全宽顶栏；
       - 响应式在窄屏下改为单列堆叠（顶栏 → 侧栏 → 正文）。 -->
  <section class="two-pane" role="region" aria-label="Top + Left/Right Layout">
    <!-- 顶栏：全宽吸顶，背景透明，具体视觉由顶栏组件决定（透明/毛玻璃） -->
    <div class="layout-header sticky-top">
      <slot name="topFull" />
    </div>
    <!-- 下方内容区：左右两列（侧栏 + 正文），占满剩余空间 -->
    <div class="bottom-row">
      <aside class="col-left">
        <!-- 左列：通常用于放置 SideNav，粘性与偏移在 SideNav 内部处理 -->
        <slot name="left" />
      </aside>
      <div class="col-right">
        <!-- 正文容器：可滚动内容区域，占满右列剩余空间 -->
        <div class="right-main scrollable-content">
          <slot name="rightMain" />
        </div>
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
/* 总体布局：上顶栏 + 下左右分栏
   - 上：一行（auto）用于顶栏；
   - 下：一行（1fr）用于主体；主体内部再做左右分栏。 */
.two-pane {
  display: grid;
  grid-template-rows: auto 1fr; /* 顶栏行（自适应） + 主体行（占满剩余） */
  /* 统一行间距（如果顶栏与主体之间需要间距，可调此处） */
  row-gap: 8px;
  /* 占满视口高度，确保粘性定位的滚动上下文正确 */
  /* 修改：将 min-height 升级为固定高度，并隐藏页面级滚动
     目的：让右侧正文成为唯一滚动容器，侧栏与顶栏不随“页面滚动”而移动。
     说明：
     - height: 100vh → 网格容器固定为视口高度；
     - overflow: hidden → 禁止 body/页面级滚动，仅允许内部子区域滚动；
     - 配合下方 .right-main 的 overflow-y: auto，使滚动完全发生在右侧正文。 */
  height: 100vh;
  overflow: hidden;
}

/* 全宽顶栏容器：吸顶；保持背景透明，由具体顶栏控制视觉效果 */
.layout-header.sticky-top {
  /* 吸顶：粘性定位与统一层级，避免被正文或侧栏覆盖 */
  position: sticky;
  top: 0;
  z-index: 1001; /* 比右列顶栏略高，确保全宽顶栏在上层 */
  /* 背景透明：让 AppTopBar 的毛玻璃/纯色背景接管视觉 */
  background: transparent;
}

/* 下方主体行：左右分栏（侧栏 + 正文） */
.bottom-row {
  display: grid;
  grid-template-columns: auto 1fr; /* 左列内容宽度（auto） + 右列（自适应） */
  column-gap: 12px; /* 列间距 */
  /* 让左右两列拉伸填满主体行高度，便于右侧正文计算 100% 高度 */
  align-items: stretch;
  /* 关键：允许子网格（右侧正文）正确计算滚动高度
     说明：
     - 在 CSS Grid 中，容器默认有最小高度约束，可能导致子项的滚动区域无法收缩；
     - 设置 min-height: 0 可解除约束，让右侧 .right-main 的 overflow 生效。 */
  min-height: 0;
  /* 隐藏主体行本身的溢出，避免产生“页面滚动条” */
  overflow: hidden;
}

/* 左列容器：不强加额外定位，SideNav 内部使用 sticky 控制吸顶与偏移 */
.col-left { min-width: 0; }

/* 右列容器：仅包含正文区域 */
.col-right { 
  min-width: 0; 
  /* 使右列自身也拉伸填满主体行高度 */
  height: 100%;
  /* 使用 Flex 让内部正文容器容易占满剩余高度 */
  display: flex; 
  flex-direction: column;
  min-height: 0; /* 解除最小高度约束，保证子项可滚动 */
}

/* 旧的右列顶栏（rightTop）已移除，统一通过 .layout-header 承载顶栏 */

/* 正文区：改为唯一滚动容器，占满剩余空间并负责滚动 */
.right-main.scrollable-content { 
  /* 使滚动发生在右列父容器，避免正文内部再产生二级滚动 */
  overflow-y: auto;
  overflow-x: hidden;
  /* 解除最小高度约束并填满主体行高度 */
  min-height: 0;
  height: 100%;
  
  /* 隐藏滚动条但保持滚动功能 */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/Edge */
}

/* 隐藏 Webkit 浏览器的滚动条 */
.right-main.scrollable-content::-webkit-scrollbar {
  display: none;
}

/* 响应式：窄屏时改为单列纵向布局，左列在上，右列在下 */
@media (max-width: 960px){
  /* 修复：移动端堆叠布局下取消固定视口高度与页面滚动隐藏，改由页面承载滚动。
     背景：桌面端使用 height:100vh + overflow:hidden，使右侧正文成为唯一滚动容器；
           窄屏仅设置 min-height: auto 无法覆盖既有 height:100vh，导致右侧正文不能展开。
     方案：明确重置 .two-pane 的 height/overflow，允许页面垂直滚动并让内容自然膨胀。 */
  .two-pane { height: auto; min-height: auto; overflow: visible; }
  /* 窄屏：主体行改为单列堆叠（侧栏在上，正文在下） */
  .bottom-row { grid-template-columns: 1fr; }
  /* 右侧正文容器在堆叠模式下：随内容自然高度，不强制内部滚动，由页面承载滚动 */
  .right-main.scrollable-content {
    height: auto;
    min-height: 0;
    overflow-y: visible;
  }
}
</style>