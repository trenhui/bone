import React, { useEffect, useState } from 'react';
import { Alert, Card, Empty, Tag, Typography } from 'antd';
import { Link, useSearchParams } from 'react-router-dom';
import { contractReviewApi } from '../services/contractReviewApi';
import {
  disclosureErrorMessage,
  resolveDisclosureText,
  type AiDisclosureDTO,
} from '../types/contractReview';

/** 合同审查披露卡片页（最小入口）：AI 仅辅助，未知态原样显示「无法确认」。 */
const ContractReviewDisclosure: React.FC = () => {
  const [params] = useSearchParams();
  const reviewId = params.get('reviewId');
  const [data, setData] = useState<AiDisclosureDTO | null>(null);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    if (!reviewId) return;
    let active = true;
    setError('');
    contractReviewApi
      .getDisclosure(reviewId)
      .then((resp) => {
        if (active && resp.data) setData(resp.data);
      })
      .catch((err: unknown) => {
        // fail-closed：拉取失败不渲染任何自动结论，且显示错误态，避免永久 loading（SUGGESTION-2）。
        if (active) setError(disclosureErrorMessage(err));
      });
    return () => {
      active = false;
    };
  }, [reviewId]);

  if (!reviewId) {
    return (
      <Card title="合同审查披露">
        <Empty description="请在 URL 中提供 reviewId" />
      </Card>
    );
  }

  return (
    <Card title="合同审查披露" loading={!data && !error}>
      {error && <Alert type="error" message={error} showIcon />}
      {data && !error && (
        <>
          <Typography.Paragraph>审查编号：{data.reviewId}</Typography.Paragraph>
          <Typography.Paragraph>
            AI 参与情况：
            <Tag color={data.canConfirm ? 'green' : 'default'}>
              {resolveDisclosureText(data.aiAvailableCode, data.disclosureText)}
            </Tag>
            <span>（原始码：{data.aiAvailableCode}）</span>
          </Typography.Paragraph>
          {data.modelVersionSnapshot && (
            <Typography.Text type="secondary">
              模型版本快照：{data.modelVersionSnapshot}（仅版本快照，不作参与证据）
            </Typography.Text>
          )}
          <Typography.Paragraph>
            <Link to={`/contract-hitl?reviewId=${encodeURIComponent(reviewId)}`}>
              进入人工终决
            </Link>
          </Typography.Paragraph>
        </>
      )}
    </Card>
  );
};

export default ContractReviewDisclosure;
