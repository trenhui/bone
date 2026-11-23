import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import {
  Alert, Button, Card, Descriptions, Empty, Input, Space, Spin, Table, Tabs, Tag, Typography, message,
} from 'antd';
import { ArrowLeftOutlined, LinkOutlined } from '@ant-design/icons';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { AuthButton } from '@bone/ui';
import { claimApi } from '../services/claimApi';
import { claimSearchApi, type ClaimSearchHit } from '../services/aiFeedback';
import type { ClaimWithItemsProjection, ItemCategory } from '../services/types';
import { resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import { PolicyNoLink } from '../components/PolicyNoLink';
import { AiSuggestionCard } from '../components/AiSuggestionCard';
import { MoneyText } from '../components/Money';
import { formatMoney } from '../services/money';
import { AsyncBoundary } from '../components/AsyncBoundary';
import { claimStatusMeta } from '../services/claimStatus';
import { useConfirmAction } from '../hooks/useConfirmAction';
import { PERMISSIONS } from '../constants/permissions';
import {
  CASE_TABS, CASE_TAB_META, buildCaseDeepLink, isSafeClaimId,
  parseCaseLocation, type CaseTab,
} from '../workbench/caseDeepLinks';
import { isEmbeddedCaseTab, renderCaseSection } from '../cases/caseSections';
import { pushRecentCase } from '../workbench/recentCases';

/** 费用类别中英文对照（与后端 ItemCategory 枚举对齐；未知值原样展示，不猜）。 */
const ITEM_CATEGORY_LABEL: Record<ItemCategory, string> = {
  DRUG: '药品',
  SERVICE: '诊疗服务',
  EXAM: '检查检验',
  MATERIAL: '医用材料',
};

/** 时间展示：固定 zh-CN + 24 小时制，避免不同浏览器/系统区域设置导致同一时刻显示两样。 */
function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

/**
 * 案件 360 —— 一个 `claimId` 贯穿 8 个环节的单一 case record（hidden 路由）。
 *
 * P1（本 change）：六个单案作业 Tab **直接渲染无壳内容组件**（见 `cases/caseSections.tsx`），
 * `claimId` 由 URL 一次传入，切换视图不跳离案件、不出现第二套页面标题或 ID 输入条。
 * `overview` 保留概览与 AI 结论卡；`trace`（核赔工作台）是队列型工作台而非单案视图，
 * 继续保留 P0 的深链桥（`buildCaseDeepLink` 自动带 claimId），旧作业路由也照旧可达。
 *
 * URL 契约统一为 `?claimId=&tab=`（与 useClaimIdParam 同一真源），可分享、可回退、可进通知。
 * 业界对位：Guidewire ClaimCenter / Duck Creek 把 tasks、documents、decision points、
 * event history 绑到同一 case record；Fiori 1-1-3 要求 ≤3 屏完成一次作业。
 */
export function Case360Page(): ReactElement {
  const location = useLocation();
  const navigate = useNavigate();
  const [, setSearchParams] = useSearchParams();
  const parsed = useMemo(() => parseCaseLocation(location.search), [location.search]);
  const { claimId, tab, tabRecovered } = parsed;

  const [rows, setRows] = useState<ClaimWithItemsProjection[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [keyword, setKeyword] = useState('');
  /** 关联历史案件：消费后端现成的跨键检索（含 insured_id_hash），不前端推断风险。 */
  const [historyHits, setHistoryHits] = useState<ClaimSearchHit[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyReload, setHistoryReload] = useState(0);
  /** 写动作成功后自增，触发案件详情重新拉取（同意状态、挂起状态等即时回写）。 */
  const [reloadKey, setReloadKey] = useState(0);
  const confirmAction = useConfirmAction();

  useEffect(() => {
    if (!claimId) {
      setRows([]);
      setError(null);
      setNotFound(false);
      return;
    }
    pushRecentCase(claimId, tab);
    let alive = true;
    setLoading(true);
    setError(null);
    setNotFound(false);
    claimApi.detail(claimId)
      .then((res) => {
        if (!alive) return;
        if (res?.code !== 200) {
          setError(res?.message ?? '案件详情返回异常');
          setRows([]);
          return;
        }
        const list = res.data ?? [];
        setRows(list);
        setNotFound(list.length === 0);
      })
      .catch((err) => {
        if (!alive) return;
        // 区分「查无此案」与「系统故障」：404 是服务端答复，不该报成加载失败。
        const text = resolveErrorMessage(err, '案件详情加载失败');
        setNotFound(/404|不存在|未找到|not found/i.test(text));
        setError(text);
        setRows([]);
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [claimId, tab, reloadKey]);

  /** 撤回同意：高危合规动作——先二次确认并明示"不可在本系统撤销"，成功后刷新案件详情。 */
  const handleRevokeConsent = useCallback(() => {
    if (!claimId) return;
    confirmAction({
      title: '确认撤回个人信息处理同意？',
      content:
        '依据 PIPL 第 15 条，撤回后本系统对该案件分配/初审/核赔/复核/自动通过等处理动作将被拒绝（422）；'
        + '本系统内不可撤销，重新处理须由上游重新采集并签发新同意记录号。请确认已完成合规处置。',
      okText: '确认撤回',
      onOk: async () => {
        try {
          const res = await claimApi.revokeConsent(claimId);
          if (res?.code !== 200) {
            message.error(res?.message ?? '撤回失败，请重试');
            return;
          }
          const already = res.data === false;
          message.success(already ? '该案件此前已撤回同意' : '已撤回同意，后续处理动作将被拒绝');
          setReloadKey((k) => k + 1);
        } catch (err) {
          message.error(resolveErrorMessage(err, '撤回同意失败，请重试'));
        }
      },
    });
  }, [claimId, confirmAction]);

  const changeTab = useCallback((next: string) => {
    setSearchParams({ claimId: claimId ?? '', tab: next });
  }, [claimId, setSearchParams]);

  const openQuick = useCallback(() => {
    const id = keyword.trim();
    if (!isSafeClaimId(id)) {
      message.warning('案件 ID 只支持数字、字母、短横线与下划线（≤64 位）');
      return;
    }
    setSearchParams({ claimId: id, tab: 'overview' });
  }, [keyword, setSearchParams]);

  const copyLink = useCallback(() => {
    if (!navigator.clipboard) {
      message.warning('当前环境不支持剪贴板，请直接复制地址栏链接');
      return;
    }
    navigator.clipboard.writeText(window.location.href)
      .then(() => message.success('案件深链已复制，可直接分享或贴进客服工单'))
      .catch(() => message.warning('复制失败，请手动复制地址栏链接'));
  }, []);

  const head = rows[0];

  /**
   * 同被保人历史案件：只用后端已假名化的 insuredIdHash 检索。
   * 后端检索规则要求关键词 2..64 字符，故长度不合法时保持空态而不是发必然 400 的请求。
   * 不展示证件/姓名原值；matchedKey 是后端受控分类码，不是 PII。
   */
  useEffect(() => {
    const hash = head?.insuredIdHash?.trim();
    if (!hash || hash.length < 2 || hash.length > 64) {
      setHistoryHits([]);
      setHistoryLoading(false);
      setHistoryError(null);
      return;
    }
    let alive = true;
    setHistoryLoading(true);
    setHistoryError(null);
    claimSearchApi.search(hash, 20)
      .then((res) => {
        if (!alive) return;
        if (res?.code !== 200) {
          setHistoryError(res?.message ?? '关联历史案件检索失败');
          setHistoryHits([]);
          return;
        }
        setHistoryHits((res.data ?? []).filter((h) => String(h.claimId) !== String(claimId)));
      })
      .catch((err) => {
        if (!alive) return;
        setHistoryError(resolveErrorMessage(err, '关联历史案件检索失败'));
        setHistoryHits([]);
      })
      .finally(() => { if (alive) setHistoryLoading(false); });
    return () => { alive = false; };
  }, [head?.insuredIdHash, claimId, historyReload]);


  /**
   * 费用明细：后端按「每明细一行」扁平投影，无明细时 itemId 为 null。
   * 金额一律走 MoneyText，缺值不伪装成 0。
   */
  const feeItems = useMemo(() => rows.filter((r) => r.itemId != null), [rows]);
  const feeAmountSum = useMemo(
    () => feeItems.reduce((sum, r) => sum + Number(r.itemAmount ?? 0), 0),
    [feeItems],
  );
  const feeValidSum = useMemo(
    () => feeItems.reduce((sum, r) => sum + Number(r.itemValidAmount ?? 0), 0),
    [feeItems],
  );
  /** 是否至少有一行核定金额：全为 null 时合计必须显示「未核定」，不能合计成 ¥0.00。 */
  const feeValidKnown = useMemo(() => feeItems.some((r) => r.itemValidAmount != null), [feeItems]);
  /** 单次渲染只取一次「现在」，避免同一屏不同项跨秒判定不一致。 */
  const now = Date.now();
  const slaOverdue = head?.slaDueAt ? new Date(head.slaDueAt).getTime() < now : false;
  const legalOverdue = head?.legalDueAt ? new Date(head.legalDueAt).getTime() < now : false;
  const amountMismatch = head != null && feeItems.length > 0
    && Math.abs(feeAmountSum - Number(head.claimAmount ?? 0)) > 0.01;
  const validMismatch = head != null && head.approvedAmount != null && feeValidKnown
    && Math.abs(feeValidSum - Number(head.approvedAmount)) > 0.01;

  /**
   * 待关注事项：只用后端已返回的事实，不推导、不估算。
   *
   * <p>核赔第一屏最需要的是「这单有什么坑」，而不是再抄一遍字段。这里覆盖三类高风险状态：
   * 同意撤回（处理动作会被拒）、案件挂起（恢复前不可作业）、拒赔缺受控原因（审计断链）。
   */
  const attention = useMemo(() => {
    const list: { tone: 'error' | 'warning' | 'info'; text: string }[] = [];
    if (!head) return list;
    if (head.consentWithdrawnFlag === 1) {
      list.push({
        tone: 'error',
        text: '被保人已撤回个人信息处理同意：后端处理类动作将被拒绝（422），请先走合规处置，勿继续核赔。',
      });
    } else if (head.consentRecordId == null) {
      list.push({
        tone: 'info',
        text: '未登记 PIPL 单独同意记录号：不等于未取得同意，但需核对渠道留痕后再处理敏感医疗信息。',
      });
    }
    if (head.hangUpStatus > 0) {
      list.push({ tone: 'warning', text: '案件处于挂起状态：恢复前不可继续常规作业。' });
    }
    if (head.status === 'REVIEWING' && !head.secondReviewerId) {
      list.push({ tone: 'info', text: '案件已进入复核中，但尚未登记第二审核人（复核完成前不得结案）。' });
    }
    if (head.status === 'CLOSED_REJECTED' && !head.rejectType) {
      list.push({ tone: 'warning', text: '已拒赔但缺少受控拒赔原因码：审计追溯会断链，请补录。' });
    }
    if (head.slaDueAt && new Date(head.slaDueAt).getTime() < Date.now()) {
      list.push({ tone: 'warning', text: '已超过内部作业 SLA 截止时间：请优先处理，或按流程说明超时原因。' });
    }
    if (head.legalDueAt && new Date(head.legalDueAt).getTime() < Date.now()) {
      list.push({ tone: 'error', text: '已超过法定时限：属监管风险事项，需立即上报并留痕。' });
    }
    return list;
  }, [head]);

  if (!claimId) {
    return (
      <div style={{ padding: '16px 20px 32px' }}>
        <PageHeader title="案件 360" description="一个 claimId 看全材料、理算、资金、申诉与决策轨迹" />
        <Card size="small">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未选择案件" />
          <Space style={{ marginTop: 12, justifyContent: 'center', width: '100%' }}>
            <Input
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={openQuick}
              placeholder="输入案件 ID"
              allowClear
              style={{ width: 260 }}
            />
            <Button type="primary" onClick={openQuick}>打开案件</Button>
            <Button onClick={() => navigate('/workbench')}>返回我的工作台</Button>
          </Space>
        </Card>
      </div>
    );
  }

  const tabItems = CASE_TABS.map((key: CaseTab) => {
    const meta = CASE_TAB_META[key];
    if (key === 'overview') {
      return {
        key,
        label: meta.label,
        children: (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {loading ? <Spin tip="读取案件事实…" /> : null}
            {error ? (
              <Alert
                type={notFound ? 'warning' : 'error'}
                showIcon
                message={notFound ? '查无此案' : error}
                description={notFound
                  ? '该案件 ID 没有对应记录（或当前数据权限不可见）。请核对 ID、确认租户与数据权限，或从待办队列进入。'
                  : '案件详情服务返回异常，请稍后重试；AI 结论卡仍会按 fail-closed 口径独立展示。'}
              />
            ) : null}
            {attention.length > 0 ? (
              <Alert
                type={attention.some((a) => a.tone === 'error') ? 'error' : 'warning'}
                showIcon
                message="待关注事项"
                description={(
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {attention.map((a) => (
                      <li key={a.text}>{a.text}</li>
                    ))}
                  </ul>
                )}
              />
            ) : null}
            {head ? (
              <Card size="small" title="案件概览（只读）">
                <Descriptions size="small" column={2} bordered>
                  <Descriptions.Item label="案件号">{head.claimNo || '—'}</Descriptions.Item>
                  <Descriptions.Item label="状态">
                    <Tag color={claimStatusMeta(head.status).color}>{claimStatusMeta(head.status).label}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="保单号"><PolicyNoLink policyNo={head.policyNo} /></Descriptions.Item>
                  <Descriptions.Item label="计划 / 版本">{head.planId || '—'} / {head.planVersion || '—'}</Descriptions.Item>
                  <Descriptions.Item label="案件类型">{head.claimType || '—'}</Descriptions.Item>
                  <Descriptions.Item label="来源渠道">{head.sourceChannel || '—'}</Descriptions.Item>
                  <Descriptions.Item label="出险时间">{head.outInsureTime || '—'}</Descriptions.Item>
                  <Descriptions.Item label="事项阶段">{head.stage || '—'}</Descriptions.Item>
                  <Descriptions.Item label="外部状态">{head.externalStatus || '—'}</Descriptions.Item>
                  <Descriptions.Item label="同意状态">
                    <Space size={6} wrap>
                      <Tag color={head.consentWithdrawnFlag === 1 ? 'red' : head.consentRecordId ? 'green' : 'default'}>
                        {head.consentWithdrawnFlag === 1 ? '已撤回' : head.consentRecordId ? '已登记' : '未登记'}
                      </Tag>
                      {head.consentWithdrawnFlag !== 1 && head.consentRecordId ? (
                        <AuthButton
                          code={PERMISSIONS.CLAIMS_WRITE}
                          size="small"
                          danger
                          onClick={handleRevokeConsent}
                          data-testid="revoke-consent-btn"
                        >
                          撤回同意
                        </AuthButton>
                      ) : null}
                    </Space>
                  </Descriptions.Item>
                  <Descriptions.Item label="挂起状态">
                    <Tag color={head.hangUpStatus > 0 ? 'orange' : 'default'}>
                      {head.hangUpStatus > 0 ? '挂起中' : '正常'}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="第二审核人">{head.secondReviewerId || '—'}</Descriptions.Item>
                  <Descriptions.Item label="申请金额"><MoneyText value={head.claimAmount} /></Descriptions.Item>
                  <Descriptions.Item label="核定金额">
                    <MoneyText value={head.approvedAmount} unknownText="待核定" />
                  </Descriptions.Item>
                  {head.rejectType ? (
                    <Descriptions.Item label="拒赔原因码" span={2}>
                      {head.rejectType}
                      {head.rejectComment ? ` · ${head.rejectComment}` : ''}
                    </Descriptions.Item>
                  ) : null}
                  <Descriptions.Item label="创建时间" span={2}>{head.createdAt || '—'}</Descriptions.Item>
                  <Descriptions.Item label="内部作业 SLA">
                    {head.slaDueAt ? (
                      <Space size={6} wrap>
                        <span>{formatDateTime(head.slaDueAt)}</span>
                        <Tag color={slaOverdue ? 'red' : 'green'}>{slaOverdue ? '已超期' : '在时限内'}</Tag>
                        {head.remainingWorkHours != null ? (
                          <Typography.Text type="secondary">
                            剩余 {Number(head.remainingWorkHours).toFixed(1)} 工时
                          </Typography.Text>
                        ) : null}
                        {head.slaRefreshedAt ? (
                          <Typography.Text type="secondary">
                            物化于 {formatDateTime(head.slaRefreshedAt)}
                          </Typography.Text>
                        ) : null}
                      </Space>
                    ) : (
                      <Typography.Text type="secondary">未物化（刷新任务未产出，不做估算）</Typography.Text>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="法定时限">
                    {head.legalDueAt ? (
                      <Space size={6} wrap>
                        <span>{formatDateTime(head.legalDueAt)}</span>
                        <Tag color={legalOverdue ? 'red' : 'green'}>{legalOverdue ? '已超期' : '在时限内'}</Tag>
                      </Space>
                    ) : (
                      <Typography.Text type="secondary">未物化</Typography.Text>
                    )}
                  </Descriptions.Item>
                </Descriptions>
              </Card>
            ) : null}
            <Card size="small" title="关联历史案件（同被保人假名化 ID）">
              <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
                仅列示服务端按假名化 ID 命中的案件，不展示证件/姓名原值；风险判定以后端规则引擎为准，未命中不等于无风险。
              </Typography.Text>
              <AsyncBoundary
                loading={historyLoading}
                error={historyError}
                empty={historyHits.length === 0}
                emptyText="未发现同被保人历史案件（不等于无风险）"
                onRetry={() => setHistoryReload((n) => n + 1)}
              >
                <Table<ClaimSearchHit>
                  rowKey="claimId"
                  size="small"
                  pagination={false}
                  dataSource={historyHits}
                  columns={[
                    {
                      title: '案件号', dataIndex: 'claimNo',
                      render: (v: string, h) => (
                        <Button
                          type="link"
                          size="small"
                          data-testid={`history-case-${h.claimId}`}
                          onClick={() => {
                            const link = buildCaseDeepLink(String(h.claimId));
                            if (link) navigate(link);
                          }}
                        >{v || '—'}</Button>
                      ),
                    },
                    {
                      title: '状态', dataIndex: 'status',
                      render: (v: string) => <Tag color={claimStatusMeta(v).color}>{claimStatusMeta(v).label}</Tag>,
                    },
                    { title: '阶段', dataIndex: 'stage', render: (v: string) => v || '—' },
                    { title: '命中键', dataIndex: 'matchedKey', render: (v: string) => <Tag>{v || '—'}</Tag> },
                    {
                      title: '创建时间', dataIndex: 'createdAt',
                      render: (v: string) => (v ? String(v).slice(0, 19).replace('T', ' ') : '—'),
                    },
                  ]}
                />
              </AsyncBoundary>
            </Card>
            <Card size="small" title={`费用明细（${feeItems.length} 行）`}>
              {feeItems.length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="后端未返回费用明细行（该案暂无 itemId）"
                />
              ) : (
                <>
                  {amountMismatch ? (
                    <Alert
                      type="warning"
                      showIcon
                      style={{ marginBottom: 12 }}
                      message="明细金额合计与申请金额不一致"
                      description={`明细合计 ${formatMoney(feeAmountSum)} vs 申请 ${formatMoney(head?.claimAmount)}，请核对录入或理算口径。`}
                    />
                  ) : null}
                  {validMismatch ? (
                    <Alert
                      type="warning"
                      showIcon
                      style={{ marginBottom: 12 }}
                      message="明细核定金额合计与核定金额不一致"
                      description={`明细合计 ${formatMoney(feeValidSum)} vs 核定 ${formatMoney(head?.approvedAmount)}，请核对理算结果。`}
                    />
                  ) : null}
                  <Table<ClaimWithItemsProjection>
                    size="small"
                    rowKey={(r) => String(r.itemId)}
                    dataSource={feeItems}
                    pagination={false}
                    scroll={{ x: 820 }}
                    columns={[
                      { title: '费用项目', dataIndex: 'itemName', width: 180, render: (v: string | null) => v || '—' },
                      { title: '编码', dataIndex: 'itemCode', width: 120, render: (v: string | null) => v || '—' },
                      {
                        title: '类别',
                        dataIndex: 'itemCategory',
                        width: 100,
                        render: (v: ItemCategory | null) => (v ? ITEM_CATEGORY_LABEL[v] ?? v : '—'),
                      },
                      {
                        title: '数量',
                        dataIndex: 'itemQuantity',
                        width: 80,
                        align: 'right',
                        render: (v: number | null) => (v == null ? '—' : v),
                      },
                      {
                        title: '单价',
                        dataIndex: 'itemUnitPrice',
                        width: 120,
                        align: 'right',
                        render: (v: number | null) => <MoneyText value={v} />,
                      },
                      {
                        title: '金额',
                        dataIndex: 'itemAmount',
                        width: 130,
                        align: 'right',
                        render: (v: number | null) => <MoneyText value={v} />,
                      },
                      {
                        title: '核定金额',
                        dataIndex: 'itemValidAmount',
                        width: 140,
                        align: 'right',
                        render: (v: number | null) => <MoneyText value={v} unknownText="未核定" />,
                      },
                    ]}
                    summary={() => (
                      <Table.Summary.Row>
                        <Table.Summary.Cell index={0} colSpan={5}>合计</Table.Summary.Cell>
                        <Table.Summary.Cell index={1} align="right">
                          <MoneyText value={feeAmountSum} />
                        </Table.Summary.Cell>
                        <Table.Summary.Cell index={2} align="right">
                          <MoneyText value={feeValidKnown ? feeValidSum : null} unknownText="未核定" />
                        </Table.Summary.Cell>
                      </Table.Summary.Row>
                    )}
                  />
                </>
              )}
            </Card>
            <AiSuggestionCard claimId={claimId} scene="case" />
          </Space>
        ),
      };
    }
    // 六个单案作业视图：同一 claimId 直接内嵌，不再跳离案件、不再手输 ID。
    if (isEmbeddedCaseTab(key)) {
      return {
        key,
        label: meta.label,
        children: (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Typography.Text type="secondary">
              当前案件 {claimId} · {meta.description}（案件上下文已带入，无需再输入 ID）
            </Typography.Text>
            {meta.guarded ? (
              <Alert
                type="warning"
                showIcon
                message="该环节承载受保护动作"
                description="资金、FRAUD 挂起恢复、后督 DIFF 必须逐案人工终审，禁批量、禁自动采纳。"
              />
            ) : null}
            {renderCaseSection(key, claimId)}
          </Space>
        ),
      };
    }
    // trace（队列型核赔工作台）保留既有深链桥：它不是单案视图，不做假内聚。
    return {
      key,
      label: meta.label,
      children: (
        <Card size="small" title={`${meta.label} · 案件 ${claimId}`}>
          <Space direction="vertical" size="middle">
            <Typography.Text>{meta.description}</Typography.Text>
            <Alert
              type="info"
              showIcon
              message="队列型工作台：带着案件上下文前往核赔工作台"
              description="该环节面向待办队列而非单一案件，保留独立工作台入口；点击后目标页会自动带上 claimId，不必再手输。"
            />
            {meta.guarded ? (
              <Alert type="warning" showIcon message="该环节承载受保护动作" description="资金、FRAUD 挂起恢复、后督 DIFF 必须逐案人工终审，禁批量、禁自动采纳。" />
            ) : null}
            <Button
              type="primary"
              onClick={() => {
                const link = buildCaseDeepLink(claimId, key);
                if (link) navigate(link);
              }}
            >
              带着案件上下文前往「{meta.label}」
            </Button>
            <Button type="link" size="small" onClick={() => navigate('/workbench')}>返回我的工作台</Button>
          </Space>
        </Card>
      ),
    };
  });

  return (
    <div style={{ padding: '16px 20px 32px' }}>
      <PageHeader
        title={`案件 360 · ${claimId}`}
        description={`来源：我的工作台 › 当前案件 · 当前视图：${CASE_TAB_META[tab].label}`}
        extra={
          <Space wrap>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/workbench')}>我的工作台</Button>
            <Button icon={<LinkOutlined />} onClick={copyLink}>复制深链</Button>
          </Space>
        }
      />
      {tabRecovered ? (
        <Alert type="warning" showIcon message="链接中的 tab 未登记，已回退到「概览」" description="不使用未知值跳转，避免落到非预期页面。" style={{ marginBottom: 12 }} />
      ) : null}
      <Tabs activeKey={tab} onChange={changeTab} items={tabItems} />
    </div>
  );
}
