import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    // The Spring Boot API runs on 8080 during development.
    proxy: { '/api': 'http://localhost:8080' },
  },
})
