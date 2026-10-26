async (page) => {
  await page.context().unroute('**/api/health');
  await page.context().route('**/api/health', route => route.fulfill({ json: { code: 0, data: { service: 'hospital-spd', status: 'UP' } } }));
  await page.context().route('**/api/auth/login', route => route.fulfill({ json: { code: 0, data: { token: 'ui-fixture-token' } } }));
  await page.context().route('**/api/mobile/context', route => route.fulfill({ json: { code: 0, data: {
    serverId: 'ui-test', userId: 9, username: '界面测试账号', capabilities: [], warehouses: []
  } } }));
  await page.getByRole('button', { name: '测试连接', exact: true }).click();
  await page.getByRole('status').filter({ hasText: 'SPD 服务连接正常' }).waitFor();
  await page.getByLabel('账号', { exact: true }).fill('ui-test');
  await page.getByLabel('密码', { exact: true }).fill('ui-fixture-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.getByText('当前账号暂无移动签收权限，请联系管理员分配。', { exact: true }).waitFor();
  await page.getByRole('button', { name: '退出登录 / 修改服务器' }).click();
  await page.getByLabel('服务器地址', { exact: true }).waitFor();
  if (await page.getByLabel('密码', { exact: true }).inputValue() !== '') throw new Error('Password retained');
  await page.setViewportSize({ width: 360, height: 800 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Horizontal overflow');
  await page.screenshot({ path: 'D:/SpdApp/spd-implementation/mobile/output/playwright/login-360.png', fullPage: true });
  console.log('PASS: connection feedback, login, permission-empty state, logout/settings, password cleared, 360px layout');
}
