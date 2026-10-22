import { useState, type ReactElement } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Alert, Button, Card, Input, Select, Space, Typography, message } from 'antd';
import { PauseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { claimApi, type HangUpType } from '../services/claimApi';

/**
 * 挂起/恢复（正交挂起机制，不改状态码；FRAUD 仅风控岗可恢复）。
 *
 * 动作走 `claimApi.hangUp`（ClaimCaseController，@PreAuthorize('claim:review:write')）：
 * - HANG_UP + X-Hangup-Type → 挂起
 * - RESUME（无 type）→ 恢复
 * 该接口只返回 Void，无状态查询出口，故本页为动作控制台（不缓存挂起态）。
 */

const REVIEWER_ID = 'reviewer-001';

const HANGUP_TYPES: { value: HangUpType; label: string }[] = [
  { value: 'EVIDENCE', label: 'EVIDENCE 缺件挂起' },
  { value: 'INVESTIGATION', label: 'INVESTIGATION 调查挂起（风控/金额异常）' },
  { value: 'TIMEOUT', label: 'TIMEOUT 阶段 SLA 超时挂起' },
  { value: 'FRAUD', label: 'FRAUD 疑似欺诈挂起（仅风控岗可恢复）' },
];

export function SuspendPage(): ReactElement {
  const [searchParams, setSearchParams] = useSearchParams();
  const claimId = searchParams.get('claimId') ?? '1';
  const [input, setInput] = useState(claimId);
  const [type, setType] = useState<HangUpType>('EVIDENCE');
  const [acting, setActing] = useState(false);

  const onSearch = () => {
    const id = input.trim();
    if (id) setSearchParams({ claimId: id });
  };

  const run = async (fn: () => Promise<unknown>, okMsg: string) => {
    setActing(true);
    try {
      await fn();
      message.success(okMsg);
    } catch (err) {
      const errorCode = (err as { errorCode?: string }).errorCode;
      if (errorCode === 'SC_CLAIM_HANGUP_CONFLICT') {
        message.error('挂起状态冲突（可能已挂起/已恢复）');
      } else {
        message.error('操作失败');
      }
    } finally {
      setActing(false);
    }
  };

  const onHangUp = () => run(() => claimApi.hangUp(claimId, type, REVIEWER_ID), `已挂起（${type}）`);
  const onResume = () => run(() => claimApi.hangUp(claimId, undefined, REVIEWER_ID), '已恢复');

  return (
    <div style={{ padding: 24 }}>
      <Card title="挂起 / 恢复（正交机制）">
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Space wrap>
            <Input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onPressEnter={onSearch}
              placeholder="案件ID"
              style={{ width: 160 }}
            />
            <Button onClick={onSearch}>查询</Button>
          </Space>

          <Alert
            type="warning"
            showIcon
            message="挂起为闭环正交状态（不改状态码）；FRAUD 挂起仅风控岗可恢复。"
          />

          <Space wrap>
            <Select<HangUpType>
              value={type}
              onChange={setType}
              style={{ width: 280 }}
              options={HANGUP_TYPES}
            />
            <Button
              danger
              icon={<PauseCircleOutlined />}
              loading={acting}
              onClick={onHangUp}
            >
              挂起
            </Button>
            <Button icon={<PlayCircleOutlined />} loading={acting} onClick={onResume}>
              恢复
            </Button>
          </Space>

          <Typography.Paragraph type="secondary">
            当前案件：{claimId}。该接口无挂起态查询出口，挂起/恢复结果以服务端为准；如需回显，请在核赔工作台接入挂起标记字段。
          </Typography.Paragraph>
        </Space>
      </Card>
    </div>
  );
}
