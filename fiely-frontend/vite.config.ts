import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// During development the Vite dev server proxies `/api` and `/actuator`
// requests to the Fiely backend so the frontend and backend feel like a
// single origin. The backend URL is configurable via the VITE_API_PROXY
// env var so the same config works in Docker (`http://fiely:8080`) and on
// a bare host (`http://localhost:8080`).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiTarget = env.VITE_API_PROXY ?? 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      host: true,
      proxy: {
        '/api': { target: apiTarget, changeOrigin: true },
        '/actuator': { target: apiTarget, changeOrigin: true },
        // NOTE: plugin bundles will live under /apps/{id}/{version}/... and
        // need to reach the backend too; the proxy rule is added when the
        // plugin loader lands (it must distinguish SPA routes from assets).
      },
    },
  };
});
