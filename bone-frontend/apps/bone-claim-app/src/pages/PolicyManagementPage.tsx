import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SettingOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { AuthButton } from '@bone/ui';
import { useConfirmAction } from '../hooks/useConfirmAction';
import { PERMISSIONS } from '../constants/permissions';
import { ERROR_CODES } from '../constants/errorCodes';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import { InsurerSelect } from '../components/InsurerSelect';
import { BatchImportModal, type ImportColumn } from '../components/BatchImportModal';
import { FORM_VALIDATION_MESSAGE, isFormValidationError } from '../utils/formError';
import { csvFilename, downloadBlob } from '../utils/downloadBlob';
import {
  POLICY_CONFIG_STATUS_META,
  claimConfigApi,
  parseInsuredSnapshot,
  policyTypeLabel,
  toTotal,
  type CreatePolicyReq,
  type PolicyConfigStatus,
  type PolicyView,
} from '../services/claimConfigApi';

/**
 * 保单管理（配置面）。
 *
 * <p><b>为什么单独一页而不是塞进「理赔申请」</b>：保单是<b>配置</b>（影响所有未来赔案的口径），
 * 赔案是<b>业务数据</b>。参考实现 pk-tpa-saas 也是分开的两层（团险保单列表 / 保单规则配置），
 * 本页对齐该信息架构。
 *
 * <p><b>被保人清单只存假名化 ID</b>：本页展示的是服务端 HMAC 后的 ID（与额度账户的
 * {@code insured_id_hash} 同一口径），库里不落明文证件——页面因此也拿不到、也不需要明文。
 */
/** 保单批量导入列定义（模板表头用英文键，避免中文编码坑）。 */
const policyImportColumns: ImportColumn[] = [
  { key: 'policyNo', label: '保单号', required: true, example: 'POL-2026-0001' },
  { key: 'policyName', label: '保单名称', example: '2026团体门诊险' },
  { key: 'insurerName', label: '保险公司', example: '某保险公司' },
  { key: 'branchName', label: '保险分公司', example: '华东分公司' },
  { key: 'insureName', label: '投保公司', example: '某科技有限公司' },
  { key: 'bizIdentityCode', label: '业务主体码', example: 'BIZ-001' },
  { key: 'productType', label: '产品类型', example: 'OUTPATIENT' },
  { key: 'policyType', label: '保单类型(GROUP/INDIVIDUAL,空为团体)', example: 'GROUP' },
  { key: 'effectiveStart', label: '生效起(ISO时间)', required: true, example: '2026-01-01T00:00:00.000Z' },
  { key: 'effectiveEnd', label: '生效止(ISO时间)', required: true, example: '2026-12-31T23:59:59.000Z' },
  { key: 'waitingPeriod', label: '等待期(天)', example: '0' },
  { key: 'policyVersion', label: '保单版本', example: 'v1' },
  { key: 'insuredHashes', label: '被保人假名ID(逗号分隔)', example: 'hash-001,hash-002' },
];

