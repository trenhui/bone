import { useCallback, useEffect, useMemo, useState, type Key, type ReactElement } from 'react';
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
  Typography,
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
import { useConfirmAction } from '../hooks/useConfirmAction';
import { PERMISSIONS } from '../constants/permissions';
import { ERROR_CODES } from '../constants/errorCodes';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import { BatchImportModal, type ImportColumn } from '../components/BatchImportModal';
import { usePolicyOptions } from '../hooks/usePolicyOptions';
import { FORM_VALIDATION_MESSAGE, isFormValidationError } from '../utils/formError';
import {
  CONFIG_STATUS_META,
  claimConfigApi,
  type ConfigStatus,
  type CoverageView,
  type CreatePlanReq,
  type LiabilityType,
  type PlanView,
} from '../services/claimConfigApi';

/**
 * 计划配置（配置面）—— 保单 → 计划 → 险种 三层树。
 *
 * <p><b>为什么按三层而不是平铺</b>：参考实现 pk-tpa-saas 的「责任规则」页把
 * <b>计划层 / 险种层 / 责任层</b>三列并排在一张表里，用行合并单元格表达层级
 * （`span-method` 把计划行的单元格纵向合并到它的险种行）。本仓沿用同一信息架构，
 * 但用<b>展开式行</b>（antd `children`）而非合并单元格：合并单元格在虚拟滚动、
 * 展开/收起、行选择上都会出各种边界问题，而"计划 → 险种"两层在本仓已足够表达
 * （责任层由 {@code t_adj_liability} 承载，见下）。
 *
 * <p><b>关于责任层</b>：责任配置（赔付比例 / 三层免赔 / 日限额 / 适用限定）落在
 * {@code t_adj_liability}，是<b>版本化</b>的（同一 liability_uuid 多版本并存，
 * 已生效版本不可改）。本页只承载"计划与险种"这一层结构；责任参数由理赔域的
 * 责任配置入口维护并随计划发布生效——两者分开是因为**责任版本化**与
 * **计划结构**的变更节奏与审批口径不同（改责任要发版本、改险种是结构调整）。
 */
/** 计划批量导入列定义（模板表头用英文键）。 */
const planImportColumns: ImportColumn[] = [
  { key: 'policyNo', label: '保单号', required: true, example: 'POL-2026-0001' },
  { key: 'planCode', label: '计划编码', required: true, example: 'PLAN-OUTPATIENT' },
  { key: 'planName', label: '计划名称', example: '门诊计划' },
  { key: 'planLimit', label: '计划额度(元,0=不限)', example: '0' },
  { key: 'waitingPeriod', label: '等待期(天)', example: '0' },
  { key: 'versionNo', label: '计划版本', example: 'v1' },
];

function buildPlanRequest(row: Record<string, string>): CreatePlanReq | null {
  return {
    policyNo: row.policyNo.trim(),
    planCode: row.planCode.trim(),
    planName: row.planName?.trim() || undefined,
    planLimit: row.planLimit ? Number(row.planLimit) : 0,
    waitingPeriod: row.waitingPeriod ? Number(row.waitingPeriod) : 0,
    versionNo: row.versionNo?.trim() || 'v1',
  };
}

function validatePlanRow(row: Record<string, string>): string | null {
  if (!row.planCode?.trim()) return '计划编码不能为空';
  if (!row.policyNo?.trim()) return '保单号不能为空（可在模板填写，或先在本页选中保单）';
  return null;
}

