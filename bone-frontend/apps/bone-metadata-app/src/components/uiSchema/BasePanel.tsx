/**
 * 模板跟随面板（协议 §11.4 收敛形态：租户确认式 pull，永不自动推送）。
 *
 * 业界对标：Salesforce 受管包升级 / NocoBase 模板版本化——运营方发版 → 租户确认升级，
 * 从不自动覆盖租户自定义（多租户下自动推送 = 允许模板运营方写全租户生产数据）。
 *
 * 三步（后端已有点，前端此前只字未接）：
 *   1. 检查更新 base-update：digest 只含计数（sections/rules/actions 增减），不返全量；
 *   2. 归并预览 merge-preview：服务端算三路归并（base.old → base.new → instance.exclusive），
 *      租户未改过的段自动取新版，改过的保留实例值 + WARNING；
 *   3. 确认合入 merge-confirm：@Version 再断言（防合入期间有人编辑），
 *      409 = 模板又发版或草稿被并发修改 → 重新跑 preview。
 *
 * 前置诚实门（协议 §11.4）：模板资产未完整（无 uiSchema 快照）时 base-update 返回
 * reason=TEMPLATE_ASSET_INCOMPLETE，横幅 disabled + tooltip 明示，不静默开放半吊子功能。
 */
import { useState } from 'react';
import { Alert, Button, Descriptions, Empty, Space, Switch, Tag, Tooltip, Typography } from 'antd';
import { CloudDownloadOutlined, CloudUploadOutlined, MergeOutlined, SyncOutlined } from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import type { MergePreview, TemplateUpdateCheck } from '../../services/metadataApi';
import { metadataUiSchemaApi } from '../../services/metadataApi';
import { updateBase } from './schemaOps';

const { Text } = Typography;

interface Props {
  entityId: string;
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  /** 当前乐观锁版本（merge-confirm 走 If-Match 头） */
  lockVersion: number;
  /** 合入成功后重新加载（服务端已写回草稿，前端以服务端为准） */
  onMerged: () => void;
}

