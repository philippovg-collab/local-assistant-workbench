import path from "path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const apiProxyTarget = "http://127.0.0.1:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api": {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: "127.0.0.1",
    proxy: {
      "/api": {
        target: apiProxyTarget,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    testTimeout: 20000,
    coverage: {
      provider: "v8",
      reporter: ["text", "lcov"],
      reportsDirectory: "./coverage",
      thresholds: {
        lines: 70,
        functions: 55,
        statements: 70,
        branches: 68,
        "src/api/**": {
          lines: 55,
          functions: 50,
          statements: 55,
          branches: 48,
        },
        "src/hooks/**": {
          lines: 64,
          functions: 56,
          statements: 64,
          branches: 68,
        },
        "src/utils/**": {
          lines: 82,
          functions: 90,
          statements: 82,
          branches: 72,
        },
      },
    },
  },
});
