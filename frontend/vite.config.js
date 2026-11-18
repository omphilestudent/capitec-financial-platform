import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The frontend talks only to the API gateway, which routes each request to the
// microservice that owns it (customer-service, account-service).
const API_GATEWAY = 'http://localhost:8000'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/v1': {
        target: API_GATEWAY,
        changeOrigin: true,
      },
    },
  },
})
