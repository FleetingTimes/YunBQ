<template>
  <footer class="transparent-footer" :style="styleVars">
    <slot>
      <div class="content">
  <!-- 文案重命名：品牌统一为“拾·言” -->
  <span class="text">拾·言 · 底栏</span>
      </div>
    </slot>
  </footer>
  <div v-if="spacer" :style="{ height: height }" />
  
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  fixed: { type: Boolean, default: true },
  height: { type: String, default: '48px' },
  opacity: { type: Number, default: 0.18 },
  blur: { type: Boolean, default: true },
  spacer: { type: Boolean, default: true }, // 在页面底部加入占位，避免遮挡内容点击
})

const styleVars = computed(() => ({
  '--h': props.height,
  '--op': String(props.opacity),
  '--blur': props.blur ? 'saturate(180%) blur(8px)' : 'none',
  '--pos': props.fixed ? 'fixed' : 'absolute',
}))
</script>

<style scoped>
.transparent-footer {
  position: var(--pos);
  left: 0;
  right: 0;
  bottom: 0;
  height: var(--h);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  background: rgba(255,255,255,var(--op));
  backdrop-filter: var(--blur);
  border-top: 1px solid rgba(0,0,0,0.06);
  z-index: 100;
}
.content { display: flex; align-items: center; gap: 8px; }
.text { color: #606266; font-size: 13px; }
@media (max-width: 640px) {
  .transparent-footer { padding-bottom: calc(8px + env(safe-area-inset-bottom)); }
}
</style>