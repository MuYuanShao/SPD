import assert from 'node:assert/strict';
import { chromium } from '@playwright/test';

const base = process.env.SPD_REVIEW_APP_URL;
const password = process.env.SPD_REVIEW_ADMIN_PASSWORD;
if (!base || !password) throw new Error('Isolated review application URL and credentials are required');
const browser = await chromium.launch({
  headless: true,
  ...(process.platform === 'win32' ? { executablePath: process.env.PLAYWRIGHT_CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' } : {}),
});
const page = await browser.newPage();
try {
  await page.goto(`${base}/api/app/features/audit-log`);
  await page.getByRole('textbox', { name: '用户名' }).fill('admin');
  await page.getByRole('textbox', { name: '密码' }).fill(password);
  await page.getByRole('button', { name: /^登\s*录$/ }).click();
  await page.waitForURL('**/features/audit-log');
  const filters = page.locator('.sys-form-grid select');
  await filters.nth(0).selectOption('REVIEW_FIX');
  await filters.nth(1).selectOption('review_contract');
  const pending = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname === '/api/audit-logs' && url.searchParams.get('operationType') === 'REVIEW_FIX'
      && url.searchParams.get('bizType') === 'review_contract';
  });
  await page.getByRole('button', { name: '查询', exact: true }).click();
  const response = await pending;
  assert.equal(response.status(), 200);
  const result = await response.json();
  assert.equal(result.code, 0);
  assert.equal(result.data.total, 1);
  assert.equal(result.data.rows[0].operationType, 'REVIEW_FIX');
  assert.equal(result.data.rows[0].bizType, 'review_contract');
  await page.locator('tbody').getByText('review regression marker', { exact: true }).waitFor();
  console.log('PASS: real audit facets select the correct values and filter persisted rows in Chrome.');
} catch (error) {
  console.error('Review UI location:', page.url());
  console.error('Review UI headings:', await page.locator('h1, h2, h4').allTextContents());
  throw error;
} finally {
  await browser.close();
}