export default function BasePanel({ entityId, schema, onChange, lockVersion, onMerged }: Props): React.ReactElement {
  const base = schema.base ?? {};
  const [checking, setChecking] = useState(false);
  const [check, setCheck] = useState<TemplateUpdateCheck | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [preview, setPreview] = useState<MergePreview | null>(null);
  const [confirming, setConfirming] = useState(false);

  const runCheck = async () => {
    setChecking(true);
    try {
      const res = await metadataUiSchemaApi.baseUpdate(entityId);
      setCheck(res.code === 200 ? res.data ?? null : null);
      if (res.code !== 200) setPreview(null);
    } finally {
      setChecking(false);
    }
  };

  const runPreview = async () => {
    setPreviewing(true);
    try {
      const res = await metadataUiSchemaApi.mergePreview(entityId);
      setPreview(res.code === 200 ? res.data ?? null : null);
    } finally {
      setPreviewing(false);
    }
  };

  const runConfirm = async (acceptConflicts: boolean) => {
    if (!preview) return;
    setConfirming(true);
    try {
      const res = await metadataUiSchemaApi.mergeConfirm(entityId, acceptConflicts, preview.templateVersion, lockVersion);
      if (res.code === 200) {
        setPreview(null);
        setCheck(null);
        onMerged();
      } else if (res.code === 409) {
        // 模板又发版或草稿被并发修改：旧 preview 作废，重跑（协议 §8.1 故障模式）
        setPreview(null);
        await runCheck();
      }
    } finally {
      setConfirming(false);
    }
  };

  if (!base.templateCode) {
    return (
      <div>
        <Alert
          type="info" showIcon style={{ marginBottom: 12 }}
          message="该实体不是从模板实例化的"
          description="从场景模板创建的实体才会带 base 指针（templateCode + baseVersion）。指针在实例化时由服务端写入，此处只读展示与跟随操作。"
        />
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无模板指针，无需跟随更新" style={{ padding: '18px 0' }} />
      </div>
    );
  }

  return (
    <div>
      <Descriptions size="small" column={2} style={{ marginBottom: 12 }} bordered>
        <Descriptions.Item label="模板编码">{base.templateCode}</Descriptions.Item>
        <Descriptions.Item label="实例化版本">{base.baseVersion ?? '—'}</Descriptions.Item>
        <Descriptions.Item label="跟随模式">
          <Space size={4}>
            <Switch
              size="small" checked={base.followMode === 'notify'}
              onChange={(v) => onChange(updateBase(schema, { followMode: v ? 'notify' : 'pinned' }))}
            />
            <Text style={{ fontSize: 12 }}>{base.followMode === 'notify' ? 'notify · 有新版本时提醒' : 'pinned · 不提醒'}</Text>
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="模板当前版本">{check?.currentVersion ?? '（点「检查更新」获取）'}</Descriptions.Item>
      </Descriptions>

      <Space wrap style={{ marginBottom: 12 }}>
        <Button icon={<SyncOutlined spin={checking} />} loading={checking} onClick={runCheck}>
          检查更新
        </Button>        <Tooltip title="三路归并：base.old → base.new → instance.exclusive；租户未改过的段自动取新版">
          <Button icon={<MergeOutlined />} loading={previewing} onClick={runPreview} disabled={!check?.hasUpdate}>
            归并预览
          </Button>
        </Tooltip>
      </Space>

      {check && !check.hasUpdate && check.reason === 'TEMPLATE_ASSET_INCOMPLETE' && (
        <Alert
          type="warning" showIcon style={{ marginBottom: 12 }}
          message="模板资产不完整，跟随入口暂不开放"
          description="该模板缺少 uiSchema 快照（历史上 instantiate 丢 enumValues/validationRules 的前科）。资产完整性补齐前不静默开放半吊子功能——请联系模板运营方补打包。"
        />
      )}

      {check?.hasUpdate && (
        <Alert
          type="info" showIcon style={{ marginBottom: 12 }}
          icon={<CloudUploadOutlined />}
          message={`模板已发新版：${check.baseVersion} → ${check.currentVersion}`}
          description={
            <div>
              <div style={{ marginBottom: 6 }}>
                变更规模（digest，只含计数）：
                <Tag color="blue" style={{ marginLeft: 6 }}>分区 {check.digest?.sections ?? 0}</Tag>
                <Tag color="blue" style={{ marginLeft: 4 }}>规则 {check.digest?.rules ?? 0}</Tag>
                <Tag color="blue" style={{ marginLeft: 4 }}>动作 {check.digest?.actions ?? 0}</Tag>
              </div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                合入策略：租户未改过的 section/rule 自动取新版；改过的保留实例值并标 WARNING（绝不静默覆盖）。
              </Text>
            </div>
          }
        />
      )}

      {preview && (
        <div style={{ border: '1px solid #e6eaf0', borderRadius: 10, padding: 12, background: '#fff' }}>
          <Space wrap style={{ marginBottom: 8 }}>
            <Tag color="green">可自动合入 {preview.autoMerged.length}</Tag>
            <Tag color="orange">冲突待确认 {preview.conflicts.length}</Tag>
            <Text type="secondary" style={{ fontSize: 12 }}>模板版本 {preview.templateVersion}</Text>
          </Space>

          {preview.autoMerged.length === 0 && preview.conflicts.length === 0 && (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无差异可合入" style={{ padding: '10px 0' }} />
          )}

          {preview.autoMerged.slice(0, 20).map((it, i) => (
            <div key={`a${i}`} style={{ fontSize: 12, padding: '3px 0', color: '#389e0d' }}>
              ✓ 自动合入 {it.path}（{it.kind}）
            </div>
          ))}
          {preview.conflicts.slice(0, 20).map((it, i) => (
            <div key={`c${i}`} style={{ fontSize: 12, padding: '3px 0', color: '#d46b08' }}>
              ⚠ 冲突 {it.path}（{it.kind}）：模板新版与你的修改不同，确认合入将保留你的版本并留 WARNING
            </div>
          ))}

          <Space style={{ marginTop: 10 }}>
            <Button
              type="primary" icon={<CloudDownloadOutlined />} loading={confirming}
              onClick={() => runConfirm(preview.conflicts.length > 0)}
            >
              {preview.conflicts.length > 0 ? '确认合入（冲突保留我的版本）' : '确认合入'}
            </Button>
            {preview.conflicts.length > 0 && (
              <Tooltip title="冲突项改用模板新版（你的修改将被覆盖，请谨慎）">
                <Button danger loading={confirming} onClick={() => runConfirm(true)}>
                  冲突也取新版
                </Button>
              </Tooltip>
            )}
          </Space>
        </div>
      )}
    </div>
  );
}
