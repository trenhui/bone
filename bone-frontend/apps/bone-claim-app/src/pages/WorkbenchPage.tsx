import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import {
  Alert, Button, Card, Col, Empty, Input, Row, Space, Tag, Typography, message,
} from 'antd';
import { SearchOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { claimApi, normalizeTotal } from '../services/claimApi';
import { aiFeedbackApi, claimSearchApi, type AiFeedbackSummaryResp, type ClaimSearchHit } from '../services/aiFeedback';
import { resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import { useWorkbenchProfile } from '../workbench/useWorkbenchProfile';
import { assertFioriQuota, type WorkbenchCard } from '../workbench/profiles';
import { buildCase360Link, isClaimIdLike, normalizeTab } from '../workbench/caseDeepLinks';
import { readRecentCases, type RecentCase } from '../workbench/recentCases';
import { recentWorkbenchEvents } from '../workbench/telemetry';

/**
 * 我的工作台 —— 登录后的默认落点（hidden 路由，不占 IAM 菜单）。
 *
 * 为什么首页而不是给每个角色复制一套侧栏
 * --------------------------------------
 * SAP Fiori Launchpad 的 My Home 是跨 Space 的个性化首页；Spaces/Pages 官方配额
 * 2–5 sections、3–7 apps/section、≤25 apps/page。本项目 15 条菜单已由 IAM 统一下发且
 * `0078` 把读权限码钉死，复制 N 套侧栏既会漂移又会撞 G32/G35/G37/G40 与单导航探针。
 * 因此：**一套骨架 + 按能力画像排序的四个 Section**，成本 O(1)，且不改任何菜单数据。
 *
 * 界面上刻意保留三件「不撒谎」的信息：
 *  1. 计数取不到显示「—」而不是 0；
 *  2. 画像来源与判定理由可见，且明写「仅展示裁剪，不是权限边界」；
 *  3. 尚未落库的能力（保存视图）标注 P1，不冒充已上线；AI 回流后端契约已落地并真机联调通过。
 */
export function WorkbenchPage(): ReactElement {
  const navigate = useNavigate();
  const location = useLocation();
  const profile = useWorkbenchProfile();
  const [counts, setCounts] = useState<Record<string, number | null>>({});
  const [keyword, setKeyword] = useState('');
  const [recent, setRecent] = useState<RecentCase[]>([]);
  const [searchHits, setSearchHits] = useState<ClaimSearchHit[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [summary, setSummary] = useState<AiFeedbackSummaryResp | null>(null);

  const panel = useMemo(
    () => new URLSearchParams(location.search).get('panel') ?? '',
    [location.search],
  );

  const metricCards = useMemo(
    () => profile.sections.flatMap((s) => s.cards).filter((c) => c.metric !== 'none'),
    [profile],
  );

  useEffect(() => {
    let alive = true;
    const load = async () => {
      const results = await Promise.allSettled(
        metricCards.map((c) =>
          c.metric === 'reviewTasks'
            ? claimApi.reviewTaskApi.tasks({ size: 1 })
            : claimApi.page({ statuses: c.statuses ? [...c.statuses] : undefined, size: 1 }),
        ),
      );
      if (!alive) return;
      const next: Record<string, number | null> = {};
      results.forEach((r, i) => {
        const id = metricCards[i].id;
        next[id] = r.status === 'fulfilled' && r.value?.code === 200
          ? normalizeTotal(r.value?.data?.total)
          : null; // 取不到就是「—」，绝不拿 0 冒充真实指标
      });
      setCounts(next);
    };
    void load();
    setRecent(readRecentCases());
    // 服务端采纳率概览（30 天滚动窗口），与本地事件样本并列展示。
    aiFeedbackApi.summary()
      .then((res) => { if (alive && res?.code === 200) setSummary(res.data ?? null); })
      .catch(() => { if (alive) setSummary(null); });
    return () => { alive = false; };
  }, [metricCards]);

  const quotaProblems = useMemo(() => assertFioriQuota(profile.sections), [profile]);

  const openCase = useCallback(() => {
    const id = keyword.trim();
    // 纯数字雪花号才当作案件 ID 直接跳案件 360；保单号/报案号/手机号等一律走后端跨键检索。
    if (isClaimIdLike(id)) {
      const link = buildCase360Link(id);
      if (link) navigate(link);
      return;
    }
    // 非纯案件 ID → 走后端跨键检索（报案号/保单号/发票号/姓名/手机等）。
    if (id.length < 2) {
      message.warning('请输入至少 2 个字符：案件 ID、报案号、保单号、发票号或姓名/手机。');
      return;
    }
    let alive = true;
    setSearching(true);
    setSearchError(null);
    claimSearchApi.search(id, 10)
      .then((res) => {
        if (!alive) return;
        if (res?.code !== 200) {
          setSearchError(res?.message ?? '检索失败');
          setSearchHits(null);
          return;
        }
        setSearchHits(res.data ?? []);
      })
      .catch((err) => {
        if (!alive) return;
        setSearchError(resolveErrorMessage(err, '跨键检索失败'));
        setSearchHits(null);
      })
      .finally(() => { if (alive) setSearching(false); });
    return () => { alive = false; };
  }, [keyword, navigate]);

  const go = useCallback((card: WorkbenchCard) => {
    navigate(card.path);
  }, [navigate]);

  const localEvents = recentWorkbenchEvents();
  const impressions = localEvents.filter((e) => e.type === 'ai_suggestion_impression').length;
  const dispositions = localEvents.filter((e) => e.type === 'ai_suggestion_disposition').length;

  return (
    <div style={{ padding: '16px 20px 32px' }}>
      <PageHeader
        title="我的工作台"
        description={`${profile.label} · ${profile.summary}`}
        extra={<Tag color="processing">画像来源：{profile.source}</Tag>}
      />

      <Card size="small" style={{ marginBottom: 16 }}>
        <Space wrap>
          <Input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={openCase}
            prefix={<SearchOutlined />}
            placeholder="输入案件 ID 或 报案号/保单号/发票号/姓名/手机"
            allowClear
            style={{ width: 320 }}
          />
          <Button type="primary" loading={searching} onClick={openCase}>打开 / 检索</Button>
          <Button icon={<ThunderboltOutlined />} onClick={() => navigate('/workbench?panel=search')}>
            ⌘K 全局检索与命令面板
          </Button>
          <Typography.Text type="secondary">跨键检索已接入后端（/api/v1/claims/search，网关已白名单）</Typography.Text>
        </Space>
        {searchError ? (
          <Alert type="error" showIcon message={searchError} style={{ marginTop: 12 }} />
        ) : null}
        {searchHits ? (
          <div style={{ marginTop: 12 }}>
            {searchHits.length === 0 ? (
              <Typography.Text type="secondary">未检索到匹配案件（结果按租户隔离，且不含 PII 原值）。</Typography.Text>
            ) : (
              <Space wrap>
                {searchHits.map((h) => (
                  <Button
                    key={h.claimId}
                    size="small"
                    onClick={() => navigate(buildCase360Link(String(h.claimId), 'overview') ?? '/workbench')}
                  >
                    {h.claimNo || `案件 ${h.claimId}`}
                    {h.matchedKey ? <Typography.Text type="secondary"> · {h.matchedKey}</Typography.Text> : null}
                  </Button>
                ))}
              </Space>
            )}
          </div>
        ) : null}
      </Card>

      {quotaProblems.length > 0 ? (
        <Alert type="warning" showIcon message="工作台卡片已超出 Fiori 配额，请收敛入口" description={quotaProblems.join('；')} style={{ marginBottom: 16 }} />
      ) : null}

      {panel ? (
        <Card size="small" title={`面板：${panel}`} style={{ marginBottom: 16 }} extra={<Button size="small" onClick={() => navigate('/workbench')}>关闭</Button>}>
          {panel === 'search' ? (
            <Space direction="vertical">
              <Typography.Text>在 shell 顶栏按 ⌘K / Ctrl+K / 「/」唤起全局命令面板，可按菜单名检索、按案件 ID 直达案件 360。</Typography.Text>
              <Typography.Text type="secondary">命令面板只使用 IAM 已下发的可见菜单，不构造越权入口；顶栏检索框已接入后端跨键检索（/api/v1/claims/search，网关已白名单），支持报案号/保单号/发票号/被保人假名化 ID + 干系人遮蔽姓名/手机，结果按租户隔离且不含 PII 原值。</Typography.Text>
            </Space>
          ) : null}
          {panel === 'views' ? (
            <Space direction="vertical">
              <Typography.Text>预置视图规划：我的待办 / 今日新增 / 高风险 / 临近 SLA / 待补件 / 已达封顶 / 支付失败 / 对账差异 / 质检 DIFF。</Typography.Text>
              <Alert type="info" showIcon message="P1 交付：查询状态与 URL 双向同步 + 保存视图落库" description="本版本不展示假的「已保存视图」数据。" />
            </Space>
          ) : null}
          {panel === 'recent' ? (
            recent.length === 0
              ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本次会话还没有打开过案件" />
              : (
                <Space wrap>
                  {recent.map((c) => (
                    <Button key={c.claimId} onClick={() => navigate(buildCase360Link(c.claimId, normalizeTab(c.tab)) ?? '/workbench')}>
                      案件 {c.claimId} · {c.tab}
                    </Button>
                  ))}
                </Space>
              )
          ) : null}
          {panel === 'ai' ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Typography.Text>
                本地事件样本：曝光 {impressions} 次 / 裁决 {dispositions} 次（浏览器会话内）。
              </Typography.Text>
              <Alert
                type="success"
                showIcon
                message="后端回流已接通"
                description={
                  summary
                    ? `服务端口径（${summary.windowDays} 天滚动，曝光为分母）：曝光 ${summary.impressions} / 裁决 ${summary.dispositions}（采纳 ${summary.adopts}、驳回 ${summary.rejects}、修改 ${summary.modifies}）。serverSide=${summary.serverSide}。`
                    : '服务端采纳率概览暂不可达（可能未登录或后端未起），本地样本仍可用于验证埋点链路。'
                }
              />
              {localEvents.length > 0 ? (
                <Typography.Paragraph code style={{ maxHeight: 160, overflow: 'auto' }}>
                  {localEvents.slice(-8).reverse().map((e) => `${e.at} ${e.type} ${e.claimId}${e.type === 'ai_suggestion_disposition' ? ` ${e.disposition}/${e.reasonCode ?? '-'}` : ''}`).join('\n')}
                </Typography.Paragraph>
              ) : null}
            </Space>
          ) : null}
        </Card>
      ) : null}

      {profile.sections.map((section) => (
        <div key={section.id} style={{ marginBottom: 20 }}>
          <Space style={{ marginBottom: 8 }}>
            <Typography.Title level={5} style={{ margin: 0 }}>{section.label}</Typography.Title>
            <Typography.Text type="secondary">{section.description}</Typography.Text>
            <Tag>{section.cards.length} 个入口</Tag>
          </Space>
          <Row gutter={[12, 12]}>
            {section.cards.map((card) => (
              <Col key={card.id} xs={24} sm={12} lg={8} xxl={6}>
                <Card
                  size="small"
                  hoverable
                  onClick={() => go(card)}
                  style={{ height: '100%' }}
                  title={
                    <Space size={4} wrap>
                      <span>{card.label}</span>
                      {card.guarded ? <Tag color="orange">逐案确认 · 禁批量</Tag> : null}
                      {card.aiPushed ? <Tag color="purple">AI 推送</Tag> : null}
                    </Space>
                  }
                  extra={card.metric === 'none' ? null : <Typography.Text strong>{counts[card.id] ?? '—'}</Typography.Text>}
                >
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 0, minHeight: 40 }}>
                    {card.description}
                  </Typography.Paragraph>
                </Card>
              </Col>
            ))}
          </Row>
        </div>
      ))}

      <Card size="small" title="为什么我看到这些卡片？">
        <Space direction="vertical" size={4}>
          <Typography.Text>判定：{profile.reason}</Typography.Text>
          <Typography.Text type="secondary">
            角色码 {profile.roleCodes.length} 个 · 权限码 {profile.permissionCodes.length} 个 ·
            受保护动作 {profile.guardedCardIds.length} 类
          </Typography.Text>
          <Alert
            type="info"
            showIcon
            message="排序与裁剪只作用于展示，不是权限边界"
            description="后端 @PreAuthorize 与 row_policies 仍是唯一安全下限；被隐藏的入口仍可通过 URL 直达，前端不会据此放宽任何按钮或接口。"
          />
        </Space>
      </Card>
    </div>
  );
}
