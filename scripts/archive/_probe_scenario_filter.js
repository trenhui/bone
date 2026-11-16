const { chromium } = require('playwright');

(async () => {
  let fail = 0;
  const note = (name, ok, detail = '') => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}  ${detail}`); if (!ok) fail++; };
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1680, height: 1000 } });
  const page = await ctx.newPage();

  await page.goto('http://localhost:3000/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2500);
  if (await page.locator('#username').count()) {
    await page.fill('#username', 'admin');
    await page.fill('#password', '123456');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(3000);
  }

  const probe = async (eid, label, { expectClaim, expectExpense }) => {
    await page.goto(`http://localhost:3000/metadata#/entities/${eid}?tab=uischema`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(6000);
    // 关闭「3 步上手」引导 Modal（首次访问实体时出现，会挡住后续点击）
    const startBtn = page.getByRole('button', { name: '开始使用' });
    if (await startBtn.count()) { await startBtn.click(); await page.waitForTimeout(500); }
    const body = await page.evaluate(() => document.body.innerText);
    note(`${label}: 设计器挂载`, body.includes('界面设计器') || body.includes('添加分区'));
    // 预览沙盒场景 Tabs
    note(`${label}: 预览场景${expectClaim ? '含' : '不含'}门诊快赔`, expectClaim ? body.includes('门诊快赔') : !body.includes('门诊快赔'));
    note(`${label}: 预览场景${expectExpense ? '含' : '不含'}差旅报销`, expectExpense ? body.includes('差旅报销') : !body.includes('差旅报销'));
    note(`${label}: 预览场景含空白`, body.includes('空白'));
    // 载入场景样例下拉
    const btn = page.getByRole('button', { name: '载入场景样例' });
    if (await btn.count()) {
      await btn.click();
      await page.waitForTimeout(600);
      const menuText = await page.evaluate(() => {
        const m = document.querySelector('.ant-dropdown:not(.ant-dropdown-hidden)');
        return m ? m.innerText : '';
      });
      note(`${label}: 样例下拉${expectClaim ? '含' : '不含'}门诊快赔`, expectClaim ? menuText.includes('门诊快赔') : !menuText.includes('门诊快赔'));
      note(`${label}: 样例下拉${expectExpense ? '含' : '不含'}差旅报销`, expectExpense ? menuText.includes('差旅报销') : !menuText.includes('差旅报销'));
      await page.keyboard.press('Escape');
    } else {
      note(`${label}: 样例下拉`, false, '按钮不存在');
    }
    await page.screenshot({ path: `/tmp/probe_scenario_${label}.png` });
  };

  await probe('765601783912333312', '报销单', { expectClaim: false, expectExpense: true });
  await probe('765601790585470976', '理赔案件', { expectClaim: true, expectExpense: false });

  await browser.close();
  console.log(fail ? `\n${fail} FAILED` : '\nALL PASS');
  process.exit(fail ? 1 : 0);
})();
