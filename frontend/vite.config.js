import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  const parseAllowedHosts = (raw) => {
    if (!raw || raw.trim() === '') return undefined
    if (raw.trim().toLowerCase() === 'true') return true
    return raw.split(',').map(s => s.trim()).filter(Boolean)
  }

  const server = { host: true }
  const allowedHosts = parseAllowedHosts(env.DEV_ALLOWED_HOSTS)
  if (allowedHosts !== undefined) server.allowedHosts = allowedHosts
  if (env.DEV_ORIGIN && env.DEV_ORIGIN.trim() !== '') server.origin = env.DEV_ORIGIN.trim()
  const hmr = {}
  if (env.DEV_HMR_HOST && env.DEV_HMR_HOST.trim() !== '') hmr.host = env.DEV_HMR_HOST.trim()
  if (env.DEV_HMR_PROTOCOL && env.DEV_HMR_PROTOCOL.trim() !== '') hmr.protocol = env.DEV_HMR_PROTOCOL.trim().toLowerCase()
  if (env.DEV_HMR_CLIENT_PORT && String(env.DEV_HMR_CLIENT_PORT).trim() !== '') hmr.clientPort = Number(env.DEV_HMR_CLIENT_PORT)
  if (Object.keys(hmr).length > 0) server.hmr = hmr

  return {
    plugins: [vue()],
    resolve: {
      alias: { '@': path.resolve(__dirname, 'src') },
    },
    server,
  }
})
