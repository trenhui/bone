const { chromium } = require('playwright');

(async () => {
  let fail = 0;
  const note = (name, ok, detail = '') => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}  ${detail}`); if (!ok) fail++; };
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 } });
  const page = await ctx.newPage();

  const errors = [];
  page.on('console', (m) => {
    if (m.type() !== 'error') return;
    const t = m.text();
    if (t.includes('clearIcon') || t.includes('deprecated')) return; // 良性 antd v5 弃用告警
    errors.push(t);
  });
  page.on('pageerror', (e) => errors.push('PAGEERROR: ' + e.message));
  const dialogs = [];
  page.on('dialog', async (d) => {
    dialogs.push(d.type());
    if (d.type() === 'beforeunload') await d.accept(); else await d.dismiss();
  });

  await page.goto('http://localhost:3000/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2500);
  if (await page.locator('#username').count()) {
    await page.fill('#username', 'admin');
    await page.fill('#password', '123456');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(3000);
  }

  // 新手引导模态：真实按钮是「开始设计」；点掉后等模态消失，Esc 兜底
  const dismissOnboard = async () => {
    for (let i = 0; i < 5; i++) {
      const vis = await page.evaluate(() =>
        [...document.querySelectorAll('.ant-modal-wrap')].filter((w) => getComputedStyle(w).display !== 'none').length,
      );
      if (vis === 0) return;
      const primary = page.locator('.ant-modal-wrap:visible .ant-btn-primary').last();
      if (await primary.count()) { await primary.click().catch(() => {}); }
      else { await page.keyboard.press('Escape'); }
      await page.waitForTimeout(500);
    }
    await page.keyboard.press('Escape');
    await page.waitForTimeout(400);
  };
  const visiblePopconfirm = async () =>
    page.evaluate(() =>
      [...document.querySelectorAll('.ant-popover')]
        .filter((p) => getComputedStyle(p).display !== 'none')
        .map((p) => (p.querySelector('.ant-popover-inner-content')?.innerText ?? p.querySelector('.ant-popover-title')?.innerText ?? ''))
        .join('|'),
    );
  // .ant-segmented 顺序：0=结构面板(录入/详情) 1=预览视图(表单/详情/列表) 2=预览场景 3=设备
  // Segmented 选择器全部按语义锚定，不按全局下标（防新增控件漂移）：
  // - struct：结构面板视图档（左栏，含「录入表单」选项，全局唯一）
  // - view：预览头第一个（表单/详情/列表）
  // - scenario：预览头最后一个（场景样例）——第五轮新增的「草稿/线上」开关
  //   插在视图与场景之间，全局下标法曾因此假绿失败，故改用锚定。
  const segOf = {
    struct: () => page.locator('.ant-segmented').filter({ hasText: '录入表单' }).first(),
    view: () => page.locator('.uis-pv-head .ant-segmented').first(),
    scenario: () => page.locator('.uis-pv-head .ant-segmented').last(),
  };
  const segSelected = async (which) => (await segOf[which]().locator('.ant-segmented-item-selected').innerText().catch(() => '')) || '';
  const badge = async () => (await page.evaluate(() => document.querySelector('.uis-pv-badge')?.innerText || ''));

  // ---------- 报销单 ----------
  await page.goto('http://localhost:3000/metadata#/entities/765601783912333312?tab=uischema', { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(
    () => (document.querySelector('.uis-pv-entity')?.textContent ?? '').includes('expense_claim_chain'),
    { timeout: 45000 },
  ).catch(() => console.log('WARN: 报销单 entityCode 未出现'));
  await dismissOnboard();
  const bodyA = await page.evaluate(() => document.body.innerText);
  note('报销单: 设计器挂载', bodyA.includes('界面设计器') || bodyA.includes('添加分区'));
  note('报销单: 预览默认场景=差旅报销', (await segSelected('scenario')).includes('差旅报销'), `scen=${await segSelected('scenario')}`);
  note('报销单: 预览不含跨领域门诊快赔', !bodyA.includes('门诊快赔'));
  note('报销单: 预览视图默认=表单', (await segSelected('view')).includes('表单'), `view=${await segSelected('view')}`);

  // 预览跟随编辑视图：结构面板切到「详情视图」
  await page.locator('.ant-segmented').nth(0).getByText('详情视图').click();
  await page.waitForTimeout(1000);
  note('报销单: 结构面板切到详情视图', (await segSelected('struct')).includes('详情视图'), `struct=${await segSelected('struct')}`);
  note('报销单: 预览跟随→详情视图', (await segSelected('view')).includes('详情'), `preview=${await segSelected('view')}`);
  note('报销单: 预览徽标=详情视图', (await badge()).includes('详情视图'), `badge=${await badge()}`);
  // 切回表单
  await page.locator('.ant-segmented').nth(0).getByText('录入表单').click();
  await page.waitForTimeout(600);

  // 载入样例制造 dirty，验证撤销键 + 重新加载守卫
  const loadSample = async () => {
    const btn = page.getByRole('button', { name: '载入场景样例' });
    if (await btn.count()) {
      await btn.click();
      await page.waitForTimeout(500);
      const item = page.locator('.ant-dropdown:not(.ant-dropdown-hidden) .ant-dropdown-menu-title-content, .ant-dropdown:not(.ant-dropdown-hidden) .ant-dropdown-menu-item', { hasText: '差旅报销' });
      if (await item.count()) { await item.first().click(); await page.waitForTimeout(700); return true; }
    }
    return false;
  };
  const dirtyTag = async () => (await page.evaluate(() => document.body.innerText)).includes('未保存改动');

  const loaded = await loadSample();
  note('报销单: 载入样例成功', loaded);
  note('报销单: 载入后变脏', await dirtyTag());

  await page.keyboard.press('Control+z');
  await page.waitForTimeout(800);
  note('报销单: Ctrl+Z 后回到已保存', !(await dirtyTag()), `dirty=${await dirtyTag()}`);

  await loadSample();
  await page.waitForTimeout(800);
  const dirtyBeforeReload = await dirtyTag();
  note('报销单: 二次载入后变脏', dirtyBeforeReload);
  await page.getByRole('button', { name: '重新加载' }).click();
  await page.waitForTimeout(900);
  const reloadConfirm = (await visiblePopconfirm()).includes('放弃未保存改动');
  note('报销单: 重新加载弹丢弃确认', reloadConfirm, `pop=${await visiblePopconfirm()}`);
  const cancelBtn = page.locator('.ant-popover:visible button', { hasText: /取\s*消/ });
  if (await cancelBtn.count()) await cancelBtn.first().click();
  await page.waitForTimeout(400);

  await page.screenshot({ path: 'outputs/probe_polish_报销单.png' });

  // ---------- 理赔案件 ----------
  // 注意：与报销单仅 hash 不同，page.goto 不会整页刷新；必须 reload 强制重挂载，
  // 否则读到的还是报销单设计器的旧状态（曾导致场景误判为差旅报销）。
  await page.goto('http://localhost:3000/metadata#/entities/765601790585470976?tab=uischema', { waitUntil: 'domcontentloaded' });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForFunction(
    () => (document.querySelector('.uis-pv-entity')?.textContent ?? '').includes('claim_case_chain'),
    { timeout: 45000 },
  ).catch(() => console.log('WARN: 理赔 entityCode 未出现'));
  await dismissOnboard();
  const bodyB = await page.evaluate(() => document.body.innerText);
  note('理赔: 设计器挂载', bodyB.includes('界面设计器') || bodyB.includes('添加分区'));
  note('理赔: 预览默认场景=门诊快赔', (await segSelected('scenario')).includes('门诊快赔'), `scen=${await segSelected('scenario')}`);
  note('理赔: 预览不含差旅报销', !bodyB.includes('差旅报销'));
  await page.screenshot({ path: 'outputs/probe_polish_理赔案件.png' });

  // beforeunload 守卫：变脏后离开页面必须弹浏览器确认（防静默丢失配置）
  const claimBtn = page.getByRole('button', { name: '载入场景样例' });
  if (await claimBtn.count()) {
    await claimBtn.click();
    await page.waitForTimeout(500);
    const item = page.locator('.ant-dropdown:not(.ant-dropdown-hidden) .ant-dropdown-menu-title-content', { hasText: '门诊快赔' });
    if (await item.count()) { await item.first().click(); await page.waitForTimeout(900); }
  }
  const claimDirty = (await page.evaluate(() => document.body.innerText)).includes('未保存改动');
  note('理赔: 载入样例后变脏', claimDirty);
  dialogs.length = 0;
  await page.goto('http://localhost:3000/', { waitUntil: 'commit', timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(1200);
  note('理赔: 未保存时离开页面弹 beforeunload', dialogs.some((t) => t === 'beforeunload'), `dialogs=${JSON.stringify(dialogs)}`);

  note('控制台无真实报错', errors.length === 0, errors.slice(0, 5).join(' | '));

  await browser.close();
  console.log(fail ? `\n${fail} FAILED` : '\nALL PASS');
  process.exit(fail ? 1 : 0);
})();
