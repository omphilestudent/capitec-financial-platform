import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Each API path is served by one microservice. The dev server forwards
// requests to the service that owns them, so the browser only talks to :5173.
const CUSTOMER_SERVICE = 'http://localhost:8081' // customer-service
const ACCOUNT_SERVICE = 'http://localhost:8082' // account-service

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/v1/customers': {
        target: CUSTOMER_SERVICE,
        changeOrigin: true,
      },
      '/api/v1/accounts': {
        target: ACCOUNT_SERVICE,
        changeOrigin: true,
      },
    },
  },
})
