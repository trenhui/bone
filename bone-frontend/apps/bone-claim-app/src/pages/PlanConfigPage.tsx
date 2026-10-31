import { useCallback, useEffect, useState, type Key, type ReactElement } from 'react';
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
} from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import {
  CONFIG_STATUS_META,
  claimConfigApi,
  type ConfigStatus,
  type CoverageView,
  type LiabilityType,
  type PlanView,
  type PolicyView,
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
export function PlanConfigPage(): ReactElement {
  const { message } = AntApp.useApp();
  const [policies, setPolicies] = useState<PolicyView[]>([]);
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
  /** 险种按计划缓存（展开时才加载，避免一次性拉全量）。 */
  const [coverageMap, setCoverageMap] = useState<Record<string, CoverageView[]>>({});

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
    void loadPolicies();
  }, [loadPolicies]);

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
    planForm.resetFields();
    planForm.setFieldsValue({ planLimit: 0, waitingPeriod: 0, versionNo: 'v1' });
    setPlanModalOpen(true);
  };

  const submitPlan = async () => {
    const values = await planForm.validateFields();
    setSubmitting(true);
    try {
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
      if (resolveErrorCode(err)) {
        message.error(resolveErrorMessage(err, '计划创建失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openCreateCoverage = (plan: PlanView) => {
    setCurrentPlan(plan);
    coverageForm.resetFields();
    coverageForm.setFieldsValue({ coverageLimit: 0, liabilityType: 'REIMBURSEMENT' });
    setCoverageModalOpen(true);
  };

  const submitCoverage = async () => {
    const values = await coverageForm.validateFields();
    if (!currentPlan) return;
    setSubmitting(true);
    try {
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
      if (resolveErrorCode(err)) {
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

  const openPlanEdit = (plan: PlanView) => {
    setPlanEditTarget(plan);
    planEditForm.setFieldsValue({
      planName: plan.planName,
      planLimit: plan.planLimit,
      waitingPeriod: plan.waitingPeriod,
      versionNo: plan.versionNo,
    });
  };

  const submitPlanEdit = async () => {
    if (!planEditTarget) return;
    const values = await planEditForm.validateFields();
    setSubmitting(true);
    try {
      await claimConfigApi.updatePlan(planEditTarget.planId, {
        planName: values.planName?.trim(),
        planLimit: values.planLimit ?? 0,
        waitingPeriod: values.waitingPeriod ?? 0,
        versionNo: values.versionNo ?? 'v1',
      });
      message.success('计划已更新');
      setPlanEditTarget(null);
      await loadPlans();
    } catch (err) {
      if (resolveErrorCode(err)) {
        message.error(resolveErrorMessage(err, '计划更新失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openCoverageEdit = (coverage: CoverageView) => {
    setCoverageEditTarget(coverage);
    coverageEditForm.setFieldsValue({
      coverageName: coverage.coverageName,
      coverageLimit: coverage.coverageLimit,
      liabilityType: coverage.liabilityType,
    });
  };

  const submitCoverageEdit = async () => {
    if (!coverageEditTarget) return;
    const values = await coverageEditForm.validateFields();
    setSubmitting(true);
    try {
      await claimConfigApi.updateCoverage(coverageEditTarget.coverageId, {
        coverageName: values.coverageName?.trim(),
        coverageLimit: values.coverageLimit ?? 0,
        liabilityType: values.liabilityType ?? 'REIMBURSEMENT',
      });
      message.success('险种已更新');
      setCoverageEditTarget(null);
      await loadCoverages(coverageEditTarget.planId);
    } catch (err) {
      if (resolveErrorCode(err)) {
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
            code="claim:policy:write"
            type="primary"
            icon={<PlusOutlined />}
            disabled={!policyNo}
            onClick={openCreatePlan}
          >
            新建计划
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
                      width: 100,
                      render: (_, c) => (
                        <AuthButton
                          code="claim:policy:write"
                          type="link"
                          size="small"
                          icon={<EditOutlined />}
                          onClick={() => openCoverageEdit(c)}
                        >
                          编辑
                        </AuthButton>
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
                    code="claim:policy:write"
                    type="link"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => openPlanEdit(r)}
                  >
                    编辑
                  </AuthButton>
                  <AuthButton
                    code="claim:policy:write"
                    type="link"
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={() => openCreateCoverage(r)}
                  >
                    新增险种
                  </AuthButton>
                  <AuthButton
                    code="claim:policy:write"
                    type="link"
                    size="small"
                    icon={<CloudUploadOutlined />}
                    disabled={r.status === 'ACTIVE'}
                    onClick={() => void publishPlan(r)}
                  >
                    发布
                  </AuthButton>
                  <AuthButton
                    code="claim:policy:write"
                    type="link"
                    size="small"
                    danger
                    icon={<StopOutlined />}
                    disabled={r.status === 'RETIRED'}
                    onClick={() => void retirePlan(r)}
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
        <Form form={planForm} layout="vertical" preserve={false}>
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
        <Form form={coverageForm} layout="vertical" preserve={false}>
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
        <Form form={planEditForm} layout="vertical" preserve={false}>
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
        <Form form={coverageEditForm} layout="vertical" preserve={false}>
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
    </div>
  );
}
