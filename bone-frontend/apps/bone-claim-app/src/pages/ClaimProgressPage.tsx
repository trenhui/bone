import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  Space,
  Steps,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { PageHeader } from '../components/PageHeader';
import { PolicyNoLink } from '../components/PolicyNoLink';
import {
  claimApi,
  type ClaimReportCaseBrief,
  type ClaimReportDetail,
  type ClaimStatusName,
  type ClaimWithItemsProjection,
} from '../services/claimApi';
import { claimStatusMeta } from '../services/claimStatus';
import { resolveErrorCode } from '@bone/shared-utils';
import { MoneyText } from '../components/Money';

/**
 * 员工端 · 进度追踪（PRD §7.2「进度追踪」/ delivery/05 员工端查询进度）。
 *
 * 设计取舍：员工端只读单案件进度（按案件ID 查询），不做全量列表——
 * 列表需要「当前登录用户 ↔ insuredIdHash」的可信映射（依赖 SSO 主体与员工主数据），
 * 一期不做映射即开列表等于把他人案件暴露给任意登录用户（水平越权）。
 * 映射落地后（§11.10 数据权限）再补列表，届时服务端按登录主体过滤，不由前端传 insuredIdHash。
 */
const STAGE_FLOW = ['SIGNING', 'PRE_EXAM', 'SUBMITTING', 'AUDITING', 'REVIEWING', 'FINISH'];

const STAGE_LABEL: Record<string, string> = {
  SIGNING: '受理签收',
  PRE_EXAM: '初审',
  SUBMITTING: '材料补录',
  AUDITING: '核赔',
  REVIEWING: '复核',
  FINISH: '结案',
};

/**
 * 状态文案统一取 {@link claimStatusMeta}（`services/claimStatus.ts`）。
 * 本页原有一份 `STATUS_LABEL` 与核赔工作台的 `STATUS_TAG` 各写一遍且条目集不同，
 * 后端加状态时只会改一处 ⇒ 另一处直接显示英文枚举名。
 */

