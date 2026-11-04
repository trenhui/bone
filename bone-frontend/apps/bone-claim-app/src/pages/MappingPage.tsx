import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  Upload,
} from 'antd';
import {
  DownloadOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { resolveErrorMessage } from '@bone/shared-utils';
import type { ColumnsType } from 'antd/es/table';
import { PERMISSIONS } from '../constants/permissions';
import { PageHeader } from '../components/PageHeader';
import { InsurerSelect } from '../components/InsurerSelect';
import { csvFilename, downloadBlob } from '../utils/downloadBlob';
import {
  claimConfigApi,
  mappingApi,
  toTotal,
  DOMAIN_LABEL,
  type MappingDomain,
  type MappingType,
  type MatchRule,
  type MappingView,
  type MissView,
  type PublishMappingReq,
  type MappingHealthRow,
  type StpGateRow,
  type AliasRow,
  type SplitRow,
  type FieldBindingRow,
} from '../services/claimConfigApi';

const DOMAIN_OPTIONS = (Object.entries(DOMAIN_LABEL) as [MappingDomain, string][])
  .filter(([d]) => d !== 'CERT_TYPE')
  .map(([value, label]) => ({ value, label }));

const MAPPING_TYPE_OPTIONS: { value: MappingType; label: string }[] = [
  { value: 'EXACT', label: '精确(EXACT)' },
  { value: 'SYNONYM', label: '同义(SYNONYM)' },
  { value: 'GROUP', label: '归并(GROUP)' },
];

const MATCH_RULE_OPTIONS: { value: MatchRule; label: string }[] = [
  { value: 'CODE_EQUAL', label: '码值相等' },
  { value: 'CREDIT_CODE', label: '信用代码' },
  { value: 'NAME_NORM', label: '名称归一' },
  { value: 'FUZZY', label: '模糊' },
  { value: 'MANUAL', label: '人工' },
  { value: 'AI_SUGGEST', label: 'AI建议' },
];

/** STP 五把尺中文标签（与后端 StpCondition 枚举一一对应）。 */
const STP_CONDITION_LABEL: Record<string, string> = {
  ITEM_HIT_RATE: '明细级命中率 ≥99.5%',
  CASE_STRAIGHT_THROUGH_RATE: '赔案级直通率 ≥95%',
  OPEN_MISS: 'OPEN 未命中 = 0',
  CORRECTION_EVENTS_7D: '近7天无映射纠错(reopen)',
  CONFLICT: '冲突数 = 0',
};

/** 按域从基础目录库拉取标准码选项（映射必须指向存在的标准码，否则是幽灵码）。 */
async function loadStandardOptions(domain: MappingDomain): Promise<{ label: string; value: string }[]> {
  const p = { page: 1, size: 200 };
  switch (domain) {
    case 'HOSPITAL': {
      const r = await claimConfigApi.listHospitals(p);
      return r.data.records.map((x) => ({ label: `${x.hospitalName}(${x.hospitalCode})`, value: x.hospitalCode }));
    }
    case 'DISEASE': {
      const r = await claimConfigApi.listDiseases(p);
      return r.data.records.map((x) => ({ label: `${x.diseaseName}(${x.diseaseCode})`, value: x.diseaseCode }));
    }
    case 'DRUG': {
      const r = await claimConfigApi.listDrugs(p);
      return r.data.records.map((x) => ({ label: `${x.drugName}(${x.drugCode})`, value: x.drugCode }));
    }
    case 'FEE_CATEGORY': {
      const r = await claimConfigApi.listFeeCategories(p);
      return r.data.records.map((x) => ({ label: `${x.feeName}(${x.feeCode})`, value: x.feeCode }));
    }
    default:
      return [];
  }
}

interface PublishFormValues {
  insurerItemCode: string;
  standardCode: string;
  mappingType?: MappingType;
  matchRule?: MatchRule;
  disambiguationKey?: string;
  versionNo?: string;
}

interface AliasFormValues {
  aliasCode: string;
  canonicalCode: string;
  relation?: string;
}

interface SplitFormValues {
  parentCode: string;
  childCode: string;
}

interface BindFieldValues {
  fieldCode: string;
  insurerCode?: string;
  sourceType?: string;
  refCode: string;
}

/**
 * 多保司主数据映射（保司码 → 标准码 的 crosswalk）。
 *
 * <p>基础目录库是标准码真源（一份主数据）；不同保司对同一主数据的码值 / 名称 / 标准不同，
 * 在这里登记「保司码值 → 标准码」的映射。责任规则只认标准码，收单侧按映射翻译保司来件。
 * 收单遇无映射的保司码会落入「未命中队列」，运营在此补映射后关闭。
 */
export function MappingPage(): ReactElement {
  const { message, modal } = AntApp.useApp();
  const [form] = Form.useForm<PublishFormValues>();
  const [aliasForm] = Form.useForm<AliasFormValues>();
  const [splitForm] = Form.useForm<SplitFormValues>();
  const [bindForm] = Form.useForm<BindFieldValues>();
  const [searchParams] = useSearchParams();

  const VALID_DOMAINS: MappingDomain[] = ['HOSPITAL', 'DISEASE', 'DRUG', 'FEE_CATEGORY'];
  const initialDomain = searchParams.get('domain') as MappingDomain | null;
  const [insurerCode, setInsurerCode] = useState(searchParams.get('insurerCode') ?? '');
  const [domain, setDomain] = useState<MappingDomain>(
    initialDomain && VALID_DOMAINS.includes(initialDomain) ? initialDomain : 'HOSPITAL',
  );
  const [standardOptions, setStandardOptions] = useState<{ label: string; value: string }[]>([]);

  // 映射列表
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [mappings, setMappings] = useState<MappingView[]>([]);
  const [mappingTotal, setMappingTotal] = useState(0);
  const [mappingLoading, setMappingLoading] = useState(false);
  /** 码值/标准码关键字（两列 or-like，与后端契约一致）。 */
  const [keyword, setKeyword] = useState('');
  /** 历史回放：true 时展示已停用/被替代的历史行（默认只看生效）。 */
  const [includeHistory, setIncludeHistory] = useState(false);

  // 未命中队列
  const [missPage, setMissPage] = useState(1);
  const [missSize, setMissSize] = useState(20);
  const [misses, setMisses] = useState<MissView[]>([]);
  const [missTotal, setMissTotal] = useState(0);
  const [missLoading, setMissLoading] = useState(false);
  /** 排序：occurCount 按出现次数降序（优先处置高频码），缺省按最近出现。 */
  const [missSortBy, setMissSortBy] = useState<string | undefined>(undefined);

  // 主体健康度（当前 insurer + domain）
  const [health, setHealth] = useState<MappingHealthRow | null>(null);
  // STP 闸门（映射质量→业务风险开关；allowAuto=false 即该保司×域不得自动理算）
  const [stpGate, setStpGate] = useState<StpGateRow | null>(null);

  // 标准码别名 / 拆分（标准码维度，跨保司，按域，与承保主体无关）
  const [aliases, setAliases] = useState<AliasRow[]>([]);
  const [aliasesLoading, setAliasesLoading] = useState(false);
  const [aliasModalOpen, setAliasModalOpen] = useState(false);
  const [splits, setSplits] = useState<SplitRow[]>([]);
  const [splitsLoading, setSplitsLoading] = useState(false);
  const [splitModalOpen, setSplitModalOpen] = useState(false);

  // 字段-选项集绑定（字段归一；按承保主体，含平台基线 '*'；与域无关）
  const [bindings, setBindings] = useState<FieldBindingRow[]>([]);
  const [bindingsLoading, setBindingsLoading] = useState(false);
  const [bindModalOpen, setBindModalOpen] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editingMiss, setEditingMiss] = useState<MissView | null>(null);
  const [saving, setSaving] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<{
    totalRows: number;
    createdCount: number;
    unchangedCount: number;
    versionNo: string;
  } | null>(null);

  useEffect(() => {
    void loadStandardOptions(domain).then(setStandardOptions);
  }, [domain]);

  const loadMappings = useCallback(
    async (
      code: string,
      dom: MappingDomain,
      p: number,
      s: number,
      kw: string,
      withHistory: boolean,
    ) => {
      if (!code) {
        setMappings([]);
        setMappingTotal(0);
        return;
      }
      setMappingLoading(true);
      try {
        const res = await mappingApi.listMappings({
          insurerCode: code,
          domain: dom,
          keyword: kw.trim() || undefined,
          includeHistory: withHistory || undefined,
          page: p,
          size: s,
        });
        if (res.code !== 200) {
          message.error(resolveErrorMessage(res, '映射加载失败'));
          return;
        }
        setMappings(res.data.records);
        setMappingTotal(toTotal(res.data));
      } catch (err) {
        message.error(resolveErrorMessage(err, '映射加载失败'));
      } finally {
        setMappingLoading(false);
      }
    },
    [message],
  );

  const loadMisses = useCallback(
    async (code: string, p: number, s: number, sortBy?: string) => {
      setMissLoading(true);
      try {
        const res = await mappingApi.listMisses({
          insurerCode: code || undefined,
          sortBy,
          page: p,
          size: s,
        });
        if (res.code !== 200) {
          message.error(resolveErrorMessage(res, '未命中队列加载失败'));
          return;
        }
        setMisses(res.data.records);
        setMissTotal(toTotal(res.data));
      } catch (err) {
        message.error(resolveErrorMessage(err, '未命中队列加载失败'));
      } finally {
        setMissLoading(false);
      }
    },
    [message],
  );

  const loadHealth = useCallback(
    async (code: string, dom: MappingDomain) => {
      if (!code) {
        setHealth(null);
        return;
      }
      try {
        const res = await mappingApi.mappingHealth(code, dom);
        if (res.code !== 200) {
          setHealth(null);
          return;
        }
        setHealth(res.data ?? null);
      } catch {
        // 健康度是辅助信息，加载失败不打扰主流程
        setHealth(null);
      }
    },
    [],
  );

  const loadStpGate = useCallback(
    async (code: string, dom: MappingDomain) => {
      if (!code) {
        setStpGate(null);
        return;
      }
      try {
        const res = await mappingApi.stpGate(code, dom);
        if (res.code !== 200) {
          setStpGate(null);
          return;
        }
        setStpGate(res.data ?? null);
      } catch {
        // STP 闸门是辅助风险信号，加载失败不打扰主流程
        setStpGate(null);
      }
    },
    [],
  );

  useEffect(() => {
    void loadMappings(insurerCode, domain, page, size, keyword, includeHistory);
  }, [loadMappings, insurerCode, domain, page, size, keyword, includeHistory]);

  useEffect(() => {
    void loadMisses(insurerCode, missPage, missSize, missSortBy);
  }, [loadMisses, insurerCode, missPage, missSize, missSortBy]);

  useEffect(() => {
    void loadHealth(insurerCode, domain);
  }, [loadHealth, insurerCode, domain]);

  useEffect(() => {
    void loadStpGate(insurerCode, domain);
  }, [loadStpGate, insurerCode, domain]);

  const loadAliases = useCallback(async (dom: MappingDomain) => {
    setAliasesLoading(true);
    try {
      const res = await mappingApi.listAliases(dom);
      setAliases(res.code === 200 ? res.data : []);
    } catch {
      setAliases([]);
    } finally {
      setAliasesLoading(false);
    }
  }, []);

  const loadSplits = useCallback(async (dom: MappingDomain) => {
    setSplitsLoading(true);
    try {
      const res = await mappingApi.listSplits(dom);
      setSplits(res.code === 200 ? res.data : []);
    } catch {
      setSplits([]);
    } finally {
      setSplitsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAliases(domain);
  }, [loadAliases, domain]);

  useEffect(() => {
    void loadSplits(domain);
  }, [loadSplits, domain]);

  const loadFieldBindings = useCallback(async (code: string) => {
    setBindingsLoading(true);
    try {
      const res = await mappingApi.listFieldBindings(code || undefined);
      setBindings(res.code === 200 ? res.data : []);
    } catch {
      setBindings([]);
    } finally {
      setBindingsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadFieldBindings(insurerCode);
  }, [loadFieldBindings, insurerCode]);

  const openAdd = () => {
    setEditingMiss(null);
    form.resetFields();
    form.setFieldsValue({ mappingType: 'EXACT', matchRule: 'MANUAL' });
    setModalOpen(true);
  };

  const openResolveMiss = (miss: MissView) => {
    setEditingMiss(miss);
    form.resetFields();
    form.setFieldsValue({
      insurerItemCode: miss.rawCode,
      mappingType: 'EXACT',
      matchRule: 'MANUAL',
    });
    setModalOpen(true);
  };

  const submit = async () => {
    if (!insurerCode) {
      message.warning('请先选择承保主体');
      return;
    }
    try {
      const v = await form.validateFields();
      setSaving(true);
      const req: PublishMappingReq = {
        insurerCode,
        domain,
        insurerItemCode: v.insurerItemCode.trim(),
        standardCode: v.standardCode,
        mappingType: v.mappingType,
        matchRule: v.matchRule,
        disambiguationKey: v.disambiguationKey?.trim() || undefined,
        versionNo: v.versionNo?.trim() || undefined,
        source: 'MANUAL',
      };
      const res = editingMiss
        ? await mappingApi.resolveMiss(editingMiss.id, req)
        : await mappingApi.publishMapping(req);
      if (res.code !== 200) {
        message.error(resolveErrorMessage(res, '保存失败'));
        return;
      }
      message.success(editingMiss ? '已建立映射并关闭队列项' : '映射已发布');
      setModalOpen(false);
      void loadMappings(insurerCode, domain, page, size, keyword, includeHistory);
      void loadMisses(insurerCode, missPage, missSize, missSortBy);
      void loadHealth(insurerCode, domain);
    } catch (err) {
      message.error(resolveErrorMessage(err, '保存失败'));
    } finally {
      setSaving(false);
    }
  };

  const disableMapping = async (row: MappingView) => {
    const res = await mappingApi.disableMapping(row.id);
    if (res.code !== 200) {
      message.error(resolveErrorMessage(res, '停用失败'));
      return;
    }
    message.success('已停用');
    void loadMappings(insurerCode, domain, page, size, keyword, includeHistory);
  };

  const ignoreMiss = async (miss: MissView) => {
    const res = await mappingApi.ignoreMiss(miss.id, '运营判定无需映射');
    if (res.code !== 200) {
      message.error(resolveErrorMessage(res, '忽略失败'));
      return;
    }
    message.success('已忽略');
    void loadMisses(insurerCode, missPage, missSize, missSortBy);
  };

  const submitAlias = async (v: AliasFormValues) => {
    const res = await mappingApi.addAlias({
      domain,
      aliasCode: v.aliasCode.trim(),
      canonicalCode: v.canonicalCode.trim(),
      relation: v.relation,
    });
    if (res.code !== 200) {
      message.error(resolveErrorMessage(res, '别名登记失败'));
      return;
    }
    message.success('已登记标准码别名');
    setAliasModalOpen(false);
    void loadAliases(domain);
    void loadHealth(insurerCode, domain);
  };

  const submitSplit = async (v: SplitFormValues) => {
    const res = await mappingApi.addSplit({
      domain,
      parentCode: v.parentCode.trim(),
      childCode: v.childCode.trim(),
    });
    if (res.code !== 200) {
      message.error(resolveErrorMessage(res, '拆分登记失败'));
      return;
    }
    message.success('已登记标准码拆分');
    setSplitModalOpen(false);
    void loadSplits(domain);
    void loadHealth(insurerCode, domain);
  };

  const disableAlias = async (row: AliasRow) => {
    modal.confirm({
      title: '停用该别名',
      content: `确认停用「${row.aliasCode} → ${row.canonicalCode}」？停用后该写法不再自动归并到标准码。`,
      okText: '停用',
      okButtonProps: { danger: true },
      onOk: async () => {
        const res = await mappingApi.disableAlias(row.id);
        if (res.code !== 200) {
          message.error(resolveErrorMessage(res, '停用失败'));
          return;
        }
        message.success('已停用');
        void loadAliases(domain);
        void loadHealth(insurerCode, domain);
      },
    });
  };

  const disableSplit = async (row: SplitRow) => {
    modal.confirm({
      title: '停用该拆分',
      content: `确认停用「${row.parentCode} → ${row.childCode}」？停用后该父码不再转人工拆分。`,
      okText: '停用',
      okButtonProps: { danger: true },
      onOk: async () => {
        const res = await mappingApi.disableSplit(row.id);
        if (res.code !== 200) {
          message.error(resolveErrorMessage(res, '停用失败'));
          return;
        }
        message.success('已停用');
        void loadSplits(domain);
        void loadHealth(insurerCode, domain);
      },
    });
  };

  const submitBinding = async (v: BindFieldValues) => {
    const res = await mappingApi.bindField({
      fieldCode: v.fieldCode.trim(),
      insurerCode: (v.insurerCode ?? '').trim() || undefined,
      sourceType: v.sourceType?.trim() || undefined,
      refCode: v.refCode.trim(),
    });
    if (res.code !== 200) {
      message.error(resolveErrorMessage(res, '绑定失败'));
      return;
    }
    message.success('已绑定/更新字段');
    setBindModalOpen(false);
    void loadFieldBindings(insurerCode);
  };

  /** Excel 导入：整批 validate-then-commit，任一行非法整批拒绝（后端保证，无半批数据）。 */
  const doImport = async (file: File) => {
    if (!insurerCode) {
      message.warning('请先选择承保主体');
      return;
    }
    setImporting(true);
    try {
      const res = await mappingApi.importMappings(insurerCode, domain, file);
      if (res.code !== 200) {
        message.error(resolveErrorMessage(res, '导入失败'));
        return;
      }
      const data = res.data;
      if (data.failedCount > 0) {
        message.error(`导入被整批拒绝：${data.errors?.[0]?.message ?? '存在非法行'}`);
        return;
      }
      setImportResult(data);
      message.success(
        data.createdCount === 0 && data.unchangedCount > 0
          ? '文件与上次导入内容一致（幂等命中），未产生新映射'
          : `导入成功：新建 ${data.createdCount} 条`,
      );
      void loadMappings(insurerCode, domain, 1, size, keyword, includeHistory);
      void loadHealth(insurerCode, domain);
    } catch (err) {
      message.error(resolveErrorMessage(err, '导入失败'));
    } finally {
      setImporting(false);
    }
  };

  const downloadTemplate = async () => {
    try {
      const blob = await mappingApi.importTemplate();
      downloadBlob(blob, csvFilename('mapping-template'));
    } catch (err) {
      message.error(resolveErrorMessage(err, '模板下载失败'));
    }
  };

  const mappingColumns: ColumnsType<MappingView> = [
    { title: '保司码值', dataIndex: 'insurerItemCode', width: 160 },
    { title: '标准码', dataIndex: 'standardCode', width: 140 },
    { title: '标准名称', dataIndex: 'standardName', width: 200, ellipsis: true, render: (v) => v || '—' },
    { title: '域', dataIndex: 'domain', width: 110, render: (v) => DOMAIN_LABEL[v as MappingDomain] },
    { title: '类型', dataIndex: 'mappingType', width: 100, render: (v) => v || '—' },
    {
      title: '状态',
      dataIndex: 'active',
      width: 90,
      render: (v: boolean) => <Tag color={v ? 'green' : 'default'}>{v ? '生效' : '已停用'}</Tag>,
    },
    { title: '版本', dataIndex: 'versionNo', width: 120, render: (v) => v || '—' },
    {
      title: '操作',
      width: 90,
      render: (_: unknown, row) =>
        row.active ? (
          <AuthButton
            type="link"
            size="small"
            code={PERMISSIONS.MAPPING_DISPOSE}
            onClick={() => void disableMapping(row)}
          >
            停用
          </AuthButton>
        ) : (
          <span>—</span>
        ),
    },
  ];

  const missColumns: ColumnsType<MissView> = [
    { title: '保司', dataIndex: 'insurerCode', width: 120 },
    { title: '保司码值', dataIndex: 'rawCode', width: 160 },
    { title: '保司名称', dataIndex: 'rawName', width: 200, render: (v) => v || '—' },
    { title: '域', dataIndex: 'domain', width: 110, render: (v) => DOMAIN_LABEL[v as MappingDomain] },
    { title: '出现次数', dataIndex: 'occurCount', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v) => <Tag color={v === 'OPEN' ? 'red' : 'default'}>{v}</Tag>,
    },
    { title: '处置人', dataIndex: 'resolvedBy', width: 120, render: (v) => v || '—' },
    {
      title: '操作',
      width: 150,
      render: (_: unknown, row) => (
        <Space size={4}>
          <AuthButton type="link" size="small" code={PERMISSIONS.MAPPING_DISPOSE} onClick={() => openResolveMiss(row)}>
            建立映射
          </AuthButton>
          <AuthButton type="link" size="small" code={PERMISSIONS.MAPPING_DISPOSE} onClick={() => void ignoreMiss(row)}>
            忽略
          </AuthButton>
        </Space>
      ),
    },
  ];

  const aliasColumns: ColumnsType<AliasRow> = [
    { title: '别名码（来件写法）', dataIndex: 'aliasCode', width: 200 },
    { title: '标准码（归并目标）', dataIndex: 'canonicalCode', width: 200 },
    { title: '域', dataIndex: 'domain', width: 90, render: (v) => DOMAIN_LABEL[v as MappingDomain] },
    {
      title: '操作',
      width: 80,
      render: (_: unknown, row) => (
        <AuthButton type="link" size="small" code={PERMISSIONS.POLICY_WRITE} onClick={() => void disableAlias(row)}>
          停用
        </AuthButton>
      ),
    },
  ];

  const splitColumns: ColumnsType<SplitRow> = [
    { title: '父码（打包码）', dataIndex: 'parentCode', width: 200 },
    { title: '子码（标准码）', dataIndex: 'childCode', width: 200 },
    { title: '域', dataIndex: 'domain', width: 90, render: (v) => DOMAIN_LABEL[v as MappingDomain] },
    {
      title: '操作',
      width: 80,
      render: (_: unknown, row) => (
        <AuthButton type="link" size="small" code={PERMISSIONS.POLICY_WRITE} onClick={() => void disableSplit(row)}>
          停用
        </AuthButton>
      ),
    },
  ];

  const bindingColumns: ColumnsType<FieldBindingRow> = [
    { title: '字段编码', dataIndex: 'fieldCode', width: 180 },
    {
      title: '承保主体',
      dataIndex: 'insurerCode',
      width: 150,
      render: (v: string) => (v === '*' ? <Tag color="blue">平台基线</Tag> : v),
    },
    { title: '来源类型', dataIndex: 'sourceType', width: 140, render: (v) => v || '—' },
    { title: '标准选项集', dataIndex: 'refCode', width: 200 },
  ];

  return (
    <>
      <PageHeader
        title="多保司主数据映射"
        description="基础目录库是标准码真源（一份主数据）；不同保司码值/名称/标准不同，在此登记「保司码 → 标准码」映射。收单遇无映射的保司码落入未命中队列，运营在此补映射。"
      />
      <Card>
        <Space wrap style={{ marginBottom: 12 }}>
          <Space>
            <span>承保主体：</span>
            <InsurerSelect
              value={insurerCode}
              style={{ width: 260 }}
              onChange={(code) => {
                setInsurerCode(code);
                setPage(1);
                setMissPage(1);
              }}
            />
          </Space>
          <Space>
            <span>主数据域：</span>
            <Select
              value={domain}
              style={{ width: 140 }}
              options={DOMAIN_OPTIONS}
              onChange={(d) => {
                setDomain(d);
                setPage(1);
              }}
            />
          </Space>
          <Button icon={<ReloadOutlined />} onClick={() => { void loadMappings(insurerCode, domain, page, size, keyword, includeHistory); void loadMisses(insurerCode, missPage, missSize, missSortBy); void loadHealth(insurerCode, domain); }}>
            刷新
          </Button>
        </Space>
        {health && (
          <Space size={32} wrap style={{ marginBottom: 12 }}>
            <Statistic title="标准目录总数" value={health.catalogTotal} />
            <Statistic title="已映射标准码" value={health.mappedStandardCodes} />
            <Statistic
              title="覆盖率"
              value={health.coverage < 0 ? '—' : `${(health.coverage * 100).toFixed(1)}%`}
              valueStyle={{ color: health.coverage >= 0 && health.coverage < 0.6 ? '#cf1322' : undefined }}
            />
            <Statistic
              title="未处理未命中"
              value={health.openMissCount}
              valueStyle={{ color: health.openMissCount > 0 ? '#cf1322' : undefined }}
            />
            <Statistic title="近7天新未命中" value={health.recentMissCount} />
          </Space>
        )}
        {stpGate && (
          <Alert
            style={{ marginBottom: 12 }}
            type={stpGate.allowAuto ? 'success' : 'error'}
            showIcon
            message={
              stpGate.allowAuto
                ? 'STP 自动理算：放行（该保司×域映射质量达标，赔案可自动理算）'
                : 'STP 自动理算：阻断（该保司×域映射质量不达标，赔案须转人工）'
            }
            description={
              <div>
                {stpGate.conditions.map((c) => (
                  <div key={c.condition} style={{ lineHeight: 1.9 }}>
                    <Typography.Text type={c.passed ? 'success' : 'danger'}>
                      {c.passed ? '✓ ' : '✗ '}
                      {STP_CONDITION_LABEL[c.condition] ?? c.condition}
                    </Typography.Text>
                    {!c.passed && c.detail ? (
                      <Typography.Text type="secondary"> — {c.detail}</Typography.Text>
                    ) : null}
                  </div>
                ))}
              </div>
            }
          />
        )}
        <Tabs
          items={[
            {
              key: 'mappings',
              label: '码值映射',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                  <Space wrap>
                    <AuthButton type="primary" icon={<PlusOutlined />} code={PERMISSIONS.POLICY_WRITE} onClick={openAdd}>
                      新增映射
                    </AuthButton>
                    <AuthButton icon={<UploadOutlined />} code={PERMISSIONS.POLICY_WRITE} onClick={() => { setImportResult(null); setImportOpen(true); }}>
                      Excel 导入
                    </AuthButton>
                    <Input.Search
                      allowClear
                      placeholder="保司码值 / 标准码"
                      style={{ width: 220 }}
                      onSearch={(v) => {
                        setPage(1);
                        setKeyword(v.trim());
                      }}
                    />
                    <Space size={4}>
                      <Typography.Text type="secondary">含历史行</Typography.Text>
                      <Switch
                        size="small"
                        checked={includeHistory}
                        onChange={(v) => {
                          setIncludeHistory(v);
                          setPage(1);
                        }}
                      />
                    </Space>
                  </Space>
                  <Table<MappingView>
                    rowKey="id"
                    size="small"
                    loading={mappingLoading}
                    columns={mappingColumns}
                    dataSource={mappings}
                    pagination={{
                      current: page,
                      pageSize: size,
                      total: mappingTotal,
                      showSizeChanger: true,
                      showTotal: (t) => `共 ${t} 条`,
                      onChange: (p, s) => {
                        setPage(p);
                        setSize(s);
                      },
                    }}
                  />
                </Space>
              ),
            },
            {
              key: 'misses',
              label: '未命中队列',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                  <Space size={8}>
                    <span>
                      <Typography.Text type="secondary">排序：</Typography.Text>
                    </span>
                    <Select
                      allowClear
                      placeholder="最近出现"
                      style={{ width: 160 }}
                      value={missSortBy}
                      options={[{ value: 'occurCount', label: '出现次数最多' }]}
                      onChange={(v) => {
                        setMissSortBy(v);
                        setMissPage(1);
                      }}
                    />
                  </Space>
                  <Table<MissView>
                    rowKey="id"
                    size="small"
                    loading={missLoading}
                    columns={missColumns}
                    dataSource={misses}
                    pagination={{
                      current: missPage,
                      pageSize: missSize,
                      total: missTotal,
                      showSizeChanger: true,
                      showTotal: (t) => `共 ${t} 条`,
                      onChange: (p, s) => {
                        setMissPage(p);
                        setMissSize(s);
                      },
                    }}
                  />
                </Space>
              ),
            },
            {
              key: 'aliases',
              label: '标准码别名',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    别名是<b>标准码维度（跨保司生效）</b>：把来件里可能出现的不同写法（别名码）归并到规范标准码，
                    解析时自动 canonicalize。与承保主体无关，按当前主数据域维护。
                  </Typography.Paragraph>
                  <AuthButton
                    type="primary"
                    icon={<PlusOutlined />}
                    code={PERMISSIONS.POLICY_WRITE}
                    onClick={() => {
                      aliasForm.resetFields();
                      setAliasModalOpen(true);
                    }}
                  >
                    新增别名
                  </AuthButton>
                  <Table<AliasRow>
                    rowKey="id"
                    size="small"
                    loading={aliasesLoading}
                    columns={aliasColumns}
                    dataSource={aliases}
                    pagination={false}
                    locale={{ emptyText: '暂无别名' }}
                  />
                </Space>
              ),
            },
            {
              key: 'splits',
              label: '标准码拆分',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    拆分是<b>标准码维度（跨保司生效）</b>：把来件里的打包父码拆成多个标准子码。
                    命中父码时解析层标记为需人工拆分。与承保主体无关，按当前主数据域维护。
                  </Typography.Paragraph>
                  <AuthButton
                    type="primary"
                    icon={<PlusOutlined />}
                    code={PERMISSIONS.POLICY_WRITE}
                    onClick={() => {
                      splitForm.resetFields();
                      setSplitModalOpen(true);
                    }}
                  >
                    新增拆分
                  </AuthButton>
                  <Table<SplitRow>
                    rowKey="id"
                    size="small"
                    loading={splitsLoading}
                    columns={splitColumns}
                    dataSource={splits}
                    pagination={false}
                    locale={{ emptyText: '暂无拆分' }}
                  />
                </Space>
              ),
            },
            {
              key: 'field-bindings',
              label: '字段归一',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                    字段归一是<b>字段维度（与域无关）</b>：把保司来件里的字段绑定到平台标准选项集。
                    insurerCode 空 = 平台基线 <Tag color="blue">*</Tag>；选中承保主体时同时展示基线与该保司的专属覆盖。
                    同一 (字段, 保司) 重复提交会覆盖更新。
                  </Typography.Paragraph>
                  <AuthButton
                    type="primary"
                    icon={<PlusOutlined />}
                    code={PERMISSIONS.POLICY_WRITE}
                    onClick={() => {
                      bindForm.resetFields();
                      bindForm.setFieldsValue({ insurerCode });
                      setBindModalOpen(true);
                    }}
                  >
                    绑定/更新字段
                  </AuthButton>
                  <Table<FieldBindingRow>
                    rowKey="id"
                    size="small"
                    loading={bindingsLoading}
                    columns={bindingColumns}
                    dataSource={bindings}
                    pagination={false}
                    locale={{ emptyText: '暂无绑定' }}
                  />
                </Space>
              ),
            },
          ]}
        />
      </Card>
      <Modal
        title={editingMiss ? '建立映射并关闭队列项' : '新增映射'}
        open={modalOpen}
        confirmLoading={saving}
        onOk={() => void submit()}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="insurerItemCode"
            label="保司码值"
            rules={[{ required: true, message: '请输入保司侧码值' }]}
          >
            <Input maxLength={64} placeholder="保司来件里的码值" />
          </Form.Item>
          <Form.Item
            name="standardCode"
            label="标准码（来自基础目录库）"
            rules={[{ required: true, message: '请选择标准码' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择标准码"
              options={standardOptions}
            />
          </Form.Item>
          <Form.Item name="mappingType" label="映射类型" initialValue="EXACT">
            <Select options={MAPPING_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="matchRule" label="匹配规则" initialValue="MANUAL">
            <Select options={MATCH_RULE_OPTIONS} />
          </Form.Item>
          <Form.Item name="disambiguationKey" label="消歧键" tooltip="同保司同码存在多条时的消歧维度">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="versionNo" label="发布批次">
            <Input maxLength={32} placeholder="如 release-2026-11" />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={`Excel 批量导入（${DOMAIN_LABEL[domain]}）`}
        open={importOpen}
        footer={null}
        onCancel={() => setImportOpen(false)}
        destroyOnHidden
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            列契约：A=保司码值、B=保司名称、C=标准码。整批校验——任一行非法（空值 / 标准码不存在 /
            文件内重复）则整批拒绝；同一文件重复上传按内容指纹幂等，不会产生重复映射。
          </Typography.Paragraph>
          <Button icon={<DownloadOutlined />} onClick={() => void downloadTemplate()}>
            下载导入模板
          </Button>
          <Upload.Dragger
            accept=".xlsx"
            maxCount={1}
            showUploadList={false}
            disabled={importing || !insurerCode}
            customRequest={({ file }) => void doImport(file as File)}
          >
            <p className="ant-upload-drag-icon">
              <UploadOutlined />
            </p>
            <p className="ant-upload-text">{importing ? '导入中…' : '点击或拖拽 .xlsx 文件到此处上传'}</p>
            {!insurerCode && <p className="ant-upload-hint">请先在页面上方选择承保主体</p>}
          </Upload.Dragger>
          {importResult && (
            <div>
              <Typography.Text>
                批次 {importResult.versionNo}：共 {importResult.totalRows} 行，新建{' '}
                {importResult.createdCount} 条，无变化 {importResult.unchangedCount} 条
              </Typography.Text>
            </div>
          )}
        </Space>
      </Modal>
      <Modal
        title="新增标准码别名"
        open={aliasModalOpen}
        onOk={() => aliasForm.submit()}
        onCancel={() => setAliasModalOpen(false)}
        destroyOnHidden
      >
        <Form form={aliasForm} layout="vertical" onFinish={(v) => void submitAlias(v)}>
          <Form.Item
            name="aliasCode"
            label="别名码（来件写法）"
            rules={[{ required: true, message: '请输入别名码' }]}
          >
            <Input maxLength={64} placeholder="来件里可能出现的写法" />
          </Form.Item>
          <Form.Item
            name="canonicalCode"
            label="标准码（归并目标）"
            rules={[{ required: true, message: '请输入标准码' }]}
          >
            <Input maxLength={64} placeholder="规范标准码" />
          </Form.Item>
          <Form.Item name="relation" label="关系" initialValue="MERGE">
            <Select
              options={[
                { value: 'MERGE', label: '归并 MERGE' },
                { value: 'RENAME', label: '更名 RENAME' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="新增标准码拆分"
        open={splitModalOpen}
        onOk={() => splitForm.submit()}
        onCancel={() => setSplitModalOpen(false)}
        destroyOnHidden
      >
        <Form form={splitForm} layout="vertical" onFinish={(v) => void submitSplit(v)}>
          <Form.Item
            name="parentCode"
            label="父码（打包码）"
            rules={[{ required: true, message: '请输入父码' }]}
          >
            <Input maxLength={64} placeholder="来件里的打包码" />
          </Form.Item>
          <Form.Item
            name="childCode"
            label="子码（标准码）"
            rules={[{ required: true, message: '请输入子码' }]}
          >
            <Input maxLength={64} placeholder="拆分后的标准码" />
          </Form.Item>
        </Form>
        </Modal>
        <Modal
        title="绑定/更新字段到选项集"
        open={bindModalOpen}
        onOk={() => bindForm.submit()}
        onCancel={() => setBindModalOpen(false)}
        destroyOnHidden
        >
        <Form form={bindForm} layout="vertical" onFinish={(v) => void submitBinding(v)}>
        <Form.Item
          name="fieldCode"
          label="字段编码"
          rules={[{ required: true, message: '请输入字段编码' }]}
        >
          <Input maxLength={64} placeholder="如 gender / visit_type" />
        </Form.Item>
        <Form.Item
          name="insurerCode"
          label="承保主体（空 = 平台基线）"
          tooltip="空值表示平台标准基线 '*'；填值表示该保司的专属覆盖"
        >
          <Input maxLength={64} placeholder="空 = 平台基线 '*'" />
        </Form.Item>
        <Form.Item name="sourceType" label="来源类型" initialValue="">
          <Input maxLength={64} placeholder="可选" />
        </Form.Item>
        <Form.Item
          name="refCode"
          label="标准选项集编码"
          rules={[{ required: true, message: '请输入标准选项集编码' }]}
        >
          <Input maxLength={64} placeholder="绑定的标准选项集" />
        </Form.Item>
        </Form>
        </Modal>
        </>
        );
        }
