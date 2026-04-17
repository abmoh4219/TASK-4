// @ts-check
const { test, expect } = require('@playwright/test');

async function loginAs(page, username, password) {
  await page.goto('/login');
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => !url.toString().endsWith('/login'));
}

test.describe('student journey: browse, order, grade report', () => {
  test('browses catalog, sees seeded courses', async ({ page }) => {
    await loginAs(page, 'student', 'Student@Reg24!');
    await page.goto('/catalog');
    const body = await page.locator('body').innerText();
    expect(body.toLowerCase()).toMatch(/calculus|math201|course/);
  });

  test('reaches the grade report', async ({ page }) => {
    await loginAs(page, 'student', 'Student@Reg24!');
    await page.goto('/grades/report');
    await expect(page).toHaveURL(/grades\/report/);
    await expect(page.locator('body')).toContainText(/GPA|credits|grade/i);
  });

  test('reaches the in-app message center', async ({ page }) => {
    await loginAs(page, 'student', 'Student@Reg24!');
    await page.goto('/messages');
    await expect(page.locator('body')).toContainText(/message|notif|quiet/i);
  });
});
