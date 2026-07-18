import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
  },
  server: {
    port: 5173,
    proxy: {
      '/api/market-data': {
        target: 'http://localhost:8082',
        rewrite: (path) => path.replace('/api/market-data', ''),
      },
      '/api/pricing': {
        target: 'http://localhost:8083',
        rewrite: (path) => path.replace('/api/pricing', ''),
      },
      '/api/oms': {
        target: 'http://localhost:8084',
        rewrite: (path) => path.replace('/api/oms', ''),
      },
    },
  },
})
