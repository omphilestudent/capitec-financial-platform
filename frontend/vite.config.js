import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The frontend talks only to the API gateway, which routes each request to the
// microservice that owns it (customer-service, account-service).
const API_GATEWAY = 'http://localhost:8000'

export default defineConfig({
  plugins: [react()],
  server: {
    // 5173 is taken by the Docker frontend container (docker-compose.yml), so the
    // dev server gets its own fixed port. strictPort makes Vite fail loudly instead
    // of silently moving to another port, which breaks HMR and the open tab.
    port: 5174,
    strictPort: true,
    proxy: {
      '/api/v1': {
        target: API_GATEWAY,
        changeOrigin: true,
      },
    },
  },
})
