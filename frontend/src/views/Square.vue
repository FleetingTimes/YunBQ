<template>
  <!-- 两栏布局：左侧 SideNav，右侧上方顶栏、下方广场正文
       说明：
       - 顶栏移入右列上方（rightTop），保持“左右两部分、右侧再分上下”的结构；
       - 侧栏移至左列（left），从 SquareBody 中抽离，避免嵌套影响布局；
       - 选择侧栏项时，通过父组件桥接调用 SquareBody 的滚动方法，不改变原有锚点与高亮行为。 -->
  <TwoPaneLayout>
    <!-- 左侧：通用侧栏组件（复用公共导航配置）
         行为：
         - 使用 v-model 绑定父级 activeId，以保持与右侧滚动联动的高亮状态；
         - 选择事件 @select 调用 SquareBody 的 scrollTo（通过 ref 暴露），保证滚动逻辑完全复用原实现。 -->
    <template #left>
      <SideNav :sections="sections" v-model:activeId="activeId" @select="onSelect" />
    </template>

    <!-- 全宽顶栏：跨越左右两列并吸顶，顶栏内容全屏铺满 -->
    <template #topFull>
      <!-- 开启铺满模式：fluid，让中间搜索区域在可用空间内尽可能拉伸 -->
      <!-- 固定透明顶栏：transparent=true 禁止滚动时毛玻璃切换，保持沉浸式背景 -->
      <!-- 说明：广场页需要保持极简沉浸效果，顶栏在滚动时不切换毛玻璃，以免分散注意力。 -->
      <AppTopBar fluid :transparent="true" @search="onSearch" />
    </template>

    <!-- 右下：广场正文内容，保留 query 传参；通过 ref 暴露滚动方法与高亮更新事件供父级桥接 -->
    <template #rightMain>
      <div class="square-container">
        <SquareBody ref="bodyRef" :query="query" @update:activeId="val => activeId = val" />
      </div>
    </template>
  </TwoPaneLayout>
  <!-- 右下：回到顶部组件（可见高度 360px 后出现）
       说明：
       - 指定 target 为 TwoPaneLayout 的右侧滚动容器（.scrollable-content），
         确保在“右侧正文滚动”模式下依然正确工作；
       - 位置与样式与“我的便签”页保持一致，统一交互体验；
       - visibility-height 控制显示阈值（360px），可按需调整。 -->
  <!-- 为避免目标容器尚未渲染（TwoPaneLayout 异步加载）导致的报错，使用 v-if 等待目标存在 -->
  <el-backtop v-if="hasScrollTarget" target=".scrollable-content" :right="80" :bottom="100" :visibility-height="360">
    <div class="backtop-btn" title="回到顶部">
      <img src="https://api.iconify.design/mdi/arrow-up.svg" alt="up" width="20" height="20" />
    </div>
  </el-backtop>
</template>

<script setup>
import { ref, defineAsyncComponent, onMounted, onUnmounted, nextTick } from 'vue'
// 通用两栏布局 + 侧栏组件
const TwoPaneLayout = defineAsyncComponent(() => import('@/components/TwoPaneLayout.vue'))
const SideNav = defineAsyncComponent(() => import('@/components/SideNav.vue'))

const AppTopBar = defineAsyncComponent(() => import('@/components/AppTopBar.vue'))
const SquareBody = defineAsyncComponent(() => import('./square/SquareBody.vue'))
// 使用新的导航数据管理组合式函数
import { useNavigation } from '@/composables/useNavigation'

// 初始化导航数据（使用 sideNavSections，并通过 fetchCategories 加载）
const { sideNavSections: sections, loading: sectionsLoading, error: sectionsError, fetchCategories } = useNavigation()

const query = ref('')
function onSearch(q){ query.value = q || '' }
// 侧栏当前高亮项（与右侧滚动联动），初始化为热门区
const activeId = ref('hot')
// 引用正文组件实例以桥接滚动方法
const bodyRef = ref(null)
function onSelect(id){
  // 通过子组件暴露的 scrollTo 保持滚动逻辑与偏移计算的一致性
  try{ bodyRef.value?.scrollTo?.(id) }catch{ /* 忽略异常以保障选择稳定 */ }
}

// 顶栏背景切换（保留解释性注释）：默认透明；当滚动使顶栏底部接触到内容区域时，切换为纯白
// 说明：
// - 通过检测页面容器（.square-container）相对视窗的顶部位置与顶栏高度来判断接触；
// - 当 container 顶部的可见位置 <= 顶栏高度，视为“顶栏底部接触到内容区域”，置 solid=true；
// - 该方案无需修改内容组件，鲁棒且性能开销低。
const topbarSolid = ref(false)
// 回到顶部控件的目标存在性：TwoPaneLayout 为异步组件，需等待其插入 DOM 后再渲染 Backtop
// 说明：
// - Element Plus 的 Backtop 在初始化时会查询 target；若不存在则抛错；
// - 通过 hasScrollTarget + v-if，确保仅在 `.scrollable-content` 可用时渲染 Backtop；
// - 采用短轮询（最多 3 秒）与 nextTick 组合，稳妥等待异步组件加载完成。
const hasScrollTarget = ref(false)
let targetPollTimer = null
function checkBacktopTarget(){
  try{
    const el = document.querySelector('.scrollable-content')
    if (el){
      hasScrollTarget.value = true
      if (targetPollTimer){ clearInterval(targetPollTimer); targetPollTimer = null }
    }
  }catch{ /* 忽略异常，继续轮询或在下次用户交互后再检查 */ }
}

