import React, { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Space, Table, Tag, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { readinessApi } from '../services/readinessApi';
import { governanceApi } from '../services/governanceApi';
import { readinessText, verdictColor, verdictTextOrRaw, type AiReadinessDTO } from '../types/readinessView';
import {
  GOVERNANCE_POSTURE_FETCH_ERROR,
  postureVerdictColor,
  postureVerdictTextOrRaw,
  type CapabilityPostureEntryDTO,
  type GovernancePostureDTO,
} from '../types/governancePostureView';

/** AI 治理态势只读页：聚合能力裁决/漂移/监督判词与就绪度事实，不触发任何写操作。 */
const GovernanceStatus: React.FC = () => {
  const [data, setData] = useState<AiReadinessDTO | null>(null);
  const [posture, setPosture] = useState<GovernancePostureDTO | null>(null);
  const [postureError, setPostureError] = useState<string>('');

  useEffect(() => {
    let active = true;
    readinessApi
      .getReadiness()
      .then((resp) => {
        if (active) setData(resp.data ?? null);
      })
      .catch(() => {
        if (active) setData(null);
      });
    governanceApi
      .getPosture()
      .then((resp) => {
        if (active) setPosture(resp.data ?? null);
      })
      .catch(() => {
        if (active) {
          setPosture(null);
          setPostureError(GOVERNANCE_POSTURE_FETCH_ERROR);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const columns = [
    { title: '能力码', dataIndex: 'code', key: 'code' },
    { title: '名称', dataIndex: 'displayName', key: 'displayName' },
    { title: '状态', dataIndex: 'state', key: 'state' },
    { title: '准入', dataIndex: 'decisionAllowed', key: 'decisionAllowed' },
    { title: '漂移', dataIndex: 'driftVerdict', key: 'driftVerdict' },
    { title: '监督', dataIndex: 'oversightVerdict', key: 'oversightVerdict' },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Card title="AI 治理态势">
        {postureError && <Alert type="warning" message={postureError} showIcon />}
        {posture && !postureError && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="汇总判词">
              <Tag color={postureVerdictColor(posture.verdict)}>
                {postureVerdictTextOrRaw(posture.verdict)}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="需关注 / 不可判定能力">
              {posture.attentionCount} / {posture.undeterminedCount}
            </Descriptions.Item>
            <Descriptions.Item label="能力明细">
              <Table<CapabilityPostureEntryDTO>
                size="small"
                pagination={false}
                rowKey="code"
                dataSource={posture.capabilities}
                columns={columns}
              />
            </Descriptions.Item>
            <Descriptions.Item label="合同审查披露">
              <Link to="/contract-review">查看合同审查披露</Link>
              <Typography.Text type="secondary">（披露按单次审查查询，此处仅提供入口）</Typography.Text>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>
      <Card title="AI 治理状态">
        {data && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="就绪度判词">
              <Tag color={verdictColor(data.verdict)}>{verdictTextOrRaw(data.verdict)}</Tag>
              <span>（{readinessText(data.verdict)}）</span>
            </Descriptions.Item>
            <Descriptions.Item label="治理能力">
              {data.governanceEnabled ? <Tag color="blue">已启用</Tag> : <Tag>未启用</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="全域总闸">
              {data.killSwitchEngaged ? <Tag color="red">已停用 AI</Tag> : <Tag color="green">正常运行</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="说明">
              <Typography.Text type="secondary">{data.authorizationNotice}</Typography.Text>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>
    </Space>
  );
};

export default GovernanceStatus;
