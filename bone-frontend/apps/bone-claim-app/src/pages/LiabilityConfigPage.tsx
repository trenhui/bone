import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import {
  CloudUploadOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import { BatchImportModal, type ImportColumn } from '../components/BatchImportModal';
import {
  CONFIG_STATUS_META,
  claimConfigApi,
  type ConfigStatus,
  type CreateLiabilityReq,
  type LiabilityType,
  type LiabilityView,
  type PlanView,
  type PolicyView,
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

/** 责任批量导入列定义（模板表头用英文键）。 */
const liabilityImportColumns: ImportColumn[] = [
  { key: 'planId', label: '计划ID', required: true, example: '' },
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

function buildLiabilityRequest(row: Record<string, string>): Record<string, unknown> | null {
  return {
    planId: row.planId.trim(),
    liabilityCode: row.liabilityCode.trim(),
    liabilityType: row.liabilityType?.trim() || undefined,
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
    status: row.status?.trim() || undefined,
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
  const [policies, setPolicies] = useState<PolicyView[]>([]);
  const [policyNo, setPolicyNo] = useState('');
  const [plans, setPlans] = useState<PlanView[]>([]);
  const [planId, setPlanId] = useState('');
  const [liabilities, setLiabilities] = useState<LiabilityView[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<LiabilityView | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [importOpen, setImportOpen] = useState(false);

  const loadPolicies = useCallback(async () => {
    try {
      const res = await claimConfigApi.listPolicies({ page: 1, size: 200 });
      const list = res.data?.records ?? [];
      setPolicies(list);
      setPolicyNo((prev) => prev || list[0]?.policyNo || '');
    } catch (err) {
      message.error(resolveErrorMessage(err, '保单列表加载失败'));
    }
  }, [message]);

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

  useEffect(() => {
    void loadPolicies();
  }, [loadPolicies]);

  const onPolicyChange = async (pn: string) => {
    setPolicyNo(pn);
    setPlanId('');
    setLiabilities([]);
    await loadPlans(pn);
  };

  const onPlanChange = async (pid: string) => {
    setPlanId(pid);
    await loadLiabilities(pid);
  };

  const openCreate = () => {
    setEditTarget(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (row: LiabilityView) => {
    setEditTarget(row);
    form.setFieldsValue({
      liabilityCode: row.liabilityCode,
      liabilityType: row.liabilityType,
      payPercent: row.payPercent,
      liabilityDeduct: row.liabilityDeduct,
      liabilityLimit: row.liabilityLimit,
      invoiceDeduct: row.invoiceDeduct,
      adjustDeduct: row.adjustDeduct,
      dailyLimit: row.dailyLimit,
      dailyLimitBasis: row.dailyLimitBasis,
      limitMode: row.limitMode,
      limitHospitalCodes: row.limitHospitalCodes,
      limitDiagnosisCodes: row.limitDiagnosisCodes,
      catalogPolicy: row.catalogPolicy,
      catalogCode: row.catalogCode,
      socialOnly: row.socialOnly,
      waitingPeriod: row.waitingPeriod,
    });
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
        liabilityType: clean(values.liabilityType),
        payPercent: clean(values.payPercent),
        liabilityDeduct: clean(values.liabilityDeduct),
        liabilityLimit: clean(values.liabilityLimit),
        invoiceDeduct: clean(values.invoiceDeduct),
        adjustDeduct: clean(values.adjustDeduct),
        dailyLimit: clean(values.dailyLimit),
        dailyLimitBasis: clean(values.dailyLimitBasis),
        limitMode: clean(values.limitMode),
        limitHospitalCodes: clean(values.limitHospitalCodes),
        limitDiagnosisCodes: clean(values.limitDiagnosisCodes),
        catalogPolicy: clean(values.catalogPolicy),
        catalogCode: clean(values.catalogCode),
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
        await claimConfigApi.updateLiability(
          editTarget.liabilityId,
          base as unknown as UpdateLiabilityReq,
        );
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

  const publish = async (row: LiabilityView) => {
    try {
      await claimConfigApi.publishLiability(row.liabilityId);
      message.success('责任已发布');
      await loadLiabilities(planId);
    } catch (err) {
      message.error(resolveErrorMessage(err, '发布失败'));
    }
  };

  const retire = async (row: LiabilityView) => {
    try {
      await claimConfigApi.retireLiability(row.liabilityId);
      message.success('责任已下线');
      await loadLiabilities(planId);
    } catch (err) {
      message.error(resolveErrorMessage(err, '下线失败'));
    }
  };

  const columns = [
    { title: '责任编码', dataIndex: 'liabilityCode', width: 110 },
    {
      title: '类型',
      dataIndex: 'liabilityType',
      width: 130,
      render: (v: LiabilityType) => v || '-',
    },
    { title: '赔付比例(%)', dataIndex: 'payPercent', width: 100, render: (v: string) => v ?? '0' },
    { title: '年度免赔', dataIndex: 'liabilityDeduct', width: 100 },
    { title: '责任额度', dataIndex: 'liabilityLimit', width: 100 },
    { title: '日限额', dataIndex: 'dailyLimit', width: 90 },
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
      width: 230,
      render: (_: unknown, row: LiabilityView) => (
        <Space size={4}>
          <AuthButton
            code="claim:policy:write"
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(row)}
          >
            编辑
          </AuthButton>
          <AuthButton
            code="claim:policy:write"
            type="link"
            size="small"
            icon={<CloudUploadOutlined />}
            disabled={row.status === 'ACTIVE'}
            onClick={() => void publish(row)}
          >
            发布
          </AuthButton>
          <AuthButton
            code="claim:policy:write"
            type="link"
            size="small"
            danger
            icon={<StopOutlined />}
            disabled={row.status === 'RETIRED'}
            onClick={() => void retire(row)}
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
        subtitle="保险责任的版本化配置（赔付比例 / 多层免赔 / 日限额 / 适用限定 / 目录准入）。编辑已生效责任会生成新版本草稿，发布后旧版本自动下线。"
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
            code="claim:policy:write"
            type="primary"
            icon={<PlusOutlined />}
            disabled={!planId}
            onClick={openCreate}
          >
            新建责任
          </AuthButton>
          <AuthButton
            code="claim:policy:write"
            icon={<UploadOutlined />}
            disabled={!planId}
            onClick={() => setImportOpen(true)}
          >
            批量导入
          </AuthButton>
          <Button icon={<ReloadOutlined />} onClick={() => void loadLiabilities(planId)}>
            刷新
          </Button>
        </Space>
        <Table<LiabilityView>
          rowKey="liabilityId"
          size="small"
          loading={loading}
          dataSource={liabilities}
          columns={columns}
          pagination={liabilities.length > 10 ? { pageSize: 10 } : false}
        />
      </Card>

      <Modal
        open={modalOpen}
        title={editTarget ? `编辑责任（${editTarget.liabilityCode}）` : '新建责任'}
        onCancel={() => setModalOpen(false)}
        onOk={() => void submit()}
        confirmLoading={submitting}
        destroyOnClose
        width={760}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="责任编码"
            name="liabilityCode"
            rules={[{ required: true, message: '责任编码不能为空' }]}
          >
            <Input placeholder="如 R01" disabled={!!editTarget} />
          </Form.Item>
          <Space size="large" wrap>
            <Form.Item label="责任类型" name="liabilityType">
              <Select
                options={LIABILITY_TYPE_OPTIONS}
                style={{ width: 240 }}
                placeholder="REIMBURSEMENT"
              />
            </Form.Item>
            <Form.Item label="赔付比例(%)" name="payPercent">
              <Input placeholder="90" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item label="年度免赔额" name="liabilityDeduct">
              <Input placeholder="0" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item label="责任额度上限" name="liabilityLimit">
              <Input placeholder="0=不限" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item label="发票级免赔" name="invoiceDeduct">
              <Input placeholder="0" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item label="理算级免赔" name="adjustDeduct">
              <Input placeholder="0" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item label="日赔付限额" name="dailyLimit">
              <Input placeholder="0=不启用" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item label="日窗口口径" name="dailyLimitBasis">
              <Select
                options={DAILY_BASIS_OPTIONS}
                style={{ width: 200 }}
                placeholder="NONE"
              />
            </Form.Item>
            <Form.Item label="适用限定方式" name="limitMode">
              <Select
                options={LIMIT_MODE_OPTIONS}
                style={{ width: 200 }}
                placeholder="NONE"
              />
            </Form.Item>
            <Form.Item label="限定医院编码" name="limitHospitalCodes">
              <Input placeholder="逗号分隔" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item label="限定病种编码" name="limitDiagnosisCodes">
              <Input placeholder="逗号分隔" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item label="目录准入策略" name="catalogPolicy">
              <Select
                options={CATALOG_POLICY_OPTIONS}
                style={{ width: 200 }}
                placeholder="NONE"
              />
            </Form.Item>
            <Form.Item label="目录编码" name="catalogCode">
              <Input placeholder="如 NATIONAL_2024" style={{ width: 200 }} />
            </Form.Item>
            <Form.Item label="仅赔社保内(1/0)" name="socialOnly">
              <InputNumber min={0} max={1} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item label="等待期(天)" name="waitingPeriod">
              <InputNumber min={0} style={{ width: 160 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <BatchImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        title="批量导入责任"
        fileName="liability-template"
        columns={liabilityImportColumns}
        defaults={{ planId }}
        buildRequest={buildLiabilityRequest}
        validateRow={validateLiabilityRow}
        createOne={(req) => claimConfigApi.createLiability(req as unknown as CreateLiabilityReq)}
        onSuccess={() => void loadLiabilities(planId)}
      />
    </div>
  );
}
