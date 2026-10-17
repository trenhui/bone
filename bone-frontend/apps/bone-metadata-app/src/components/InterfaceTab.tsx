import { useEffect, useMemo, useState } from 'react';
import {
  Alert, Button, Checkbox, Dropdown, Empty, Form, Input, InputNumber, Modal, Segmented, Select, Space, Switch,
  Table, Tag, Tooltip, Typography, DatePicker, message,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  AimOutlined, ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, EditOutlined, EyeOutlined,
  PlusOutlined, QuestionCircleOutlined, ReloadOutlined, SettingOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MetaField } from '../types';
import { FIELD_TYPE_MAP, parseEnumValues, RUNTIME_READONLY_FIELDS } from '../types';
import {
  activeOverlay, activeViewOf, createView, defaultBundle, defaultOverlay, deleteView,
  fieldsOutsideContract, isEnumField, isRenderSupported, projectRuntimeColumns, renameView,
  reorderOverlay, resetActiveViewToDefault, switchView, tableSizeOf, updateActiveView,
  type PersonalView, type UiOverlayBundle,
} from '../utils/columnProjection';
import { fetchOverlay, persistOverlay, resetOverlay } from '../utils/overlaySync';
import { useBoneUiSchema } from '../hooks/useBoneUiSchema';

const { Text } = Typography;

/**
 * 列表显示 Tab（个人偏好层 · P0 定位修正 + P1 真源收敛 + v3 多个人视图）。
 *
 * 关键：预览列与运行时数据列都由同一个 `projectRuntimeColumns(fields, overlay, 契约)` 投影得到，
 * 因此「界面预览列 ≡ 发布后数据列」在结构上成立（PRD v0.32 §0.6.3）。
 *
 * 与「界面设计器」的分层（对标 Salesforce Search Layouts 组织级 + List View 个人偏好）：
 * - 契约层（设计器 ui-schema views.list，发布后生效）：列集合/列序/列宽/固定列/列标题；
 * - 偏好层（本页 overlay，即时生效）：个人显隐/密度/个人改名/个人列序。
 * 任一维度可隐藏列（展示需两层都放行）；契约存在时列序/列宽/固定列以契约为准，
 * 本页的 ↑↓ 置灰并引导去设计器——避免「两处都能配、用户不知谁生效」的认知重叠。
 *
 * 多个人视图（v3 bundle，对标 Airtable / Notion personal views）：偏好按「命名视图」
 * 组织（如「我的待审」「大额专项」），各自保存显隐/列序/密度/改名，切换即生效；
 * 存储为服务端不透明 blob（键含 userId，前端传不了他人键），v2 单套存量无损迁移。
 *
 * 覆盖存储：服务端优先 + localStorage 离线缓存（0042 · P2-④，hydrate 策略见 utils/overlaySync.ts）。
 */
interface Props {
  entityId?: string | null;
  entityCode: string;
  fields: MetaField[];
  /** 空态 CTA「去结构添加第一个字段」：由 EntityDetail 注入，避免反向依赖路由 */
  onAddField?: () => void;
  /** UID-11：定位某列对应的字段 —— 跳到「结构」Tab 并高亮该字段 */
  onLocateField?: (code: string) => void;
  /** strict 集合引导：未列入契约的字段，跳「界面设计器 → 列表」加入 */
  onOpenDesigner?: () => void;
}

type ViewMode = 'list' | 'form';

