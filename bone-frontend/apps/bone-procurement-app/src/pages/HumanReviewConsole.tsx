import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Tag, Typography } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { qualificationApi, type QualificationDTO } from '../services/qualificationApi';

/**
 * 人工终决工作台：AI 建议只作待决展示，采纳/驳回写回后端后刷新读回，不用本地乐观值。
 * 租户来自 URL 显式参数（不默认，缺失即 fail-closed）；租户/复核人由后端从登录态权威解析，前端不自报。
 */
const HumanReviewConsole: React.FC = () => {
  const [params] = useSearchParams();
  const qualificationId = params.get('qualificationId');
  const tenantId = params.get('tenantId');
  const [data, setData] = useState<QualificationDTO | null>(null);
  const [error, setError] = useState<string>('');
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(() => {
    if (!qualificationId || !tenantId) return;
    let active = true;
    setError('');
    qualificationApi
      .findOne(qualificationId, tenantId)
      .then((resp) => {
        if (active) setData(resp.data ?? null);
      })
      .catch(() => {
        if (active) {
          setData(null);
          setError('无法确认');
        }
      });
    return () => {
      active = false;
    };
  }, [qualificationId, tenantId]);

  useEffect(() => load(), [load]);

  const decide = async (accurate: boolean) => {
    if (!qualificationId || !tenantId) {
      setError('缺少证照编号或租户上下文，无法终决');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await qualificationApi.reviewOcr(qualificationId, { accurate });
      await load();
    } catch {
      setError('无法确认');
    } finally {
      setSubmitting(false);
    }
  };

  if (!qualificationId || !tenantId) {
    return (
      <Card title="人工终决工作台">
        <Alert
          type="warning"
          showIcon
          message="缺少证照编号或租户上下文，无法终决"
          description="请在 URL 中显式提供 qualificationId 与 tenantId，不接受默认租户。"
        />
      </Card>
    );
  }

  const final = data?.verifyStatus === 'VERIFIED' || data?.verifyStatus === 'REJECTED';

  return (
    <Card title="人工终决工作台" loading={!data && !error}>
      {error && <Alert type="error" message={error} showIcon />}
      {data && !error && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="证照编号">{data.id}</Descriptions.Item>
            <Descriptions.Item label="供应商">{data.supplierId}</Descriptions.Item>
            <Descriptions.Item label="证照类型">{data.certType}</Descriptions.Item>
            <Descriptions.Item label="OCR 自报结果">
              <Tag>待人工确认（不作自动结论）</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="终决状态">{data.verifyStatus}</Descriptions.Item>
            <Descriptions.Item label="复核人">{data.reviewedBy ?? '尚未复核'}</Descriptions.Item>
          </Descriptions>
          <Typography.Text type="secondary">复核人与租户由后端按登录态权威记录，前端不自报。</Typography.Text>
          <Space>
            <Button type="primary" onClick={() => decide(true)} loading={submitting} disabled={final}>
              确认 OCR 准确
            </Button>
            <Button danger onClick={() => decide(false)} loading={submitting} disabled={final}>
              驳回（OCR 不准确）
            </Button>
          </Space>
          {final && <Alert type="info" showIcon message="该证照已人工终决，不可由他人覆写。" />}
        </Space>
      )}
    </Card>
  );
};

export default HumanReviewConsole;
