import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // decision-api(8081) 执行面端点：同步/按规则键/异步决策与决策流执行。
      // 以 ^ 开头的键按正则匹配，优先于下方 /api 通用前缀——
      // 这些端点仅存在于 decision-api(8081)，落入 admin(8080) 会 404。
      '^/api/v1/decide': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '^/api/v1/decision-flows/[^/]+/execute': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      // admin-api(8080)：管理面 CRUD 与其余查询（默认）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
