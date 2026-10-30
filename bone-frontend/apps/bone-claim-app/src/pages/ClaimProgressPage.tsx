import { useState, type ReactElement } from 'react';
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
  message,
} from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { PageHeader } from '../components/PageHeader';
import { claimApi, type ClaimWithItemsProjection } from '../services/claimApi';
import { claimStatusMeta } from '../services/claimStatus';
import { resolveErrorCode } from '@bone/shared-utils';

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
  const [claimIdText, setClaimIdText] = useState('');
  const [rows, setRows] = useState<ClaimWithItemsProjection[] | null>(null);
  const [loading, setLoading] = useState(false);

  const search = async () => {
    const idText = claimIdText.trim();
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
  };

  const head = rows?.[0];
  const currentStageIndex = head ? STAGE_FLOW.indexOf(head.stage) : -1;
  const items = rows?.filter((r) => r.itemId !== null) ?? [];

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
          <Card title="案件信息" style={{ marginBottom: 16 }}>
            <Descriptions column={2} size="small">
              <Descriptions.Item label="报案号">{head.claimNo}</Descriptions.Item>
              <Descriptions.Item label="保单号">{head.policyNo}</Descriptions.Item>
              <Descriptions.Item label="当前状态">
                <Tag color={claimStatusMeta(head.status).color}>{claimStatusMeta(head.status).label}</Tag>
                {head.externalStatus && <Tag>{head.externalStatus}</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="申请金额">¥{Number(head.claimAmount).toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="核定金额">
                {head.approvedAmount === null ? '待核定' : `¥${Number(head.approvedAmount).toFixed(2)}`}
              </Descriptions.Item>
              <Descriptions.Item label="受理时间">{head.createdAt}</Descriptions.Item>
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
                description="如对结论有异议，可在收到通知后 48 小时内发起申诉（拒赔通知书中含申诉渠道与法定时限）。"
              />
            )}
          </Card>

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
                    render: (v: number | null) => (v === null ? '-' : `¥${Number(v).toFixed(2)}`),
                  },
                  {
                    title: '合理费用',
                    dataIndex: 'itemValidAmount',
                    width: 120,
                    render: (v: number | null) => (v === null ? '-' : `¥${Number(v).toFixed(2)}`),
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
