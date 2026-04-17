// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * Real-browser login journey for each seeded role. Opens the Thymeleaf login
 * page, submits the form, and asserts we land on a page whose heading reflects
 * the signed-in user. No mocking — this runs against the live app + MySQL.
 */
async function loginAs(page, username, password) {
  await page.goto('/login');
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => !url.toString().endsWith('/login'));
}

test.describe('login journeys', () => {
  test('student can sign in and reach the dashboard', async ({ page }) => {
    await loginAs(page, 'student', 'Student@Reg24!');
    await expect(page).toHaveURL(/\/$|dashboard/);
    await expect(page.locator('body')).toContainText(/student|Aiko/i);
  });

  test('faculty can sign in', async ({ page }) => {
    await loginAs(page, 'faculty', 'Faculty@Reg2024!');
    await expect(page.locator('body')).toContainText(/Faculty|Eleanor|Vance/i);
  });

  test('reviewer can sign in', async ({ page }) => {
    await loginAs(page, 'reviewer', 'Review@Reg2024!');
    await expect(page.locator('body')).toContainText(/Reviewer|Holloway/i);
  });

  test('admin can sign in and see admin nav', async ({ page }) => {
    await loginAs(page, 'admin', 'Admin@Registrar24!');
    await expect(page.locator('body')).toContainText(/Admin|System Administrator/i);
  });

  test('invalid credentials redirect back with error hint', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input[name="username"]', 'student');
    await page.fill('input[name="password"]', 'not-the-password');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/login.*error/);
  });
});
