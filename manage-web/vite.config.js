import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
const target = process.env.VITE_API_TARGET || 'http://localhost:8080';
export default defineConfig({
  plugins: [vue()],
  base: './',
  server: {
    port: 8089,
    strictPort: true,
    proxy: {
      '/tijiancms': { target, changeOrigin: true },
      '/tijian': { target, changeOrigin: true },
    },
  },
  preview: { port: 8089 },
});
