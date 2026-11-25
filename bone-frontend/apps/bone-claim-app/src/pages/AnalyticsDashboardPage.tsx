import { useCallback, useEffect, useMemo, useState, type ReactElement } from 'react';
import { Alert, Button, Card, Col, Row, Select, Space, Spin, Statistic, Table, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { resolveErrorMessage } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import {
  adoptionRateText,
  analyticsApi,
  analyticsCountText,
  analyticsWindow,
  decisionResultLabel,
  durationText,
  pushRateText,
  reviewRateText,
  shadowRateText,
  type AnalyticsSummary,
} from '../services/analytics';
import { aiFeedbackApi, type AiFeedbackSummaryResp } from '../services/aiFeedback';

/**
 * 运营分析看板（正式侧栏菜单项；仍可从工作台治理空间深链进入）。
 *
 * 口径更新（2026-11-22）：本页已由 hidden 深链路由提升为正式菜单项（迁移 0136 已落 IAM 种子）。
 * 鉴权不随之上移：App.tsx 的 HIDDEN_ROUTE_REQUIRED 按 path 查表保留 Auth 守卫，
 * 直接输 hash 绕过侧栏时依然 fail-closed——不能因为进了菜单就去掉鉴权。
 *
 * 口径纪律：所有数字来自后端 `GET /analytics/summary`，前端零假数据。后端 `scopeNote`
 * 原样上屏——影子自动化率 ≠ 实际出金率（自动放款开关默认关闭、E1/E2 未交付），
 * 这条说明不是免责声明而是指标定义，删掉就是把影子值伪装成经营成果。
 */
type Load<T> = { state: 'loading' | 'ok' | 'error'; data?: T; error?: string };

const WINDOW_OPTIONS = [
  { value: 7, label: '近 7 天' },
  { value: 14, label: '近 14 天' },
  { value: 30, label: '近 30 天' },
];

export function AnalyticsDashboardPage(): ReactElement {
  const [days, setDays] = useState(7);
  const [load, setLoad] = useState<Load<AnalyticsSummary>>({ state: 'loading' });
  /** AI 采纳率来自 aiFeedback summary（30 天滚动窗口），失败只降级该卡，不连坐主指标。 */
  const [aiSummary, setAiSummary] = useState<AiFeedbackSummaryResp | null>(null);

  const window = useMemo(() => analyticsWindow(days), [days]);

  const loadSummary = useCallback(async () => {
    setLoad({ state: 'loading' });
    void aiFeedbackApi
      .summary()
      .then((resp) => setAiSummary(resp?.code === 200 ? (resp.data ?? null) : null))
      .catch(() => setAiSummary(null));
    try {
      const resp = await analyticsApi.summary(window.from, window.to);
      // 拦截器已把 2xx + success:false 归一为 reject；走到这里只看 code。
      if (resp?.code !== 200) {
        setLoad({ state: 'error', error: resp?.message || '后端拒绝了时间窗查询' });
        return;
      }
      setLoad({ state: 'ok', data: resp?.data ?? undefined });
    } catch (err) {
      setLoad({ state: 'error', error: resolveErrorMessage(err, '运营指标查询失败') });
    }
  }, [window.from, window.to]);

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  const data = load.state === 'ok' ? load.data : undefined;

  const distribution = useMemo(
    () =>
      Object.entries(data?.byDecisionResult ?? {})
        .map(([code, count]) => ({
          key: code,
          label: decisionResultLabel(code),
          count: analyticsCountText(count),
          raw: typeof count === 'string' ? Number(count) : count,
        }))
        .sort((a, b) => (b.raw || 0) - (a.raw || 0)),
    [data],
  );

  return (
    <div>
      <PageHeader
        title="运营分析看板"
        description="决策终态分布 · AI 降级 · 平均耗时 · 影子自动化率（只读，窗口最多 31 天）"
        extra={
          <Space>
            <Select
              aria-label="统计时间窗"
              value={days}
              options={WINDOW_OPTIONS}
              onChange={setDays}
              style={{ width: 120 }}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void loadSummary()} loading={load.state === 'loading'}>
              刷新
            </Button>
          </Space>
        }
      />

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="影子口径说明（指标定义，非免责声明）"
        description={
          data?.scopeNote
            ?? '影子自动化率 = AUTO_APPROVE / 已收敛执行数；自动放款开关默认关闭、E1/E2 未交付，此值不等于实际出金率'
        }
      />

      {load.state === 'loading' ? (
        <Card><Spin /></Card>
      ) : load.state === 'error' || !data ? (
        <Card>
          <Alert type="error" showIcon message="运营指标不可用" description={load.error ?? '端点未返回数据'} />
        </Card>
      ) : (
        <>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="已收敛执行数"
                  value={analyticsCountText(data.converged)}
                  data-testid="analytics-converged"
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="AI 降级数（FALLBACK）"
                  value={analyticsCountText(data.fallbackCount)}
                  data-testid="analytics-fallback"
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="影子自动化率（≠出金率）"
                  value={shadowRateText(data.shadowAutomationRate)}
                  data-testid="analytics-shadow-rate"
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="平均决策耗时"
                  value={durationText(data.avgDurationMs)}
                  data-testid="analytics-avg-duration"
                />
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="复核率（二次复核 / 已结案）"
                  value={reviewRateText(data.reviewedCount, data.secondReviewedCount)}
                  data-testid="analytics-review-rate"
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="推送成功率（成功 / 成功+失败）"
                  value={pushRateText(data.pushSucceeded, data.pushFailed)}
                  data-testid="analytics-push-rate"
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Row gutter={8}>
                  <Col span={12}>
                    <Statistic
                      title="单案时长 P50"
                      value={durationText(data.caseDurationP50Ms)}
                      data-testid="analytics-duration-p50"
                    />
                  </Col>
                  <Col span={12}>
                    <Statistic
                      title="P90"
                      value={durationText(data.caseDurationP90Ms)}
                      data-testid="analytics-duration-p90"
                    />
                  </Col>
                </Row>
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="AI 采纳率（30 天滚动）"
                  value={adoptionRateText(
                    aiSummary?.adopts,
                    aiSummary?.rejects,
                    aiSummary?.modifies,
                  )}
                  data-testid="analytics-ai-adoption"
                />
              </Card>
            </Col>
          </Row>

          <Card title="决策结论分布（受控枚举）" style={{ marginTop: 16 }}>
            <Table
              rowKey="key"
              size="small"
              pagination={false}
              dataSource={distribution}
              locale={{ emptyText: '窗口内无已收敛执行' }}
              columns={[
                {
                  title: '结论',
                  dataIndex: 'label',
                  render: (label: string, row) => (
                    <Space>
                      <span>{label}</span>
                      {row.key === 'AUTO_APPROVE' ? <Tag color="blue">影子</Tag> : null}
                      {row.key === 'UNKNOWN' || decisionResultLabel(row.key) === row.key ? (
                        <Tag color="default">未知码原样</Tag>
                      ) : null}
                    </Space>
                  ),
                },
                { title: '数量', dataIndex: 'count', align: 'right', width: 120 },
              ]}
            />
            <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
              统计窗口：{data.windowFrom} → {data.windowTo}（UTC，右端不含）
            </Typography.Text>
          </Card>
        </>
      )}
    </div>
  );
}
