import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Input, Select, Space, Typography } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { contractReviewApi } from '../services/contractReviewApi';
import {
  disclosureErrorMessage,
  resolveDisclosureText,
  type AiDisclosureDTO,
} from '../types/contractReview';
import {
  buildOverridePayload,
  canFinalize,
  displayRiskLevel,
  FINAL_RISK_LEVELS,
  type ContractReviewReportDTO,
  type FinalRiskLevel,
} from '../types/contractHitlView';

/**
 * 合同审查人工终决工作台：AI 结论只作待复核建议；复核人由后端从登录态权威解析，
 * 前端只提交理由与终决风险等级，并在写回后重新读取后端报告，不使用本地乐观终值。
 */
const ContractHitlConsole: React.FC = () => {
  const [params] = useSearchParams();
  const reviewId = params.get('reviewId');
  const [report, setReport] = useState<ContractReviewReportDTO | null>(null);
  const [disclosure, setDisclosure] = useState<AiDisclosureDTO | null>(null);
  const [error, setError] = useState('');
  const [reason, setReason] = useState('');
  const [risk, setRisk] = useState<FinalRiskLevel>('UNKNOWN');
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(() => {
    if (!reviewId) return;
    let active = true;
    setError('');
    Promise.all([
      contractReviewApi.getReport(reviewId),
      contractReviewApi.getDisclosure(reviewId),
    ])
      .then(([reportResp, disclosureResp]) => {
        if (active) {
          setReport(reportResp.data ?? null);
          setDisclosure(disclosureResp.data ?? null);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setReport(null);
          setDisclosure(null);
          setError(disclosureErrorMessage(err));
        }
      });
    return () => {
      active = false;
    };
  }, [reviewId]);

  useEffect(() => load(), [load]);

  if (!reviewId) {
    return (
      <Card title="合同人工终决">
        <Alert
          type="warning"
          showIcon
          message="缺少审查编号，无法终决"
          description="请在 URL 中显式提供 reviewId；不接受默认审查或自报结论。"
        />
      </Card>
    );
  }

  const finalize = async () => {
    const payload = buildOverridePayload(reason, risk);
    if (!payload) {
      setError('请填写终决理由并选择终决风险等级');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await contractReviewApi.override(reviewId, payload);
      load();
    } catch {
      setError('无法确认');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card title="合同人工终决" loading={!report && !error}>
      {error && <Alert type="error" message={error} showIcon />}
      {report && !error && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="审查编号">{report.reviewId}</Descriptions.Item>
            <Descriptions.Item label="合同编号">{report.contractNo}</Descriptions.Item>
            <Descriptions.Item label="审查状态">{report.status}</Descriptions.Item>
            <Descriptions.Item label="AI 建议风险">{displayRiskLevel(report.riskLevel)}</Descriptions.Item>
            <Descriptions.Item label="AI 参与情况">
              {disclosure
                ? resolveDisclosureText(
                  disclosure.aiAvailableCode,
                  disclosure.disclosureText,
                )
                : '无法确认'}
              {disclosure ? `（原始码：${disclosure.aiAvailableCode}）` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="人工终决状态">
              {report.humanFinalized ? '已终决' : '待人工终决'}
            </Descriptions.Item>
            <Descriptions.Item label="人工终决理由">{report.humanReason ?? '尚未终决'}</Descriptions.Item>
          </Descriptions>

          <Typography.Text type="secondary">
            AI 结论仅作待复核建议；复核人与租户由后端按登录态权威记录，前端不自报。
          </Typography.Text>

          {canFinalize(report) ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Input.TextArea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="请填写人工终决理由"
                rows={3}
              />
              <Select
                value={risk}
                onChange={setRisk}
                options={FINAL_RISK_LEVELS.map((level) => ({
                  value: level,
                  label: displayRiskLevel(level),
                }))}
              />
              <Button type="primary" loading={submitting} onClick={finalize}>
                提交人工终决
              </Button>
            </Space>
          ) : (
            <Alert type="info" showIcon message="该审查已人工终决，不可由他人覆写。" />
          )}
        </Space>
      )}
    </Card>
  );
};

export default ContractHitlConsole;