const InterfaceTab: React.FC<Props> = ({ entityId = null, entityCode, fields, onAddField, onLocateField, onOpenDesigner }) => {
  // 仅用户可见的数据列参与投影（排除系统只读列，与数据 Tab 口径一致）
  const baseFields = useMemo(
    () => fields.filter((f) => !RUNTIME_READONLY_FIELDS.has(f.code)),
    [fields],
  );

  // 契约层：执行期 ui-schema（published 快照优先）的列表视图；无契约时整页回落纯偏好模式
  const uiSchemaPipe = useBoneUiSchema(entityId);
  const contractListView = useMemo(
    () =>
      uiSchemaPipe.executionSchema?.views.list?.columns?.length
        ? uiSchemaPipe.executionSchema.views.list
        : null,
    [uiSchemaPipe.executionSchema],
  );
  const contractByCode = useMemo(
    () => new Map((contractListView?.columns ?? []).map((c) => [c.fieldCode, c])),
    [contractListView],
  );
  /** strict 集合：未列入契约的字段不出现在运行时列表——显式提示 + 跳设计器，不静默 */
  const outsideContract = useMemo(
    () => (contractListView ? fieldsOutsideContract(baseFields, contractListView) : []),
    [baseFields, contractListView],
  );

  const [mode, setMode] = useState<'config' | 'preview'>('config');
  const [view, setView] = useState<ViewMode>('list');
  /** 个人偏好 bundle（v3 多视图）；null 仅在 hydrate 前瞬间存在 */
  const [bundle, setBundle] = useState<UiOverlayBundle | null>(null);
  /** 视图管理弹窗（create/rename/delete 三态复用） */
  const [viewModal, setViewModal] = useState<{ mode: 'create' | 'rename' | 'delete'; id?: string } | null>(null);
  const [viewName, setViewName] = useState('');
  const [viewCopy, setViewCopy] = useState(true);

  const defaultOv = useMemo(() => defaultOverlay(baseFields), [baseFields]);

  useEffect(() => {
    // 服务端优先 hydrate（0042）：请求期间用默认 bundle 占位，回来后静默替换 ——
    // 不用全屏 loading：列配置是次要信息，骨架/占位比阻塞主内容更轻。
    // cancelled 标志防竞态：快速切换实体时旧实体的覆盖晚到，会把新实体的列投影带偏。
    let cancelled = false;
    setBundle(defaultBundle(baseFields));
    fetchOverlay(entityCode).then((b) => {
      if (!cancelled) setBundle(b ?? defaultBundle(baseFields));
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityCode]);

  /** 生效视图 → 扁平覆盖（投影层单一入口；预览列与运行时数据列共用） */
  const activeOv = useMemo(() => activeOverlay(bundle), [bundle]);
  const activeView = useMemo(() => activeViewOf(bundle), [bundle]);

  // 预览列与运行时数据列共用同一投影（契约优先 + 个人偏好叠加）——结构上保证两处一致
  const projectedColumns = useMemo(
    () => projectRuntimeColumns(baseFields, activeOv, contractListView),
    [baseFields, activeOv, contractListView],
  );
  const projected = useMemo(() => projectedColumns.map((c) => c.field), [projectedColumns]);
  const projectedCodes = useMemo(() => projected.map((f) => f.code), [projected]);

  /** 契约 viewKind=kanban 时表格密度无意义（P2.6 联动置灰，而非静默失效） */
  const kanbanContract = contractListView?.viewKind === 'kanban';

  const dirty = useMemo(() => {
    if (!activeView) return false;
    return JSON.stringify(activeView.overrides) !== JSON.stringify(defaultOv.overrides)
      || (activeView.density ?? 'comfortable') !== 'comfortable';
  }, [activeView, defaultOv]);

  const unsupported = useMemo(() => baseFields.filter((f) => !isRenderSupported(f)), [baseFields]);

  /** 唯一写口：本地状态 + 服务端/本地缓存（视图增删改与偏好编辑都经这里） */
  const persist = (next: UiOverlayBundle) => {
    setBundle(next);
    persistOverlay(entityCode, next);
  };

  /** 生效视图补丁写口（显隐/改名/密度/列序） */
  const patchActive = (patch: (v: PersonalView) => PersonalView) => {
    if (!bundle) return;
    persist(updateActiveView(bundle, patch));
  };

  const toggleVisible = (code: string, visible: boolean) =>
    patchActive((v) => ({ ...v, overrides: { ...v.overrides, [code]: { ...v.overrides[code], visible } } }));

  const rename = (code: string, displayName: string) =>
    patchActive((v) => ({ ...v, overrides: { ...v.overrides, [code]: { ...v.overrides[code], displayName } } }));

  const move = (code: string, direction: 'up' | 'down') =>
    patchActive((v) => ({ ...v, overrides: reorderOverlay(v, projectedCodes, code, direction).overrides }));

  const setDensity = (density: 'comfortable' | 'compact') => patchActive((v) => ({ ...v, density }));

  /* ---------------- 视图管理（新建/重命名/删除/恢复该视图默认） ---------------- */
  const openCreateView = () => { setViewModal({ mode: 'create' }); setViewName(''); setViewCopy(true); };
  const openRenameView = () => {
    if (!activeView) return;
    setViewModal({ mode: 'rename', id: activeView.id });
    setViewName(activeView.name);
  };
  const openDeleteView = () => {
    if (!activeView) return;
    setViewModal({ mode: 'delete', id: activeView.id });
  };
  const confirmViewModal = () => {
    if (!bundle || !viewModal) return;
    if (viewModal.mode === 'create') {
      persist(createView(bundle, viewName, baseFields, viewCopy ? 'copy' : 'blank'));
      message.success(`已新建并切换到个人视图「${viewName.trim() || '未命名视图'}」`);
    } else if (viewModal.mode === 'rename' && viewModal.id) {
      persist(renameView(bundle, viewModal.id, viewName));
    } else if (viewModal.mode === 'delete' && viewModal.id) {
      persist(deleteView(bundle, viewModal.id));
      message.success('已删除个人视图');
    }
    setViewModal(null);
  };
  const viewMenuItems: MenuProps['items'] = [
    { key: 'new', icon: <PlusOutlined />, label: '新建视图…', onClick: openCreateView },
    { key: 'rename', icon: <EditOutlined />, label: '重命名当前视图…', onClick: openRenameView },
    {
      key: 'reset',
      icon: <ReloadOutlined />,
      label: '恢复该视图默认列',
      onClick: () => bundle && persist(resetActiveViewToDefault(bundle, baseFields)),
    },
    { type: 'divider' },
    {
      key: 'delete',
      icon: <DeleteOutlined />,
      label: '删除当前视图',
      danger: true,
      disabled: (bundle?.views.length ?? 0) <= 1,
      onClick: openDeleteView,
    },
  ];

  const restoreDefault = async () => {
    await resetOverlay(entityCode);
    setBundle(defaultBundle(baseFields));
    message.success('已恢复默认显示');
  };

  /** 未支持渲染的类型：显式降级提示（PRD §5.5 禁静默渲染空） */
  const renderUnsupported = (field: MetaField) => (
    <Alert
      type="warning"
      showIcon
      message={`字段「${field.displayName || field.code}」的类型 ${field.type} 暂不支持自动生成界面控件`}
      description="该字段仍会随结构发布到运行时；如需界面支持请使用受支持类型（STRING/TEXT/INTEGER/LONG/DECIMAL/BOOLEAN/DATE/DATETIME/ENUM）。"
      style={{ marginBottom: 8 }}
    />
  );

  /** 按元数据类型渲染预览控件（与数据 Tab 表单同源；此处仅为预览，禁用输入） */
  const renderPreviewControl = (field: MetaField) => {
    const type = (field.type || 'STRING').toUpperCase();
    if (type === 'BOOLEAN') return <Switch disabled />;
    if (type === 'INTEGER' || type === 'LONG') return <InputNumber disabled style={{ width: '100%' }} />;
    if (type === 'DECIMAL') return <InputNumber disabled step={0.01} style={{ width: '100%' }} />;
    if (type === 'DATE') return <DatePicker disabled style={{ width: '100%' }} />;
    if (type === 'DATETIME') return <DatePicker disabled showTime style={{ width: '100%' }} />;
    if (isEnumField(field)) {
      const opts = parseEnumValues(field.enumValues).map((o) => ({ value: o.code, label: `${o.label}（${o.code}）` }));
      return <Select disabled options={opts} placeholder={opts.length ? '请选择' : '该枚举未配置码表'} style={{ width: '100%' }} />;
    }
    if (type === 'TEXT') return <Input.TextArea disabled rows={3} />;
    return <Input disabled />;
  };

  const configColumns: ColumnsType<MetaField> = [
    { title: '字段', dataIndex: 'displayName', width: 150, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 130, ellipsis: true },
    {
      title: '类型', dataIndex: 'type', width: 90,
      render: (t: string) => <Tag color={FIELD_TYPE_MAP[t]?.color ?? 'default'}>{t}</Tag>,
    },
    {
      title: '显示',
      key: 'visible',
      width: 110,
      render: (_, f) => {
        // strict 集合：未列入契约的字段不进运行时列表——置灰 + 引导去设计器加入
        if (contractListView && !contractByCode.has(f.code)) {
          return (
            <Space size={2}>
              <Switch size="small" checked={false} disabled />
              <Tag color="orange" style={{ fontSize: 11 }}>未在契约</Tag>
              {onOpenDesigner && (
                <Button type="link" size="small" style={{ padding: 0 }} onClick={onOpenDesigner}>
                  去加入
                </Button>
              )}
            </Space>
          );
        }
        // 契约隐藏：置灰 + 解释（对齐设计系统 disable 模式：让用户分清「契约不给显示」与「我可以自己开」）
        if (contractByCode.get(f.code)?.visible === false) {
          return (
            <Tooltip title="该列被「界面设计器」契约隐藏；如需显示请去设计器修改并发布">
              <Switch size="small" checked={false} disabled />
            </Tooltip>
          );
        }
        const personalHidden = activeView?.overrides[f.code]?.visible === false;
        return (
          <Space size={2}>
            <Switch size="small" checked={!personalHidden} onChange={(v) => toggleVisible(f.code, v)} />
            {personalHidden && <Tag style={{ fontSize: 11 }}>个人隐藏</Tag>}
          </Space>
        );
      },
    },
    {
      title: '覆盖显示名',
      key: 'displayNameOverride',
      render: (_, f) => (
        <Input
          size="small"
          defaultValue={activeView?.overrides[f.code]?.displayName ?? f.displayName}
          placeholder={f.displayName}
          onChange={(e) => rename(f.code, e.target.value)}
        />
      ),
    },
    {
      title: '列序',
      key: 'order',
      width: 100,
      render: (_, f) => {
        // 契约存在时列序归设计器：置灰 ↑↓ 并明示真源，避免「两处都能配、不知谁生效」
        if (contractListView) {
          return contractByCode.has(f.code) ? (
            <Tooltip title="列序由「界面设计器」契约定义（发布后生效）">
              <Tag color="blue" style={{ fontSize: 11 }}>契约</Tag>
            </Tooltip>
          ) : (
            <Tooltip title="该字段未列入列表契约，运行时不可见；去设计器「列表」加入后可参与排序">
              <Tag color="orange" style={{ fontSize: 11 }}>未在契约</Tag>
            </Tooltip>
          );
        }
        const idx = projectedCodes.indexOf(f.code);
        // 无契约：纯偏好模式。↑↓ 可点但必须说清「这是你的个人列序」——
        // 与契约模式的置灰形态统一解释口径，避免用户误以为在调团队列序
        return (
          <Tooltip title="仅调整你的个人列序（仅自己可见）；团队列序请到「界面设计器」配置契约并发布">
            <Space size={2}>
              <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={idx <= 0} onClick={() => move(f.code, 'up')} />
              <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={idx < 0 || idx >= projectedCodes.length - 1} onClick={() => move(f.code, 'down')} />
            </Space>
          </Tooltip>
        );
      },
    },
    {
      // UID-11：从「显示设置」的某一列反查到「结构」里的字段定义（双向联动的一半）
      title: '定位',
      key: 'locate',
      width: 70,
      render: (_: unknown, f: MetaField) =>
        onLocateField ? (
          <Tooltip title="跳到「结构」并高亮该字段">
            <Button size="small" type="link" icon={<AimOutlined />} onClick={() => onLocateField(f.code)} />
          </Tooltip>
        ) : null,
    },
  ];

  const listPreviewColumns: ColumnsType<MetaField> = [
    { title: '显示名', dataIndex: 'displayName', width: 160, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 140, ellipsis: true },
    { title: '类型', dataIndex: 'type', width: 90, render: (t: string) => <Tag color={FIELD_TYPE_MAP[t]?.color ?? 'default'}>{t}</Tag> },
  ];

  // 空态：无字段 ⇒ 单 CTA 引导去结构添加第一个字段（融合方案 §3.4.3）
  if (baseFields.length === 0) {
    return (
      <Empty
        description="还没有字段，界面会随结构自动生成"
        style={{ padding: '32px 0' }}
      >
        {onAddField && (
          <Button type="primary" icon={<PlusOutlined />} onClick={onAddField}>
            去结构添加第一个字段
          </Button>
        )}
      </Empty>
    );
  }

  return (
    <div>
      <Space style={{ marginBottom: 12, width: '100%', justifyContent: 'space-between' }} wrap>
        <Space wrap>
          <Segmented
            value={mode}
            onChange={(v) => setMode(v as 'config' | 'preview')}
            options={[
              { value: 'config', label: <Space size={4}><SettingOutlined /><span>配置</span></Space> },
              { value: 'preview', label: <Space size={4}><EyeOutlined /><span>预览</span></Space> },
            ]}
          />
          <Segmented
            value={view}
            onChange={(v) => setView(v as ViewMode)}
            options={[{ value: 'list', label: '列表' }, { value: 'form', label: '表单' }]}
          />
          {/* P0.1：常驻身份徽标——从命名上断绝「这是第二个设计器」的误读（对标 Airtable personal view） */}
          <Tooltip title="个人偏好层：仅调整你本人的列显隐/密度/改名/列序，按用户存于服务端（仅自己可见）；团队级列集合/列序/列宽由「界面设计器」契约定义，发布后生效">
            <Tag color="blue">个人偏好 · 仅自己可见</Tag>
          </Tooltip>
          {mode === 'preview' && projected.length === 0 && <Tag color="orange">当前没有可见列</Tag>}
        </Space>
        <Space wrap>
          <Tooltip title={kanbanContract ? '契约 viewKind=kanban：看板视图不使用表格密度' : '切换表格行高（紧凑模式一屏看更多行）'}>
            <span>
              <Text type="secondary" style={{ fontSize: 12, marginRight: 4 }}>密度</Text>
              <Segmented
                size="small"
                disabled={kanbanContract || !activeView}
                value={activeView?.density ?? 'comfortable'}
                onChange={(v) => setDensity(v as 'comfortable' | 'compact')}
                options={[{ value: 'comfortable', label: '标准' }, { value: 'compact', label: '紧凑' }]}
              />
            </span>
          </Tooltip>
          {/* P0.3：明示重置口径——只清个人偏好，不碰团队契约 */}
          <Tooltip title="清除全部个人视图与偏好（显隐/列序/密度/改名），恢复默认投影；不影响团队契约与其他成员">
            <Button icon={<ReloadOutlined />} size="small" onClick={restoreDefault} disabled={!dirty}>
              恢复默认显示
            </Button>
          </Tooltip>
          {dirty ? <Tag color="orange">界面配置已调整</Tag> : <Tag color="green">与默认一致</Tag>}
        </Space>
      </Space>

      {/* 个人视图切换器（P1.4 多视图）：命名视图各自保存偏好，切换即生效 */}
      <Space style={{ marginBottom: 12 }} wrap>
        <Text type="secondary" style={{ fontSize: 12 }}>个人视图</Text>
        <Segmented
          size="small"
          value={activeView?.id}
          onChange={(v) => { if (bundle) persist(switchView(bundle, String(v))); }}
          options={(bundle?.views ?? []).map((v) => ({ value: v.id, label: v.name }))}
        />
        <Dropdown menu={{ items: viewMenuItems }} trigger={['click']}>
          <Button size="small" icon={<SettingOutlined />}>管理</Button>
        </Dropdown>
        <Tooltip title="个人视图按用户存于服务端，仅自己可见；与「界面设计器」契约叠加生效——契约定列集合/列序，视图定你的显隐/密度/改名">
          <QuestionCircleOutlined style={{ color: '#bfbfbf' }} />
        </Tooltip>
      </Space>

      {contractListView && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="列集合、列序、列宽、固定列与列标题由「界面设计器」契约定义（发布后生效）"
          description={
            <div style={{ fontSize: 13 }}>
              <div>本页管理你的个人偏好：显隐、表格密度与个人改名——任一层隐藏该列即不显示。</div>
              <div>
                未列入契约的字段不会出现在运行时列表
                {outsideContract.length > 0 && <>（当前有 {outsideContract.length} 个：{outsideContract.slice(0, 5).map((f) => f.displayName || f.code).join('、')}{outsideContract.length > 5 ? ' 等' : ''}）</>}
                {onOpenDesigner && outsideContract.length > 0 && (
                  <Button type="link" size="small" style={{ padding: '0 4px' }} onClick={onOpenDesigner}>
                    去设计器加入
                  </Button>
                )}
              </div>
            </div>
          }
        />
      )}

      {unsupported.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={`有 ${unsupported.length} 个字段的类型暂不支持自动生成控件`}
          description={<div>{unsupported.map((f) => <div key={f.id}>· {f.displayName}（{f.code}）：{f.type}</div>)}</div>}
        />
      )}

      {mode === 'config' ? (
        <>
          <div style={{ marginBottom: 8 }}><Text strong>列配置</Text><Text type="secondary">（显隐 / 显示名 / 列序{onLocateField ? '；点击任意行可跳到「结构」高亮该字段' : ''}）</Text></div>
          <Table
            rowKey="code"
            size="small"
            pagination={false}
            columns={configColumns}
            dataSource={baseFields}
            scroll={{ x: 'max-content' }}
            /* UID-11：整行可点，等价于右侧「定位」按钮（大目标更好点）。
               ⚠ 交互控件（开关/输入框/按钮）必须自己处理点击：行 onClick 不设防时，
               点「显示」开关会顺手把用户弹到「结构」页（开关态变了、人也走了）——
               生产探针实测踩中过这个真 bug，用 closest 守卫把控件点击让给控件自己。 */
            onRow={(f) => ({
              onClick: (e: React.MouseEvent) => {
                if ((e.target as HTMLElement).closest('button, input, textarea, .ant-switch, .ant-btn, .ant-input, .ant-select, .ant-segmented')) return;
                onLocateField?.(f.code);
              },
              style: { cursor: onLocateField ? 'pointer' : 'default' },
            })}
          />
          <div style={{ margin: '12px 0 8px' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              个人调整（显隐 / 密度 / 改名 / 列序）即时生效于「预览」与运行时数据列表（两者共用同一套投影）；
              可保存多套「个人视图」按场景切换（如我的待审 / 大额专项）。覆盖按用户保存于服务端（离线时回落本机缓存）。
              契约列（列序 / 列宽 / 固定列）以「界面设计器」发布版为准。
            </Text>
          </div>
        </>
      ) : view === 'list' ? (
        <Table
          rowKey="code"
          size={tableSizeOf(activeOv.density)}
          pagination={false}
          columns={listPreviewColumns}
          dataSource={projected}
          locale={{ emptyText: '当前没有可见列，请在「配置」中开启显示' }}
          scroll={{ x: 'max-content' }}
          onRow={(f) => ({
            onClick: () => onLocateField?.(f.code),
            style: { cursor: onLocateField ? 'pointer' : 'default' },
          })}
        />
      ) : (
        <div style={{ maxWidth: 560 }}>
          <Form layout="vertical" disabled>
            {projected.length === 0 ? (
              <Empty description="当前没有可见字段，请在「配置」中开启显示" />
            ) : (
              projected.map((f) => (
                <div key={f.id}>
                  {isRenderSupported(f) ? (
                    <Form.Item label={f.displayName} extra={<Text type="secondary" style={{ fontSize: 12 }}>{f.code} · {f.type}{f.required ? ' · 必填' : ''}</Text>}>
                      {renderPreviewControl(f)}
                    </Form.Item>
                  ) : (
                    renderUnsupported(f)
                  )}
                </div>
              ))
            )}
          </Form>
        </div>
      )}
      {/* 视图管理弹窗：新建（可复制当前偏好）/ 重命名 / 删除（最后一个是底线） */}
      <Modal
        title={viewModal?.mode === 'create' ? '新建个人视图' : viewModal?.mode === 'rename' ? '重命名个人视图' : '删除个人视图'}
        open={!!viewModal}
        onOk={confirmViewModal}
        onCancel={() => setViewModal(null)}
        okText={viewModal?.mode === 'delete' ? '删除' : '确定'}
        okButtonProps={viewModal?.mode === 'delete' ? { danger: true } : undefined}
        confirmLoading={false}
        destroyOnHidden
      >
        {viewModal?.mode === 'delete' ? (
          <div style={{ lineHeight: 1.8 }}>
            确定删除个人视图「{bundle?.views.find((v) => v.id === viewModal.id)?.name}」？
            <br />
            <Text type="secondary" style={{ fontSize: 12 }}>
              删除后该视图保存的显隐 / 列序 / 密度 / 改名偏好一并清除，其他视图不受影响；个人视图至少保留一个。
            </Text>
          </div>
        ) : (
          <Form layout="vertical">
            <Form.Item label="视图名称" required extra="切换器里显示的名字，建议按使用场景命名">
              <Input
                value={viewName}
                maxLength={20}
                showCount
                placeholder="如：我的待审、大额专项"
                onChange={(e) => setViewName(e.target.value)}
              />
            </Form.Item>
            {viewModal?.mode === 'create' && (
              <Form.Item style={{ marginBottom: 0 }}>
                <Checkbox checked={viewCopy} onChange={(e) => setViewCopy(e.target.checked)}>
                  复制当前视图的列偏好（显隐 / 列序 / 密度 / 改名）
                </Checkbox>
              </Form.Item>
            )}
          </Form>
        )}
      </Modal>
    </div>
  );
};

export default InterfaceTab;
