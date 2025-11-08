import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  // 开发服务器配置（Cloudflare Tunnel 反向代理友好）
  // 说明：
  // - 当通过 Cloudflare Tunnel 使用自定义域名（如 app.shiyan.online）访问 Vite 开发服务器时，
  //   Vite v5 默认会进行 Host 检查（防止 DNS 重绑定攻击），未显式允许的域名请求会被拒绝（常见现象：手机外网访问失败）。
  // - 下方配置显式允许外部域名并修正 HMR/WebSocket 连接参数，保证隧道下的热更新与页面访问正常。
  server: {
    /**
     * 允许外部主机名访问（DNS 重绑定防护白名单）
     * - 设置为包含隧道域名的数组，例如 ['app.shiyan.online']；
     * - 若希望临时放开所有主机，可改为 'true'，但不建议在生产或公网环境使用。
     */
    allowedHosts: ['app.shiyan.online'],

    /**
     * 监听网络地址（不仅仅是 localhost）
     * - true 表示绑定到 0.0.0.0，使局域网/反向代理可访问；
     * - Cloudflare Tunnel 在本机通过 127.0.0.1 转发也可工作，但建议开启以避免某些代理场景异常。
     */
    host: true,

    /**
     * 指定静态资源的绝对来源
     * - 某些反向代理场景下，Dev Server 生成的资源 URL 需要明确的 origin；
     * - 设置为你的外网域名，保证脚本/模块的地址在移动端和外网一致。
     */
    origin: 'https://app.shiyan.online',

    /**
     * HMR（热更新）设置，确保隧道下的 WebSocket 能正确连接
     * - host：指定 HMR 连接所使用的主机名（你的外网前端域名）；
     * - protocol：在 HTTPS 隧道下使用 'wss'；
     * - clientPort：Cloudflare 终止 HTTPS 后转发到本地，客户端通常使用 443 端口发起 WS 连接。
     */
    hmr: {
      host: 'app.shiyan.online',
      protocol: 'wss',
      clientPort: 443,
    },
  },
})
