import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// Quinoa serves the build output (dist) and, in dev mode, proxies to this dev server (port 5173).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  build: {
    outDir: "dist",
    // Every asset is a file of this origin: the Content Security Policy allows no data: URLs.
    assetsInlineLimit: 0,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["src/test/setup.ts"],
  },
});
