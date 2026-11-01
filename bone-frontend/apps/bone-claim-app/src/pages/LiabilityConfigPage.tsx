import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  CloudUploadOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { resolveErrorMessage } from '@bone/shared-utils';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { BatchImportModal, type ImportColumn } from '../components/BatchImportModal';
import { usePolicyOptions } from '../hooks/usePolicyOptions';
import { useConfirmAction } from '../hooks/useConfirmAction';
import { PERMISSIONS } from '../constants/permissions';
import {
  CONFIG_STATUS_META,
  POLICY_CONFIG_STATUS_META,
  claimConfigApi,
  type ConfigStatus,
  type CoverageView,
  type CreateLiabilityReq,
  type FeeRuleView,
  type LiabilityType,
  type LiabilityView,
  type PlanView,
  type UpdateLiabilityReq,
} from '../services/claimConfigApi';

const LIABILITY_TYPE_OPTIONS = [
  { value: 'REIMBURSEMENT', label: '报销型 REIMBURSEMENT' },
  { value: 'FIXED_AMOUNT', label: '定额给付 FIXED_AMOUNT' },
  { value: 'ALLOWANCE', label: '津贴 ALLOWANCE' },
];
const DAILY_BASIS_OPTIONS = [
  { value: 'NONE', label: '不启用' },
  { value: 'SAME_DAY_INVOICE', label: '按同日发票' },
  { value: 'SAME_DAY_CLAIM', label: '按同日赔案' },
];
const LIMIT_MODE_OPTIONS = [
  { value: 'NONE', label: '不限定' },
  { value: 'WHITELIST', label: '白名单' },
  { value: 'BLACKLIST', label: '黑名单' },
];
const CATALOG_POLICY_OPTIONS = [
  { value: 'NONE', label: '不查目录' },
  { value: 'MANDATORY', label: '必须查到结论' },
];

/**
 * 成对配置的联动校验（与后端 {@code LiabilityConfigApplicationService#assertRuleUsable} 同一口径）。
 *
 * <p><b>为什么前端也要判一次</b>：这三对字段（金额↔口径、模式↔名单、策略↔目录编码）只配一边时，
 * 库里是合法数据、页面上看着"已配置"，但理算期会整类拒绝——配置岗看不出任何异常。后端已经会拒，
 * 前端这一层只是把这次往返省掉，并让错误出现在<b>出问题的那个字段下面</b>而不是顶部一条 toast。
 * 判定权仍在服务端：这里漏判只会多点一次保存，不会让脏配置进库。
 *
 * <p>校验器只挂在"主字段"上（金额 / 模式 / 策略）并用 {@code dependencies} 联动另一侧：
 * 一个矛盾只报一次错，不会两个字段各弹一条。
 */
function pairValidator(check: (getFieldValue: (name: string) => unknown) => string | null) {
  return ({ getFieldValue }: { getFieldValue: (name: string) => unknown }) => ({
    validator() {
      const problem = check(getFieldValue);
      return problem ? Promise.reject(new Error(problem)) : Promise.resolve();
    },
  });
}

/** 数值型字段读成数字（antd InputNumber 在 stringMode 下给的是字符串）。 */
function toNum(v: unknown): number {
  const n = Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
}

/** 日限额金额 ↔ 口径：两边必须同时配或同时不配。 */
function dailyPairProblem(getFieldValue: (name: string) => unknown): string | null {
  const hasLimit = toNum(getFieldValue('dailyLimit')) > 0;
  const basis = String(getFieldValue('dailyLimitBasis') ?? 'NONE');
  const hasBasis = basis !== '' && basis !== 'NONE';
  if (hasLimit && !hasBasis) {
    return '配了日限额就必须选「日」的口径，否则判不出「同日」是哪一天';
  }
  if (!hasLimit && hasBasis) {
    return '选了日窗口口径但日限额为 0，该口径永远不会生效（会被读成不限额）';
  }
  return null;
}

/** 名单字段值归一：库选择器（tags 多选）给数组，批量导入/旧数据给逗号串 —— 统一成逗号串判空。 */
function codesText(v: unknown): string {
  if (Array.isArray(v)) {
    return v.map((c) => String(c).trim()).filter(Boolean).join(',');
  }
  return String(v ?? '').trim();
}

