import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
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

/**
 * 多保司主数据映射（保司码 → 标准码 的 crosswalk）。
 *
 * <p>基础目录库是标准码真源（一份主数据）；不同保司对同一主数据的码值 / 名称 / 标准不同，
 * 在这里登记「保司码值 → 标准码」的映射。责任规则只认标准码，收单侧按映射翻译保司来件。
 * 收单遇无映射的保司码会落入「未命中队列」，运营在此补映射后关闭。
 */
export function MappingPage(): ReactElement {
  const { message } = AntApp.useApp();
  const [form] = Form.useForm<PublishFormValues>();
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

  useEffect(() => {
    void loadMappings(insurerCode, domain, page, size, keyword, includeHistory);
  }, [loadMappings, insurerCode, domain, page, size, keyword, includeHistory]);

  useEffect(() => {
    void loadMisses(insurerCode, missPage, missSize, missSortBy);
  }, [loadMisses, insurerCode, missPage, missSize, missSortBy]);

  useEffect(() => {
    void loadHealth(insurerCode, domain);
  }, [loadHealth, insurerCode, domain]);

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
    </>
  );
}
