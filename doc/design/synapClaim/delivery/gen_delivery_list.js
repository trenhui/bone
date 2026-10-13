const path = require('path');
const h = require(path.join(process.env.SKILL_PATH, 'docx', 'scripts', 'docx-helper'))({
  fonts: { heading: 'Microsoft YaHei', body: 'Microsoft YaHei' },
  colors: { primary: '1F4E79', accent: '4A90D9', text: '333333', light: 'EAF1F8' },
  sizes: { h1: 32, h2: 26, body: 22 },
});

const C = h.colors;
const hf = h.headerFooter('ClaimSphere 智能理赔平台 · 落地交付包清单');

function coverSection() {
  return [
    h.spacer(3000),
    h.p('ClaimSphere 智能理赔平台', { size: 56, bold: true, color: C.primary, align: 'center' }),
    h.spacer(300),
    h.p('落地交付包清单', { size: 44, bold: true, align: 'center' }),
    h.divider(C.primary),
    h.spacer(600),
    h.p('版本 V1.0 ｜ 2026-10-12 ｜ Synap（思纳普）出品', { size: 22, color: '666666', align: 'center' }),
    h.spacer(400),
    h.p('面向保司 / TPA 的试点合作交付包总览', { size: 22, color: '666666', align: 'center' }),
  ];
}

function section1() {
  return [
    h.h1('一、交付包定位'),
    h.p('本交付包是 ClaimSphere 智能理赔平台与贵司（保司 / TPA）开展试点合作的完整材料集，覆盖从协议签署、数据安全、系统接入、业务验证到规模化商务的全过程。'),
    h.p('交付包以「可量化、可审计、可回退」为原则：'),
    h.bullet('可量化：每个环节都有明确的指标口径与验收标准，不空谈效果'),
    h.bullet('可审计：每笔审核决策可回放（证据 → 解析 → 核责 → 理算 → 决策）'),
    h.bullet('可回退：试点全程旁路运行，不改变贵司现有理赔流程，随时可关停'),
  ];
}

function section2() {
  return [
    h.h1('二、交付物总览（共 10 项）'),
    h.table({
      widths: [1100, 3200, 4500, 1200],
      header: ['编号', '交付物', '一句话说明', '交付时点'],
      rows: [
        ['01', '试点合作框架协议（MOU / NDA / DPA）', '三份基础契约：锁定试点范围、数据授权与成功标准', '签约期'],
        ['02', '数据脱敏与个人信息处理方案', '脱敏字段、算法、验证方法与泄露应急响应', '签约期'],
        ['03', 'POC 演示脚本', '45 分钟端到端演示剧本与常见异议应答', '签约期'],
        ['04', '业务规则书（Rulebook）', '理赔规则全集：责任范围、免赔、比例、封顶、风控', '开发期'],
        ['05', '接口契约与数据字典', '系统对接接口规范与核心数据字段定义', '开发期'],
        ['06', '医学目录库建设方案', '医保 / 药品 / 疾病库数据来源与维护机制', '开发期'],
        ['07', '测试方案与性能基准', '五层测试计划与 SLA 指标口径', '试点准备'],
        ['08', '部署运维手册', '部署、监控、备份恢复、应急手册', '试点准备'],
        ['09', '培训与用户手册', '核赔员 / 运营操作手册与人责归属说明', '试点准备'],
        ['10', '商务方案与规模化准备', '定价模式与合同条款要点（内部材料）', '规模化'],
      ],
      headerColor: C.primary,
      altColor: C.light,
    }),
  ];
}

