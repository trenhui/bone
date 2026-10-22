/**
 * 新手指引（Onboarding）纯函数层：0-1 里程碑判定 + 引导步骤定义。
 *
 * 为什么把判定逻辑放纯函数而不是散在组件里：
 * 进度卡（OnboardingChecklist）与探针（Playwright）都要消费同一份「做到哪一步了」——
 * 判定只有一份实现，UI 与验收才不会各说各话（单真源纪律，同 uiSchemaChangePreview）。
 *
 * 里程碑语义对齐业界新手指引范式（Notion / Linear / Salesforce Guided Setup）：
 * 每条里程碑必须由「用户真实做过的配置」推定，不靠前端时间戳近似；
 * 未配置的默认态一律判未完成——首开进度卡显示 0/5 是特性不是 bug
 * （默认 schema 把全字段平铺进单一分区，恰好是最需要引导的状态）。
 */
import type { BoneUiSchema } from '@bone/ui-schema';

export interface OnboardingMilestone {
  key: 'form' | 'list' | 'rules' | 'lint' | 'publish';
  label: string;
  hint: string;
  done: boolean;
  /** 设计器内跳转的 tab；publish 无内部 tab（发布在实体详情独立页签），由调用方决定去向 */
  tab?: 'structure' | 'list' | 'rules' | 'actions' | 'variants' | 'lint';
}

/**
 * 0-1 五里程碑判定。
 *
 * @param schema      当前 schema（null=未加载）
 * @param publishState 发布三态（后端 published 快照真源）
 * @param errCount    本地自检 error 数
 * @param fieldCount  实体字段总数（列表「精选」需要对照：平铺全字段 = 没精选）
 */
export function onboardingMilestones(
  schema: BoneUiSchema | null,
  publishState: 'never' | 'pending' | 'published',
  errCount: number,
  fieldCount: number,
): OnboardingMilestone[] {
  const formSections = schema?.views?.form?.sections ?? [];
  const cols = schema?.views?.list?.columns ?? [];
  const ruleCount =
    (schema?.linkageRules?.length ?? 0) +
    (schema?.submitRules?.length ?? 0) +
    (schema?.actionBar?.length ?? 0) +
    (schema?.variants?.length ?? 0);
  const formDone = formSections.length >= 2;
  const listDone =
    cols.length > 0 &&
    (cols.length < fieldCount || cols.some((c) => (c.width ?? 120) !== 120) || cols.some((c) => c.pinned));
  const rulesDone = ruleCount > 0;
  // 「自检零错误」要求先有过真实配置：默认 schema（全字段平铺）本身零错误，
  // 若不设前置，首开进度卡就亮一颗星——把「什么都没配」显示成「自检通过」是误导。
  const lintDone = !!schema && errCount === 0 && (formDone || listDone || rulesDone);

  return [
    {
      key: 'form',
      label: '组织表单分区',
      hint: '默认结构把全部字段堆在「基本信息」一个分区；按业务语义拆分（如 报案信息 / 金额信息），终端录入更高效',
      done: formDone,
      tab: 'structure',
    },
    {
      key: 'list',
      label: '精选列表列',
      hint: '列表默认全字段平铺；审批人 80% 时间在列表页——只留关键列、调列宽、固定首列',
      done: listDone,
      tab: 'list',
    },
    {
      key: 'rules',
      label: '配置业务规则',
      hint: '联动（字段显隐/带值）、提交校验（如 金额须大于 0）、动作条按钮、按身份生效的变体——至少配一条',
      done: rulesDone,
      tab: 'rules',
    },
    {
      key: 'lint',
      label: '自检零错误',
      hint: '「自检」面板核对协议一致性（字段/动作码是否真实存在）；存在错误会阻断发布',
      done: lintDone,
      tab: 'lint',
    },
    {
      key: 'publish',
      label: '发布上线',
      hint: '保存只存草稿；到实体详情「发布」页签确认发布，终端用户才看得到',
      done: publishState === 'published',
    },
  ];
}

