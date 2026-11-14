import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests/browser',
  timeout: 90000,
  fullyParallel: false,
  workers: 1,
  outputDir: 'output/test-results/browser',
  reporter: [['list'], ['html', { outputFolder: 'output/playwright-report', open: 'never' }]],
  use: {
    channel: 'msedge',
    headless: true,
    viewport: { width: 1440, height: 1000 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
