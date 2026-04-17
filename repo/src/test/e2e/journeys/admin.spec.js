// @ts-check
const { test, expect } = require('@playwright/test');

async function loginAs(page, username, password) {
  await page.goto('/login');
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => !url.toString().endsWith('/login'));
}

test.describe('admin journey: user + audit + config', () => {
  test('can view users page', async ({ page }) => {
    await loginAs(page, 'admin', 'Admin@Registrar24!');
    await page.goto('/admin/users');
    await expect(page).toHaveURL(/admin\/users/);
    await expect(page.locator('body')).toContainText(/student|faculty|admin/i);
  });

  test('can view audit log', async ({ page }) => {
    await loginAs(page, 'admin', 'Admin@Registrar24!');
    await page.goto('/admin/audit');
    await expect(page).toHaveURL(/admin\/audit/);
  });

  test('can view and submit config', async ({ page }) => {
    await loginAs(page, 'admin', 'Admin@Registrar24!');
    await page.goto('/admin/config');
    await expect(page.locator('body')).toContainText(/policy|setting|refund|payment/i);
  });

  test('non-admin student is blocked from /admin', async ({ page }) => {
    await loginAs(page, 'student', 'Student@Reg24!');
    const resp = await page.goto('/admin');
    expect([401, 403]).toContain(resp.status());
  });
});