function section3() {
  return [
    h.h1('三、交付节奏（四阶段）'),
    h.table({
      widths: [1600, 1800, 3800, 3000],
      header: ['阶段', '周期', '交付物', '里程碑'],
      rows: [
        ['签约期', '1-2 周', '01 协议、02 脱敏方案、03 演示脚本', '双方签署三份协议，完成数据交接'],
        ['开发期', '6-10 周', '04 规则书、05 接口契约、06 目录库', '一张发票走通「上传 → 解析 → 核责 → 理算 → 建议」，30 秒内出结果'],
        ['试点准备', '试点前 4 周', '07 测试方案、08 运维手册、09 培训手册', '旁路（Shadow）模式接入贵司环境，评测报告交付'],
        ['规模化', 'Phase B 之后', '10 商务方案', '试点达标后转入辅助上线与商务合作'],
      ],
      headerColor: C.primary,
      altColor: C.light,
    }),
  ];
}

function section4() {
  return [
    h.h1('四、服务承诺（量化口径）'),
    h.table({
      widths: [2200, 2300, 3900],
      header: ['指标', '承诺口径', '说明'],
      rows: [
        ['单案处理成本', '下降 ≥50%', '以贵司当前人工处理基线对比'],
        ['审核建议时效', '95% 请求 ≤30 秒返回', '自动路径（材料解析 + 规则核责 + 理算）'],
        ['字段提取准确率', '≥95%', '评测集对照人工标注，口径可审计'],
        ['规则匹配准确率', '≥90%', '规则结果对照人工核责结论'],
        ['人工复核率', '≤30%', '需人工介入案件占比'],
        ['重大错误率', '0 容忍', '错赔 / 漏赔案件全部转人工兜底'],
        ['试点接入', '4-6 周旁路运行', '只读接入，不改变现有流程，可随时回退'],
      ],
      headerColor: C.primary,
      altColor: C.light,
    }),
    h.spacer(200),
    h.p('以上指标均以授权脱敏样本评测集为测量基准，评测方法与样本口径随交付物 04 / 07 提供，贵司可复核。', { size: 20, color: '555555' }),
  ];
}

function section5() {
  return [
    h.h1('五、合规与数据安全承诺'),
    h.numbered('数据分级脱敏：敏感个人信息（病历、身份信息）默认不出域，处理遵循 PIPL 要求', { ref: 'sec5' }),
    h.numbered('协议保障：MOU / NDA / DPA 三份契约覆盖数据授权、用途限制、留存与删除义务', { ref: 'sec5' }),
    h.numbered('审计能力：每笔决策全程留痕（输入摘要、模型输出、动作结果），资金 / 决策日志保留 ≥7 年', { ref: 'sec5' }),
    h.numbered('人责归属：系统输出为辅助建议，最终审核结论由贵司核赔员 / 保司负责；重大错误率 0 容忍', { ref: 'sec5' }),
    h.numbered('应急响应：数据安全事件 24 小时内书面通知，附取证、整改与复盘流程', { ref: 'sec5' }),
  ];
}

function section6() {
  return [
    h.h1('六、联系与签署'),
    h.table({
      widths: [2500, 2900, 3000],
      header: ['角色', '联系人', '电话'],
      rows: [
        ['商务联系人', '＿＿＿＿＿＿＿＿', '＿＿＿＿＿＿＿＿'],
        ['技术联系人', '＿＿＿＿＿＿＿＿', '＿＿＿＿＿＿＿＿'],
        ['数据接口人', '＿＿＿＿＿＿＿＿', '＿＿＿＿＿＿＿＿'],
      ],
      headerColor: C.primary,
      altColor: C.light,
    }),
    h.spacer(200),
    h.p('签署流程：NDA → MOU → DPA → 数据交接 → 旁路部署 → 评测报告（详见交付物 01）'),
    h.spacer(400),
    h.divider(C.primary),
    h.spacer(200),
    h.p('本清单为 ClaimSphere 落地交付包总览，各交付物详细内容见对应文档。版本 V1.0，2026-10-12。', { size: 20, color: '888888', align: 'center' }),
  ];
}

h.build({
  sections: [
    { role: 'cover', children: coverSection() },
    { role: 'body', ...hf, children: [
      ...section1(),
      ...section2(),
      ...section3(),
      ...section4(),
      ...section5(),
      ...section6(),
    ]},
  ],
});
