import React from 'react';
import { Alert, Button, Card, Space, Tag, Typography, message } from 'antd';
import { supplierRiskApi } from '../services/supplierRiskApi';

/**
 * 供应商准入工作台：证照 OCR 人工复核与准入终决入口。
 * 身份只由后端登录态裁决，请求体不含 tenantId/operator/reviewer（fail-closed）。
 */
interface SupplierOnboardingProps {
  /** 证照是否已过期：过期证书不得进入人工复核终决链路。 */
  certExpired?: boolean;
}

const SupplierOnboarding: React.FC<SupplierOnboardingProps> = ({ certExpired = true }) => {
  const onReview = async () => {
    const r = await supplierRiskApi.reviewOcr({
      qualificationId: 'Q-DEMO',
      accurate: true,
      reason: '人工复核无误',
    });
    if (!r.success) message.error(r.degradedReason ?? '无法确认');
    else message.success('复核已记录');
  };

  return (
    <div>
      <Typography.Title level={3}>供应商准入</Typography.Title>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Card title="证照 OCR 复核">
          {certExpired ? (
            <Alert
              type="warning"
              message="证照已过期"
              description="过期证照不进入人工复核终决，需供应商更新后重新提交"
            />
          ) : (
            <Tag color="green">证照有效</Tag>
          )}
          <Button onClick={onReview} disabled={certExpired}>
            复核通过
          </Button>
        </Card>
      </Space>
    </div>
  );
};

export default SupplierOnboarding;
