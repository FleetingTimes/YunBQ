// 应用入口（Bootstrap）
// 说明：
// - 初始化 Vue 应用并挂载到根节点 `#app`；
// - 全局注册 Element Plus 组件库与路由；
// - 样式入口：引入 Element Plus 样式与全局样式 `style.css`。
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'

createApp(App).use(ElementPlus).use(router).mount('#app')
