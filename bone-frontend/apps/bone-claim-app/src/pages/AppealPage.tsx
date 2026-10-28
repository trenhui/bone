import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined, FileProtectOutlined } from '@ant-design/icons';
import { PageHeader } from '../components/PageHeader';
import { appealApi, type AppealReviewResult, type AppealResult } from '../services/claimApi';

/**
 * 申诉复议页（A8）。
 *
 * - 结案/拒赔生效后 48h 内可发起申诉（幂等）；
 * - 复议结论：UPHELD 维持（→45）/ REOPEN 推翻重启审核（→41）。
 * 读走 `appealApi.latest`（controller），写走同 controller（自带 @PreAuthorize + 幂等）。
 */

const APPEAL_STATUS: Record<string, { color: string; label: string }> = {
  SUBMITTED: { color: 'processing', label: '已受理' },
  REVIEWED: { color: 'green', label: '已复议' },
  CLOSED: { color: 'default', label: '已关闭' },
};

const REVIEW_RESULT: Record<string, { color: string; label: string }> = {
  UPHELD: { color: 'red', label: '维持原结论' },
  REOPEN: { color: 'green', label: '推翻·重启审核' },
};

export function AppealPage(): ReactElement {
  const [searchParams, setSearchParams] = useSearchParams();
  const claimId = searchParams.get('claimId') ?? '1';
  const [input, setInput] = useState(claimId);
  const [appeal, setAppeal] = useState<AppealResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);
  const [reason, setReason] = useState('');
  const [reviewResult, setReviewResult] = useState<AppealReviewResult>('UPHELD');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await appealApi.latest(claimId);
      setAppeal(resp?.data ?? null);
    } catch {
      message.error('申诉查询失败');
    } finally {
      setLoading(false);
    }
  }, [claimId]);

  useEffect(() => {
    void load();
  }, [load]);

  const onSearch = () => {
    const id = input.trim();
    if (id) setSearchParams({ claimId: id });
  };

  const run = async (fn: () => Promise<unknown>, okMsg: string) => {
    setActing(true);
    try {
      await fn();
      message.success(okMsg);
      setReason('');
      void load();
    } catch (err) {
      const errorCode = (err as { errorCode?: string }).errorCode;
      if (errorCode === 'SC_CLAIM_APPEAL_EXPIRED') {
        message.error('申诉已超 48h 受理窗口');
      } else if (errorCode === 'SC_CLAIM_APPEAL_CONFLICT') {
        message.error('案件状态不可申诉（须已终态）');
      } else {
        message.error('操作失败');
      }
    } finally {
      setActing(false);
    }
  };

  const onSubmit = () =>
    run(() => appealApi.submit(claimId, { reason }), '申诉已受理');
  const onReview = () =>
    run(() => appealApi.review(claimId, { result: reviewResult, reason }), '复议结论已生效');

  const statusTag = appeal ? (APPEAL_STATUS[appeal.status] ?? { color: 'default', label: appeal.status }) : null;
  const resultTag = appeal?.reviewResult
    ? REVIEW_RESULT[appeal.reviewResult] ?? { color: 'default', label: appeal.reviewResult }
    : null;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="申诉复议" />
      <Card
        title="申诉记录"
        loading={loading}
        extra={
          <Space>
            <Input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onPressEnter={onSearch}
              placeholder="案件ID"
              style={{ width: 140 }}
            />
            <Button icon={<ReloadOutlined />} onClick={onSearch}>
              查询
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message="申诉须在结案/拒赔生效后 48h 内发起；复议维持则维持原结论，推翻则重启审核。"
          />

          {appeal ? (
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="申诉单号">{appeal.appealNo}</Descriptions.Item>
              <Descriptions.Item label="原状态">{appeal.originalStatus}</Descriptions.Item>
              <Descriptions.Item label="申诉状态">
                <Tag color={statusTag?.color}>{statusTag?.label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="复议结论">
                {resultTag ? <Tag color={resultTag.color}>{resultTag.label}</Tag> : '-'}
              </Descriptions.Item>
            </Descriptions>
          ) : (
            <Typography.Text type="secondary">该案件暂无申诉记录</Typography.Text>
          )}

          {!appeal && (
            <Card type="inner" title="发起申诉">
              <Space direction="vertical" style={{ width: '100%' }}>
                <Input.TextArea
                  rows={3}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="申诉理由（事实认定 + 条款依据 + 证据位置）"
                />
                <Button
                  type="primary"
                  icon={<FileProtectOutlined />}
                  loading={acting}
                  onClick={onSubmit}
                >
                  发起申诉
                </Button>
              </Space>
            </Card>
          )}

          {appeal?.status === 'SUBMITTED' && (
            <Card type="inner" title="复议结论">
              <Space direction="vertical" style={{ width: '100%' }}>
                <Select<AppealReviewResult>
                  value={reviewResult}
                  onChange={setReviewResult}
                  style={{ width: 240 }}
                  options={[
                    { value: 'UPHELD', label: '维持原结论（→45）' },
                    { value: 'REOPEN', label: '推翻·重启审核（→41）' },
                  ]}
                />
                <Input.TextArea
                  rows={3}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="复议说明"
                />
                <Button type="primary" loading={acting} onClick={onReview}>
                  提交复议结论
                </Button>
              </Space>
            </Card>
          )}
        </Space>
      </Card>
    </div>
  );
}
