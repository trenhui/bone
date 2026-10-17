import { useEffect, useState } from 'react';
import {
  Alert, Button, Collapse, Modal, Space, Spin, Table, Tag, Tooltip, Typography, message,
} from 'antd';
import { CopyOutlined, ExclamationCircleOutlined, CheckCircleOutlined, TableOutlined, PlusOutlined, SafetyCertificateOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { errorMessage, metadataEntityApi, metadataUiSchemaApi, type UiSchemaIssue } from '../services/metadataApi';
import type { EntityValidationIssue, MetaField, PublishPreview, PendingAuthorization } from '../types';
import { META_DELIVERY_RUNTIME } from '../types';
import { parseSchemaDiff } from '../utils/schemaDiff';

const { Text, Paragraph } = Typography;

const LEVEL_TAG: Record<string, { color: string; label: string }> = {
  ERROR: { color: 'red', label: '错误' },
  WARNING: { color: 'orange', label: '警告' },
  INFO: { color: 'blue', label: '提示' },
};

/** IAM 权限管理页深链（qiankun 子应用间唯一可靠的跨应用导航 = 整页指派，token 同源随行） */
const IAM_PERMISSIONS_URL = '/iam#/permissions';

/** 复制权限码：clipboard API 优先，非安全上下文（http 非 localhost）降级 execCommand */
const copyCode = async (code: string) => {
  try {
    await navigator.clipboard.writeText(code);
    message.success(`已复制：${code}`);
    return;
  } catch {
    /* 走兜底 */
  }
  const ta = document.createElement('textarea');
  ta.value = code;
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  const ok = document.execCommand('copy');
  document.body.removeChild(ta);
  if (ok) message.success(`已复制：${code}`);
  else message.error('复制失败，请手动选择复制');
};

/**
 * 发布摘要预览面板（UC-W7 摘要级，先行于 ADR-0039 R1 发布包）。
 *
 * 抽出自 PublishPreviewModal，使「实体详情 → 发布 Tab」与「顶栏发布 Modal」复用同一份内容。
 * 内容：静态校验问题（ERROR 阻断发布）+ 字段清单 + 物理 inspect 计划（将要执行的建表/加列语句）。
 */
interface Props {
  entityId: string;
  entityName?: string;
  onPublished?: () => void;
  /** 发布成功后「打开数据」回调（仅 RUNTIME 已发布实体有意义） */
  onOpenData?: () => void;
  /** 发布成功后「查看界面预览」回调（跳界面 Tab） */
  onViewInterface?: () => void;
  /** 发布成功后「调个人偏好」回调（跳列表显示 Tab——契约生效后顺手配个人视图） */
  onGoPersonal?: () => void;
  /** 发布成功后「继续加字段」回调（跳结构 Tab 并唤起字段向导） */
  onAddField?: () => void;
  /** 界面协议存在阻断错误时「去修复」回调（跳界面设计器 Tab） */
  onFixUiSchema?: () => void;
}

const PublishPreviewPanel: React.FC<Props> = ({
  entityId,
  entityName,
  onPublished,
  onOpenData,
  onViewInterface,
  onGoPersonal,
  onAddField,
  onFixUiSchema,
}) => {
  const [preview, setPreview] = useState<PublishPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [previewFailed, setPreviewFailed] = useState(false);
  const [justPublished, setJustPublished] = useState(false);
  /** 界面协议阻断问题（ERROR 级；发布前由后端 validate 兜底，与本地自检双轨） */
  const [schemaIssues, setSchemaIssues] = useState<UiSchemaIssue[] | null>(null);
  const [checkingSchema, setCheckingSchema] = useState(false);

  const refresh = () => {
    if (!entityId) return;
    setLoading(true);
    setPreviewFailed(false);
    setJustPublished(false);
    metadataEntityApi
      .publishPreview(entityId)
      .then((res) => {
        if (res.code === 200) setPreview(res.data);
        else setPreviewFailed(true);
      })
      .catch(() => setPreviewFailed(true))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityId]);

  // schema diff（PRD §5.4 规则 32：+ / ✎ / - 三色；MVP 仅 + / -，✎ 受后端发布保护不可达）
  const schemaDiff = parseSchemaDiff(preview?.physical ?? null, preview?.fields.map((f) => f.code) ?? []);

  // P1 onboarding：待授权权限应付清单（后端不感知授权态，恒返回逃逸动作细码全量）
  const pendingAuths: PendingAuthorization[] = preview?.pendingAuthorizations ?? [];
  const pendingCount = pendingAuths.length;

  const handlePublish = async () => {
    if (!entityId) return;
    /**
     * 界面协议硬门禁（发布链路最后一道闸，业界低代码平台「配置错误不静默上线」范式）：
     * 调后端 validate（跨真源一致性：字段/动作码是否真实存在），ERROR 级即阻断。
     * - 保存仍允许存草稿（不阻断保存），此处才拦——与原型 doPublish 的 realErrCount 门禁同语义；
     * - validate 自身失败（服务不可用）同样 fail-closed：宁可挡住也不放行未知状态的配置。
     */
    setCheckingSchema(true);
    let blocking: UiSchemaIssue[] = [];
    try {
      const res = await metadataUiSchemaApi.validate(entityId);
      if (res.code !== 200) throw new Error(errorMessage(res));
      blocking = (res.data ?? []).filter((i) => i.level === 'ERROR');
    } catch {
      setCheckingSchema(false);
      setSchemaIssues(null);
      message.error('界面协议校验失败（服务不可用），已阻止发布；请稍后重试');
      return;
    }
    setCheckingSchema(false);
    if (blocking.length > 0) {
      setSchemaIssues(blocking);
      message.error(`界面协议存在 ${blocking.length} 个错误，已阻止发布，请先到「界面设计器」修复`);
      return;
    }
    setSchemaIssues(null);
    const doPublish = async () => {
      setPublishing(true);
      try {
        const res = await metadataEntityApi.publish(entityId);
        if (res.code === 200) {
          message.success(`「${entityName || preview?.entityCode || entityId}」已发布`);
          setJustPublished(true);
          onPublished?.();
          refresh();
        } else {
          message.error(errorMessage(res));
        }
      } catch {
        message.error('发布失败，请确认服务可用');
      } finally {
        setPublishing(false);
      }
    };
    // L2 破坏性变更（删列）需二次确认（PRD §0.3 G6 L2 管控）
    if (preview && schemaDiff.l2Count > 0) {
      Modal.confirm({
        title: '本次发布包含破坏性变更（L2）',
        content: `将删除 ${schemaDiff.droppedColumns.join('、')} 列，历史数据将不可恢复。确认继续发布？`,
        okText: '确认发布',
        okButtonProps: { danger: true },
        cancelText: '取消',
        onOk: doPublish,
      });
      return;
    }
    doPublish();
  };

  const issueColumns: ColumnsType<EntityValidationIssue> = [
    { title: '级别', dataIndex: 'level', width: 70, render: (lv: string) => <Tag color={LEVEL_TAG[lv]?.color ?? 'default'}>{LEVEL_TAG[lv]?.label ?? lv}</Tag> },
    { title: '问题码', dataIndex: 'code', width: 190, ellipsis: true },
    { title: '说明', dataIndex: 'message', ellipsis: true },
  ];

  const pendingAuthColumns: ColumnsType<PendingAuthorization> = [
    { title: '动作', dataIndex: 'actionLabel', width: 140 },
    { title: '动作编码', dataIndex: 'actionCode', width: 170, ellipsis: true },
    { title: '需授予的权限码', dataIndex: 'code', ellipsis: true },
    {
      title: '操作',
      key: 'copy',
      width: 80,
      render: (_, r) => (
        <Button type="link" size="small" icon={<CopyOutlined />} onClick={() => copyCode(r.code)}>
          复制
        </Button>
      ),
    },
  ];

  const fieldColumns: ColumnsType<MetaField> = [
    { title: '发布动作', dataIndex: 'code', width: 100, render: (code: string) => {
      if (schemaDiff.addedColumns.includes(code)) return <Tag color="green" icon={<PlusOutlined />}>新增 L1</Tag>;
      if (schemaDiff.droppedColumns.includes(code)) return <Tag color="red">删除 L2</Tag>;
      return <Tag style={{ color: '#8c8c8c', borderColor: '#d9d9d9', background: '#fafafa' }}>稳定</Tag>;
    } },
    { title: '字段', dataIndex: 'displayName', width: 140, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 140, ellipsis: true },
    { title: '类型', dataIndex: 'type', width: 90 },
    { title: '长度', dataIndex: 'length', width: 70, render: (v: number | null) => v ?? '—' },
    { title: '必填', dataIndex: 'required', width: 60, render: (v: boolean) => (v ? <Tag color="red">是</Tag> : <Tag>否</Tag>) },
  ];


  return (
    <div>
      {justPublished && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 12 }}
          message="发布成功，闭环已完成"
          description={
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              <span>下一步：在「显示设置」Tab 微调展示，或在「数据」Tab 录入首条记录。下方为本次发布的变更摘要，可核对。</span>
              {/* P1 onboarding：发布成功≠功能可用——逃逸动作须动作级授权，发布当场告知缺什么，
                  避免用户到数据页看到置灰按钮再来问「为什么不能打款」 */}
              {pendingCount > 0 && (
                <Space size={8} wrap>
                  <SafetyCertificateOutlined style={{ color: '#faad14' }} />
                  <span>
                    另有 {pendingCount} 个逃逸动作（资金/外部副作用）需动作级授权后生效，授权前数据页对应按钮置灰不可用——见下方「待授权权限」。
                  </span>
                  <Button size="small" type="link" onClick={() => { window.location.assign(IAM_PERMISSIONS_URL); }}>
                    去授权
                  </Button>
                </Space>
              )}
            </Space>
          }
          action={
            <Space size={8} wrap>
              {onViewInterface && (
                <Button size="small" icon={<SettingOutlined />} onClick={onViewInterface}>
                  查看界面预览
                </Button>
              )}
              {onGoPersonal && (
                <Tooltip title="打开「列表显示」：调整仅自己可见的个人偏好（列显隐/密度/改名/个人视图）">
                  <Button size="small" icon={<UserOutlined />} onClick={onGoPersonal}>
                    调个人偏好
                  </Button>
                </Tooltip>
              )}
              {onOpenData && (
                <Button size="small" type="primary" icon={<TableOutlined />} onClick={onOpenData}>
                  打开数据
                </Button>
              )}
              {onAddField && (
                <Button size="small" icon={<PlusOutlined />} onClick={onAddField}>
                  继续加字段
                </Button>
              )}
            </Space>
          }
        />
      )}

      {schemaIssues && schemaIssues.length > 0 && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          message={`界面协议存在 ${schemaIssues.length} 个阻断错误，发布已阻止`}
          description={
            <div>
              {/* 全量展示不截断：阻断错误必须逐条可见可定位——静默隐藏第 9 条起的错误
                  正是 G33 禁令针对的「看不见也解释不了」反模式；超长时滚动而非截断。 */}
              <div style={{ maxHeight: 220, overflowY: 'auto' }}>
                {schemaIssues.map((it, i) => (
                  <div key={i} style={{ fontSize: 12, lineHeight: 1.8 }}>
                    <Tag color="red" style={{ fontSize: 10, marginRight: 6 }}>{it.code}</Tag>
                    {it.message}
                    {it.fieldCode && <Text code style={{ fontSize: 11, marginLeft: 6 }}>{it.fieldCode}</Text>}
                  </div>
                ))}
              </div>
              {onFixUiSchema && (
                <div style={{ marginTop: 6 }}>
                  <Button size="small" type="link" style={{ padding: 0 }} onClick={onFixUiSchema}>
                    去「界面设计器」修复 →
                  </Button>
                </div>
              )}
            </div>
          }
        />
      )}

      {loading && (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
          {/* tip 仅 nest/fullscreen 模式生效，独立 Spin 带 tip 会告警；文案作兄弟节点渲染，视觉等价 */}
          <div style={{ marginTop: 8, color: '#00000073', fontSize: 12 }}>正在生成发布预览...</div>
        </div>
      )}

      {!loading && previewFailed && (
        <Alert type="warning" showIcon message="预览服务暂不可用" description="无法获取变更摘要，仍可发布（后端发布期校验将兜底拦截漂移等问题）。" />
      )}

      {!loading && preview && (
        <>
          {preview.hasBlockingErrors ? (
            <Alert type="error" showIcon icon={<ExclamationCircleOutlined />} message="存在阻断性问题，发布按钮已禁用" description="请先解决下方「错误」级别的问题再发布。" style={{ marginBottom: 12 }} />
          ) : (
            <Alert
              type="success"
              showIcon
              icon={<CheckCircleOutlined />}
              message="校验通过，可发布"
              description={
                <Space size={8} wrap>
                  <span>字段 {preview.fields.length} 个 · 关系 {preview.relationCount} 条</span>
                  {schemaDiff.l1Count > 0 && <Tag color="green">新增 {schemaDiff.l1Count} 列（L1）</Tag>}
                  {schemaDiff.l2Count > 0 && <Tag color="red">删除 {schemaDiff.l2Count} 列（L2）</Tag>}
                  {schemaDiff.l1Count === 0 && schemaDiff.l2Count === 0 && <Tag>结构无变更</Tag>}
                </Space>
              }
              style={{ marginBottom: 12 }}
            />
          )}

          {/* P1：交付模式后果明示——把「发布成功却无表/无数据入口」前移为发布前可见信息 */}
          {preview.deliveryModeNotice && (
            <Alert
              type={preview.deliveryMode === META_DELIVERY_RUNTIME ? 'info' : 'warning'}
              showIcon
              message="交付模式"
              description={preview.deliveryModeNotice}
              style={{ marginBottom: 12 }}
            />
          )}

          <Collapse
            size="small"
            defaultActiveKey={preview.hasBlockingErrors ? ['issues'] : []}
            items={[
              {
                key: 'issues',
                label: `校验问题（${preview.validationIssues.length}）`,
                children: (
                  // rowKey 用内容组合键：antd 已弃用 rowKey 函数的 index 参数
                  <Table rowKey={(r) => `${r.code}-${r.fieldId ?? r.fieldName ?? ''}-${r.message}`} columns={issueColumns} dataSource={preview.validationIssues} pagination={false} size="small" />
                ),
              },
              {
                key: 'fields',
                label: `字段清单（${preview.fields.length}）`,
                children: (
                  <Table rowKey="id" columns={fieldColumns} dataSource={preview.fields} pagination={false} size="small" scroll={{ y: 220 }} />
                ),
              },
              // P1 onboarding：逃逸动作细码应付清单——发布前可见，授权不断链
              ...(pendingCount > 0
                ? [{
                  key: 'auth',
                  label: `待授权权限（${pendingCount}）`,
                  children: (
                    <>
                      <Paragraph type="secondary" style={{ marginBottom: 8 }}>
                          逃逸动作（资金/外部副作用）采用<b>动作级授权</b>（fail-closed，后端强制校验）：发布后需在 IAM「权限管理」授予以下权限码，数据页对应按钮才会启用。非逃逸的状态流转动作由实体级写权限覆盖，无需逐条授权。
                      </Paragraph>
                      <Table rowKey="code" columns={pendingAuthColumns} dataSource={pendingAuths} pagination={false} size="small" />
                      <Button
                        type="link"
                        style={{ marginTop: 8, paddingLeft: 0 }}
                        onClick={() => { window.location.assign(IAM_PERMISSIONS_URL); }}
                      >
                          前往 IAM 权限管理授予 →
                      </Button>
                    </>
                  ),
                }]
                : []),
              ...(preview.physical
                ? [{
                  key: 'physical',
                  label: `物理结构计划（${preview.physical.createTable ? '整表新建' : '增量对齐'}，${preview.physical.statements.length} 条 DDL）`,
                  children: (
                    <>
                      <Paragraph type="secondary" style={{ marginBottom: 8 }}>
                        <Text code>{preview.physical.status}</Text> {preview.physical.message}
                      </Paragraph>
                      <pre style={{ maxHeight: 180, overflow: 'auto', fontSize: 12, background: '#f6f6f6', padding: 8, borderRadius: 4 }}>
                        {preview.physical.statements.join('\n') || '（结构与模型一致，无需执行 DDL）'}
                      </pre>
                    </>
                  ),
                }]
                : []),
            ]}
          />

          <Space style={{ marginTop: 12 }}>
            <Button type="primary" danger loading={publishing || checkingSchema} disabled={loading || (!!preview && !preview.runnable)} onClick={handlePublish}>
              确认发布
            </Button>
            <Button onClick={refresh} disabled={loading}>刷新预览</Button>
          </Space>
        </>
      )}
    </div>
  );
};

export default PublishPreviewPanel;