function buildPolicyRequest(row: Record<string, string>): CreatePolicyReq | null {
  const insured = (row.insuredHashes ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  return {
    policyNo: row.policyNo.trim(),
    policyName: row.policyName?.trim() || undefined,
    insurerName: row.insurerName?.trim() || undefined,
    branchName: row.branchName?.trim() || undefined,
    insureName: row.insureName?.trim() || undefined,
    bizIdentityCode: row.bizIdentityCode?.trim() || undefined,
    productType: row.productType?.trim() || undefined,
    policyType: row.policyType?.trim() || undefined,
    effectiveStart: row.effectiveStart.trim(),
    effectiveEnd: row.effectiveEnd.trim(),
    waitingPeriod: row.waitingPeriod ? Number(row.waitingPeriod) : 0,
    policyVersion: row.policyVersion?.trim() || 'v1',
    insuredSnapshot: insured.length ? JSON.stringify(insured) : undefined,
  };
}

function validatePolicyRow(row: Record<string, string>): string | null {
  if (!row.policyNo?.trim()) return '保单号不能为空';
  if (!row.effectiveStart?.trim()) return '生效起不能为空';
  if (!row.effectiveEnd?.trim()) return '生效止不能为空';
  return null;
}

export function PolicyManagementPage(): ReactElement {
  const { message } = AntApp.useApp();
  const confirm = useConfirmAction();
  const navigate = useNavigate();
  const [rows, setRows] = useState<PolicyView[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [configStatus, setConfigStatus] = useState<PolicyConfigStatus | undefined>(undefined);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [editTarget, setEditTarget] = useState<PolicyView | null>(null);
  const [editForm] = Form.useForm();
  const [importOpen, setImportOpen] = useState(false);
  /** 正在执行启用/停用的保单号（行级 loading，防重复提交）。 */
  const [processingKey, setProcessingKey] = useState('');
  /** 详情抽屉：正在查看的保单号（null 关闭）。 */
  const [detailPolicyNo, setDetailPolicyNo] = useState<string | null>(null);
  /** 详情数据（走 getPolicy 单查接口，含列表不展示的全字段）。 */
  const [detail, setDetail] = useState<PolicyView | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [exporting, setExporting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await claimConfigApi.listPolicies({
        keyword: keyword.trim() || undefined,
        configStatus,
        page,
        size: pageSize,
      });
      setRows(res.data?.records ?? []);
      setTotal(toTotal(res.data));
    } catch (err) {
      message.error(resolveErrorMessage(err, '保单列表加载失败'));
    } finally {
      setLoading(false);
    }
  }, [keyword, configStatus, page, pageSize, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    // 注意：这里**不能**再调 form.setFieldsValue(...)。
    // Modal 此刻还没打开、Form 还没挂载，antd 的 useForm 实例未连接到任何 Form 元素，
    // setFieldsValue 会**静默失效** ⇒ 弹窗打开是一张空表单（连默认承保期间都没有），
    // 用户点「保存」才在必填校验上炸掉。改为把初值交给 Form 的 initialValues（挂载即生效）。
    setModalOpen(true);
  };

  /** 新建表单初值（Modal 打开时挂载 ⇒ 一次到位；不缓存，避免跨天取到过期承保期间）。 */
  const createInitialValues = {
    waitingPeriod: 0,
    policyVersion: 'v1',
    effectiveStart: dayjs(),
    effectiveEnd: dayjs().add(1, 'year'),
  };

  const submit = async () => {
    setSubmitting(true);
    try {
      const values = await form.validateFields();
      // 被保人是逗号分隔的假名化 ID 文本 → 后端要 JSON 数组串
      const insured = String(values.insuredHashes ?? '')
        .split(',')
        .map((v: string) => v.trim())
        .filter(Boolean);
      await claimConfigApi.createPolicy({
        policyNo: values.policyNo.trim(),
        policyName: values.policyName?.trim(),
        insurerName: values.insurerName?.trim(),
        branchName: values.branchName?.trim(),
        insureName: values.insureName?.trim(),
        policyType: values.policyType,
        bizIdentityCode: values.bizIdentityCode?.trim(),
        productType: values.productType?.trim(),
        effectiveStart: values.effectiveStart.toISOString(),
        effectiveEnd: values.effectiveEnd.toISOString(),
        waitingPeriod: values.waitingPeriod ?? 0,
        policyVersion: values.policyVersion ?? 'v1',
        insuredSnapshot: JSON.stringify(insured),
      });
      message.success('保单已创建（规则开关为「未启用」，需另行启用后才接受报案）');
      setModalOpen(false);
      setPage(1);
      await load();
    } catch (err) {
      if (isFormValidationError(err)) {
        message.error(FORM_VALIDATION_MESSAGE);
      } else {
        // 兜底：任何异常都要有提示，绝不静默（原实现只在有 errorCode 时提示 ⇒ 其余全静默）
        message.error(resolveErrorMessage(err, '保单创建失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const toggle = (row: PolicyView, action: 'activate' | 'pause') => {
    const activating = action === 'activate';
    confirm({
      title: activating ? '启用保单责任' : '停用保单责任',
      content: activating
        ? `确认启用保单「${row.policyNo}」？启用后该保单将接受新报案。`
        : `确认停用保单「${row.policyNo}」？停用后该保单将不再接受新报案（存量赔案不受影响）。`,
      okText: activating ? '确认启用' : '确认停用',
      danger: !activating,
      onOk: () => {
        setProcessingKey(row.policyNo);
        const call = activating
          ? claimConfigApi.activatePolicy(row.policyNo)
          : claimConfigApi.pausePolicy(row.policyNo);
        return call
          .then(() => {
            message.success(activating ? '保单责任已启用' : '保单责任已停用');
            return load();
          })
          .catch((err) => {
            message.error(resolveErrorMessage(err, activating ? '启用失败' : '停用失败'));
            throw err;
          })
          .finally(() => setProcessingKey(''));
      },
    });
  };

  const openEdit = (row: PolicyView) => {
    // 同 openCreate：Modal 未打开时 setFieldsValue 会静默失效（Form 还没挂载），
    // 初值改由 Form 的 initialValues 承担，见下方 policyEditInitial。
    setEditTarget(row);
  };

  /** 打开详情抽屉：重新单查一次（列表数据可能已过期，详情要显示当前全量字段）。 */
  const openDetail = async (row: PolicyView) => {
    setDetailPolicyNo(row.policyNo);
    setDetail(row); // 先用列表行兜底，避免抽屉闪空白
    setDetailLoading(true);
    try {
      const res = await claimConfigApi.getPolicy(row.policyNo);
      setDetail(res.data ?? row);
    } catch (err) {
      message.error(resolveErrorMessage(err, '保单详情加载失败'));
      setDetailPolicyNo(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const exportCsv = async () => {
    setExporting(true);
    try {
      const blob = await claimConfigApi.exportPolicies({
        keyword: keyword.trim() || undefined,
        configStatus,
      });
      downloadBlob(blob, csvFilename('policies'));
    } catch (err) {
      message.error(resolveErrorMessage(err, '导出失败'));
    } finally {
      setExporting(false);
    }
  };

  /** 编辑表单初值：按被编辑保单派生，配合 <Form key=...> 在挂载时一次性灌入。 */
  const policyEditInitial = useMemo(
    () =>
      editTarget
        ? {
          policyName: editTarget.policyName,
          insurerName: editTarget.insurerName,
          branchName: editTarget.branchName,
          insureName: editTarget.insureName,
          policyType: editTarget.policyType || 'GROUP',
          bizIdentityCode: editTarget.bizIdentityCode,
          productType: editTarget.productType,
          waitingPeriod: editTarget.waitingPeriod,
          policyVersion: editTarget.policyVersion,
          effectiveStart: dayjs(editTarget.effectiveStart),
          effectiveEnd: dayjs(editTarget.effectiveEnd),
          insuredHashes: parseInsuredSnapshot(editTarget.insuredSnapshot).join(','),
          // 作业开关（0094）：历史数据可能为空，按 NEED 兜底与后端口径一致
          docClaimApply: editTarget.docClaimApply ?? 'NEED',
          docClaimNotice: editTarget.docClaimNotice ?? 'NEED',
        }
        : undefined,
    [editTarget],
  );

  const submitEdit = async () => {
    if (!editTarget) return;
    setSubmitting(true);
    try {
      const values = await editForm.validateFields();
      const insured = String(values.insuredHashes ?? '')
        .split(',')
        .map((v: string) => v.trim())
        .filter(Boolean);
      await claimConfigApi.updatePolicy(editTarget.policyNo, {
        policyName: values.policyName?.trim(),
        insurerName: values.insurerName?.trim(),
        branchName: values.branchName?.trim(),
        insureName: values.insureName?.trim(),
        policyType: values.policyType,
        bizIdentityCode: values.bizIdentityCode?.trim(),
        productType: values.productType?.trim(),
        effectiveStart: values.effectiveStart.toISOString(),
        effectiveEnd: values.effectiveEnd.toISOString(),
        waitingPeriod: values.waitingPeriod ?? 0,
        policyVersion: values.policyVersion ?? 'v1',
        insuredSnapshot: JSON.stringify(insured),
        docClaimApply: values.docClaimApply,
        docClaimNotice: values.docClaimNotice,
        version: editTarget.version,
      });
      message.success('保单已更新');
      setEditTarget(null);
      await load();
    } catch (err) {
      if (isFormValidationError(err)) {
        message.error(FORM_VALIDATION_MESSAGE);
        return;
      }
      const code = resolveErrorCode(err);
      if (code === ERROR_CODES.CONCURRENT_CONFLICT) {
        // 乐观锁冲突：编辑期间被他人改过。关闭弹窗并刷新，提示用户重新打开编辑。
        setEditTarget(null);
        await load();
        message.warning('该保单已被其他人修改，请重新打开编辑并保存');
      } else {
        // 兜底：启用中改承保期间等业务错误走这里；未知异常也必须有提示，绝不静默。
        message.error(resolveErrorMessage(err, '保单更新失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="保单管理"
        description="维护保单的承保期间、规则开关与被保人清单。规则启用后，该保单方可接受新报案。"
      />

      <Card>
        <Space style={{ marginBottom: 16 }} wrap>
          <Input.Search
            placeholder="保单号 / 名称"
            allowClear
            style={{ width: 240 }}
            onSearch={(v) => {
              setKeyword(v);
              setPage(1);
            }}
          />
          <Select
            placeholder="规则开关"
            allowClear
            style={{ width: 160 }}
            value={configStatus}
            onChange={(v) => {
              setConfigStatus(v);
              setPage(1);
            }}
            options={Object.entries(POLICY_CONFIG_STATUS_META).map(([value, meta]) => ({
              value,
              label: meta.label,
            }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            刷新
          </Button>
          <AuthButton
            code={PERMISSIONS.POLICY_READ}
            icon={<DownloadOutlined />}
            loading={exporting}
            onClick={() => void exportCsv()}
          >
            导出 CSV
          </AuthButton>
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreate}
          >
            新建保单
          </AuthButton>
          <AuthButton
            code={PERMISSIONS.POLICY_WRITE}
            icon={<UploadOutlined />}
            onClick={() => setImportOpen(true)}
          >
            批量导入
          </AuthButton>
        </Space>

        <Table<PolicyView>
          rowKey="policyNo"
          loading={loading}
          dataSource={rows}
          size="small"
          scroll={{ x: 1400 }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            // 翻页走 pagination.onChange 而非 table.onChange：后者的第二参是 filters
            // （Record<string, FilterValue|null>），在有过滤列的表里类型会漂，
            // 写 `p.current` 拿到的是 filters 而不是分页对象。
            onChange: (p, s) => {
              setPage(p ?? 1);
              setPageSize(s ?? 10);
            },
          }}
          columns={[
            { title: '保单号', dataIndex: 'policyNo', width: 160, fixed: 'left' },
            { title: '保单名称', dataIndex: 'policyName', width: 180, ellipsis: true },
            {
              title: '保单类型',
              dataIndex: 'policyType',
              width: 90,
              render: (v: string) => {
                const meta = policyTypeLabel(v);
                return <Tag color={meta.color}>{meta.label}</Tag>;
              },
            },
            { title: '保险公司', dataIndex: 'insurerName', width: 140, ellipsis: true },
            { title: '分公司', dataIndex: 'branchName', width: 140, ellipsis: true },
            { title: '投保公司', dataIndex: 'insureName', width: 160, ellipsis: true },
            {
              title: '承保期间',
              width: 200,
              render: (_, r) =>
                `${dayjs(r.effectiveStart).format('YYYY-MM-DD')} ~ ${dayjs(r.effectiveEnd).format('YYYY-MM-DD')}`,
            },
            {
              title: '等待期',
              dataIndex: 'waitingPeriod',
              width: 90,
              render: (v: number) => (v > 0 ? `${v} 天` : '无'),
            },
            {
              title: '被保人',
              width: 240,
              render: (_, r) => {
                const list = parseInsuredSnapshot(r.insuredSnapshot);
                // 个人保单 = 单人单账户：多于一人只可能是保单类型标错或名单串单，
                // 后果是责任额度池开到不存在的人头上。列表上直接标红，别等到发布责任才被后端拦。
                const individual = (r.policyType || 'GROUP') === 'INDIVIDUAL';
                const mismatch = individual && list.length > 1;
                if (list.length === 0) {
                  return <Typography.Text type="secondary">未录入</Typography.Text>;
                }
                return (
                  <Typography.Text
                    type={mismatch ? 'danger' : undefined}
                    ellipsis={{ tooltip: mismatch ? `${list.join('、')}（个人保单应为 1 人）` : list.join('、') }}
                  >
                    {list.length} 人（{list[0]}
                    {list.length > 1 ? ' 等' : ''}）
                    {mismatch ? ' · 个人保单应仅 1 人' : ''}
                  </Typography.Text>
                );
              },
            },
            {
              title: '规则开关',
              dataIndex: 'configStatus',
              width: 110,
              render: (v: PolicyConfigStatus) => {
                const meta = POLICY_CONFIG_STATUS_META[v] ?? {
                  color: 'default',
                  label: v,
                };
                return <Tag color={meta.color}>{meta.label}</Tag>;
              },
            },
            {
              title: '操作',
              width: 220,
              fixed: 'right',
              render: (_, r) => (
                <Space size={4}>
                  <AuthButton
                    code={PERMISSIONS.POLICY_READ}
                    type="link"
                    size="small"
                    icon={<EyeOutlined />}
                    onClick={() => void openDetail(r)}
                  >
                    详情
                  </AuthButton>
                  {/* 层级下钻：保单 → 计划配置（配置面第一层：计划 → 险种 → 责任 → 额度）。
                      计划/险种/责任强依附保单（0093 已撤下其独立菜单），「配置」按钮是
                      唯一入口：这里把保单上下文带进去，后续层级在各自页面内逐级下钻
                      （/plan 计划行「责任配置」→ /liability?policyNo=&planId=）。 */}
                  <AuthButton
                    code={PERMISSIONS.POLICY_READ}
                    type="link"
                    size="small"
                    icon={<SettingOutlined />}
                    onClick={() => navigate(`/plan?policyNo=${encodeURIComponent(r.policyNo)}`)}
                  >
                    配置
                  </AuthButton>
                  <AuthButton
                    code={PERMISSIONS.POLICY_WRITE}
                    type="link"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => openEdit(r)}
                  >
                    编辑
                  </AuthButton>
                  <AuthButton
                    code={PERMISSIONS.POLICY_WRITE}
                    type="link"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    disabled={r.configStatus === 'ACTIVE'}
                    loading={processingKey === r.policyNo}
                    onClick={() => toggle(r, 'activate')}
                  >
                    启用
                  </AuthButton>
                  <AuthButton
                    code={PERMISSIONS.POLICY_WRITE}
                    type="link"
                    size="small"
                    danger
                    icon={<PauseCircleOutlined />}
                    disabled={r.configStatus === 'PAUSE'}
                    loading={processingKey === r.policyNo}
                    onClick={() => toggle(r, 'pause')}
                  >
                    停用
                  </AuthButton>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title="新建保单"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => void submit()}
        confirmLoading={submitting}
        okText="创建"
        width={720}
        destroyOnHidden
      >
        <Form
          key="policy-create"
          form={form}
          layout="vertical"
          preserve={false}
          initialValues={createInitialValues}
        >
          <Space size="middle" wrap style={{ width: '100%' }}>
            <Form.Item
              name="policyNo"
              label="保单号"
              rules={[{ required: true, message: '保单号不能为空' }]}
              style={{ width: 220 }}
            >
              <Input placeholder="如 POL-2026-0001" />
            </Form.Item>
            <Form.Item name="policyName" label="保单名称" style={{ width: 260 }}>
              <Input placeholder="选填" />
            </Form.Item>
            <Form.Item name="policyVersion" label="保单版本" style={{ width: 140 }}>
              <Input placeholder="v1" />
            </Form.Item>
          </Space>
          <Space size="middle" wrap style={{ width: '100%' }}>
            <Form.Item
              name="bizIdentityCode"
              label="承保主体（保险公司）"
              style={{ width: 260 }}
              tooltip="从受控承保主体中选择；保单通过业务主体码引用，避免同一家保司写成多种名称导致映射分裂"
            >
              <InsurerSelect
                style={{ width: '100%' }}
                onChange={(_code, name) => form.setFieldsValue({ insurerName: name })}
              />
            </Form.Item>
            <Form.Item name="insurerName" hidden>
              <Input />
            </Form.Item>
            <Form.Item name="branchName" label="保险分公司" style={{ width: 220 }}>
              <Input placeholder="选填" />
            </Form.Item>
            <Form.Item name="insureName" label="投保公司" style={{ width: 220 }}>
              <Input placeholder="选填" />
            </Form.Item>
          </Space>
          <Space size="middle" wrap style={{ width: '100%' }}>

            <Form.Item
              name="policyType"
              label="保单类型"
              style={{ width: 220 }}
              initialValue="GROUP"
              tooltip="团体：企业/机构投保，多名被保人共享责任与额度池；个人：自然人投保，单人单账户"
            >
              <Radio.Group
                options={[
                  { label: '团体', value: 'GROUP' },
                  { label: '个人', value: 'INDIVIDUAL' },
                ]}
              />
            </Form.Item>
            <Form.Item name="productType" label="产品类型" style={{ width: 220 }}>
              <Input placeholder="如 OUTPATIENT" />
            </Form.Item>
            <Form.Item
              name="waitingPeriod"
              label="等待期（天）"
              style={{ width: 180 }}
              rules={[{ type: 'number', min: 0, message: '等待期不能为负' }]}
            >
              <InputNumber min={0} style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Space size="middle" wrap style={{ width: '100%' }}>
            <Form.Item
              name="effectiveStart"
              label="生效起"
              rules={[{ required: true, message: '生效起不能为空' }]}
              style={{ width: 220 }}
            >
              <DatePicker style={{ width: 200 }} />
            </Form.Item>
            <Form.Item
              name="effectiveEnd"
              label="生效止"
              rules={[{ required: true, message: '生效止不能为空' }]}
              style={{ width: 220 }}
            >
              <DatePicker style={{ width: 200 }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="insuredHashes"
            label="被保人假名化 ID"
            tooltip="填服务端计算出的假名化被保人标识（与额度账户的 insured_id_hash 同口径）；库里不落明文证件"
            extra="多个用逗号分隔。留空表示该保单暂未录入被保人，此时任何报案都会被「被保人不匹配」拒收。"
          >
            <Input placeholder="如 e2e-insured-hash-0001,e2e-insured-hash-0002" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editTarget ? `编辑保单（${editTarget.policyNo}）` : '编辑保单'}
        open={editTarget !== null}
        onCancel={() => setEditTarget(null)}
        onOk={() => void submitEdit()}
        confirmLoading={submitting}
        okText="保存"
        width={720}
        destroyOnHidden
      >
        <Form
          key={editTarget?.policyNo ?? 'policy-edit-none'}
          form={editForm}
          layout="vertical"
          preserve={false}
          initialValues={policyEditInitial}
        >
          <Space size="middle" wrap style={{ width: '100%' }}>
            <Form.Item name="policyName" label="保单名称" style={{ width: 260 }}>
              <Input placeholder="选填" />
            </Form.Item>
            <Form.Item name="policyVersion" label="保单版本" style={{ width: 140 }}>
              <Input placeholder="v1" />
            </Form.Item>
          </Space>
          <Space size="middle" wrap style={{ width: '100%' }}>
            <Form.Item
              name="bizIdentityCode"
              label="承保主体（保险公司）"
              style={{ width: 260 }}
              tooltip="从受控承保主体中选择；保单通过业务主体码引用，避免同一家保司写成多种名称导致映射分裂"
            >
              <InsurerSelect
                style={{ width: '100%' }}
                onChange={(_code, name) => form.setFieldsValue({ insurerName: name })}
              />
            </Form.Item>
            <Form.Item name="insurerName" hidden>
              <Input />
            </Form.Item>
            <Form.Item name="branchName" label="保险分公司" style={{ width: 220 }}>
              <Input placeholder="选填" />
            </Form.Item>
            <Form.Item name="insureName" label="投保公司" style={{ width: 220 }}>
              <Input placeholder="选填" />
            </Form.Item>
          </Space>
          <Space size="middle" wrap style={{ width: '100%' }}>

            <Form.Item
              name="policyType"
              label="保单类型"
              style={{ width: 220 }}
              initialValue="GROUP"
              tooltip="团体：企业/机构投保，多名被保人共享责任与额度池；个人：自然人投保，单人单账户"
            >
              <Radio.Group
                options={[
                  { label: '团体', value: 'GROUP' },
                  { label: '个人', value: 'INDIVIDUAL' },
                ]}
              />
            </Form.Item>
            <Form.Item name="productType" label="产品类型" style={{ width: 220 }}>
              <Input placeholder="如 OUTPATIENT" />
            </Form.Item>
            <Form.Item
              name="waitingPeriod"
              label="等待期（天）"
              style={{ width: 180 }}
              rules={[{ type: 'number', min: 0, message: '等待期不能为负' }]}
            >
              <InputNumber min={0} style={{ width: 160 }} />
            </Form.Item>
          </Space>
          <Space size="middle" wrap style={{ width: '100%' }}>
            <Form.Item
              name="effectiveStart"
              label="生效起"
              rules={[{ required: true, message: '生效起不能为空' }]}
              style={{ width: 220 }}
            >
              <DatePicker style={{ width: 200 }} disabled={editTarget?.configStatus === 'ACTIVE'} />
            </Form.Item>
            <Form.Item
              name="effectiveEnd"
              label="生效止"
              rules={[{ required: true, message: '生效止不能为空' }]}
              style={{ width: 220 }}
            >
              <DatePicker
                style={{ width: 200 }}
                disabled={editTarget?.configStatus === 'ACTIVE'}
              />
            </Form.Item>
          </Space>
          <Space size="middle" wrap style={{ width: '100%' }}>
            <Form.Item
              name="docClaimApply"
              label="理赔申请书"
              tooltip="作业开关：是否为该保单的赔案自动生成「理赔申请书」单证（部分线上自助理赔渠道无需申请书）"
              style={{ width: 220 }}
            >
              <Select
                options={[
                  { value: 'NEED', label: '需生成（NEED）' },
                  { value: 'NOT_NEED', label: '无需生成（NOT_NEED）' },
                ]}
              />
            </Form.Item>
            <Form.Item
              name="docClaimNotice"
              label="理赔通知单"
              tooltip="作业开关：是否为该保单的赔案自动生成「理赔通知单」单证"
              style={{ width: 220 }}
            >
              <Select
                options={[
                  { value: 'NEED', label: '需生成（NEED）' },
                  { value: 'NOT_NEED', label: '无需生成（NOT_NEED）' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item
            name="insuredHashes"
            label="被保人假名化 ID"
            tooltip="填服务端计算出的假名化被保人标识（与额度账户的 insured_id_hash 同口径）；库里不落明文证件"
            extra="多个用逗号分隔。留空表示该保单暂未录入被保人，此时任何报案都会被「被保人不匹配」拒收。"
          >
            <Input placeholder="如 e2e-insured-hash-0001,e2e-insured-hash-0002" />
          </Form.Item>
          {editTarget?.configStatus === 'ACTIVE' && (
            <Typography.Paragraph type="warning">
              保单当前为「启用中」，承保期间已锁定，如需修改生效起止请先停用。
            </Typography.Paragraph>
          )}
        </Form>
      </Modal>

      <Drawer
        title={detailPolicyNo ? `保单详情（${detailPolicyNo}）` : '保单详情'}
        open={detailPolicyNo !== null}
        onClose={() => setDetailPolicyNo(null)}
        width={640}
      >
        {detailLoading && !detail ? (
          <Spin />
        ) : (
          detail && (
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="保单号">{detail.policyNo}</Descriptions.Item>
              <Descriptions.Item label="保单名称">
                {detail.policyName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="规则开关">
                <Tag
                  color={POLICY_CONFIG_STATUS_META[detail.configStatus]?.color ?? 'default'}
                >
                  {POLICY_CONFIG_STATUS_META[detail.configStatus]?.label ?? detail.configStatus}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="保险公司">
                {detail.insurerName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="保险分公司">
                {detail.branchName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="投保公司">
                {detail.insureName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="业务主体码">
                {detail.bizIdentityCode || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="产品类型">
                {detail.productType || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="保单类型">
                {(() => {
                  const meta = policyTypeLabel(detail.policyType);
                  return <Tag color={meta.color}>{meta.label}</Tag>;
                })()}
              </Descriptions.Item>
              <Descriptions.Item label="承保期间">
                {dayjs(detail.effectiveStart).format('YYYY-MM-DD HH:mm')} ~{' '}
                {dayjs(detail.effectiveEnd).format('YYYY-MM-DD HH:mm')}
              </Descriptions.Item>
              <Descriptions.Item label="等待期">
                {detail.waitingPeriod > 0 ? `${detail.waitingPeriod} 天` : '无'}
              </Descriptions.Item>
              <Descriptions.Item label="保单版本">
                {detail.policyVersion || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {detail.updatedAt ? dayjs(detail.updatedAt).format('YYYY-MM-DD HH:mm:ss') : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {detail.createdAt ? dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm:ss') : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="被保人清单（假名化 ID）">
                {parseInsuredSnapshot(detail.insuredSnapshot).length ? (
                  <Typography.Paragraph style={{ marginBottom: 0 }} copyable>
                    {parseInsuredSnapshot(detail.insuredSnapshot).join(',')}
                  </Typography.Paragraph>
                ) : (
                  '未录入'
                )}
              </Descriptions.Item>
            </Descriptions>
          )
        )}
      </Drawer>

      <BatchImportModal
        open={importOpen}
        onClose={() => setImportOpen(false)}
        title="批量导入保单"
        fileName="policy-template"
        columns={policyImportColumns}
        buildRequest={buildPolicyRequest}
        validateRow={validatePolicyRow}
        createOne={(req) => claimConfigApi.createPolicy(req)}
        onSuccess={() => void load()}
      />
    </div>
  );
}