/* ---------------- localStorage 键（按实体隔离：0-1 是每个实体各自的旅程） ---------------- */

export const tourStorageKey = (entityId: string): string => `bone-designer-tour-v2:${entityId}`;
export const checklistDismissKey = (entityId: string): string => `bone-designer-onboard-dismissed:${entityId}`;

/* ---------------- 引导步骤定义 ---------------- */

export type TourAnchor =
  | 'left-panel'
  | 'palette-fab'
  | 'section'
  | 'tab-list'
  | 'tab-rules'
  | 'save'
  | 'publish-state'
  | 'none';

export interface TourStepDef {
  key: string;
  title: string;
  /** 支持 \n 分段渲染 */
  body: string;
  anchor: TourAnchor;
  placement: 'right' | 'left' | 'top' | 'bottom' | 'center';
  /** action 步骤：用户真实点击锚点即完成（建立肌肉记忆），同时保留「继续」兜底不卡死 */
  action?: boolean;
  doneText?: string;
}

/**
 * 六步 0-1 引导（对标 driver.js / Figma onboarding 的 spotlight 范式，但步骤锚在真实操作上）：
 * 欢迎（认识两栏）→ 搭表单 → 整分区 → 精选列表列 → 加规则 → 自检保存发布。
 * action 步（palette / tab-list / tab-rules）要求用户真点——看过 ≠ 会做。
 * 注意：标题不带「第 N 步」——卡片右上角计数器（x/6）是唯一编号源，双编号会漂移
 * （曾出现标题「第 5 步」而计数 6/6 的割裂感）。
 */
export const TOUR_STEPS: TourStepDef[] = [
  {
    key: 'welcome',
    title: '两栏工作台：左配置 · 右预览',
    body: '0-1 路径：建模字段 → 设计界面 → 自检发布。\n右侧预览跑的是运行时同一套引擎——左改右见，不用先保存就能看到效果。顶部进度卡会带你走完五个里程碑。',
    anchor: 'left-panel',
    placement: 'right',
  },
  {
    key: 'palette',
    title: '搭表单：从字段库开始',
    body: '点右下角「＋」调出字段库，点字段即落入当前分区。\n默认结构已把全部字段放入表单——你的工作是把它们组织进正确的分区：可从分区移出放错位置的字段，再从字段库点选加到目标分区。',
    anchor: 'palette-fab',
    placement: 'top',
    action: true,
    doneText: '字段库已打开，点选字段即可加入分区',
  },
  {
    key: 'section',
    title: '整分区：拖拽即信息架构',
    body: '拖住控件行可在分区内 / 跨分区移动；点控件就地展开属性（显示名、占位、栅格宽度、只读）。\n分区头的把手可调整分区顺序。',
    anchor: 'section',
    placement: 'right',
  },
  {
    key: 'list',
    title: '精选列表列',
    body: '列表视图默认全字段平铺——终端用户只看关键几列。\n到「列表」面板勾选可见列、调列宽、固定首列。',
    anchor: 'tab-list',
    placement: 'bottom',
    action: true,
    doneText: '列表视图已配置',
  },
  {
    key: 'rules',
    title: '加第一条业务规则',
    body: '「规则」面板配联动与提交校验（如 金额须大于 0）；「动作条」配提交 / 作废按钮；「变体」让不同身份看到不同界面。',
    anchor: 'tab-rules',
    placement: 'bottom',
    action: true,
    doneText: '规则已配置',
  },
  {
    key: 'publish',
    title: '自检 → 保存 → 发布',
    body: '保存只存草稿；先在「自检」确认零错误，再点工具栏「去发布」跳到发布页——发布后才对终端用户生效。\n上线前可先看「变更预告」，用业务语言核对这次改了什么。',
    anchor: 'save',
    placement: 'top',
  },
];
