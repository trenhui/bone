import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { FileProtectOutlined, ReloadOutlined } from '@ant-design/icons';
import { PageHeader } from '../components/PageHeader';
import { CaseIdBar, CaseNotSelected } from '../components/CaseIdBar';
import { useClaimIdParam } from '../hooks/useClaimIdParam';
import { appealApi, type AppealReviewResult, type AppealResult } from '../services/claimApi';
import { claimStatusMeta } from '../services/claimStatus';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';

/**
 * 申诉复议页（A8）。
 *
 * - 结案/拒赔生效后 48h 内可发起申诉（幂等）；
 * - 复议结论：UPHELD 维持（→45）/ REOPEN 推翻重启审核（→41）。
 * 读走 `appealApi.latest`（controller），写走同 controller（自带 @PreAuthorize + 幂等）。
 *
 * <p><b>本次改造</b>：
 * <ul>
 *   <li>原代码按 `SC_CLAIM_APPEAL_EXPIRED` / `SC_CLAIM_APPEAL_CONFLICT` 分支，<b>两个码在
 *       `ClaimErrorCodes` 里都不存在</b>（后端只抛 `SC_CLAIM_STATUS_CONFLICT` 与
 *       `SC_CLAIM_APPEAL_NOT_FOUND`）⇒ 两条分支恒不命中，全部落「操作失败」。
 *       现按真实码分支；48h 超时由后端补上 `SC_CLAIM_APPEAL_EXPIRED`（422）后可精确提示。</li>
 *   <li>`originalStatus` 原样上屏（`CLOSED_REJECTED`），业务看不懂；改用状态字典取中文。</li>
 *   <li>占位 `?? '1'` 已去掉：未选案件不发请求，改渲染空态。</li>
 * </ul>
 */

const APPEAL_STATUS: Record<string, { color: string; label: string }> = {
  SUBMITTED: { color: 'processing', label: '已受理' },
  DECIDED: { color: 'green', label: '已复议' },
  REVIEWED: { color: 'green', label: '已复议' },
  CLOSED: { color: 'default', label: '已关闭' },
};

const REVIEW_RESULT: Record<string, { color: string; label: string }> = {
  UPHELD: { color: 'red', label: '维持原结论' },
  REOPEN: { color: 'green', label: '推翻·重启审核' },
};

const MSG_KEY = 'claim-appeal-action';

export function AppealPage(): ReactElement {
  const { claimId, input, setInput, submit: submitCaseId } = useClaimIdParam();
  const [appeal, setAppeal] = useState<AppealResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);
  /** 发起申诉理由 与 复议说明 是两个语义，分开存：共用一份会让撤销后残留上一步的文本 */
  const [submitReason, setSubmitReason] = useState('');
  const [reviewReason, setReviewReason] = useState('');
  const [reviewResult, setReviewResult] = useState<AppealReviewResult>('UPHELD');

  const load = useCallback(async () => {
    if (!claimId) {
      setAppeal(null);
      return;
    }
    setLoading(true);
    try {
      const resp = await appealApi.latest(claimId);
      setAppeal(resp?.data ?? null);
    } catch (err) {
      // 「该案无申诉记录」后端返回 null（200），不是 404；走到这里是真错误
      message.error(resolveErrorMessage(err, '申诉记录查询失败'));
    } finally {
      setLoading(false);
    }
  }, [claimId]);

  useEffect(() => {
    void load();
  }, [load]);

  const run = async (fn: () => Promise<unknown>, okMsg: string) => {
    setActing(true);
    try {
      await fn();
      message.success({ content: okMsg, key: MSG_KEY });
      setSubmitReason('');
      setReviewReason('');
      await load();
    } catch (err) {
      const code = resolveErrorCode(err);
      if (code === 'SC_CLAIM_STATUS_CONFLICT') {
        // 通用译文是「案件状态冲突（当前状态不允许该动作）」，在本场景指向不明确，补上具体前提
        message.error({ content: '案件尚未结案/拒赔生效，不能发起申诉', key: MSG_KEY });
      } else {
        message.error({ content: resolveErrorMessage(err, '操作失败'), key: MSG_KEY });
      }
    } finally {
      setActing(false);
    }
  };

  const onSubmit = () => run(() => appealApi.submit(claimId, { reason: submitReason }), '申诉已受理');
  const onReview = () =>
    run(() => appealApi.review(claimId, { result: reviewResult, reason: reviewReason }), '复议结论已生效');

  const statusTag = appeal ? (APPEAL_STATUS[appeal.status] ?? { color: 'default', label: appeal.status }) : null;
  const resultTag = appeal?.reviewResult
    ? REVIEW_RESULT[appeal.reviewResult] ?? { color: 'default', label: appeal.reviewResult }
    : null;
  const originalMeta = appeal ? claimStatusMeta(appeal.originalStatus) : null;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="申诉复议" />
      <Card
        title="申诉记录"
        loading={loading}
        extra={
          <CaseIdBar
            value={input}
            onChange={setInput}
            onSubmit={submitCaseId}
            loading={loading}
            extra={
              <Button
                icon={<ReloadOutlined />}
                onClick={() => void load()}
                disabled={!claimId}
                loading={loading}
              >
                刷新
              </Button>
            }
          />
        }
      >
        {!claimId ? (
          <CaseNotSelected hint="申诉按案件归集，输入案件ID后即可查询或发起" />
        ) : (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Alert
              type="info"
              showIcon
              message="申诉须在结案/拒赔生效后 48h 内发起；复议维持则维持原结论，推翻则重启审核。"
            />

            {appeal ? (
              <Descriptions bordered column={2} size="small">
                <Descriptions.Item label="申诉单号">{appeal.appealNo}</Descriptions.Item>
                <Descriptions.Item label="原状态">
                  {originalMeta ? (
                    <Tag color={originalMeta.color}>{originalMeta.label}</Tag>
                  ) : (
                    '-'
                  )}
                </Descriptions.Item>
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
                    value={submitReason}
                    onChange={(e) => setSubmitReason(e.target.value)}
                    placeholder="申诉理由（事实认定 + 条款依据 + 证据位置）"
                  />
                  <Popconfirm
                    title="确认发起申诉？"
                    description="案件将进入申诉中状态，48h 窗口内仅可受理一次。"
                    okText="确认发起"
                    cancelText="取消"
                    onConfirm={() => void onSubmit()}
                  >
                    <Button type="primary" icon={<FileProtectOutlined />} loading={acting}>
                      发起申诉
                    </Button>
                  </Popconfirm>
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
                    value={reviewReason}
                    onChange={(e) => setReviewReason(e.target.value)}
                    placeholder="复议说明"
                  />
                  <Popconfirm
                    title="确认提交复议结论？"
                    description="结论一经提交不可改写，如需再议须另起申诉。"
                    okText="确认提交"
                    cancelText="取消"
                    onConfirm={() => void onReview()}
                  >
                    <Button type="primary" loading={acting}>
                      提交复议结论
                    </Button>
                  </Popconfirm>
                </Space>
              </Card>
            )}
          </Space>
        )}
      </Card>
    </div>
  );
}
