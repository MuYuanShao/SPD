async (page) => {
  await page.context().unroute('**/api/**');
  const results = new Map();
  const submits = [];
  let validations = 0;
  const task = { id: 10, documentNo: 'UI-PS-10', status: 'picked', deliveryType: 'loose', deptId: 2,
    deptName: '测试科室', sourceWarehouseId: 3, sourceWarehouseName: '测试中心库', warehouseId: 4,
    warehouseName: '测试科室库', productCode: 'T1', productName: '界面验收耗材', quantity: 4, unit: '个' };
  const packageTask = { ...task, id: 20, documentNo: 'UI-PS-20', deliveryType: 'package' };
  const details = new Map([[10, { task, items: [] }], [20, { task: packageTask, items: [
    { type: 'package', id: 11, code: 'PK-11', alternateCode: '', status: 'delivered' },
    { type: 'package', id: 12, code: 'PK-12', alternateCode: '', status: 'delivered' }
  ] }]]);
  await page.context().route('**/api/**', async route => {
    const request = route.request();
    const path = request.url().replace(/^https?:\/\/[^/]+/, '').split('?')[0];
    const body = request.method() === 'POST' ? request.postDataJSON() : null;
    let data;
    if (path.endsWith('/health')) data = { service: 'hospital-spd', status: 'UP' };
    else if (path.endsWith('/auth/login')) data = { token: 'ui-signing-token' };
    else if (path.endsWith('/mobile/context')) data = { serverId: 'signing-ui-fixture', userId: 1, username: '签收界面验收',
      capabilities: ['SIGN_DELIVERY'], warehouses: [{ deptId: 2, deptName: '测试科室', warehouseId: 4, warehouseName: '测试科室库' }] };
    else if (path.endsWith('/mobile/tasks')) {
      const rows = [task, packageTask].filter(t => !Array.from(results.values()).some(r => r.taskId === t.id));
      data = { rows, total: rows.length, page: 1, size: 20 };
    } else if (path.includes('/mobile/tasks/SIGN_DELIVERY/')) data = details.get(Number(path.split('/').pop()));
    else if (path.endsWith('/scans/resolve')) {
      const item = details.get(body.taskId).items.find(i => i.code === body.rawCode);
      if (!item) { await route.fulfill({ status: 400, json: { code: 400, message: '此码不属于当前配送单' } }); return; }
      data = item;
    } else if (path.endsWith('/operations/validate')) {
      validations++;
      if (body.taskId === 10 && !body.looseConfirmed) throw new Error('Missing loose confirmation');
      if (body.taskId === 20 && JSON.stringify(body.packageIds.slice().sort()) !== '[11,12]') throw new Error('Incomplete package scan');
      data = { reviewHash: 'ui-review', expiresAt: new Date(Date.now() + 300000).toISOString(), summary: details.get(body.taskId) };
    } else if (path.endsWith('/mobile/operations')) {
      if (!body.reviewHash) throw new Error('No review token');
      submits.push(body);
      data = { operationId: body.operationId, kind: 'SIGN_DELIVERY', taskId: body.taskId,
        documentNo: details.get(body.taskId).task.documentNo, status: 'succeeded', userId: 1,
        deviceId: body.deviceId, completedAt: new Date().toISOString(), message: '配送整单签收成功' };
      results.set(body.operationId, data);
      if (body.taskId === 20) { await route.fulfill({ status: 503, json: { code: 503, message: '模拟响应丢失' } }); return; }
    } else if (path.includes('/mobile/operations/')) data = results.get(path.split('/').pop());
    else throw new Error('Unexpected API: ' + path);
    if (!data) throw new Error('Missing fixture: ' + path);
    await route.fulfill({ json: { code: 0, message: 'success', data } });
  });
  async function login() {
    await page.getByLabel('账号', { exact: true }).fill('ui-signing');
    await page.getByLabel('密码', { exact: true }).fill('ui-only-password');
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await page.getByRole('button',{name:'登录',exact:true}).waitFor({state:'detached'});
  }
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto('http://127.0.0.1:1831/');
  await page.evaluate(() => {
    for (const key of Object.keys(sessionStorage)) if (key.startsWith('spd-dev-signing:')) sessionStorage.removeItem(key);
  });
  await login();await page.getByRole('button',{name:/^签收/}).click();
  await page.getByRole('combobox', { name: '科室与库房' }).selectOption('4');
  await page.locator('article').filter({ hasText: 'UI-PS-10' }).getByRole('button', { name: '打开核对' }).click();
  if (await page.getByRole('button', { name: '校验本次签收' }).isEnabled()) throw new Error('Unconfirmed loose delivery enabled');
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: '校验本次签收' }).click();
  await page.getByRole('button', { name: '确认整单签收' }).waitFor();
  if (submits.length !== 0) throw new Error('Validation caused a business write');
  await page.getByRole('button', { name: '确认整单签收' }).click();
  await page.getByText('配送整单签收成功', { exact: true }).waitFor();
  await page.screenshot({ path: 'D:/SpdApp/spd-implementation/mobile/output/playwright/signing-success.png', fullPage: true });
  await page.getByRole('button', { name: '完成并刷新任务' }).click();
  await page.locator('article').filter({ hasText: 'UI-PS-20' }).getByRole('button', { name: '打开核对' }).click();
  const input = page.getByLabel('PDA 扫码 / 手工输入', { exact: true });
  await input.fill('WRONG'); await input.press('Enter');
  await page.getByRole('status').filter({ hasText: '此码不属于当前配送单' }).waitFor();
  await input.fill('PK-11'); await input.press('Enter');
  await page.getByText('实物核对：1 / 2', { exact: true }).waitFor();
  await input.fill('PK-11'); await input.press('Enter');
  await page.getByRole('status').filter({ hasText: '未重复累计' }).waitFor();
  if (await page.getByRole('button', { name: '校验本次签收' }).isEnabled()) throw new Error('Incomplete package delivery enabled');
  await input.fill('PK-12'); await input.press('Enter');
  await page.getByText('实物核对：2 / 2', { exact: true }).waitFor();
  await page.getByRole('button', { name: '校验本次签收' }).click();
  await page.getByRole('button', { name: '确认整单签收' }).click();
  await page.getByRole('button', { name: '查询原操作结果' }).waitFor();
  await page.reload(); await login();
  await page.getByRole('button', { name: '查询原操作结果' }).click();
  await page.getByText('配送整单签收成功', { exact: true }).waitFor();
  if (submits.length !== 2 || validations !== 2) throw new Error('Unexpected duplicate write or review');
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Horizontal overflow');
  await page.screenshot({ path: 'D:/SpdApp/spd-implementation/mobile/output/playwright/signing-recovery.png', fullPage: true });
  await page.getByRole('button', { name: '完成并刷新任务' }).click();
  await page.getByText('当前库房没有待签收任务', { exact: true }).waitFor();
  console.log('PASS: explicit confirmation; exact code checks; duplicate prevention; timeout/reload/original receipt recovery; 360px layout');
}
