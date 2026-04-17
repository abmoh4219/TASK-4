// @ts-check
const { test, expect } = require('@playwright/test');

async function loginAs(page, username, password) {
  await page.goto('/login');
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => !url.toString().endsWith('/login'));
}

test.describe('faculty & reviewer evaluation journeys', () => {
  test('faculty reaches evaluations page', async ({ page }) => {
    await loginAs(page, 'faculty', 'Faculty@Reg2024!');
    await page.goto('/evaluations');
    await expect(page).toHaveURL(/evaluations/);
    await expect(page.locator('body')).toContainText(/cycle|evaluation/i);
  });

  test('reviewer reaches evaluations page and sees submitted cycles area', async ({ page }) => {
    await loginAs(page, 'reviewer', 'Review@Reg2024!');
    await page.goto('/evaluations');
    await expect(page.locator('body')).toContainText(/cycle|evaluation/i);
  });

  test('student is forbidden from /evaluations', async ({ page }) => {
    await loginAs(page, 'student', 'Student@Reg24!');
    const resp = await page.goto('/evaluations');
    expect([401, 403]).toContain(resp.status());
  });
});