export function ClaimProgressPage(): ReactElement {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [claimIdText, setClaimIdText] = useState(() => searchParams.get('claimId') ?? '');
  const [rows, setRows] = useState<ClaimWithItemsProjection[] | null>(null);
  const [loading, setLoading] = useState(false);
  /**
   * 同案赔案（同一报案下的其它赔案）。
   *
   * <p>为什么不塞进主查询返回：报案是「事故」维度、赔案是「赔付」维度，一个详情页要同时展示两层。
   * 后端刻意不把同案清单拼进赔案详情（那会让每行明细都重复携带整份清单），故这里二次拉取。
   * 拉不到不影响赔案本身的展示——同案清单是增强信息，不是主数据。
   */
  const [report, setReport] = useState<ClaimReportDetail | null>(null);
  const [reportLoading, setReportLoading] = useState(false);

  /** 按案件 ID 查询（同案清单里点「查看」也走这条路径，避免两套查询逻辑漂移）。 */
  const runSearch = useCallback(async (rawId: string) => {
    const idText = rawId.trim();
    /**
     * 只做「非空 + 纯数字」校验，**不要**转 Number 判大小。
     * 案件 ID 是雪花 ID（19 位），超出 `Number.MAX_SAFE_INTEGER`，`Number('1846...')` 会静默丢精度；
     * 而且 `Number('1e5')` 也能通过 isFinite，会把非法输入放进请求。
     */
    if (!/^\d{1,19}$/.test(idText) || idText === '0') {
      message.warning('请输入合法的案件ID（纯数字）');
      return;
    }
    setLoading(true);
    try {
      const resp = await claimApi.detail(idText);
      const data = resp?.data ?? [];
      setRows(data.length > 0 ? data : null);
      if (data.length === 0) {
        message.info('未查询到该案件（或不属于当前租户）');
      }
    } catch (err) {
      const errorCode = resolveErrorCode(err);
      message.error(errorCode === 'SC_CLAIM_NOT_FOUND' ? '案件不存在或无权查看' : '查询失败，请稍后重试');
      setRows(null);
    } finally {
      setLoading(false);
    }
  }, []);

  const search = useCallback(() => {
    void runSearch(claimIdText);
  }, [claimIdText, runSearch]);

  const head = rows?.[0];
  const currentStageIndex = head ? STAGE_FLOW.indexOf(head.stage) : -1;
  const items = rows?.filter((r) => r.itemId !== null) ?? [];

  /**
   * 从深链进入时自动查询（`?claimId=`）——追加赔案成功后要能一跳回到原赔案，
   * 而不是让用户再手抄一次 19 位雪花 ID。
   */
  useEffect(() => {
    if (searchParams.get('claimId')) {
      void runSearch(searchParams.get('claimId') ?? '');
    }
    // 只认首次挂载时的参数：runSearch 是稳定引用，重复执行会变成「每次渲染都查询」
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** 赔案变了就重新取它的报案（报案号可能为空：回填未完成的历史赔案）。 */
  useEffect(() => {
    const reportNo = head?.reportNo;
    if (!reportNo) {
      setReport(null);
      return;
    }
    let alive = true;
    setReportLoading(true);
    claimApi
      .reportDetail(reportNo)
      .then((resp) => {
        if (alive) setReport(resp?.data ?? null);
      })
      .catch(() => {
        // 同案清单是增强信息：查不到（权限/数据未回填）时静默降级，不能让整页报错
        if (alive) setReport(null);
      })
      .finally(() => {
        if (alive) setReportLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [head?.reportNo]);

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 24 }}>
      <PageHeader title="理赔进度" />

      <Card size="small" style={{ marginBottom: 16 }}>
        <Space>
          <Input
            placeholder="输入案件ID"
            value={claimIdText}
            onChange={(e) => setClaimIdText(e.target.value)}
            onPressEnter={search}
            style={{ width: 260 }}
          />
          <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={search}>
            查询
          </Button>
        </Space>
      </Card>

      {head && (
        <>
          {head.consentWithdrawnFlag === 1 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="该案的个人信息主体已撤回同意"
              description="按 PIPL 第 15 条，撤回后不再开展新的处理（分配初审、核赔、复核、自动通过均会被拒绝）；已完成的留存与法定审计记录不受影响。如被保人重新同意，须由上游重新采集并签发新的同意记录号。"
            />
          )}
          <Card title="案件信息" style={{ marginBottom: 16 }}>
            <Descriptions column={2} size="small">
              {/*
                两条都展示，且标签必须写准：这里原先只有一条「报案号」而值其实是 claim_no（赔案号），
                属于「标签与数据不是同一个东西」——用户拿它去客服报「报案号」，客服按报案号查不到；
                开发按标签理解字段，就会把赔案号当报案号写进对账脚本。拆分后两者都有真实来源。
              */}
              <Descriptions.Item label="赔案号">{head.claimNo}</Descriptions.Item>
              <Descriptions.Item label="报案号">
                {head.reportNo ? (
                  <Typography.Text copyable={{ text: head.reportNo }}>{head.reportNo}</Typography.Text>
                ) : (
                  <Typography.Text type="secondary">未关联（历史数据未回填）</Typography.Text>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="保单号"><PolicyNoLink policyNo={head.policyNo} /></Descriptions.Item>
              <Descriptions.Item label="当前状态">
                <Tag color={claimStatusMeta(head.status).color}>{claimStatusMeta(head.status).label}</Tag>
                {head.externalStatus && <Tag>{head.externalStatus}</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="申请金额"><MoneyText value={head.claimAmount} /></Descriptions.Item>
              <Descriptions.Item label="核定金额">
                <MoneyText value={head.approvedAmount} unknownText="待核定" />
              </Descriptions.Item>
              <Descriptions.Item label="受理时间">{head.createdAt}</Descriptions.Item>
              {/**
               * 同意留痕必须「可被看见」：只写库不在界面呈现，等于合规抽查时还要开发跑 SQL。
               * 未登记显示「未登记」而不是「无」——两者的合规含义不同（见 claimApi 类型注释）。
               */}
              <Descriptions.Item label="同意记录号">
                {head.consentRecordId ? (
                  <Typography.Text copyable={{ text: head.consentRecordId }}>
                    {head.consentRecordId}
                  </Typography.Text>
                ) : (
                  <Typography.Text type="secondary">未登记</Typography.Text>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="同意状态">
                {head.consentWithdrawnFlag === 1 ? (
                  <Tag color="red">已撤回</Tag>
                ) : (
                  <Tag color="green">有效</Tag>
                )}
              </Descriptions.Item>
              {head.rejectComment && (
                <Descriptions.Item label="拒赔说明" span={2}>
                  <Alert type="error" showIcon message={head.rejectComment} />
                </Descriptions.Item>
              )}
            </Descriptions>
          </Card>

          <Card title="处理进度" style={{ marginBottom: 16 }}>
            <Steps
              current={currentStageIndex < 0 ? 0 : currentStageIndex}
              status={head.status === 'CLOSED_REJECTED' ? 'error' : 'process'}
              items={STAGE_FLOW.map((s) => ({ title: STAGE_LABEL[s] ?? s }))}
            />
            {head.status === 'CLOSED_REJECTED' && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 16 }}
                message="本次理赔未通过核赔"
                description="如对结论有异议，可在收到通知后 14 日内发起申诉（对客告知签收后 10 个工作日内）（拒赔通知书中含申诉渠道与法定时限）。"
              />
            )}
          </Card>

          {/**
           * 同案赔案清单 + 追加入口。
           *
           * 这一块是「报案 1 : 0..N 赔案」在界面上的落点：用户在一次事故里产生了第二张发票，
           * 应该挂到同一条报案下追加赔案，而不是重新报案——重新报案会让两次就诊变成两个「事故」，
           * 年度免赔、责任限额与对账都会被拆到两条互不相干的链上。
           */}
          {head.reportNo && (
            <Card
              title={`同案赔案（${report ? report.caseCount : '…'} 笔）`}
              style={{ marginBottom: 16 }}
              loading={reportLoading}
              extra={
                <Space>
                  <Tag color={report?.status === 'CLOSED' ? 'default' : 'green'}>
                    {report?.status === 'CLOSED' ? '报案已关闭' : '报案开放中'}
                  </Tag>
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    // 只有 REPORTED 才接受追加；CLOSED 是「不再受理新赔付」，不是「在办案件作废」，
                    // 所以这里只禁用新增入口，不影响下方已在办赔案的查看。
                    disabled={!report || report.status !== 'REPORTED'}
                    onClick={() => navigate(`/submit?reportNo=${encodeURIComponent(head.reportNo ?? '')}`)}
                  >
                    追加赔案
                  </Button>
                </Space>
              }
            >
              {report ? (
                <>
                  <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
                    一次事故告知（报案）可对应多笔赔付（赔案）：同一次事故的多张发票、后续复诊追加，
                    都应挂到同一个报案下；年度免赔与责任限额按同案累计。
                  </Typography.Paragraph>
                  <Table<ClaimReportCaseBrief>
                    rowKey="claimId"
                    dataSource={report.cases}
                    size="small"
                    pagination={false}
                    locale={{ emptyText: <Empty description="该报案下暂无赔案" /> }}
                    columns={[
                      { title: '赔案号', dataIndex: 'claimNo' },
                      {
                        title: '状态',
                        dataIndex: 'status',
                        width: 120,
                        render: (v: string) => {
                          const meta = claimStatusMeta(v as ClaimStatusName);
                          return <Tag color={meta.color}>{meta.label}</Tag>;
                        },
                      },
                      {
                        title: '申请金额',
                        dataIndex: 'claimAmount',
                        width: 120,
                        render: (v: number | null) => <MoneyText value={v} />,
                      },
                      {
                        title: '核定金额',
                        dataIndex: 'approvedAmount',
                        width: 120,
                        render: (v: number | null) => <MoneyText value={v} unknownText="待核定" />,
                      },
                      { title: '受理时间', dataIndex: 'createdAt', width: 180 },
                      {
                        title: '操作',
                        width: 90,
                        render: (_, row) =>
                          row.claimId === head.claimId ? (
                            <Typography.Text type="secondary">当前</Typography.Text>
                          ) : (
                            <Button
                              type="link"
                              onClick={() => {
                                setClaimIdText(row.claimId);
                                void runSearch(row.claimId);
                              }}
                            >
                              查看
                            </Button>
                          ),
                      },
                    ]}
                  />
                </>
              ) : (
                <Empty description="未取到同案赔案清单（报案可能尚未回填或无权查看）" />
              )}
            </Card>
          )}

          <Card title={`费用明细（${items.length} 项）`}>
            {items.length === 0 ? (
              <Empty description="暂无明细" />
            ) : (
              <Table<ClaimWithItemsProjection>
                rowKey="itemId"
                dataSource={items}
                pagination={false}
                size="small"
                columns={[
                  { title: '项目', dataIndex: 'itemName' },
                  { title: '医保编码', dataIndex: 'itemCode', width: 140 },
                  { title: '类别', dataIndex: 'itemCategory', width: 100 },
                  {
                    title: '金额',
                    dataIndex: 'itemAmount',
                    width: 120,
                    render: (v: number | null) => <MoneyText value={v} />,
                  },
                  {
                    title: '合理费用',
                    dataIndex: 'itemValidAmount',
                    width: 120,
                    render: (v: number | null) => <MoneyText value={v} />,
                  },
                ]}
              />
            )}
          </Card>
        </>
      )}
    </div>
  );
}