function updateTopbarSolid(){
  try{
    const topbar = document.querySelector('.topbar')
    const h = (topbar?.getBoundingClientRect()?.height) || 56
    const containerTop = document.querySelector('.square-container')?.getBoundingClientRect()?.top ?? 0
    topbarSolid.value = containerTop <= h
  }catch{ /* 忽略异常以保证滚动过程中渲染稳定 */ }
}

onMounted(async () => {
  // 加载导航分类数据
  await fetchCategories()
  
  // 初始计算一次（避免进入页面时出现闪烁）
  updateTopbarSolid()
  // 初始与下一个渲染周期检查回到顶部目标容器
  checkBacktopTarget()
  nextTick(checkBacktopTarget)
  // 短轮询：考虑到 TwoPaneLayout 异步加载与插槽渲染可能晚于当前组件，最多尝试 3 秒
  let elapsed = 0
  targetPollTimer = setInterval(() => {
    if (hasScrollTarget.value){ clearInterval(targetPollTimer); targetPollTimer = null; return }
    checkBacktopTarget(); elapsed += 200
    if (elapsed >= 3000){ clearInterval(targetPollTimer); targetPollTimer = null }
  }, 200)
  // 监听滚动与视窗尺寸变化，保持状态同步
  window.addEventListener('scroll', updateTopbarSolid, { passive: true })
  window.addEventListener('resize', updateTopbarSolid)
})

onUnmounted(() => {
  window.removeEventListener('scroll', updateTopbarSolid)
  window.removeEventListener('resize', updateTopbarSolid)
  if (targetPollTimer){ clearInterval(targetPollTimer); targetPollTimer = null }
})
</script>

<style scoped>
  /* 统一顶栏包裹：限定最大宽度为 1080px、居中显示，并保留左右 16px 安全边距
     说明：这使顶栏在所有页面显示为一致宽度，不受容器（如 .container/.square-container）影响 */
  /* 顶栏宽度限制已移除：使用 TwoPaneLayout 的全宽插槽，AppTopBar 自行吸顶与视觉控制 */
  /* 页面容器样式
     说明：
     - 在容器上声明 CSS 变量以配置子组件（SquareBody）里的广场标题区高度；
     - 变量会向子树继承，`.square-header` 使用 `min-height: var(--square-header-height)` 读取该值；
     - 滚动定位逻辑会动态读取真实高度并叠加 36px 安全间距，调整后仍能准确避让。 */
  /* 回退：广场页容器恢复为居中定宽布局
     说明：
     - 恢复为最大宽度 960px，居中显示；
     - 采用通用内边距 16px，以保持内容安全边距；
     - 该回退仅影响广场页容器，不改动内部组件布局。 */
  .square-container {
   /* 右侧正文外层容器：占满右列高度并作为 Flex 父容器
      说明：
      - height: 100% 让子组件（SquareBody）可读取到明确的高度上下文；
      - display:flex + flex-direction: column 为子层级的滚动容器（content-scroll）提供空间约束；
      - 其余宽度与内边距保持不变，确保视觉结构一致。 */
   height: 100%;
   display: flex;
   flex-direction: column;
   max-width: 960px; margin: 0 auto; padding: 16px;
   /* 设置广场标题高度为 12px：最小化标题占用空间
      说明：`.square-header` 使用 min-height，因此总高度≈min-height+padding；
      我们将上下内边距设为 0，以使总高度尽量接近 12px。
      警告：标题文字与图标有自身字体大小与行高（当前 h1 为 20px），
      在 12px 的容器下可能出现拥挤或裁切。若出现不理想效果，可按需：
      - 轻微调大此值（如 14px/16px），或
      - 在 SquareBody.vue 中将 `.brand h1 { font-size: 20px; }` 调小。
      滚动定位会动态读取实际高度并叠加 36px 安全间距，仍能准确避让。 */
   --square-header-height: 12px;
   /* 将上下内边距设为 0，确保总高度尽量接近 20px（可按需改回 4px/6px/8px） */
   --square-header-padding-block: 0px;

  /* 左侧侧边栏偏移配置
      需求：将导航栏“向下、向右”移动一些以避开顶部元素或贴边区域。
      用法：SideNav.vue 中读取以下变量以控制定位与间距：
      - --side-nav-offset-y → 作用于 position: sticky 的 top（向下移动）
      - --side-nav-offset-x → 作用于 margin-left（向右移动）
      默认值分别为 16px/0px；此处设置为 36px/12px 以获得更舒适的间距。 */
  --side-nav-offset-y: 36px;  /* 垂直向下偏移（粘性顶部距离） */
  --side-nav-offset-x: 12px;  /* 水平向右偏移（列内左右空隙） */
  }
  /* 回退说明：移除页面级 :deep(.topbar) 吸顶覆写与 .content-fade 渐隐遮罩，
     保持广场页原始样式与行为，仅保留标题高度与侧边栏偏移变量。 */
</style>