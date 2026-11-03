import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { resolveErrorMessage } from '@bone/shared-utils';
import type { ColumnsType } from 'antd/es/table';
import { PERMISSIONS } from '../constants/permissions';
import { PageHeader } from '../components/PageHeader';
import { InsurerSelect } from '../components/InsurerSelect';
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

  const [insurerCode, setInsurerCode] = useState('');
  const [domain, setDomain] = useState<MappingDomain>('HOSPITAL');
  const [standardOptions, setStandardOptions] = useState<{ label: string; value: string }[]>([]);

  // 映射列表
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [mappings, setMappings] = useState<MappingView[]>([]);
  const [mappingTotal, setMappingTotal] = useState(0);
  const [mappingLoading, setMappingLoading] = useState(false);

  // 未命中队列
  const [missPage, setMissPage] = useState(1);
  const [missSize, setMissSize] = useState(20);
  const [misses, setMisses] = useState<MissView[]>([]);
  const [missTotal, setMissTotal] = useState(0);
  const [missLoading, setMissLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editingMiss, setEditingMiss] = useState<MissView | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void loadStandardOptions(domain).then(setStandardOptions);
  }, [domain]);

  const loadMappings = useCallback(
    async (code: string, dom: MappingDomain, p: number, s: number) => {
      if (!code) {
        setMappings([]);
        setMappingTotal(0);
        return;
      }
      setMappingLoading(true);
      try {
        const res = await mappingApi.listMappings({ insurerCode: code, domain: dom, page: p, size: s });
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
    async (code: string, p: number, s: number) => {
      setMissLoading(true);
      try {
        const res = await mappingApi.listMisses({ page: p, size: s });
        // 后端按租户返回；前端再按 insurerCode 过滤（未命中队列跨保司，运营通常按保司盯）
        const all = res.code === 200 ? res.data.records : [];
        const filtered = code ? all.filter((m) => m.insurerCode === code) : all;
        setMisses(filtered);
        setMissTotal(filtered.length);
      } catch (err) {
        message.error(resolveErrorMessage(err, '未命中队列加载失败'));
      } finally {
        setMissLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    void loadMappings(insurerCode, domain, page, size);
  }, [loadMappings, insurerCode, domain, page, size]);

  useEffect(() => {
    void loadMisses(insurerCode, missPage, missSize);
  }, [loadMisses, insurerCode, missPage, missSize]);

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
      void loadMappings(insurerCode, domain, page, size);
      void loadMisses(insurerCode, missPage, missSize);
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
    void loadMappings(insurerCode, domain, page, size);
  };

  const ignoreMiss = async (miss: MissView) => {
    const res = await mappingApi.ignoreMiss(miss.id, '运营判定无需映射');
    if (res.code !== 200) {
      message.error(resolveErrorMessage(res, '忽略失败'));
      return;
    }
    message.success('已忽略');
    void loadMisses(insurerCode, missPage, missSize);
  };

  const mappingColumns: ColumnsType<MappingView> = [
    { title: '保司码值', dataIndex: 'insurerItemCode', width: 160 },
    { title: '标准码', dataIndex: 'standardCode', width: 160 },
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
            code={PERMISSIONS.POLICY_WRITE}
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
    {
      title: '操作',
      width: 150,
      render: (_: unknown, row) => (
        <Space size={4}>
          <AuthButton type="link" size="small" code={PERMISSIONS.POLICY_WRITE} onClick={() => openResolveMiss(row)}>
            建立映射
          </AuthButton>
          <AuthButton type="link" size="small" code={PERMISSIONS.POLICY_WRITE} onClick={() => void ignoreMiss(row)}>
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
          <Button icon={<ReloadOutlined />} onClick={() => { void loadMappings(insurerCode, domain, page, size); void loadMisses(insurerCode, missPage, missSize); }}>
            刷新
          </Button>
        </Space>
        <Tabs
          items={[
            {
              key: 'mappings',
              label: '码值映射',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                  <AuthButton type="primary" icon={<PlusOutlined />} code={PERMISSIONS.POLICY_WRITE} onClick={openAdd}>
                    新增映射
                  </AuthButton>
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
    </>
  );
}