/** 逗号分隔编码串 → 库选择器的数组值（编辑回显用；空串给 undefined 让表单留空）。 */
function toCodeList(v?: string | null): string[] {
  return (v ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

/** 库选择器下拉选项（value=编码）。 */
interface LibraryOption {
  value: string;
  label: string;
}

/** 适用限定：开了白/黑名单就必须给名单（空名单 = 全不赔 或 等于不设限，都不是业务意图）。 */
function limitModePairProblem(getFieldValue: (name: string) => unknown): string | null {
  const mode = String(getFieldValue('limitMode') ?? 'NONE');
  if (mode === '' || mode === 'NONE') {
    return null;
  }
  const hospitals = codesText(getFieldValue('limitHospitalCodes'));
  const diagnoses = codesText(getFieldValue('limitDiagnosisCodes'));
  if (!hospitals && !diagnoses) {
    return '启用了适用限定就必须选医院或病种名单，否则白名单等于全不赔、黑名单等于不设限';
  }
  return null;
}

/** 目录准入：策略与目录编码必须同配或同不配。 */
function catalogPairProblem(getFieldValue: (name: string) => unknown): string | null {
  const policy = String(getFieldValue('catalogPolicy') ?? 'NONE');
  const code = codesText(getFieldValue('catalogCode'));
  if ((policy === '' || policy === 'NONE') && code) {
    return '策略为「不查目录」时不该选药品目录——该编码永远不会被查询';
  }
  if (policy === 'MANDATORY' && !code) {
    return '策略为「必须查到结论」时必须选药品目录编码，否则不知道该查哪一版目录';
  }
  return null;
}

/**
 * 「已启规则」摘要：把十一维里真正生效的那几维在列表上一眼看全。
 *
 * <p>此前列表只有「日限额」一列，多出来的免赔层 / 适用限定 / 目录准入只能点开编辑弹窗才知道，
 * 运营排查"这条责任为什么拒赔"时只能逐行打开。
 */
function ruleSummaryTags(row: LiabilityView): ReactElement {
  const tags: ReactElement[] = [];
  if (toNum(row.invoiceDeduct) > 0) {
    tags.push(<Tag key="invoice">发票免赔 {row.invoiceDeduct}</Tag>);
  }
  if (toNum(row.adjustDeduct) > 0) {
    tags.push(<Tag key="adjust">理算免赔 {row.adjustDeduct}</Tag>);
  }
  if (toNum(row.dailyLimit) > 0) {
    const basisLabel =
      DAILY_BASIS_OPTIONS.find((o) => o.value === row.dailyLimitBasis)?.label ?? row.dailyLimitBasis;
    tags.push(
      <Tag key="daily" color="orange">
        {`日限额 ${row.dailyLimit}（${basisLabel}）`}
      </Tag>,
    );
  }
  if (row.limitMode && row.limitMode !== 'NONE') {
    const modeLabel = LIMIT_MODE_OPTIONS.find((o) => o.value === row.limitMode)?.label ?? row.limitMode;
    tags.push(
      <Tag key="limit" color="purple">
        {`限定 ${modeLabel}`}
      </Tag>,
    );
  }
  if (row.catalogPolicy === 'MANDATORY') {
    tags.push(
      <Tag key="catalog" color="blue">
        {`目录 ${row.catalogCode || '未指定版本'}`}
      </Tag>,
    );
  }
  if (row.socialOnly === 0) {
    tags.push(<Tag key="social">社保内外</Tag>);
  }
  if (tags.length === 0) {
    return <Typography.Text type="secondary">仅基础规则</Typography.Text>;
  }
  return (
    <Space size={4} wrap>
      {tags}
    </Space>
  );
}

/** 责任批量导入列定义（模板表头用英文键）。 */
const liabilityImportColumns: ImportColumn[] = [
  { key: 'planId', label: '计划ID', required: true, example: '' },
  { key: 'coverageId', label: '保障ID', required: false, example: '' },
  { key: 'liabilityCode', label: '责任编码', required: true, example: 'R01' },
  { key: 'liabilityType', label: '责任类型', example: 'REIMBURSEMENT' },
  { key: 'payPercent', label: '赔付比例(%)', example: '90' },
  { key: 'liabilityDeduct', label: '年度免赔额', example: '0' },
  { key: 'liabilityLimit', label: '责任额度上限', example: '0' },
  { key: 'invoiceDeduct', label: '发票级免赔', example: '0' },
  { key: 'adjustDeduct', label: '理算级免赔', example: '0' },
  { key: 'dailyLimit', label: '日赔付限额', example: '0' },
  { key: 'dailyLimitBasis', label: '日窗口口径', example: 'NONE' },
  { key: 'limitMode', label: '适用限定方式', example: 'NONE' },
  { key: 'limitHospitalCodes', label: '限定医院编码(分隔)', example: '' },
  { key: 'limitDiagnosisCodes', label: '限定病种编码(分隔)', example: '' },
  { key: 'catalogPolicy', label: '目录准入策略', example: 'NONE' },
  { key: 'catalogCode', label: '目录编码', example: '' },
  { key: 'socialOnly', label: '仅赔社保内(1/0)', example: '1' },
  { key: 'waitingPeriod', label: '等待期(天)', example: '0' },
  { key: 'status', label: '初始状态', example: 'DRAFT' },
];

function buildLiabilityRequest(row: Record<string, string>): CreateLiabilityReq | null {
  return {
    planId: row.planId.trim(),
    coverageId: row.coverageId?.trim() || undefined,
    liabilityCode: row.liabilityCode.trim(),
    liabilityType: (row.liabilityType?.trim() || undefined) as LiabilityType | undefined,
    payPercent: row.payPercent?.trim() || undefined,
    liabilityDeduct: row.liabilityDeduct?.trim() || undefined,
    liabilityLimit: row.liabilityLimit?.trim() || undefined,
    invoiceDeduct: row.invoiceDeduct?.trim() || undefined,
    adjustDeduct: row.adjustDeduct?.trim() || undefined,
    dailyLimit: row.dailyLimit?.trim() || undefined,
    dailyLimitBasis: row.dailyLimitBasis?.trim() || undefined,
    limitMode: row.limitMode?.trim() || undefined,
    limitHospitalCodes: row.limitHospitalCodes?.trim() || undefined,
    limitDiagnosisCodes: row.limitDiagnosisCodes?.trim() || undefined,
    catalogPolicy: row.catalogPolicy?.trim() || undefined,
    catalogCode: row.catalogCode?.trim() || undefined,
    socialOnly: row.socialOnly ? Number(row.socialOnly) : undefined,
    waitingPeriod: row.waitingPeriod ? Number(row.waitingPeriod) : undefined,
    status: (row.status?.trim() || undefined) as ConfigStatus | undefined,
  };
}

function validateLiabilityRow(row: Record<string, string>): string | null {
  if (!row.liabilityCode?.trim()) return '责任编码不能为空';
  if (!row.planId?.trim()) return '计划ID不能为空（请先在本页选择计划，模板将自动带入）';
  return null;
}

/**
 * 责任配置（配置面，A4 配置端）。
 *
 * <p><b>版本化</b>：编辑已生效（ACTIVE）责任不会改动原行，而是生成<b>同 liabilityUuid 的新版本草稿</b>；
 * 发布新版本时自动将同 UUID 的旧 ACTIVE 版本置为 RETIRED，保证同时只有一个生效版本（§8.2「已生效版本不可改」）。
 *
 * <p>责任参数（赔付比例 / 多层免赔 / 日限额 / 适用限定 / 目录准入）是理算引擎的口径来源；本页只负责
 * 配置与版本管理，理算时按当期版本钉入 {@code formula_snapshot}，保证历史案件可自包含复算。
 */
export function LiabilityConfigPage(): ReactElement {
  const { message } = AntApp.useApp();
  const confirm = useConfirmAction();
  const { policies } = usePolicyOptions();
  // 支持 /liability?policyNo=xxx 深链：从保单管理「配置」跳入时带保单上下文
  const [searchParams] = useSearchParams();
  const [policyNo, setPolicyNo] = useState('');
  const [plans, setPlans] = useState<PlanView[]>([]);
  const [planId, setPlanId] = useState('');
  const [coverages, setCoverages] = useState<CoverageView[]>([]);
  /**
   * 保障维度筛选桶。
   *
   * <p>`ALL` = 全部；`UNASSIGNED` = 未挂保障（历史未分级 / 只配到方案级）；其它值为具体 coverageId。
   * 为什么不用「空字符串代表全部、null 代表未分配」：空串与 null 在 JSX 的 `value=` 与 URL 参数里
   * 都会退化成同一个"无值"，两个语义会互相吞掉。
   */
  const [bucket, setBucket] = useState<string>('ALL');
  const [liabilities, setLiabilities] = useState<LiabilityView[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<LiabilityView | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [importOpen, setImportOpen] = useState(false);
  /** 正在执行发布/下线的责任行（防重复提交并发控制）。 */
  const [processingId, setProcessingId] = useState('');
  /**
   * 弹窗里当前的「责任类型」。非报销型（定额/津贴）在 V1 没有赔付公式，
   * 保存/发布都会成功但理赔转人工——这一点必须在录入时就告诉配置岗，
   * 否则很容易配完以为会自动赔，直到第一笔理赔卡住才发现。
   */
  const watchedLiabilityType = Form.useWatch('liabilityType', form) as LiabilityType | undefined;
  const manualOnly = !!watchedLiabilityType && watchedLiabilityType !== 'REIMBURSEMENT';

  // ===================== 基础目录库（0094）：库选择器数据源 =====================

  /** 目录下拉选项（value=编码，label=`编码 名称`；加载大页一次，列表页维护目录）。 */
  const [hospitalOptions, setHospitalOptions] = useState<LibraryOption[]>([]);
  const [diseaseOptions, setDiseaseOptions] = useState<LibraryOption[]>([]);
  const [drugOptions, setDrugOptions] = useState<LibraryOption[]>([]);
  const [feeCategoryOptions, setFeeCategoryOptions] = useState<LibraryOption[]>([]);

  useEffect(() => {
    void (async () => {
      try {
        const [hosp, dise, drug, fee] = await Promise.all([
          claimConfigApi.listHospitals({ page: 1, size: 500 }),
          claimConfigApi.listDiseases({ page: 1, size: 500 }),
          claimConfigApi.listDrugs({ page: 1, size: 500 }),
          claimConfigApi.listAllFeeCategories(),
        ]);
        setHospitalOptions(
          (hosp.data?.records ?? []).map((x) => ({
            value: x.hospitalCode,
            label: `${x.hospitalCode} ${x.hospitalName}`,
          })),
        );
        setDiseaseOptions(
          (dise.data?.records ?? []).map((x) => ({
            value: x.diseaseCode,
            label: `${x.diseaseCode} ${x.diseaseName}`,
          })),
        );
        setDrugOptions(
          (drug.data?.records ?? []).map((x) => ({
            value: x.drugCode,
            label: `${x.drugCode} ${x.drugName}`,
          })),
        );
        setFeeCategoryOptions(
          (fee.data ?? []).map((x) => ({ value: x.feeCode, label: `${x.feeCode} ${x.feeName}` })),
        );
      } catch {
        // 目录加载失败不阻断责任配置：选择器仍可手输编码（tags 模式），与旧行为兼容
        message.error('基础目录库加载失败，名单选择器降级为手动输入编码');
      }
    })();
    // 只在挂载时加载一次目录快照；目录变更由「基础目录库」页维护
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ===================== 费用类别规则（0094） =====================

  /** 费用规则抽屉的行（feeName 冗余展示，提交时按 FeeRuleItem 提交）。 */
  interface FeeRuleRow {
    feeCode: string;
    feeName: string;
    payPercent?: string;
    limitAmount?: string;
    socialOnly: number;
  }

  const [feeRuleOpen, setFeeRuleOpen] = useState(false);
  const [feeRuleTarget, setFeeRuleTarget] = useState<LiabilityView | null>(null);
  const [feeRuleRows, setFeeRuleRows] = useState<FeeRuleRow[]>([]);
  const [feeRuleLoading, setFeeRuleLoading] = useState(false);
  const [feeRuleSaving, setFeeRuleSaving] = useState(false);

  const openFeeRules = async (row: LiabilityView) => {
    setFeeRuleTarget(row);
    setFeeRuleRows([]);
    setFeeRuleOpen(true);
    setFeeRuleLoading(true);
    try {
      const res = await claimConfigApi.listFeeRules(row.liabilityId);
      setFeeRuleRows(
        (res.data ?? []).map((r: FeeRuleView) => ({
          feeCode: r.feeCode,
          feeName: r.feeName,
          payPercent: r.payPercent,
          limitAmount: r.limitAmount,
          socialOnly: r.socialOnly ?? 0,
        })),
      );
    } catch (err) {
      message.error(resolveErrorMessage(err, '费用规则加载失败'));
    } finally {
      setFeeRuleLoading(false);
    }
  };

  const updateFeeRule = (idx: number, patch: Partial<FeeRuleRow>) => {
    setFeeRuleRows((rows) => rows.map((r, i) => (i === idx ? { ...r, ...patch } : r)));
  };

  const removeFeeRule = (idx: number) => {
    setFeeRuleRows((rows) => rows.filter((_, i) => i !== idx));
  };

  const addFeeRule = () => {
    setFeeRuleRows((rows) => [...rows, { feeCode: '', feeName: '', socialOnly: 0 }]);
  };

  const saveFeeRules = async () => {
    if (!feeRuleTarget) {
      return;
    }
    const rows = feeRuleRows;
    if (rows.some((r) => !r.feeCode)) {
      message.error('每行费用规则都必须选择费用类别');
      return;
    }
    const dup = rows.find((r, i) => rows.findIndex((x) => x.feeCode === r.feeCode) !== i);
    if (dup) {
      message.error(`费用类别重复: ${dup.feeCode}`);
      return;
    }
    const badPercent = rows.find(
      (r) => r.payPercent !== undefined && r.payPercent !== '' && Number(r.payPercent) > 100,
    );
    if (badPercent) {
      message.error(`赔付比例必须在 0-100 之间（${badPercent.feeCode}）`);
      return;
    }
    setFeeRuleSaving(true);
    try {
      const res = await claimConfigApi.saveFeeRules(feeRuleTarget.liabilityId, {
        items: rows.map((r) => ({
          feeCode: r.feeCode,
          payPercent: r.payPercent || undefined,
          limitAmount: r.limitAmount || undefined,
          socialOnly: r.socialOnly,
        })),
      });
      if (res.code !== 200) {
        message.error(resolveErrorMessage(res, '费用规则保存失败'));
        return;
      }
      message.success('费用规则已保存（全量替换生效）');
      setFeeRuleRows(
        (res.data ?? []).map((r: FeeRuleView) => ({
          feeCode: r.feeCode,
          feeName: r.feeName,
          payPercent: r.payPercent,
          limitAmount: r.limitAmount,
          socialOnly: r.socialOnly ?? 0,
        })),
      );
    } catch (err) {
      message.error(resolveErrorMessage(err, '费用规则保存失败'));
    } finally {
      setFeeRuleSaving(false);
    }
  };

  // ===================== 启用联动（0094） =====================

  /** 当前所选保单；规则开关未启用（INACTIVE/PAUSE）时禁用全部写操作。 */
  const currentPolicy = policies.find((p) => p.policyNo === policyNo);
  const policyRuleDisabled = !!currentPolicy && currentPolicy.configStatus !== 'ACTIVE';

  const loadPlans = useCallback(async (pn: string) => {
    if (!pn) {
      setPlans([]);
      return;
    }
    try {
      const res = await claimConfigApi.listPlans(pn);
      setPlans(res.data ?? []);
    } catch (err) {
      message.error(resolveErrorMessage(err, '计划列表加载失败'));
    }
  }, [message]);

  const loadCoverages = useCallback(async (pid: string) => {
    if (!pid) {
      setCoverages([]);
      return;
    }
    try {
      const res = await claimConfigApi.listCoverages(pid);
      setCoverages(res.data ?? []);
    } catch (err) {
      message.error(resolveErrorMessage(err, '保障列表加载失败'));
    }
  }, [message]);

  const loadLiabilities = useCallback(async (pid: string) => {
    if (!pid) {
      setLiabilities([]);
      return;
    }
    setLoading(true);
    try {
      const res = await claimConfigApi.listLiabilities(pid);
      setLiabilities(res.data ?? []);
    } catch (err) {
      message.error(resolveErrorMessage(err, '责任列表加载失败'));
    } finally {
      setLoading(false);
    }
  }, [message]);

  /**
   * 保单初值：优先 URL 深链（从保单管理「配置」跳入），否则取列表首条，并随之加载计划。
   *
   * <p>为什么不能只 {@code setPolicyNo}：加载计划的入口是保单下拉的 onChange，
   * 初值若绕过 onChange，页面会停在"已选中保单、计划列表却为空"的状态。</p>
   */
  useEffect(() => {
    if (policyNo || policies.length === 0) {
      return;
    }
    const initial = searchParams.get('policyNo') || policies[0]?.policyNo || '';
    if (!initial) {
      return;
    }
    setPolicyNo(initial);
    void loadPlans(initial);
    // searchParams 只用于初始化，不进依赖：URL 变化不应触发重复加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [policies, policyNo, loadPlans]);

  /** 保障按 ID 索引（表格列与筛选桶共用，避免每行都 find 一次）。 */
  const coverageById = useMemo(() => {
    const m = new Map<string, CoverageView>();
    coverages.forEach((c) => m.set(c.coverageId, c));
    return m;
  }, [coverages]);

  /**
   * 各桶责任数：让「四级建模」在筛选条上直接可读，而不是逐个点开才知道有没有责任。
   *
   * <p>`l.coverageId === 'null'` 是**防御历史数据**：旧版视图把 `null` 序列化成了字面量 `"null"`，
   * 这类行若只按 `!coverageId` 判断会被归进"某个保障"，从界面上彻底消失（既不在具体保障下，
   * 也不在未分配里）。两种形态一律按"未分配"处理。
   */
  const bucketCounts = useMemo(() => {
    const byCoverage = new Map<string, number>();
    let unassigned = 0;
    liabilities.forEach((l) => {
      if (!l.coverageId || l.coverageId === 'null') {
        unassigned += 1;
      } else {
        byCoverage.set(l.coverageId, (byCoverage.get(l.coverageId) ?? 0) + 1);
      }
    });
    return { byCoverage, unassigned, total: liabilities.length };
  }, [liabilities]);

  /** 当前筛选桶下的责任行。 */
  const visibleLiabilities = useMemo(() => {
    if (bucket === 'ALL') {
      return liabilities;
    }
    if (bucket === 'UNASSIGNED') {
      return liabilities.filter((l) => !l.coverageId || l.coverageId === 'null');
    }
    return liabilities.filter((l) => l.coverageId === bucket);
  }, [bucket, liabilities]);

  /** 当前桶对应的保障（新建时预填；ALL / UNASSIGNED 不预填）。 */
  const bucketCoverageId = bucket !== 'ALL' && bucket !== 'UNASSIGNED' ? bucket : undefined;

  /**
   * 桶失效自愈：切换方案后原选中保障可能已不属于当前列表（换了方案 / 险种被下线）。
   *
   * <p>不处理的表现是"筛选条上一个高亮都没有、表格却空着"——用户会以为责任全丢了。
   * 这里在保障列表非空（已加载完）时才回退，避免加载中的空数组把选择清掉。
   */
  useEffect(() => {
    if (bucket === 'ALL' || bucket === 'UNASSIGNED') {
      return;
    }
    if (coverages.length > 0 && !coverages.some((c) => c.coverageId === bucket)) {
      setBucket('ALL');
    }
  }, [bucket, coverages]);

  const onPolicyChange = async (pn: string) => {
    setPolicyNo(pn);
    setPlanId('');
    setBucket('ALL');
    setCoverages([]);
    setLiabilities([]);
    await loadPlans(pn);
  };

  const onPlanChange = async (pid: string) => {
    setPlanId(pid);
    setBucket('ALL');
    setLiabilities([]);
    await loadCoverages(pid);
    await loadLiabilities(pid);
  };

  /**
   * 计划深链：`/liability?policyNo=P&planId=X`（从计划配置行「责任配置」跳入）。
   *
   * <p>必须等计划列表加载完再校验归属：把 URL 里的 planId 直接塞进 state 会让
   * 「不属于当前保单的过期链接」也选中一个不存在于列表的计划——页面看起来正常，
   * 但下拉是空的、责任表恒空，用户无法判断是没配还是跳错了。校验失败就退回
   * 「未选计划 + 明确提示」，比静默空表可诊断。
   *
   * <p>{@code consumed} 保证只消费一次：否则用户切保单时 {@code plans} 变化会让
   * 该 effect 重跑，把 URL 里那张旧计划又硬塞回选择框（切了保单却跳回原计划）。
   */
  const planDeepLinkConsumed = useRef(false);
  useEffect(() => {
    if (planDeepLinkConsumed.current || planId || plans.length === 0) {
      return;
    }
    const want = searchParams.get('planId');
    if (!want) {
      return;
    }
    planDeepLinkConsumed.current = true;
    if (!plans.some((p) => p.planId === want)) {
      void message.warning('链接中的计划不属于当前保单（可能已下线或换了保单），请重新选择计划');
      return;
    }
    void onPlanChange(want);
    // searchParams 只用于初始化，不进依赖：URL 变化不应触发重复加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plans, planId]);

  const openCreate = () => {
    setEditTarget(null);
    setModalOpen(true);
  };

  const liabilityEditInitial = useMemo<Record<string, unknown> | undefined>(() => {
    if (!editTarget) return undefined;
    return {
      liabilityCode: editTarget.liabilityCode,
      coverageId: editTarget.coverageId ?? undefined,
      liabilityType: editTarget.liabilityType,
      payPercent: editTarget.payPercent,
      liabilityDeduct: editTarget.liabilityDeduct,
      liabilityLimit: editTarget.liabilityLimit,
      invoiceDeduct: editTarget.invoiceDeduct,
      adjustDeduct: editTarget.adjustDeduct,
      dailyLimit: editTarget.dailyLimit,
      dailyLimitBasis: editTarget.dailyLimitBasis,
      limitMode: editTarget.limitMode,
      // 库选择器（tags 多选）以数组承接；提交时再拼回逗号分隔编码串（理算引擎存量口径）
      limitHospitalCodes: toCodeList(editTarget.limitHospitalCodes),
      limitDiagnosisCodes: toCodeList(editTarget.limitDiagnosisCodes),
      catalogPolicy: editTarget.catalogPolicy,
      catalogCode: toCodeList(editTarget.catalogCode),
      socialOnly: editTarget.socialOnly,
      waitingPeriod: editTarget.waitingPeriod,
    };
  }, [editTarget]);

  /**
   * 弹窗表单初值：编辑取行值；新建取当前保障桶预填（在「未分配保障」桶里新建则留空）。
   *
   * <p>为什么用 {@code initialValues} 而不是打开后再 {@code setFieldsValue}：弹窗首次打开时
   * Form 尚未挂载，命令式赋值会静默失效（表现为"编辑弹窗一片空白"）；声明式初值 + {@code key}
   * 让每次打开都拿到一份干净的表单。</p>
   */
  const liabilityFormInitial = editTarget
    ? liabilityEditInitial
    : bucketCoverageId
      ? { coverageId: bucketCoverageId }
      : undefined;

  const openEdit = (row: LiabilityView) => {
    setEditTarget(row);
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const clean = (v: unknown) =>
        v === undefined || v === null || v === '' ? undefined : v;
      const base: Record<string, unknown> = {
        liabilityCode: values.liabilityCode?.trim(),
        coverageId: values.coverageId ? String(values.coverageId) : undefined,
        liabilityType: clean(values.liabilityType),
        payPercent: clean(values.payPercent),
        liabilityDeduct: clean(values.liabilityDeduct),
        liabilityLimit: clean(values.liabilityLimit),
        invoiceDeduct: clean(values.invoiceDeduct),
        adjustDeduct: clean(values.adjustDeduct),
        dailyLimit: clean(values.dailyLimit),
        dailyLimitBasis: clean(values.dailyLimitBasis),
        limitMode: clean(values.limitMode),
        // 名单/目录从库选择器（数组）拼回逗号分隔编码串——理算引擎按编码串读取，向后兼容
        limitHospitalCodes: codesText(values.limitHospitalCodes) || undefined,
        limitDiagnosisCodes: codesText(values.limitDiagnosisCodes) || undefined,
        catalogPolicy: clean(values.catalogPolicy),
        catalogCode: codesText(values.catalogCode) || undefined,
        socialOnly:
          values.socialOnly === undefined || values.socialOnly === null
            ? undefined
            : Number(values.socialOnly),
        waitingPeriod:
          values.waitingPeriod === undefined || values.waitingPeriod === null
            ? undefined
            : Number(values.waitingPeriod),
      };
      if (editTarget) {
        await claimConfigApi.updateLiability(editTarget.liabilityId, {
          ...(base as unknown as UpdateLiabilityReq),
          // 清空"所属保障"是显式解除归属：coverageId 传空后端无法与"没传"区分，
          // 必须带 detachCoverage，否则界面清空了、库里还挂着旧保障。
          detachCoverage:
            !values.coverageId && editTarget.coverageId ? true : undefined,
        });
        message.success('责任已更新（生效版本将生成新版本草稿）');
      } else {
        await claimConfigApi.createLiability({
          ...(base as unknown as CreateLiabilityReq),
          planId,
        });
        message.success('责任已创建');
      }
      setModalOpen(false);
      await loadLiabilities(planId);
    } catch (err) {
      message.error(resolveErrorMessage(err, '保存失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const publish = (row: LiabilityView) => {
    confirm({
      title: '发布责任',
      content: `确认发布「${row.liabilityCode}」（${row.liabilityVersion}）？发布后该 UUID 的旧生效版本将自动下线，且立即参与新赔案理算。`,
      okText: '确认发布',
      danger: false,
      onOk: () => {
        setProcessingId(row.liabilityId);
        return claimConfigApi
          .publishLiability(row.liabilityId)
          .then(() => {
            message.success('责任已发布');
            return loadLiabilities(planId);
          })
          .catch((err) => {
            message.error(resolveErrorMessage(err, '发布失败'));
            throw err;
          })
          .finally(() => setProcessingId(''));
      },
    });
  };

  const retire = (row: LiabilityView) => {
    confirm({
      title: '下线责任',
      content: `确认下线「${row.liabilityCode}」（${row.liabilityVersion}）？下线后将不再参与理算，历史版本保留可查。`,
      okText: '确认下线',
      onOk: () => {
        setProcessingId(row.liabilityId);
        return claimConfigApi
          .retireLiability(row.liabilityId)
          .then(() => {
            message.success('责任已下线');
            return loadLiabilities(planId);
          })
          .catch((err) => {
            message.error(resolveErrorMessage(err, '下线失败'));
            throw err;
          })
          .finally(() => setProcessingId(''));
      },
    });
  };

  const columns = [
    { title: '责任编码', dataIndex: 'liabilityCode', width: 110 },
    {
      /*
       * 「所属保障」列：四级建模下责任可能分属不同险种，只显示一张平表时运营无从判断
       * 某条责任归谁。空值渲染成「方案级」标签而不是留空——留白会被读成"数据缺失"，
       * 而它其实是合法形态（历史未分级 / 只配到方案级）。
       */
      title: '所属保障',
      dataIndex: 'coverageId',
      width: 190,
      ellipsis: true,
      render: (v: string | null) => {
        if (!v || v === 'null') {
          return <Tag>方案级</Tag>;
        }
        const coverage = coverageById.get(v);
        if (!coverage) {
          return <Typography.Text type="secondary">{`未知保障 ${v}`}</Typography.Text>;
        }
        return `${coverage.coverageCode}${coverage.coverageName ? ` ${coverage.coverageName}` : ''}`;
      },
    },
    {
      title: '类型',
      dataIndex: 'liabilityType',
      width: 140,
      render: (v: LiabilityType) => {
        // 定额/津贴在 V1 无赔付公式（只能转人工）。列表上就标出来，避免运营以为"配了就会自动赔"。
        if (!v || v === 'REIMBURSEMENT') {
          return v || 'REIMBURSEMENT';
        }
        return (
          <Space size={4}>
            <span>{v}</span>
            <Tag color="gold">转人工</Tag>
          </Space>
        );
      },
    },
    { title: '赔付比例(%)', dataIndex: 'payPercent', width: 100, render: (v: string) => v ?? '0' },
    { title: '年度免赔', dataIndex: 'liabilityDeduct', width: 100 },
    { title: '责任额度', dataIndex: 'liabilityLimit', width: 100 },
    {
      /* 「已启规则」取代原来的「日限额」单列：十一维里真正生效的那几维（多层免赔 / 日限额 / 适用限定 /
         目录准入 / 社保范围）都要能在列表上一眼看全，否则排查"这条责任为什么拒赔"只能逐行点开弹窗。 */
      title: '已启规则',
      width: 260,
      render: (_: unknown, row: LiabilityView) => ruleSummaryTags(row),
    },
    { title: '版本', dataIndex: 'liabilityVersion', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (s: ConfigStatus) => {
        const m = CONFIG_STATUS_META[s];
        return <Tag color={m?.color}>{m?.label ?? s}</Tag>;
      },
    },
    {
      title: '操作',
      width: 290,
      render: (_: unknown, row: LiabilityView) => (
        <Space size={4}>
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            type="link"
            size="small"
            icon={<EditOutlined />}
            disabled={policyRuleDisabled}
            onClick={() => openEdit(row)}
          >
            编辑
          </AuthButton>
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            type="link"
            size="small"
            disabled={policyRuleDisabled}
            onClick={() => void openFeeRules(row)}
          >
            费用规则
          </AuthButton>
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            type="link"
            size="small"
            icon={<CloudUploadOutlined />}
            disabled={row.status === 'ACTIVE' || policyRuleDisabled}
            loading={processingId === row.liabilityId}
            onClick={() => publish(row)}
          >
            发布
          </AuthButton>
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            type="link"
            size="small"
            danger
            icon={<StopOutlined />}
            disabled={row.status === 'RETIRED'}
            loading={processingId === row.liabilityId}
            onClick={() => retire(row)}
          >
            下线
          </AuthButton>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="责任配置"
        description="保险责任的版本化配置（赔付比例 / 多层免赔 / 日限额 / 适用限定 / 目录准入）。编辑已生效责任会生成新版本草稿，发布后旧版本自动下线。"
      />
      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            placeholder="选择保单"
            style={{ width: 260 }}
            value={policyNo || undefined}
            options={policies.map((p) => ({
              value: p.policyNo,
              label: `${p.policyNo} ${p.policyName ?? ''}`,
            }))}
            onChange={(v) => void onPolicyChange(v)}
            showSearch
            optionFilterProp="label"
          />
          <Select
            placeholder="选择计划"
            style={{ width: 260 }}
            value={planId || undefined}
            disabled={!policyNo}
            options={plans.map((p) => ({
              value: p.planId,
              label: `${p.planCode} ${p.planName ?? ''}`,
            }))}
            onChange={(v) => void onPlanChange(v)}
            showSearch
            optionFilterProp="label"
          />
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            type="primary"
            icon={<PlusOutlined />}
            disabled={!planId || policyRuleDisabled}
            onClick={openCreate}
          >
            新建责任
          </AuthButton>
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            icon={<UploadOutlined />}
            disabled={!planId || policyRuleDisabled}
            onClick={() => setImportOpen(true)}
          >
            批量导入
          </AuthButton>
          <Button icon={<ReloadOutlined />} onClick={() => void loadLiabilities(planId)}>
            刷新
          </Button>
        </Space>

        {planId && coverages.length === 0 && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="该方案尚未配置保障（险种）"
            description="责任会挂在方案级，配置与理算不受影响；如需「保单 → 方案 → 保障 → 责任」四级建模，请先在「保单配置 → 责任规则」为该方案新增险种。"
          />
        )}

        {policyRuleDisabled && currentPolicy && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message={`保单「${currentPolicy.policyName ?? policyNo}」的责任规则未启用（当前：${
              POLICY_CONFIG_STATUS_META[currentPolicy.configStatus]?.label ??
              currentPolicy.configStatus
            }）`}
            description="未启用保单下的责任不参与理赔受理。新建 / 编辑 / 发布已锁定，请先在「保单管理」启用该保单的责任规则。"
          />
        )}

        {planId && (
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 8,
              alignItems: 'center',
              marginBottom: 12,
            }}
          >
            <Typography.Text type="secondary">保障维度</Typography.Text>
            <Button
              size="small"
              type={bucket === 'ALL' ? 'primary' : 'default'}
              onClick={() => setBucket('ALL')}
            >
              {`全部 ${bucketCounts.total}`}
            </Button>
            {coverages.map((c) => {
              const count = bucketCounts.byCoverage.get(c.coverageId) ?? 0;
              const statusSuffix =
                c.status === 'ACTIVE' ? '' : c.status === 'RETIRED' ? '（已下线）' : '（草稿）';
              const name = c.coverageName ? ` ${c.coverageName}` : '';
              return (
                <Button
                  key={c.coverageId}
                  size="small"
                  type={bucket === c.coverageId ? 'primary' : 'default'}
                  onClick={() => setBucket(c.coverageId)}
                >
                  {`${c.coverageCode}${name}${statusSuffix} ${count}`}
                </Button>
              );
            })}
            {(bucketCounts.unassigned > 0 || bucket === 'UNASSIGNED') && (
              <Button
                size="small"
                type={bucket === 'UNASSIGNED' ? 'primary' : 'default'}
                onClick={() => setBucket('UNASSIGNED')}
              >
                {`未分配保障 ${bucketCounts.unassigned}`}
              </Button>
            )}
          </div>
        )}

        <Table<LiabilityView>
          rowKey="liabilityId"
          size="small"
          loading={loading}
          dataSource={visibleLiabilities}
          columns={columns}
          /* 加了「所属保障」列后总宽 >1200：不给横向滚动，窄屏下金额列会被挤到折行 */
          scroll={{ x: 'max-content' }}
          pagination={visibleLiabilities.length > 10 ? { pageSize: 10 } : false}
        />
      </Card>

      <Modal
        open={modalOpen}
        title={editTarget ? `编辑责任（${editTarget.liabilityCode}）` : '新建责任'}
        onCancel={() => setModalOpen(false)}
        onOk={() => void submit()}
        confirmLoading={submitting}
        destroyOnHidden
        width={760}
      >
        <Form
          form={form}
          layout="vertical"
          key={editTarget?.liabilityId ?? `liability-create-${bucketCoverageId ?? 'none'}`}
          initialValues={liabilityFormInitial}
        >
          {manualOnly && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              message="该责任类型不参与自动理算（理赔转人工）"
              description="V1 只对「报销型 REIMBURSEMENT」给出赔付公式与控额口径；定额给付 / 津贴可以正常建档、发布与备案，但引擎不会算钱，赔案会走人工理算。"
            />
          )}

          <Card size="small" title="基础信息" style={{ marginBottom: 12 }}>
            <Space size="large" wrap>
              <Form.Item
                label="责任编码"
                name="liabilityCode"
                rules={[{ required: true, message: '责任编码不能为空' }]}
              >
                <Input placeholder="如 R01" disabled={!!editTarget} />
              </Form.Item>
              <Form.Item
                label="所属保障"
                name="coverageId"
                rules={
                  coverages.length > 0 && !editTarget
                    ? [{ required: true, message: '请选择保障（或先在计划配置中新增险种）' }]
                    : []
                }
              >
                <Select
                  options={coverages.map((c) => ({
                    value: c.coverageId,
                    label: `${c.coverageCode} ${c.coverageName ?? ''}`,
                  }))}
                  style={{ width: 260 }}
                  placeholder={
                    coverages.length > 0 ? '选择保障' : '该方案暂无保障，可留空（方案级）'
                  }
                  allowClear
                  showSearch
                  optionFilterProp="label"
                />
              </Form.Item>
              <Form.Item label="责任类型" name="liabilityType">
                <Select
                  options={LIABILITY_TYPE_OPTIONS}
                  style={{ width: 240 }}
                  placeholder="REIMBURSEMENT"
                />
              </Form.Item>
            </Space>
          </Card>

          <Card size="small" title="赔付与限额" style={{ marginBottom: 12 }}>
            <Space size="large" wrap>
              <Form.Item
                label="赔付比例(%)"
                name="payPercent"
                rules={[{ type: 'number', min: 0, max: 100, message: '赔付比例必须在 0-100 之间' }]}
              >
                <InputNumber
                  stringMode
                  min={0}
                  max={100}
                  placeholder="90"
                  style={{ width: 160 }}
                />
              </Form.Item>
              <Form.Item label="年度免赔额" name="liabilityDeduct">
                <InputNumber stringMode min={0} placeholder="0" style={{ width: 160 }} />
              </Form.Item>
              <Form.Item label="责任额度上限" name="liabilityLimit">
                <InputNumber stringMode min={0} placeholder="0=不限" style={{ width: 160 }} />
              </Form.Item>
            </Space>
          </Card>

          <Card
            size="small"
            title="多层免赔与日限额"
            extra={<Typography.Text type="secondary">日限额的金额与口径须同配</Typography.Text>}
            style={{ marginBottom: 12 }}
          >
            <Space size="large" wrap>
              <Form.Item label="发票级免赔" name="invoiceDeduct">
                <InputNumber stringMode min={0} placeholder="0" style={{ width: 160 }} />
              </Form.Item>
              <Form.Item label="理算级免赔" name="adjustDeduct">
                <InputNumber stringMode min={0} placeholder="0" style={{ width: 160 }} />
              </Form.Item>
              <Form.Item
                label="日赔付限额"
                name="dailyLimit"
                dependencies={['dailyLimitBasis']}
                rules={[pairValidator(dailyPairProblem)]}
              >
                <InputNumber stringMode min={0} placeholder="0=不启用" style={{ width: 160 }} />
              </Form.Item>
              <Form.Item label="日窗口口径" name="dailyLimitBasis">
                <Select
                  options={DAILY_BASIS_OPTIONS}
                  style={{ width: 200 }}
                  placeholder="NONE"
                />
              </Form.Item>
            </Space>
          </Card>

          <Card
            size="small"
            title="适用限定"
            extra={<Typography.Text type="secondary">启用后必须给医院或病种名单</Typography.Text>}
            style={{ marginBottom: 12 }}
          >
            <Space size="large" wrap>
              <Form.Item
                label="适用限定方式"
                name="limitMode"
                dependencies={['limitHospitalCodes', 'limitDiagnosisCodes']}
                rules={[pairValidator(limitModePairProblem)]}
              >
                <Select
                  options={LIMIT_MODE_OPTIONS}
                  style={{ width: 200 }}
                  placeholder="NONE"
                />
              </Form.Item>
              <Form.Item label="限定医院" name="limitHospitalCodes">
                <Select
                  mode="tags"
                  options={hospitalOptions}
                  style={{ width: 300 }}
                  placeholder="从医院库选择，可多选"
                  tokenSeparators={[',']}
                  open={hospitalOptions.length > 0}
                  allowClear
                  showSearch
                  optionFilterProp="label"
                />
              </Form.Item>
              <Form.Item label="限定病种" name="limitDiagnosisCodes">
                <Select
                  mode="tags"
                  options={diseaseOptions}
                  style={{ width: 300 }}
                  placeholder="从疾病库选择，可多选"
                  tokenSeparators={[',']}
                  open={diseaseOptions.length > 0}
                  allowClear
                  showSearch
                  optionFilterProp="label"
                />
              </Form.Item>
            </Space>
          </Card>

          <Card
            size="small"
            title="目录准入"
            extra={<Typography.Text type="secondary">策略与目录编码须同配</Typography.Text>}
            style={{ marginBottom: 12 }}
          >
            <Space size="large" wrap>
              <Form.Item
                label="目录准入策略"
                name="catalogPolicy"
                dependencies={['catalogCode']}
                rules={[pairValidator(catalogPairProblem)]}
              >
                <Select
                  options={CATALOG_POLICY_OPTIONS}
                  style={{ width: 200 }}
                  placeholder="NONE"
                />
              </Form.Item>
              <Form.Item label="药品目录" name="catalogCode">
                <Select
                  mode="tags"
                  options={drugOptions}
                  style={{ width: 300 }}
                  placeholder="从药品库选择目录编码"
                  tokenSeparators={[',']}
                  open={drugOptions.length > 0}
                  allowClear
                  showSearch
                  optionFilterProp="label"
                />
              </Form.Item>
            </Space>
          </Card>

          <Card size="small" title="其他" style={{ marginBottom: 0 }}>
            <Space size="large" wrap>
              <Form.Item label="社保范围限制" name="socialOnly">
                <Select
                  style={{ width: 200 }}
                  options={[
                    { value: 1, label: '仅赔社保范围内' },
                    { value: 0, label: '社保内外均可' },
                  ]}
                />
              </Form.Item>
              <Form.Item
                label="等待期(天)"
                name="waitingPeriod"
                rules={[{ type: 'number', min: 0, message: '等待期不能为负' }]}
              >
                <InputNumber min={0} style={{ width: 160 }} />
              </Form.Item>
            </Space>
          </Card>
        </Form>
      </Modal>

      <Drawer
        title={`费用类别规则 · ${feeRuleTarget?.liabilityCode ?? ''}`}
        width={760}
        open={feeRuleOpen}
        onClose={() => setFeeRuleOpen(false)}
        destroyOnHidden
        extra={
          <Space>
            <Button icon={<PlusOutlined />} onClick={addFeeRule}>
              加一行
            </Button>
            <Button type="primary" loading={feeRuleSaving} onClick={() => void saveFeeRules()}>
              保存
            </Button>
          </Space>
        }
      >
        {feeRuleTarget && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="当前版本：规则只存档，不参与理算"
            description={
              <>
                理算引擎的归集维度仍是「医保范围内 / 自付二 / 总自费」，尚未按费用类别拆分，
                所以这里填的比例与限额<b>不会改变任何一笔赔付</b>。
                为避免「界面写着手术费 60%、实赔仍按责任主规则」这类静默错赔，
                责任存在规则时后端会<b>拒绝发布</b>（提示清空后再发布）。
                规则可先作为预配置留存，引擎支持后自动放开。
              </>
            }
          />
        )}
        {feeRuleLoading ? (
          <Typography.Text type="secondary">加载中…</Typography.Text>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            {feeRuleRows.length === 0 && (
              <Alert
                type="info"
                showIcon
                message="尚未配置费用类别规则"
                description="理算将按责任主规则对全部费用整体赔付，不做类别级拆分。可在此预配置「药品费 80%、手术费 90%」这类口径，待引擎支持类别级理算后生效。"
              />
            )}
            {feeRuleRows.map((row, idx) => (
              <Space key={idx} align="baseline" wrap>
                <Select
                  value={row.feeCode || undefined}
                  options={feeCategoryOptions}
                  style={{ width: 240 }}
                  placeholder="费用类别"
                  showSearch
                  optionFilterProp="label"
                  onChange={(v) => {
                    const opt = feeCategoryOptions.find((o) => o.value === v);
                    updateFeeRule(idx, { feeCode: v, feeName: opt?.label ?? v });
                  }}
                />
                <InputNumber
                  stringMode
                  min="0"
                  max="100"
                  value={row.payPercent || undefined}
                  placeholder="比例%"
                  style={{ width: 110 }}
                  onChange={(v) => updateFeeRule(idx, { payPercent: v ? String(v) : undefined })}
                />
                <InputNumber
                  stringMode
                  min="0"
                  value={row.limitAmount || undefined}
                  placeholder="限额(0=不限)"
                  style={{ width: 130 }}
                  onChange={(v) => updateFeeRule(idx, { limitAmount: v ? String(v) : undefined })}
                />
                <Select
                  value={row.socialOnly}
                  options={[
                    { value: 1, label: '仅社保内' },
                    { value: 0, label: '不限' },
                  ]}
                  style={{ width: 110 }}
                  onChange={(v) => updateFeeRule(idx, { socialOnly: v })}
                />
                <Button type="link" danger icon={<DeleteOutlined />} onClick={() => removeFeeRule(idx)}>
                  删除
                </Button>
              </Space>
            ))}
          </Space>
        )}
      </Drawer>

      <BatchImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        title="批量导入责任"
        fileName="liability-template"
        columns={liabilityImportColumns}
        defaults={{ planId, coverageId: bucketCoverageId ?? '' }}
        buildRequest={buildLiabilityRequest}
        validateRow={validateLiabilityRow}
        createOne={(req) => claimConfigApi.createLiability(req)}
        onSuccess={() => void loadLiabilities(planId)}
      />
    </div>
  );
}
