import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Drawer,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import {
  EditOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { AuthButton } from '@bone/ui';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import {
  QUOTA_CHANGE_TYPE_META,
  claimConfigApi,
  type PolicyView,
  type QuotaAccountView,
  type QuotaChangeView,
  type QuotaType,
} from '../services/claimConfigApi';

/**
 * 额度管理（配置面）—— 个人额度的开户 / 调额 / 减人 + 保全记录。
 *
 * <p><b>为什么与「资金工作台」分开</b>：资金工作台是<b>理赔作业面</b>的只读视图
 * （"这件赔案占了多少额度"），本页是<b>运营配置面</b>的读写（"这个被保人有多少额度、保额改了多少"）。
 * 两者读同一张表，但权限与风险面不同：作业面误操作只影响一件赔案，配置面误操作影响该被保人<b>所有</b>赔案。
 *
 * <p><b>调额下界</b>：下调后的初始额度不得低于已占用额度（已赔 + 已冻结），否则
 * {@code available = initial − claimed − frozen} 变负、守恒被破坏、后续占额全被拦下。
 * 后端强制此约束，前端在提交前给出同口径提示（前端提示只为省一次往返，<b>判定权在服务端</b>）。
 */
export function QuotaConfigPage(): ReactElement {
  const { message } = AntApp.useApp();
  const [policies, setPolicies] = useState<PolicyView[]>([]);
  const [policyNo, setPolicyNo] = useState<string>('');
  const [accounts, setAccounts] = useState<QuotaAccountView[]>([]);
  const [loading, setLoading] = useState(false);
  const [openModalOpen, setOpenModalOpen] = useState(false);
  const [adjustTarget, setAdjustTarget] = useState<QuotaAccountView | null>(null);
  const [changesOf, setChangesOf] = useState<QuotaAccountView | null>(null);
  const [changes, setChanges] = useState<QuotaChangeView[]>([]);
  const [changesLoading, setChangesLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [openForm] = Form.useForm();
  const [adjustForm] = Form.useForm();

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

  const loadAccounts = useCallback(async () => {
    if (!policyNo) {
      setAccounts([]);
      return;
    }
    setLoading(true);
    try {
      const res = await claimConfigApi.listQuotaAccounts(policyNo);
      setAccounts(res.data ?? []);
    } catch (err) {
      message.error(resolveErrorMessage(err, '额度账户加载失败'));
    } finally {
      setLoading(false);
    }
  }, [policyNo, message]);

  useEffect(() => {
    void loadPolicies();
  }, [loadPolicies]);

  useEffect(() => {
    void loadAccounts();
  }, [loadAccounts]);

  const openCreate = () => {
    openForm.resetFields();
    openForm.setFieldsValue({
      quotaYear: new Date().getFullYear(),
      quotaType: 'PERSONAL',
      quotaInitial: 0,
    });
    setOpenModalOpen(true);
  };

  const submitOpen = async () => {
    const values = await openForm.validateFields();
    setSubmitting(true);
    try {
      await claimConfigApi.openQuotaAccount({
        insuredIdHash: values.insuredIdHash.trim(),
        policyNo,
        planId: String(values.planId),
        quotaYear: values.quotaYear,
        quotaType: values.quotaType ?? 'PERSONAL',
        liabilityUuid: values.liabilityUuid?.trim(),
        quotaInitial: values.quotaInitial ?? 0,
        remark: values.remark?.trim(),
      });
      message.success('额度账户已开户');
      setOpenModalOpen(false);
      await loadAccounts();
    } catch (err) {
      if (resolveErrorCode(err)) {
        message.error(resolveErrorMessage(err, '开户失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openAdjust = (row: QuotaAccountView) => {
    setAdjustTarget(row);
    adjustForm.resetFields();
    adjustForm.setFieldsValue({ newInitial: row.initial, remark: '' });
  };

  const submitAdjust = async () => {
    if (!adjustTarget) return;
    const values = await adjustForm.validateFields();
    setSubmitting(true);
    try {
      await claimConfigApi.adjustQuota(adjustTarget.quotaAccountId, {
        newInitial: values.newInitial,
        remark: values.remark?.trim(),
      });
      message.success('额度已调整');
      setAdjustTarget(null);
      await loadAccounts();
    } catch (err) {
      if (resolveErrorCode(err)) {
        message.error(resolveErrorMessage(err, '调额失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const toggleRemoved = async (row: QuotaAccountView, action: 'remove' | 'restore') => {
    try {
      if (action === 'remove') {
        await claimConfigApi.removeInsured(row.quotaAccountId, {});
        message.success('已减人（该被保人不再参与额度累计）');
      } else {
        await claimConfigApi.restoreInsured(row.quotaAccountId, {});
        message.success('已恢复');
      }
      await loadAccounts();
    } catch (err) {
      message.error(resolveErrorMessage(err, action === 'remove' ? '减人失败' : '恢复失败'));
    }
  };

  const openChanges = async (row: QuotaAccountView) => {
    setChangesOf(row);
    setChangesLoading(true);
    try {
      const res = await claimConfigApi.listQuotaChanges(row.quotaAccountId);
      setChanges(res.data ?? []);
    } catch (err) {
      message.error(resolveErrorMessage(err, '保全记录加载失败'));
    } finally {
      setChangesLoading(false);
    }
  };

  const totals = accounts.reduce(
    (acc, a) => ({
      initial: acc.initial + (a.initial || 0),
      claimed: acc.claimed + (a.claimed || 0),
      frozen: acc.frozen + (a.frozen || 0),
      available: acc.available + (a.available || 0),
    }),
    { initial: 0, claimed: 0, frozen: 0, available: 0 },
  );

  return (
    <div>
      <PageHeader
        title="额度管理"
        description="维护被保人的个人额度：开户、调额、减人；每次变更均留痕（保全记录）。"
      />

      <Card>
        {/* 工具条与统计卡分两行：同一行时 antd 的 Space wrap 会把「开户」按钮挤到
            统计卡文字上（实测截图确认过），而统计卡是不可点的展示区、被按钮压住会读不清。
            分行后各行独立对齐，窄屏也不会互相遮挡。 */}
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="选择保单"
            style={{ width: 300 }}
            value={policyNo || undefined}
            onChange={(v) => setPolicyNo(v)}
            options={policies.map((p) => ({
              value: p.policyNo,
              label: `${p.policyNo}${p.policyName ? ` · ${p.policyName}` : ''}`,
            }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void loadAccounts()}>
            刷新
          </Button>
          <AuthButton
            code="claim:quota:write"
            type="primary"
            icon={<PlusOutlined />}
            disabled={!policyNo}
            onClick={openCreate}
          >
            开户
          </AuthButton>
        </Space>

        <Space size="large" wrap style={{ marginBottom: 16, display: 'flex' }}>
          <Statistic title="初始额度合计" value={totals.initial} precision={2} prefix="¥" />
          <Statistic title="已扣减合计" value={totals.claimed} precision={2} prefix="¥" />
          <Statistic title="已冻结合计" value={totals.frozen} precision={2} prefix="¥" />
          <Statistic title="可用额度合计" value={totals.available} precision={2} prefix="¥" />
        </Space>

        <Table<QuotaAccountView>
          rowKey="quotaAccountId"
          loading={loading}
          dataSource={accounts}
          size="small"
          scroll={{ x: 1300 }}
          locale={{ emptyText: '该保单下还没有额度账户' }}
          pagination={false}
          columns={[
            {
              title: '被保人（假名化 ID）',
              dataIndex: 'insuredIdHash',
              width: 240,
              ellipsis: true,
            },
            {
              title: '计划 ID',
              dataIndex: 'planId',
              width: 150,
              ellipsis: true,
            },
            { title: '年度', dataIndex: 'quotaYear', width: 80 },
            {
              title: '维度',
              dataIndex: 'quotaType',
              width: 140,
              render: (v: QuotaType) =>
                v === 'PERSONAL' ? '个人额度' : v === 'LIABILITY_CAP' ? '责任封顶' : '责任个人额度',
            },
            {
              title: '初始额度',
              dataIndex: 'initial',
              width: 140,
              render: (v: number) => `¥${(v ?? 0).toFixed(2)}`,
            },
            {
              title: '已扣减',
              dataIndex: 'claimed',
              width: 130,
              render: (v: number) => `¥${(v ?? 0).toFixed(2)}`,
            },
            {
              title: '已冻结',
              dataIndex: 'frozen',
              width: 130,
              render: (v: number) => `¥${(v ?? 0).toFixed(2)}`,
            },
            {
              title: '可用额度',
              dataIndex: 'available',
              width: 140,
              render: (v: number) => (
                <Typography.Text strong>¥{(v ?? 0).toFixed(2)}</Typography.Text>
              ),
            },
            {
              title: '状态',
              width: 100,
              render: (_, r) =>
                r.removed ? (
                  <Tag color="red">已减人</Tag>
                ) : (
                  <Tag color="green">正常</Tag>
                ),
            },
            {
              title: '操作',
              width: 250,
              fixed: 'right',
              render: (_, r) => (
                <Space size={4}>
                  <AuthButton
                    code="claim:quota:write"
                    type="link"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => openAdjust(r)}
                  >
                    调额
                  </AuthButton>
                  {/* 减人/恢复 互斥（一个账户只会处于其中一种状态），故收进下拉：
                      四个按钮平铺在 190px 列里会被挤成两行、把「保全记录」挤出可视区。 */}
                  <Dropdown
                    menu={{
                      items: [
                        {
                          key: 'remove',
                          label: '减人',
                          icon: <StopOutlined />,
                          danger: true,
                          disabled: r.removed,
                        },
                        {
                          key: 'restore',
                          label: '恢复',
                          icon: <UndoOutlined />,
                          disabled: !r.removed,
                        },
                      ],
                      onClick: ({ key }) =>
                        void toggleRemoved(r, key === 'remove' ? 'remove' : 'restore'),
                    }}
                  >
                    <Button type="link" size="small" icon={<MoreOutlined />}>
                      更多
                    </Button>
                  </Dropdown>
                  <Button type="link" size="small" onClick={() => void openChanges(r)}>
                    保全记录
                  </Button>
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title="额度开户"
        open={openModalOpen}
        onCancel={() => setOpenModalOpen(false)}
        onOk={() => void submitOpen()}
        confirmLoading={submitting}
        okText="开户"
        destroyOnHidden
      >
        <Form form={openForm} layout="vertical" preserve={false}>
          <Form.Item
            name="insuredIdHash"
            label="被保人假名化 ID"
            rules={[{ required: true, message: '被保人标识不能为空' }]}
            extra="与保单被保人清单中的 ID 同一口径；库里不落明文证件"
          >
            <Input placeholder="如 e2e-insured-hash-0001" />
          </Form.Item>
          <Form.Item
            name="planId"
            label="计划 ID"
            rules={[{ required: true, message: '计划ID不能为空' }]}
            extra="在「计划配置」页可查看该保单下的计划 ID"
          >
            <Input placeholder="雪花 ID 字符串" />
          </Form.Item>
          <Space size="middle" wrap style={{ width: '100%' }}>
            <Form.Item name="quotaYear" label="额度年度" style={{ width: 180 }}>
              <InputNumber min={2000} max={2100} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="quotaType" label="额度维度" style={{ width: 220 }}>
              <Select
                options={[
                  { value: 'PERSONAL', label: '个人额度' },
                  { value: 'LIABILITY_CAP', label: '责任封顶' },
                  { value: 'LIABILITY_PERSONAL', label: '责任个人额度' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item
            name="quotaInitial"
            label="初始额度"
            rules={[{ required: true, message: '初始额度不能为空' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: 220 }} />
          </Form.Item>
          <Form.Item name="liabilityUuid" label="责任 UUID（责任维度必填）">
            <Input placeholder="LIABILITY_* 维度需填写" />
          </Form.Item>
          <Form.Item name="remark" label="变更说明">
            <Input placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={adjustTarget ? `调整额度（${adjustTarget.insuredIdHash}）` : '调整额度'}
        open={adjustTarget !== null}
        onCancel={() => setAdjustTarget(null)}
        onOk={() => void submitAdjust()}
        confirmLoading={submitting}
        okText="确认调额"
        destroyOnHidden
      >
        {adjustTarget && (
          <Typography.Paragraph type="secondary">
            当前已占用 ¥{(adjustTarget.claimed + adjustTarget.frozen).toFixed(2)}
            （已赔 ¥{adjustTarget.claimed.toFixed(2)} + 已冻结 ¥{adjustTarget.frozen.toFixed(2)}）。
            新的初始额度不得低于该值，否则可用额度会变为负数、后续占额将被守恒校验拦下。
          </Typography.Paragraph>
        )}
        <Form form={adjustForm} layout="vertical" preserve={false}>
          <Form.Item
            name="newInitial"
            label="新初始额度"
            rules={[{ required: true, message: '初始额度不能为空' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: 240 }} />
          </Form.Item>
          <Form.Item name="remark" label="变更说明">
            <Input placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="保全记录"
        open={changesOf !== null}
        onClose={() => setChangesOf(null)}
        width={620}
      >
        {changesLoading ? (
          <Spin />
        ) : (
          <>
            <Timeline
              items={changes.map((c) => ({
                color: QUOTA_CHANGE_TYPE_META[c.changeType]?.color ?? 'blue',
                children: (
                  <div>
                    <Space wrap>
                      <Typography.Text strong>
                        {QUOTA_CHANGE_TYPE_META[c.changeType]?.label ?? c.changeType}
                      </Typography.Text>
                      <Typography.Text type="secondary">
                        ¥{c.beforeInitial.toFixed(2)} → ¥{c.afterInitial.toFixed(2)}
                      </Typography.Text>
                    </Space>
                    <div>
                      <Typography.Text type="secondary">
                        {dayjs(c.occurredAt).format('YYYY-MM-DD HH:mm:ss')} · 操作人{' '}
                        {c.operatorName || c.operatorId}
                      </Typography.Text>
                    </div>
                    {c.remark && <div>{c.remark}</div>}
                  </div>
                ),
              }))}
            />
            {!changes.length && <Typography.Text type="secondary">暂无保全记录</Typography.Text>}
          </>
        )}
      </Drawer>
    </div>
  );
}
