// @ts-check
const { defineConfig, devices } = require('@playwright/test');

/**
 * Runs against a real Spring Boot + MySQL stack (docker compose up). Override
 * BASE_URL if the compose stack is listening on a different host/port.
 */
module.exports = defineConfig({
  testDir: './journeys',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  retries: 0,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.BASE_URL || 'http://app:8080',
    trace: 'retain-on-failure',
    video: 'off',
    ignoreHTTPSErrors: true,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
