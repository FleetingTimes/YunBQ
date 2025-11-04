<template>
  <div class="auth-wrapper">
    <div class="auth-card p-3 rot-3" style="max-width:420px;">
      <div class="auth-title">
        <img src="https://api.iconify.design/mdi/account-check.svg" alt="callback" width="26" height="26"/>
        <h2>授权回调</h2>
      </div>
      <div>
        <p v-if="token">登录成功，正在跳转...</p>
        <p v-else>登录失败或未返回 token</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { setToken } from '@/utils/auth';

const route = useRoute();
const router = useRouter();
const token = ref('');

onMounted(() => {
  const t = route.query.token || '';
  token.value = t;
  if (t) {
    setToken(t, true);
    ElMessage.success('登录成功');
// 路由重命名：OAuth 回调后跳转至 /shiyan（保留 /notes 别名，兼容旧地址）
router.replace('/shiyan');
  } else {
    ElMessage.error('登录失败');
    router.replace('/login');
  }
});
</script>