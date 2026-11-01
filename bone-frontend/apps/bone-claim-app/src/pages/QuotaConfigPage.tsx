import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
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
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import { useSearchParams } from 'react-router-dom';
import {
  DownloadOutlined,
  EditOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { AuthButton } from '@bone/ui';
import { useConfirmAction } from '../hooks/useConfirmAction';
import { PERMISSIONS } from '../constants/permissions';
import { ERROR_CODES } from '../constants/errorCodes';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import { FORM_VALIDATION_MESSAGE, isFormValidationError } from '../utils/formError';
import { usePolicyOptions } from '../hooks/usePolicyOptions';
import { csvFilename, downloadBlob } from '../utils/downloadBlob';
import {
  QUOTA_CHANGE_TYPE_META,
  claimConfigApi,
  toTotal,
  type PlanView,
  type PublicQuotaSummaryView,
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
  const confirm = useConfirmAction();
  const { policies } = usePolicyOptions();
  const [searchParams] = useSearchParams();
  const [policyNo, setPolicyNo] = useState<string>('');
  const [plans, setPlans] = useState<PlanView[]>([]);
  const [accounts, setAccounts] = useState<QuotaAccountView[]>([]);
  const [loading, setLoading] = useState(false);
  /**
   * 公账/个账双视图（对齐 TPA 业界口径）：
   * - 个账（PERSONAL）= 被保人个人年度额度；
   * - 公账（LIABILITY_CAP）= 责任额度池（这项责任今年还能赔多少），理算按责任 UUID 占用。
   * 两本账读写同一张表但语义、受众不同（个账面向被保人保全，公账面向责任额度监控），故分视图呈现。
   */
  const [quotaTab, setQuotaTab] = useState<'personal' | 'public'>(
    searchParams.get('tab') === 'public' ? 'public' : 'personal',
  );
  // 分页与筛选（后端分页：账号多的保单全量拉取会超时/撑爆内存）
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  /** 输入框里的草稿值（回车/点搜索才生效，避免每敲一个字打一次后端）。 */
  const [keywordDraft, setKeywordDraft] = useState('');
  /** 已生效的关键字（个账按被保人假名化 ID 搜，公账同表亦可按该列搜）。 */
  const [keyword, setKeyword] = useState('');
  const [removedFilter, setRemovedFilter] = useState<boolean | undefined>();
  /** 公账汇总（责任额度池按责任 UUID 聚合），仅公账视图加载。 */
  const [publicSummary, setPublicSummary] = useState<PublicQuotaSummaryView[]>([]);
  const [exporting, setExporting] = useState(false);
  const [openModalOpen, setOpenModalOpen] = useState(false);
  const [adjustTarget, setAdjustTarget] = useState<QuotaAccountView | null>(null);
  const [changesOf, setChangesOf] = useState<QuotaAccountView | null>(null);
  const [changes, setChanges] = useState<QuotaChangeView[]>([]);
  const [changesLoading, setChangesLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [openForm] = Form.useForm();
  const [adjustForm] = Form.useForm();

  /** 当前视图对应的额度维度（列表/导出/开户默认值统一从这里取，避免三处各写一份）。 */
  const effectiveQuotaType: QuotaType = quotaTab === 'personal' ? 'PERSONAL' : 'LIABILITY_CAP';

  /**
   * 保单初值：优先 URL 深链（从保单配置页跳入时带保单上下文），否则取列表首条。
   *
   * <p>列表由 {@link usePolicyOptions} 拉取，这里只做「初值选择」；{@code prev ||} 保证用户
   * 已手动切换的选择不被覆盖。</p>
   */
  useEffect(() => {
    if (policies.length === 0) {
      return;
    }
    setPolicyNo((prev) => prev || searchParams.get('policyNo') || policies[0]?.policyNo || '');
    // searchParams 只用于初始化，不进依赖：URL 变化不应触发重复设置
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [policies]);

  /** 拉某保单下的计划（供开户表单的计划下拉用，避免手输雪花 ID）。 */
  const loadPlans = useCallback(async () => {
    if (!policyNo) {
      setPlans([]);
      return;
    }
    try {
      const res = await claimConfigApi.listPlans(policyNo);
      setPlans(res.data ?? []);
    } catch (err) {
      message.error(resolveErrorMessage(err, '计划列表加载失败'));
    }
  }, [policyNo, message]);

  const loadAccounts = useCallback(async () => {
    if (!policyNo) {
      setAccounts([]);
      setTotal(0);
      return;
    }
    setLoading(true);
    try {
      const res = await claimConfigApi.listQuotaAccounts({
        policyNo,
        keyword: keyword || undefined,
        quotaType: effectiveQuotaType,
        removed: removedFilter,
        page,
        size: pageSize,
      });
      setAccounts(res.data?.records ?? []);
      setTotal(toTotal(res.data));
    } catch (err) {
      message.error(resolveErrorMessage(err, '额度账户加载失败'));
    } finally {
      setLoading(false);
    }
  }, [policyNo, keyword, effectiveQuotaType, removedFilter, page, pageSize, message]);

  useEffect(() => {
    setPolicyNo((prev) => prev || policies[0]?.policyNo || '');
  }, [policies]);

  useEffect(() => {
    void loadPlans();
  }, [loadPlans]);

  useEffect(() => {
    void loadAccounts();
  }, [loadAccounts]);

  /** 公账汇总只在公账视图有意义，个账视图不发这个请求（避免每次开户都白拉一遍）。 */
  const loadPublicSummary = useCallback(async () => {
    if (quotaTab !== 'public' || !policyNo) {
      setPublicSummary([]);
      return;
    }
    try {
      const res = await claimConfigApi.publicSummary(policyNo);
      setPublicSummary(res.data ?? []);
    } catch (err) {
      message.error(resolveErrorMessage(err, '公账汇总加载失败'));
    }
  }, [quotaTab, policyNo, message]);

  useEffect(() => {
    void loadPublicSummary();
  }, [loadPublicSummary]);

  /** 换保单时重置筛选与页码：跨保单沿用上一保单的筛选/页码会看到空页（以为数据丢了）。 */
  const changePolicy = (v: string) => {
    setPolicyNo(v);
    setKeywordDraft('');
    setKeyword('');
    setRemovedFilter(undefined);
    setPage(1);
  };

  /** 切换公账/个账视图：清空筛选并回第 1 页，页码与筛选在两本账间没有延续意义。 */
  const changeTab = (v: 'personal' | 'public') => {
    setQuotaTab(v);
    setKeywordDraft('');
    setKeyword('');
    setRemovedFilter(undefined);
    setPage(1);
  };

  /** 筛选变更统一回到第 1 页：留在第 3 页而筛选后只剩 1 页会显示空表。 */
  const applyFilter = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(1);
  };

  const exportCsv = async () => {
    if (!policyNo) return;
    setExporting(true);
    try {
      const blob = await claimConfigApi.exportQuotaAccounts({
        policyNo,
        keyword: keyword || undefined,
        quotaType: effectiveQuotaType,
        removed: removedFilter,
      });
      downloadBlob(blob, csvFilename(quotaTab === 'personal' ? 'quota-personal' : 'quota-liability-cap'));
    } catch (err) {
      message.error(resolveErrorMessage(err, '导出失败'));
    } finally {
      setExporting(false);
    }
  };

  const openCreate = () => {
    // Modal 未打开时 setFieldsValue 会静默失效（Form 未挂载）⇒ 初值改由 initialValues 承担。
    setOpenModalOpen(true);
  };

  /** 开户表单初值（不缓存，避免跨年后仍写旧年度）：默认取当前视图的额度维度。 */
  const openInitialValues = {
    quotaYear: new Date().getFullYear(),
    quotaType: effectiveQuotaType,
    quotaInitial: 0,
  };

  const submitOpen = async () => {
    setSubmitting(true);
    try {
      const values = await openForm.validateFields();
      await claimConfigApi.openQuotaAccount({
        insuredIdHash: values.insuredIdHash.trim(),
        policyNo,
        planId: String(values.planId),
        quotaYear: values.quotaYear,
        // 维度由当前视图决定（个账→PERSONAL、公账→LIABILITY_CAP），不在表单里再选一次：
        // 表单可选会导致"开着公账视图开了个账账户"的错位数据
        quotaType: effectiveQuotaType,
        liabilityUuid: values.liabilityUuid?.trim(),
        quotaInitial: values.quotaInitial ?? 0,
        remark: values.remark?.trim(),
      });
      message.success('额度账户已开户');
      setOpenModalOpen(false);
      await loadAccounts();
    } catch (err) {
      if (isFormValidationError(err)) {
        message.error(FORM_VALIDATION_MESSAGE);
      } else {
        message.error(resolveErrorMessage(err, '开户失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const openAdjust = (row: QuotaAccountView) => {
    // Modal 未打开时 setFieldsValue 会静默失效（Form 未挂载）⇒ 初值改由 initialValues 承担。
    setAdjustTarget(row);
  };

  /** 调额表单初值（当前初始额度 + 空说明）。 */
  const adjustInitialValues = useMemo(
    () =>
      adjustTarget
        ? { newInitial: adjustTarget.initial, remark: '' }
        : undefined,
    [adjustTarget],
  );

  const submitAdjust = async () => {
    if (!adjustTarget) return;
    setSubmitting(true);
    try {
      const values = await adjustForm.validateFields();
      await claimConfigApi.adjustQuota(adjustTarget.quotaAccountId, {
        newInitial: values.newInitial,
        remark: values.remark?.trim(),
        version: adjustTarget.version,
      });
      message.success('额度已调整');
      setAdjustTarget(null);
      await loadAccounts();
    } catch (err) {
      if (isFormValidationError(err)) {
        message.error(FORM_VALIDATION_MESSAGE);
        return;
      }
      const code = resolveErrorCode(err);
      if (code === ERROR_CODES.CONCURRENT_CONFLICT) {
        // 乐观锁冲突：调整期间被他人改过。关闭弹窗并刷新，提示用户重新打开调整。
        setAdjustTarget(null);
        await loadAccounts();
        message.warning('该额度账户已被其他人修改，请重新打开调整并保存');
      } else {
        message.error(resolveErrorMessage(err, '调额失败'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const toggleRemoved = (row: QuotaAccountView, action: 'remove' | 'restore') => {
    const isRemove = action === 'remove';
    // 减人是资金相关动作（影响该被保人所有后续赔案的额度累计），必须二次确认；
    // 误点一下就生效的破坏性操作是配置面最常见的事故来源。
    confirm({
      title: isRemove ? '确认减人？' : '确认恢复？',
      content: isRemove
        ? `减人后，被保人 ${row.insuredIdHash} 不再参与额度累计（账户与历史保留，可随时恢复）。`
        : `恢复后，被保人 ${row.insuredIdHash} 重新参与额度累计。`,
      danger: isRemove,
      okText: isRemove ? '减人' : '恢复',
      onOk: async () => {
        try {
          if (isRemove) {
            await claimConfigApi.removeInsured(row.quotaAccountId, { version: row.version });
            message.success('已减人（该被保人不再参与额度累计）');
          } else {
            await claimConfigApi.restoreInsured(row.quotaAccountId, { version: row.version });
            message.success('已恢复');
          }
          await loadAccounts();
        } catch (err) {
          const code = resolveErrorCode(err);
          if (code === ERROR_CODES.CONCURRENT_CONFLICT) {
            await loadAccounts();
            message.warning('该额度账户已被其他人修改，请重新操作');
          } else {
            message.error(resolveErrorMessage(err, isRemove ? '减人失败' : '恢复失败'));
          }
        }
      },
    });
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
        description="公账（责任额度池）与个账（被保人个人额度）双账管理：开户、调额、减人；每次变更均留痕（保全记录）。"
      />

      <Card>
        {/* 公账/个账分视图：两本账共用额度账户表，但维度列与可用操作不同
            （个账按被保人保全、减人；公账按责任 UUID 监控额度池占用）。 */}
        <Space style={{ marginBottom: 16 }} wrap>
          <Segmented
            value={quotaTab}
            onChange={(v) => changeTab(v as 'personal' | 'public')}
            options={[
              { label: '个账额度（被保人）', value: 'personal' },
              { label: '公账额度（责任池）', value: 'public' },
            ]}
          />
          <Select
            placeholder="选择保单"
            style={{ width: 300 }}
            showSearch
            optionFilterProp="label"
            value={policyNo || undefined}
            onChange={changePolicy}
            options={policies.map((p) => ({
              value: p.policyNo,
              label: `${p.policyNo}${p.policyName ? ` · ${p.policyName}` : ''}`,
            }))}
          />
          <Input.Search
            placeholder={quotaTab === 'personal' ? '被保人假名化 ID' : '被保人假名化 ID / 责任 UUID'}
            style={{ width: 260 }}
            allowClear
            enterButton={<SearchOutlined />}
            value={keywordDraft}
            onChange={(e) => setKeywordDraft(e.target.value)}
            onSearch={(v) => applyFilter(setKeyword)(v.trim())}
          />
          {quotaTab === 'personal' && (
            <Select
              placeholder="减人状态"
              style={{ width: 130 }}
              allowClear
              value={removedFilter}
              onChange={applyFilter(setRemovedFilter)}
              options={[
                { value: false, label: '正常' },
                { value: true, label: '已减人' },
              ]}
            />
          )}
          <Button icon={<ReloadOutlined />} onClick={() => void loadAccounts()}>
            刷新
          </Button>
          <AuthButton
            code={PERMISSIONS.QUOTA_READ}
            icon={<DownloadOutlined />}
            loading={exporting}
            disabled={!policyNo}
            onClick={() => void exportCsv()}
          >
            导出 CSV
          </AuthButton>
          <AuthButton
            code={PERMISSIONS.QUOTA_WRITE}
            type="primary"
            icon={<PlusOutlined />}
            disabled={!policyNo}
            onClick={openCreate}
          >
            {quotaTab === 'personal' ? '开户' : '初始化责任额度'}
          </AuthButton>
        </Space>

        {/* 统计口径是「本页」而非全量：后端分页后前端只有当前页数据，
            标成"合计"会让运营拿它对账（差一个数量级才发现是页内小计）。 */}
        {/* 公账池级汇总：明细表是"单人占用"视角，汇总是"整个责任池还剩多少"视角——
            池级风险（某责任快赔完）在明细里翻页看不出来，必须聚合呈现。 */}
        {quotaTab === 'public' && (
          <Table<PublicQuotaSummaryView>
            size="small"
            style={{ marginBottom: 16 }}
            rowKey={(r) => `${r.policyNo}-${r.planId}-${r.liabilityUuid}`}
            dataSource={publicSummary}
            loading={loading}
            pagination={false}
            locale={{ emptyText: '该保单暂无公账（责任额度池）账户：发布责任时自动初始化，或在开户弹窗手动开立' }}
            columns={[
              {
                title: '责任 UUID',
                dataIndex: 'liabilityUuid',
                ellipsis: true,
                render: (v: string) => (
                  <Typography.Text code copyable={{ text: v }}>
                    {v}
                  </Typography.Text>
                ),
              },
              {
                title: '覆盖人数',
                dataIndex: 'accountCount',
                width: 100,
                render: (v: number, r) => `${v}${r.removedCount > 0 ? `（减人 ${r.removedCount}）` : ''}`,
              },
              { title: '初始额度合计', dataIndex: 'initialTotal', width: 140, render: (v: number) => `¥ ${Number(v).toFixed(2)}` },
              { title: '已扣减合计', dataIndex: 'claimedTotal', width: 130, render: (v: number) => `¥ ${Number(v).toFixed(2)}` },
              { title: '已冻结合计', dataIndex: 'frozenTotal', width: 130, render: (v: number) => `¥ ${Number(v).toFixed(2)}` },
              {
                title: '可用额度合计',
                dataIndex: 'availableTotal',
                width: 140,
                render: (v: number) => (
                  <Typography.Text strong type={Number(v) <= 0 ? 'danger' : undefined}>
                    ¥ {Number(v).toFixed(2)}
                  </Typography.Text>
                ),
              },
            ]}
          />
        )}

        <Space size="large" wrap style={{ marginBottom: 16, display: 'flex' }}>
          <Statistic
            title={`本页${quotaTab === 'personal' ? '个账' : '公账'}初始额度合计`}
            value={totals.initial}
            precision={2}
            prefix="¥"
          />
          <Statistic title="本页已扣减合计" value={totals.claimed} precision={2} prefix="¥" />
          <Statistic title="本页已冻结合计" value={totals.frozen} precision={2} prefix="¥" />
          <Statistic
            title={`本页${quotaTab === 'personal' ? '个账' : '公账'}可用额度合计`}
            value={totals.available}
            precision={2}
            prefix="¥"
          />
        </Space>

        <Table<QuotaAccountView>
          rowKey="quotaAccountId"
          loading={loading}
          dataSource={accounts}
          size="small"
          scroll={{ x: 1300 }}
          locale={{ emptyText: '该保单下没有符合条件的额度账户' }}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100],
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
            },
          }}
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
            // 两本账各展示自己的主键维度：个账看维度口径，公账看责任 UUID（理算按它占用额度池）
            ...(quotaTab === 'personal'
              ? [
                {
                  title: '维度',
                  dataIndex: 'quotaType',
                  width: 140,
                  render: (v: QuotaType) =>
                    v === 'PERSONAL'
                      ? '个人额度'
                      : v === 'LIABILITY_CAP'
                        ? '责任封顶'
                        : '责任个人额度',
                },
              ]
              : [
                {
                  title: '责任 UUID',
                  dataIndex: 'liabilityUuid',
                  width: 220,
                  ellipsis: true,
                  render: (v: string) =>
                    v ? (
                      <Typography.Text code copyable={{ text: v }}>
                        {v}
                      </Typography.Text>
                    ) : (
                      '—'
                    ),
                },
              ]),
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
              width: quotaTab === 'personal' ? 250 : 170,
              fixed: 'right',
              render: (_, r) => (
                <Space size={4}>
                  <AuthButton
                    code={PERMISSIONS.QUOTA_WRITE}
                    type="link"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => openAdjust(r)}
                  >
                    调额
                  </AuthButton>
                  {/* 减人/恢复只对个账有意义（公账额度池没有"减人"概念），公账视图隐藏。
                      两者互斥（一个账户只会处于其中一种状态），故收进下拉：
                      四个按钮平铺在 190px 列里会被挤成两行、把「保全记录」挤出可视区。 */}
                  {quotaTab === 'personal' && (
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
                        onClick: ({ key }) => toggleRemoved(r, key === 'remove' ? 'remove' : 'restore'),
                      }}
                    >
                      <Button type="link" size="small" icon={<MoreOutlined />}>
                        更多
                      </Button>
                    </Dropdown>
                  )}
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
        title={quotaTab === 'personal' ? '个账开户（个人额度）' : '初始化责任额度（公账）'}
        open={openModalOpen}
        onCancel={() => setOpenModalOpen(false)}
        onOk={() => void submitOpen()}
        confirmLoading={submitting}
        okText="开户"
        destroyOnHidden
      >
        <Form
          key="quota-open"
          form={openForm}
          layout="vertical"
          preserve={false}
          initialValues={openInitialValues}
        >
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
            label="计划"
            rules={[{ required: true, message: '请选择计划' }]}
            extra="列出该保单下已配置的计划；无计划时到「保单管理 → 配置」页创建"
          >
            <Select
              placeholder="选择计划"
              showSearch
              optionFilterProp="label"
              options={plans.map((p) => ({
                value: p.planId,
                label: `${p.planCode}${p.planName ? ` · ${p.planName}` : ''}`,
              }))}
            />
          </Form.Item>
          <Space size="middle" wrap style={{ width: '100%' }}>
            <Form.Item name="quotaYear" label="额度年度" style={{ width: 180 }}>
              <InputNumber min={2000} max={2100} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item label="额度维度">
              <Typography.Text code>
                {quotaTab === 'personal' ? 'PERSONAL（个账）' : 'LIABILITY_CAP（公账责任池）'}
              </Typography.Text>
            </Form.Item>
          </Space>
          <Form.Item
            name="quotaInitial"
            label="初始额度"
            rules={[{ required: true, message: '初始额度不能为空' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: 220 }} />
          </Form.Item>
          {quotaTab === 'public' && (
            <Form.Item
              name="liabilityUuid"
              label="责任 UUID"
              rules={[{ required: true, message: '公账额度按责任 UUID 建池，必填' }]}
              extra="从「责任配置」页复制；理算按该 UUID 占用/归还额度池"
            >
              <Input placeholder="LIABILITY_..." />
            </Form.Item>
          )}
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
        <Form
          key={adjustTarget?.quotaAccountId ?? 'adjust-none'}
          form={adjustForm}
          layout="vertical"
          preserve={false}
          initialValues={adjustInitialValues}
        >
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
