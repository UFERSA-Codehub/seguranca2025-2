import path from "path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import basicSsl from "@vitejs/plugin-basic-ssl"
import { defineConfig } from "vite"

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), basicSsl()],
  server: {
    port: 3333,
    host: true,
    https: true,
    proxy: {
      // Proxy API requests to backend
      '/api': {
        target: 'http://172.18.64.222:9091',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/api/, '/browser/api'),
      },
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
})
