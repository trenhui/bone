import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import {
  Alert, Button, Card, Descriptions, Empty, Input, Space, Spin, Tabs, Tag, Typography, message,
} from 'antd';
import { ArrowLeftOutlined, LinkOutlined } from '@ant-design/icons';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { claimApi } from '../services/claimApi';
import type { ClaimWithItemsProjection } from '../services/types';
import { resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import { AiSuggestionCard } from '../components/AiSuggestionCard';
import {
  CASE_TABS, CASE_TAB_META, buildCaseDeepLink, isSafeClaimId,
  parseCaseLocation, type CaseTab,
} from '../workbench/caseDeepLinks';
import { isEmbeddedCaseTab, renderCaseSection } from '../cases/caseSections';
import { pushRecentCase } from '../workbench/recentCases';

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
  }, [claimId, tab]);

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
            {head ? (
              <Card size="small" title="案件概览（只读）">
                <Descriptions size="small" column={2} bordered>
                  <Descriptions.Item label="案件号">{head.claimNo || '—'}</Descriptions.Item>
                  <Descriptions.Item label="状态"><Tag>{head.status}</Tag></Descriptions.Item>
                  <Descriptions.Item label="保单号">{head.policyNo || '—'}</Descriptions.Item>
                  <Descriptions.Item label="计划 / 版本">{head.planId || '—'} / {head.planVersion || '—'}</Descriptions.Item>
                  <Descriptions.Item label="案件类型">{head.claimType || '—'}</Descriptions.Item>
                  <Descriptions.Item label="来源渠道">{head.sourceChannel || '—'}</Descriptions.Item>
                  <Descriptions.Item label="申请金额">{head.claimAmount ?? '—'}</Descriptions.Item>
                  <Descriptions.Item label="核定金额">{head.approvedAmount ?? '—'}</Descriptions.Item>
                  <Descriptions.Item label="创建时间" span={2}>{head.createdAt || '—'}</Descriptions.Item>
                  <Descriptions.Item label="SLA" span={2}>
                    <Typography.Text type="secondary">本版本后端未提供 SLA 字段，不做估算</Typography.Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="费用明细" span={2}>{rows.length} 行</Descriptions.Item>
                </Descriptions>
              </Card>
            ) : null}
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
