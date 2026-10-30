import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { Alert, Button, Card, Descriptions, Select, Space, Tag, Typography, message } from 'antd';
import { PauseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { PageHeader } from '../components/PageHeader';
import { CaseIdBar, CaseNotSelected } from '../components/CaseIdBar';
import { useClaimIdParam } from '../hooks/useClaimIdParam';
import { claimApi, type ClaimWithItemsProjection, type HangUpType } from '../services/claimApi';
import { claimStatusMeta } from '../services/claimStatus';
import { resolveErrorCode, resolveErrorMessage } from '@bone/shared-utils';

/**
 * 挂起/恢复（正交挂起机制，不改状态码；FRAUD 仅风控岗可恢复）。
 *
 * 动作走 `claimApi.hangUp`（ClaimCaseController，@PreAuthorize('claim:review:write')）：
 * - HANG_UP + X-Hangup-Type → 挂起
 * - RESUME（无 type）→ 恢复
 *
 * <p><b>本次改造</b>：
 * <ul>
 *   <li>原页面自称「该接口无挂起态查询出口，故本页为动作控制台」，但 `GET /claims/{id}` 的
 *       `ClaimWithItemsProjection` 本来就带 `hangUpStatus` —— 于是「已挂起时再点挂起」这种必然
 *       409 的动作全程可点。现在先读真实挂起标记，按钮按状态互斥启用。</li>
 *   <li>原代码按 `SC_CLAIM_HANGUP_CONFLICT` 分支，该码在 `ClaimErrorCodes` 中<b>不存在</b>（后端抛的是
 *       `SC_CLAIM_STATUS_CONFLICT`）⇒ 分支恒不命中、全部落「操作失败」。现按真实码 + 码表文案。</li>
 *   <li>占位 `?? '1'` 已去掉：未选案件不发请求、不显示可点动作。</li>
 * </ul>
 *
 * <p><b>已知边界</b>：详情投影只带 `hang_up_status`（0/1），不带 `hang_up_type`，故本页不显示挂起类型；
 * FRAUD 挂起能否恢复由服务端按角色判定，前端不做也不该做前置放行。
 */

const HANGUP_TYPES: { value: HangUpType; label: string }[] = [
  { value: 'EVIDENCE', label: 'EVIDENCE 缺件挂起' },
  { value: 'INVESTIGATION', label: 'INVESTIGATION 调查挂起（风控/金额异常）' },
  { value: 'TIMEOUT', label: 'TIMEOUT 阶段 SLA 超时挂起' },
  { value: 'FRAUD', label: 'FRAUD 疑似欺诈挂起（仅风控岗可恢复）' },
];

/** 案件头（详情接口按明细分行返回，取首行即头信息）。 */
type ClaimHead = Pick<ClaimWithItemsProjection, 'claimId' | 'claimNo' | 'status' | 'hangUpStatus'>;

const MSG_KEY = 'claim-hangup-action';

export function SuspendPage(): ReactElement {
  const { claimId, input, setInput, submit } = useClaimIdParam();
  const [type, setType] = useState<HangUpType>('EVIDENCE');
  const [head, setHead] = useState<ClaimHead | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    if (!claimId) {
      setHead(null);
      return;
    }
    setLoading(true);
    try {
      const resp = await claimApi.detail(claimId);
      const first = (resp?.data ?? [])[0];
      setHead(
        first
          ? {
            claimId: first.claimId,
            claimNo: first.claimNo,
            status: first.status,
            hangUpStatus: first.hangUpStatus,
          }
          : null,
      );
    } catch (err) {
      setHead(null);
      message.error(resolveErrorMessage(err, '案件信息查询失败'));
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
      // 动作成功后必须回读：挂起标记是服务端事实，沿用旧值会让下一次点击落到错误的启用态
      await load();
    } catch (err) {
      if (resolveErrorCode(err) === 'SC_CLAIM_STATUS_CONFLICT') {
        message.error({
          content: '案件挂起状态已变化（可能已被他人挂起/恢复），请刷新后重试',
          key: MSG_KEY,
        });
      } else {
        message.error({ content: resolveErrorMessage(err, '操作失败'), key: MSG_KEY });
      }
    } finally {
      setActing(false);
    }
  };

  const onHangUp = () => run(() => claimApi.hangUp(claimId, type), `已挂起（${type}）`);
  const onResume = () => run(() => claimApi.hangUp(claimId, undefined), '已恢复');

  const hung = head?.hangUpStatus === 1;
  const statusMeta = head ? claimStatusMeta(head.status) : null;
  const terminal = statusMeta?.terminal ?? false;

  return (
    <div style={{ padding: 24 }}>
      <PageHeader title="挂起/恢复" />
      <Card
        title="挂起操作"
        loading={loading}
        extra={<CaseIdBar value={input} onChange={setInput} onSubmit={submit} loading={loading} />}
      >
        {!claimId ? (
          <CaseNotSelected hint="挂起按案件执行，输入案件ID后即可查看当前挂起状态并操作" />
        ) : (
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions bordered size="small" column={3}>
              <Descriptions.Item label="案件号">{head?.claimNo ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="当前状态">
                {statusMeta ? <Tag color={statusMeta.color}>{statusMeta.label}</Tag> : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="挂起标记">
                {head == null ? '-' : hung ? <Tag color="orange">已挂起</Tag> : <Tag>未挂起</Tag>}
              </Descriptions.Item>
            </Descriptions>

            <Alert
              type="warning"
              showIcon
              message="挂起为闭环正交状态（不改状态码）：挂起期间案件仍停在原阶段，只停止计时与流转；FRAUD 挂起仅风控岗可恢复。"
            />

            {terminal && (
              <Alert
                type="info"
                showIcon
                message="该案件已进入终态：终态案件的挂起不会改变其状态码，如需重新处理请走申诉复议。"
              />
            )}

            <Space wrap>
              <Select<HangUpType>
                value={type}
                onChange={setType}
                style={{ width: 300 }}
                options={HANGUP_TYPES}
                disabled={hung}
              />
              <Button
                danger
                icon={<PauseCircleOutlined />}
                loading={acting}
                disabled={hung}
                onClick={() => void onHangUp()}
              >
                挂起
              </Button>
              <Button
                icon={<PlayCircleOutlined />}
                loading={acting}
                disabled={!hung}
                onClick={() => void onResume()}
              >
                恢复
              </Button>
              {hung && (
                <Typography.Text type="secondary">
                  已挂起，须先恢复才能重新挂起
                </Typography.Text>
              )}
            </Space>
          </Space>
        )}
      </Card>
    </div>
  );
}