export function PlanConfigPage(): ReactElement {
  const { message } = AntApp.useApp();
  const confirm = useConfirmAction();
  const { policies } = usePolicyOptions();
  const [policyNo, setPolicyNo] = useState<string>('');
  const [plans, setPlans] = useState<PlanView[]>([]);
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<string[]>([]);
  const [planModalOpen, setPlanModalOpen] = useState(false);
  const [coverageModalOpen, setCoverageModalOpen] = useState(false);
  const [currentPlan, setCurrentPlan] = useState<PlanView | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [planForm] = Form.useForm();
  const [coverageForm] = Form.useForm();
  const [planEditTarget, setPlanEditTarget] = useState<PlanView | null>(null);
  const [planEditForm] = Form.useForm();
  const [coverageEditTarget, setCoverageEditTarget] = useState<CoverageView | null>(null);
  const [coverageEditForm] = Form.useForm();
  const [planImportOpen, setPlanImportOpen] = useState(false);
  /** 险种按计划缓存（展开时才加载，避免一次性拉全量）。 */
  const [coverageMap, setCoverageMap] = useState<Record<string, CoverageView[]>>({});

  const loadPlans = useCallback(async () => {
    if (!policyNo) {
      setPlans([]);
      return;
    }
    setLoading(true);
    try {
      const res = await claimConfigApi.listPlans(policyNo);
      setPlans(res.data ?? []);
    } catch (err) {
      message.error(resolveErrorMessage(err, '计划列表加载失败'));
    } finally {
      setLoading(false);
    }
  }, [policyNo, message]);

  useEffect(() => {
    setPolicyNo((prev) => prev || policies[0]?.policyNo || '');
  }, [policies]);

  useEffect(() => {
    void loadPlans();
    setExpanded([]);
    setCoverageMap({});
  }, [loadPlans]);

  const loadCoverages = async (planId: string) => {
    try {
      const res = await claimConfigApi.listCoverages(planId);
      setCoverageMap((prev) => ({ ...prev, [planId]: res.data ?? [] }));
    } catch (err) {
      message.error(resolveErrorMessage(err, '险种列表加载失败'));
    }
  };

  const onExpand = async (expandedKeys: readonly Key[]) => {
    // antd 的 expandedRowKeys 是 Key[]（含 number 与 bigint）；本表的 rowKey 是 planId（string），
    // 统一转成 string 再用，避免类型不匹配与 Map key 形态不一致。
    const keys = expandedKeys.map((k) => String(k));
    setExpanded(keys);
    const pending = keys.filter((k) => !coverageMap[k]);
    for (const planId of pending) {
      await loadCoverages(planId);
    }
  };

  const openCreatePlan = () => {
    // Modal 未打开时 setFieldsValue 会静默失效（Form 未挂载）⇒ 初值改由 initialValues 承担。
    setPlanModalOpen(true);
  };

  /** 新建计划表单初值。 */
  const planCreateInitialValues = { planLimit: 0, waitingPeriod: 0, versionNo: 'v1' };

  const submitPlan = async () => {
    setSubmitting(true);
    try {
      const values = await planForm.validateFields();
      await claimConfigApi.createPlan({
        policyNo,
        planCode: values.planCode.trim(),
        planName: values.planName?.trim(),
        planLimit: values.planLimit ?? 0,
        waitingPeriod: values.waitingPeriod ?? 0,
        versionNo: values.versionNo ?? 'v1',
      });
      message.success('计划已创建（草稿态，发布后其下生效责任才参与理算）');
      setPlanModalOpen(false);
      await loadPlans();
    } catch (err) {
      if (isFormValidationError(err)) {
        message.error(FORM_VALIDATION_MESSAGE);
      } else {
        message.error(resolveErrorMessage(err, '计划创建失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openCreateCoverage = (plan: PlanView) => {
    setCurrentPlan(plan);
    setCoverageModalOpen(true);
  };

  /** 新建险种表单初值。 */
  const coverageCreateInitialValues = { coverageLimit: 0, liabilityType: 'REIMBURSEMENT' };

  const submitCoverage = async () => {
    if (!currentPlan) return;
    setSubmitting(true);
    try {
      const values = await coverageForm.validateFields();
      await claimConfigApi.createCoverage({
        planId: currentPlan.planId,
        coverageCode: values.coverageCode.trim(),
        coverageName: values.coverageName?.trim(),
        coverageLimit: values.coverageLimit ?? 0,
        liabilityType: values.liabilityType ?? 'REIMBURSEMENT',
      });
      message.success('险种已创建');
      setCoverageModalOpen(false);
      await loadCoverages(currentPlan.planId);
    } catch (err) {
      if (isFormValidationError(err)) {
        message.error(FORM_VALIDATION_MESSAGE);
      } else {
        message.error(resolveErrorMessage(err, '险种创建失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const publishPlan = async (plan: PlanView) => {
    try {
      await claimConfigApi.publishPlan(plan.planId);
      message.success('计划已发布');
      await loadPlans();
    } catch (err) {
      message.error(resolveErrorMessage(err, '计划发布失败'));
    }
  };

  const retirePlan = async (plan: PlanView) => {
    try {
      await claimConfigApi.retirePlan(plan.planId);
      message.success('计划已下线');
      await loadPlans();
    } catch (err) {
      message.error(resolveErrorMessage(err, '计划下线失败'));
    }
  };

  /** 发布险种（DRAFT → ACTIVE）：与计划同语义的二级发布。 */
  const publishCoverage = async (coverage: CoverageView) => {
    try {
      await claimConfigApi.publishCoverage(coverage.coverageId);
      message.success('险种已发布');
      await loadCoverages(coverage.planId);
    } catch (err) {
      message.error(resolveErrorMessage(err, '险种发布失败'));
    }
  };

  /** 下线险种（ACTIVE/DRAFT → RETIRED）。 */
  const retireCoverage = async (coverage: CoverageView) => {
    try {
      await claimConfigApi.retireCoverage(coverage.coverageId);
      message.success('险种已下线');
      await loadCoverages(coverage.planId);
    } catch (err) {
      message.error(resolveErrorMessage(err, '险种下线失败'));
    }
  };

  const openPlanEdit = (plan: PlanView) => {
    // 同 openCreatePlan：Modal 未打开时 setFieldsValue 静默失效 ⇒ 初值交给 initialValues。
    setPlanEditTarget(plan);
  };

  /** 编辑计划表单初值。 */
  const planEditInitialValues = useMemo(
    () =>
      planEditTarget
        ? {
          planName: planEditTarget.planName,
          planLimit: planEditTarget.planLimit,
          waitingPeriod: planEditTarget.waitingPeriod,
          versionNo: planEditTarget.versionNo,
        }
        : undefined,
    [planEditTarget],
  );

  const submitPlanEdit = async () => {
    if (!planEditTarget) return;
    setSubmitting(true);
    try {
      const values = await planEditForm.validateFields();
      await claimConfigApi.updatePlan(planEditTarget.planId, {
        planName: values.planName?.trim(),
        planLimit: values.planLimit ?? 0,
        waitingPeriod: values.waitingPeriod ?? 0,
        versionNo: values.versionNo ?? 'v1',
        version: planEditTarget.version,
      });
      message.success('计划已更新');
      setPlanEditTarget(null);
      await loadPlans();
    } catch (err) {
      if (isFormValidationError(err)) {
        message.error(FORM_VALIDATION_MESSAGE);
        return;
      }
      const code = resolveErrorCode(err);
      if (code === ERROR_CODES.CONCURRENT_CONFLICT) {
        setPlanEditTarget(null);
        await loadPlans();
        message.warning('该计划已被其他人修改，请重新打开编辑并保存');
      } else {
        message.error(resolveErrorMessage(err, '计划更新失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openCoverageEdit = (coverage: CoverageView) => {
    setCoverageEditTarget(coverage);
  };

  /** 编辑险种表单初值。 */
  const coverageEditInitialValues = useMemo(
    () =>
      coverageEditTarget
        ? {
          coverageName: coverageEditTarget.coverageName,
          coverageLimit: coverageEditTarget.coverageLimit,
          liabilityType: coverageEditTarget.liabilityType,
        }
        : undefined,
    [coverageEditTarget],
  );

  const submitCoverageEdit = async () => {
    if (!coverageEditTarget) return;
    setSubmitting(true);
    try {
      const values = await coverageEditForm.validateFields();
      await claimConfigApi.updateCoverage(coverageEditTarget.coverageId, {
        coverageName: values.coverageName?.trim(),
        coverageLimit: values.coverageLimit ?? 0,
        liabilityType: values.liabilityType ?? 'REIMBURSEMENT',
        version: coverageEditTarget.version,
      });
      message.success('险种已更新');
      setCoverageEditTarget(null);
      await loadCoverages(coverageEditTarget.planId);
    } catch (err) {
      if (isFormValidationError(err)) {
        message.error(FORM_VALIDATION_MESSAGE);
        return;
      }
      const code = resolveErrorCode(err);
      if (code === ERROR_CODES.CONCURRENT_CONFLICT) {
        setCoverageEditTarget(null);
        await loadCoverages(coverageEditTarget.planId);
        message.warning('该险种已被其他人修改，请重新打开编辑并保存');
      } else {
        message.error(resolveErrorMessage(err, '险种更新失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="计划配置"
        description="按保单维护「计划 → 险种」两层结构。计划发布后，其下生效的责任才参与理算。"
      />

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="选择保单"
            style={{ width: 280 }}
            value={policyNo || undefined}
            onChange={(v) => setPolicyNo(v)}
            options={policies.map((p) => ({
              value: p.policyNo,
              label: `${p.policyNo}${p.policyName ? ` · ${p.policyName}` : ''}`,
            }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void loadPlans()}>
            刷新
          </Button>
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            type="primary"
            icon={<PlusOutlined />}
            disabled={!policyNo}
            onClick={openCreatePlan}
          >
            新建计划
          </AuthButton>
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            icon={<UploadOutlined />}
            disabled={!policyNo}
            onClick={() => setPlanImportOpen(true)}
          >
            批量导入计划
          </AuthButton>
        </Space>

        <Table<PlanView>
          rowKey="planId"
          loading={loading}
          dataSource={plans}
          size="small"
          expandable={{
            expandedRowKeys: expanded,
            // 受控展开走 onExpandedRowsChange（参数是展开的 key 数组）；
            // onExpand 是「单行展开回调」，签名是 (expanded: boolean, record) —— 用错会拿到 boolean。
            onExpandedRowsChange: (keys) => void onExpand(keys),
            expandedRowRender: (plan) => {
              const coverages = coverageMap[plan.planId] ?? [];
              return (
                <Table<CoverageView>
                  rowKey="coverageId"
                  size="small"
                  dataSource={coverages}
                  pagination={false}
                  locale={{ emptyText: '该计划下还没有险种' }}
                  columns={[
                    { title: '险种编码', dataIndex: 'coverageCode', width: 160 },
                    { title: '险种名称', dataIndex: 'coverageName', width: 200 },
                    {
                      title: '险种额度',
                      dataIndex: 'coverageLimit',
                      width: 140,
                      render: (v: number) => (v > 0 ? `¥${v.toFixed(2)}` : '不限'),
                    },
                    {
                      title: '责任形式',
                      dataIndex: 'liabilityType',
                      width: 150,
                      render: (v: LiabilityType) =>
                        v === 'FIXED_AMOUNT' ? '定额给付' : v === 'ALLOWANCE' ? '津贴给付' : '费用补偿',
                    },
                    {
                      title: '状态',
                      dataIndex: 'status',
                      width: 100,
                      render: (v: ConfigStatus) => {
                        const meta = CONFIG_STATUS_META[v] ?? { color: 'default', label: v };
                        return <Tag color={meta.color}>{meta.label}</Tag>;
                      },
                    },
                    {
                      title: '操作',
                      width: 230,
                      render: (_, c) => (
                        <Space size={4}>
                          <AuthButton
                            code={PERMISSIONS.POLICY_WRITE}
                            type="link"
                            size="small"
                            icon={<EditOutlined />}
                            onClick={() => openCoverageEdit(c)}
                          >
                            编辑
                          </AuthButton>
                          <AuthButton
                            code={PERMISSIONS.POLICY_WRITE}
                            type="link"
                            size="small"
                            icon={<CloudUploadOutlined />}
                            disabled={c.status === 'ACTIVE'}
                            onClick={() =>
  confirm({
    title: '确认发布该险种？',
    content: '发布后险种生效，可参与理算；已受理案件按受理时快照不受影响。',
    onOk: () => publishCoverage(c),
  })
}
                          >
                            发布
                          </AuthButton>
                          <AuthButton
                            code={PERMISSIONS.POLICY_WRITE}
                            type="link"
                            size="small"
                            danger
                            icon={<StopOutlined />}
                            disabled={c.status === 'RETIRED'}
                            onClick={() =>
  confirm({
    title: '确认下线该险种？',
    content: '下线后险种停止生效，已受理案件按受理时快照不受影响。',
    onOk: () => retireCoverage(c),
  })
}
                          >
                            下线
                          </AuthButton>
                        </Space>
                      ),
                    },
                  ]}
                />
              );
            },
          }}
          columns={[
            { title: '计划编码', dataIndex: 'planCode', width: 180 },
            { title: '计划名称', dataIndex: 'planName', width: 220, ellipsis: true },
            {
              title: '计划额度',
              dataIndex: 'planLimit',
              width: 150,
              render: (v: number) => (v > 0 ? `¥${v.toFixed(2)}` : '不限'),
            },
            {
              title: '等待期',
              dataIndex: 'waitingPeriod',
              width: 100,
              render: (v: number) => (v > 0 ? `${v} 天` : '无'),
            },
            { title: '版本', dataIndex: 'versionNo', width: 90 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 110,
              render: (v: ConfigStatus) => {
                const meta = CONFIG_STATUS_META[v] ?? { color: 'default', label: v };
                return <Tag color={meta.color}>{meta.label}</Tag>;
              },
            },
            {
              title: '操作',
              width: 230,
              render: (_, r) => (
                <Space size={4}>
                  <AuthButton
                    code={PERMISSIONS.POLICY_WRITE}
                    type="link"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => openPlanEdit(r)}
                  >
                    编辑
                  </AuthButton>
                  <AuthButton
                    code={PERMISSIONS.POLICY_WRITE}
                    type="link"
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={() => openCreateCoverage(r)}
                  >
                    新增险种
                  </AuthButton>
                  <AuthButton
                    code={PERMISSIONS.POLICY_WRITE}
                    type="link"
                    size="small"
                    icon={<CloudUploadOutlined />}
                    disabled={r.status === 'ACTIVE'}
                    onClick={() =>
  confirm({
    title: '确认发布该计划？',
    content: '发布后计划生效，险种可挂接；已受理案件按受理时快照不受影响。',
    onOk: () => publishPlan(r),
  })
}
                  >
                    发布
                  </AuthButton>
                  <AuthButton
                    code={PERMISSIONS.POLICY_WRITE}
                    type="link"
                    size="small"
                    danger
                    icon={<StopOutlined />}
                    disabled={r.status === 'RETIRED'}
                    onClick={() =>
  confirm({
    title: '确认下线该计划？',
    content: '下线后计划停止生效，已受理案件按受理时快照不受影响。',
    onOk: () => retirePlan(r),
  })
}
                  >
                    下线
                  </AuthButton>
                </Space>
              ),
            },
          ]}
        />
        {!plans.length && !loading && (
          <Typography.Text type="secondary">
            该保单下还没有计划。新建计划后，方可在其下配置险种。
          </Typography.Text>
        )}
      </Card>

      <Modal
        title="新建计划"
        open={planModalOpen}
        onCancel={() => setPlanModalOpen(false)}
        onOk={() => void submitPlan()}
        confirmLoading={submitting}
        okText="创建"
        destroyOnHidden
      >
        <Form
          key="plan-create"
          form={planForm}
          layout="vertical"
          preserve={false}
          initialValues={planCreateInitialValues}
        >
          <Form.Item
            name="planCode"
            label="计划编码"
            rules={[{ required: true, message: '计划编码不能为空' }]}
          >
            <Input placeholder="保司侧产品编码，同一保单下唯一" />
          </Form.Item>
          <Form.Item name="planName" label="计划名称">
            <Input placeholder="选填" />
          </Form.Item>
          <Form.Item
            name="planLimit"
            label="计划额度（0 = 不限）"
            rules={[{ type: 'number', min: 0, message: '计划额度不能为负' }]}
          >
            <InputNumber min={0} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item
            name="waitingPeriod"
            label="等待期（天）"
            rules={[{ type: 'number', min: 0, message: '等待期不能为负' }]}
          >
            <InputNumber min={0} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="versionNo" label="计划版本">
            <Input placeholder="v1" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={currentPlan ? `新增险种（${currentPlan.planName || currentPlan.planCode}）` : '新增险种'}
        open={coverageModalOpen}
        onCancel={() => setCoverageModalOpen(false)}
        onOk={() => void submitCoverage()}
        confirmLoading={submitting}
        okText="创建"
        destroyOnHidden
      >
        <Form
          key="coverage-create"
          form={coverageForm}
          layout="vertical"
          preserve={false}
          initialValues={coverageCreateInitialValues}
        >
          <Form.Item
            name="coverageCode"
            label="险种编码"
            rules={[{ required: true, message: '险种编码不能为空' }]}
          >
            <Input placeholder="同一计划下唯一" />
          </Form.Item>
          <Form.Item name="coverageName" label="险种名称">
            <Input placeholder="选填" />
          </Form.Item>
          <Form.Item
            name="coverageLimit"
            label="险种额度（0 = 不限）"
            rules={[{ type: 'number', min: 0, message: '险种额度不能为负' }]}
          >
            <InputNumber min={0} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="liabilityType" label="责任形式">
            <Select
              options={[
                { value: 'REIMBURSEMENT', label: '费用补偿' },
                { value: 'FIXED_AMOUNT', label: '定额给付' },
                { value: 'ALLOWANCE', label: '津贴给付' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={planEditTarget ? `编辑计划（${planEditTarget.planName || planEditTarget.planCode}）` : '编辑计划'}
        open={planEditTarget !== null}
        onCancel={() => setPlanEditTarget(null)}
        onOk={() => void submitPlanEdit()}
        confirmLoading={submitting}
        okText="保存"
        destroyOnHidden
      >
        <Form
          key={planEditTarget?.planId ?? 'plan-edit-none'}
          form={planEditForm}
          layout="vertical"
          preserve={false}
          initialValues={planEditInitialValues}
        >
          <Form.Item name="planName" label="计划名称">
            <Input placeholder="选填" />
          </Form.Item>
          <Form.Item
            name="planLimit"
            label="计划额度（0 = 不限）"
            rules={[{ type: 'number', min: 0, message: '计划额度不能为负' }]}
          >
            <InputNumber min={0} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item
            name="waitingPeriod"
            label="等待期（天）"
            rules={[{ type: 'number', min: 0, message: '等待期不能为负' }]}
          >
            <InputNumber min={0} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="versionNo" label="计划版本">
            <Input placeholder="v1" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={coverageEditTarget ? `编辑险种（${coverageEditTarget.coverageName || coverageEditTarget.coverageCode}）` : '编辑险种'}
        open={coverageEditTarget !== null}
        onCancel={() => setCoverageEditTarget(null)}
        onOk={() => void submitCoverageEdit()}
        confirmLoading={submitting}
        okText="保存"
        destroyOnHidden
      >
        <Form
          key={coverageEditTarget?.coverageId ?? 'coverage-edit-none'}
          form={coverageEditForm}
          layout="vertical"
          preserve={false}
          initialValues={coverageEditInitialValues}
        >
          <Form.Item name="coverageName" label="险种名称">
            <Input placeholder="选填" />
          </Form.Item>
          <Form.Item
            name="coverageLimit"
            label="险种额度（0 = 不限）"
            rules={[{ type: 'number', min: 0, message: '险种额度不能为负' }]}
          >
            <InputNumber min={0} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="liabilityType" label="责任形式">
            <Select
              options={[
                { value: 'REIMBURSEMENT', label: '费用补偿' },
                { value: 'FIXED_AMOUNT', label: '定额给付' },
                { value: 'ALLOWANCE', label: '津贴给付' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <BatchImportModal
        open={planImportOpen}
        onClose={() => setPlanImportOpen(false)}
        title="批量导入计划"
        fileName="plan-template"
        columns={planImportColumns}
        defaults={{ policyNo }}
        buildRequest={buildPlanRequest}
        validateRow={validatePlanRow}
        createOne={(req) => claimConfigApi.createPlan(req)}
        onSuccess={() => void loadPlans()}
      />
    </div>
  );
}
