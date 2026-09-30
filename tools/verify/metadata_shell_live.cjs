// 浏览器级 live 联调：bone-shell(3000) 加载 bone-metadata-app(3004) 子应用
// 验证：实体管理列表含 blueprint 三表 / 导入向导逆向建模 / 运行时数据页（模式 B）
// 运行：NODE_PATH=/Users/renhui.trh/.workbuddy/binaries/node/workspace/node_modules node tools/verify/metadata_shell_live.cjs <TOKEN>
const { chromium } = require('playwright');

const TOKEN = process.argv[2];
const BASE = 'http://localhost:3000';
const SHELL = process.env.SHELL_USER || JSON.stringify({
  id: 1, username: 'admin', realName: '管理员', tenantId: 0, roles: ['SUPER_ADMIN'],
});

(async () => {
  const browser = await chromium.launch({
    executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
  const page = await browser.newPage();
  const errors = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message));

  await page.addInitScript(
    ({ t, u }) => {
      localStorage.setItem('token', t);
      localStorage.setItem('bone-user', u);
    },
    { t: TOKEN, u: SHELL },
  );

  const result = { entityList: {}, importWizard: {}, runtimePage: {}, errors: [] };

  // 1) 实体管理列表
  await page.goto(BASE + '/metadata#/entities', { waitUntil: 'networkidle' });
  await page.waitForSelector('text=实体管理', { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(2500);
  const body1 = await page.innerText('body');
  result.entityList = {
    hasTOrder: body1.includes('t_order'),
    hasTOrderItem: body1.includes('t_order_item'),
    hasBPayment: body1.includes('bp_payment'),
    hasBlueprintLabel: body1.includes('blueprint'),
  };
  await page.screenshot({ path: '/tmp/final-entities.png', fullPage: true });

  // 2) 导入向导（逆向建模）
  const importBtn = page.getByRole('button', { name: '从存量表导入' });
  await importBtn.click({ timeout: 10000 }).catch((e) => { result.importWizard.clickError = String(e).slice(0, 200); });
  await page.waitForTimeout(1500);
  const previewOpened = await page.locator('#tableName').count();
  result.importWizard.opened = previewOpened > 0;
  if (previewOpened > 0) {
    await page.locator('#tableName').fill('t_order');
    const previewBtn = page.getByRole('button', { name: /采集预览/ });
    await previewBtn.click({ timeout: 10000 }).catch((e) => { result.importWizard.previewError = String(e).slice(0, 200); });
    await page.waitForTimeout(2500);
    const body2 = await page.innerText('body');
    result.importWizard.mentionsSkipped = body2.includes('跳过') || body2.includes('平台列') || body2.includes('skipped');
    result.importWizard.mentionsFieldCount = /\d+\s*个?字段|\d+\s*列|业务列/.test(body2);
    result.importWizard.raw = body2.slice(0, 400);
  }
  await page.screenshot({ path: '/tmp/final-import.png', fullPage: true });

  // 3) 运行时数据页（模式 B）
  const BP_PAYMENT_ID = '760313249877983232';
  await page.goto(BASE + '/metadata#/entities/' + BP_PAYMENT_ID + '/data', { waitUntil: 'networkidle' });
  await page.waitForTimeout(3000);
  const body3 = await page.innerText('body');
  result.runtimePage = {
    mentionsModeB: body3.includes('模式 B') || body3.includes('运行时'),
    mentionsRuntimeApi: body3.includes('/api/v1/runtime'),
    hasTable: await page.locator('table').count() > 0 || body3.includes('列') || body3.includes('数据'),
  };
  await page.screenshot({ path: '/tmp/final-runtime.png', fullPage: true });

  result.errors = errors;
  console.log(JSON.stringify(result, null, 2));
  await browser.close();
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
