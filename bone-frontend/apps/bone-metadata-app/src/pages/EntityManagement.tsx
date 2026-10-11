import { useEffect, useState, useMemo, useCallback } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert, Button, Breadcrumb, Card, Dropdown, Empty, Form, Input, InputNumber, Modal, Popconfirm, Segmented, Select,
  Space, Table, Tag, Tree, Tooltip, message, Badge, Typography, Row, Col, Radio,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  PlusOutlined, AppstoreOutlined, CopyOutlined, DatabaseOutlined, FolderOutlined,
  ThunderboltOutlined, DownloadOutlined, ImportOutlined, DownOutlined, EditOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { DataNode } from 'antd/es/tree';
import { errorMessage, metadataEntityApi, metadataTemplateApi } from '../services/metadataApi';
import { normalizeTotal, formatDate } from '@bone/shared-utils';
import PublishPreviewModal from '../components/PublishPreviewModal';
import ImportModelModal from '../components/ImportModelModal';
import ImportTableModal from '../components/ImportTableModal';
import { exportEntityModel } from '../utils/modelTransfer';
import { appApi, moduleApi, type BoneModule } from '../services/appModuleApi';
import type {
  CopyEntityReq, CreateMetaEntityReq, MetaEntity, MetaTemplate, MetaTemplateField, UpdateMetaEntityReq,
} from '../types';
import { DELIVERY_MODE, ENTITY_STATUS, META_DELIVERY_GENERATIVE, META_DELIVERY_RUNTIME, META_ENTITY_PUBLISHED } from '../types';
import { Auth, AuthButton, usePermission } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';

const { Text } = Typography;

/**
 * 双界面模式卡片（PRD §0.7 · 产品主语言）：用户只选「手写界面 / 自动生成界面」，
 * 底层仍存 delivery_mode（0=GENERATIVE / 1=RUNTIME，1:1 映射、不新增字段）。
 * 新建对话框用卡片替代下拉：10 秒判据见 PRD §0.7.1（标准 CRUD→自动；整页不像 CRUD→手写）。
 */
const UiModeCard: React.FC<{
  title: string;
  badge?: string;
  desc: string;
  active: boolean;
  disabled?: boolean;
  onSelect: () => void;
}> = ({ title, badge, desc, active, disabled, onSelect }) => (
  <div
    role="radio"
    aria-checked={active}
    aria-disabled={disabled}
    onClick={disabled ? undefined : onSelect}
    style={{
      flex: 1,
      padding: '10px 12px',
      borderRadius: 8,
      cursor: disabled ? 'not-allowed' : 'pointer',
      border: active ? '2px solid #1677ff' : '2px solid #d9d9d9',
      background: active ? '#e6f4ff' : '#fff',
      opacity: disabled ? 0.5 : 1,
      transition: 'border-color .2s, background .2s',
    }}
  >
    <Space size={6}>
      <Radio checked={active} disabled={disabled} />
      <Text strong style={{ fontSize: 13 }}>{title}</Text>
      {badge && <Tag color="blue" style={{ fontSize: 10, lineHeight: '16px', marginInlineEnd: 0 }}>{badge}</Tag>}
    </Space>
    <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 4, lineHeight: 1.6 }}>{desc}</Text>
  </div>
);

/** 标识符白名单（与后端 2a UC-MT2 / validate 规则一致） */
const CODE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/;
const CODE_RULE = { pattern: CODE_PATTERN, message: '以字母开头，仅字母/数字/下划线' };

// ----- Component -----

const EntityManagement: React.FC = () => {
  const navigate = useNavigate();
  const { appId, moduleId } = useParams<{ appId: string; moduleId: string }>();
  const isScoped = !!appId && !!moduleId;
  // §3.3.3 工作台直达：?new=1 自动打开新建对话框；?import=1 打开从存量表导入（打开一次后清参，刷新不复发）
  const [searchParams, setSearchParams] = useSearchParams();

  const [form] = Form.useForm();
  /** 双界面模式当前选中（PRD §0.7）：新建对话框卡片联动用 */
  const uiModeWatch = Form.useWatch('deliveryMode', form);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<MetaEntity[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<number | null>(null);
  const [deliveryFilter, setDeliveryFilter] = useState<number | null>(null);
  const [statusFilter, setStatusFilter] = useState<number | null>(null);
  /** 全局草稿数（页头「待发布 N」角标，不受列表筛选影响） */
  const [draftTotal, setDraftTotal] = useState<number | null>(null);
  const [selectedModule, setSelectedModule] = useState<string>(isScoped ? moduleId! : 'all');
  // 树选中语义（提前声明，供 load 依赖）：all=全部分组视图；app-<id>=该应用下模块；mod-<id>=该模块平铺列表
  const selectedIsModule = selectedModule.startsWith('mod-');
  const selectedIsApp = selectedModule.startsWith('app-');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetaEntity | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  /** B2 批量结果可追溯：发布/删除的逐项失败明细（null=隐藏结果条） */
  const [batchResult, setBatchResult] = useState<{
    kind: 'publish' | 'delete'; successCount: number; failCount: number; errors: { id: string; reason: string }[];
  } | null>(null);
  /** B2 命令面板（Ctrl+K 只做「跳实体」）：打开态 + 关键字 */
  const [cmdOpen, setCmdOpen] = useState(false);
  const [cmdKeyword, setCmdKeyword] = useState('');
  /**
   * 命令面板高亮位：↑↓ 移动、Enter 打开（对标 VSCode / Linear / GitHub 快捷跳转）。
   * 此前只能「回车打开第一条」，候选多于一条时用户无法选择第二、三条 ——
   * 命令面板的价值恰恰在于快速定位到**指定**目标，不是永远取第一条。
   */
  const [cmdIndex, setCmdIndex] = useState(0);

  /** 打开面板时重置高亮，避免沿用上次的位（候选集已变，位可能越界） */
  useEffect(() => {
    if (cmdOpen) setCmdIndex(0);
  }, [cmdOpen, cmdKeyword]);

  // ----- 权限形态（UC-W8 / 2b F2）：scoped 模式下按应用角色收敛为只读；后端 403 永远兜底 -----
  const [myRole, setMyRole] = useState<string | undefined>(undefined);
  const canWrite = myRole !== 'viewer'; // undefined（平台管理员 / 非 scoped 视图）视为可写
  /** 细粒度权限判定（复制等动作需按 METADATA_MODEL_WRITE 单独判断，不能仅靠角色 canWrite 近似） */
  const { has } = usePermission();

  useEffect(() => {
    if (!appId) { setMyRole(undefined); return; }
    let cancelled = false;
    appApi.listMine({ page: 1, size: 100 }).then((res) => {
      if (!cancelled && res.code === 200) {
        const app = (res.data.records ?? []).find((a) => String(a.id) === String(appId));
        setMyRole(app?.myRole);
      }
    }).catch(() => { /* 角色获取失败时不收敛 UI，交由后端 403 兜底 */ });
    return () => { cancelled = true; };
  }, [appId]);

  // B2 命令面板：Ctrl+K 收窄为「跳实体」（融合方案 §3.3.3；PRD §5.4 规则 31）——
  // 作用域只做实体搜索，不做模块/页面，降低「应用/模块/实体三层找路」成本。
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setCmdOpen((v) => !v);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  /** B2：命令面板候选（当前列表数据 + 关键字过滤，最多 8 条） */
  const cmdResults = useMemo(() => {
    const kw = cmdKeyword.trim().toLowerCase();
    if (!kw) return data.slice(0, 8);
    return data
      .filter((e) => `${e.displayName} ${e.code} ${e.tableName}`.toLowerCase().includes(kw))
      .slice(0, 8);
  }, [data, cmdKeyword]);

  // ----- 发布预览（UC-W7 摘要级） -----
  const [previewEntity, setPreviewEntity] = useState<MetaEntity | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const openPublishPreview = (record: MetaEntity) => {
    setPreviewEntity(record);
    setPreviewOpen(true);
  };
  /** 发布成功后前向引导（动线闭环）：发布预览的「打开数据 / 查看界面 / 继续加字段」回调。
   *  列表页此前只传了 onPublished，导致这三个 CTA 被 `onOpenData &&` 守卫隐藏，
   *  发布后无法一键进入下一环；详情页已接，此处补齐以统一两条发布入口的动线。 */
  const previewGoData = () => {
    if (previewEntity?.id) navigate(`/entities/${previewEntity.id}/data`);
    setPreviewOpen(false);
  };
  const previewGoInterface = () => {
    if (previewEntity?.id) navigate(`/entities/${previewEntity.id}?tab=ui`);
    setPreviewOpen(false);
  };
  const previewGoAddField = () => {
    if (previewEntity?.id) navigate(`/entities/${previewEntity.id}?tab=structure`);
    setPreviewOpen(false);
  };

  // ----- 实体复制（UC-W2 流程 B） -----
  const [copyOpen, setCopyOpen] = useState(false);
  const [copySource, setCopySource] = useState<MetaEntity | null>(null);
  const [copyForm] = Form.useForm();

  // ----- 模型导入导出（F13） -----
  const [importOpen, setImportOpen] = useState(false);
  const [tableImportOpen, setTableImportOpen] = useState(false);
  const handleExport = async (record: MetaEntity) => {
    try {
      await exportEntityModel(record);
      message.success(`已导出「${record.displayName}」模型文件`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '导出失败');
    }
  };
  const openCopy = (record: MetaEntity) => {
    setCopySource(record);
    copyForm.resetFields();
    copyForm.setFieldsValue({
      name: `${record.name}_copy`,
      displayName: `${record.displayName} 副本`,
      code: `${record.code}_copy`,
      tableName: `${record.tableName}_copy`,
    });
    setCopyOpen(true);
  };
  const handleCopy = async () => {
    if (!copySource) return;
    const values = await copyForm.validateFields();
    try {
      const body: CopyEntityReq = {
        code: values.code,
        tableName: values.tableName,
        name: values.name,
        displayName: values.displayName,
        description: values.description,
        // ⚠ 雪花 ID 禁止 Number()（2^53 截断）；后端 Jackson 接受字符串转 Long
        targetModuleId: values.targetModuleId ?? undefined,
      };
      const res = await metadataEntityApi.copy(copySource.id, body);
      if (res.code === 200 || res.code === 201) {
        message.success(`已复制为「${values.displayName || values.code}」草稿`);
        setCopyOpen(false);
        load();
      } else {
        message.error(errorMessage(res));
      }
    } catch {
      message.error('复制失败，请检查编码/表名是否已被占用');
    }
  };

  /**
   * 当前是否有生效筛选（决定空态给「新建入口」还是「清除筛选」）。
   * 关键词/类型/交付/状态/模块树任一非默认即算有 —— 与 load() 组装的查询参数同口径。
   */
  const hasActiveFilter =
    keyword.trim() !== '' || typeFilter !== null || deliveryFilter !== null
    || statusFilter !== null || selectedModule !== 'all';

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { page: page, size: pageSize };
      if (keyword) params.keyword = keyword;
      if (typeFilter !== null) params.type = typeFilter;
      if (deliveryFilter !== null) params.deliveryMode = deliveryFilter;
      if (statusFilter !== null) params.status = statusFilter;
      // 选中具体模块时由后端按 moduleId 收敛（G2：MetaEntityPageQuery 已支持 moduleId）；
      // 应用级 / 全部仍由后端返回全量、客户端按 moduleId 分组。
      if (selectedIsModule) {
        params.moduleId = selectedModule.slice('mod-'.length);
      }

      const res = await metadataEntityApi.page(params);
      if (res.code === 200) {
        setData(res.data.records);
        setTotal(normalizeTotal(res.data.total));
      } else {
        message.error(res.message || '加载失败');
      }
    } catch {
      message.error('无法连接元数据服务，请确认 bone-metadata-server :9001 已启动');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, keyword, typeFilter, deliveryFilter, statusFilter, selectedIsModule, selectedModule]);

  useEffect(() => { load(); }, [load]);

  /**
   * 页头的「待发布 N」角标：与当前筛选无关的全局草稿数。
   *
   * 为何单独请求而不是从当前页 data 数推算：当前页可能正被 `?status=0` / 模块 /
   * 关键词筛选，此时「本页 10 条草稿」≠「还有多少草稿待发布」，用它会给出
   * 误导性的数字（用户以为发布完了其实还有）。对标 GitHub「Open issues」计数：
   * 概览数必须是**不受列表筛选影响**的口径。
   */
  useEffect(() => {
    let cancelled = false;
    metadataEntityApi.page({ page: 1, size: 1, status: 0 })
      .then((res) => { if (!cancelled) setDraftTotal(res.code === 200 ? normalizeTotal(res.data.total) : null); })
      .catch(() => { if (!cancelled) setDraftTotal(null); });
    return () => { cancelled = true; };
  }, [isScoped, selectedModule, selectedIsModule]);

  // ----- 真实应用/模块树（IAM 真源；仅非 scoped 模式加载）-----
  const [appModules, setAppModules] = useState<{ appId: string; appName: string; modules: BoneModule[] }[]>([]);
  useEffect(() => {
    if (isScoped) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await appApi.listMine({ page: 1, size: 100 });
        const apps = res.code === 200 ? res.data.records ?? [] : [];
        const pairs = await Promise.all(
          apps.map(async (a) => {
            try {
              const mr = await moduleApi.listByApp(a.id, { page: 1, size: 100 });
              return { appId: a.id, appName: a.name, modules: mr.code === 200 ? mr.data.records ?? [] : [] };
            } catch {
              return { appId: a.id, appName: a.name, modules: [] as BoneModule[] };
            }
          }),
        );
        if (!cancelled) setAppModules(pairs);
      } catch {
        // 应用/模块接口不可用时保持空树：仅剩「全部模型」根节点，列表视图不受影响
      }
    })();
    return () => { cancelled = true; };
  }, [isScoped]);

  const moduleTree = useMemo<DataNode[]>(
    () => [
      { title: '全部模型', key: 'all', icon: <AppstoreOutlined /> },
      ...appModules.map((a) => ({
        title: a.appName,
        key: `app-${a.appId}`,
        icon: <FolderOutlined />,
        children: a.modules.map((m) => ({
          title: m.name,
          key: `mod-${m.id}`,
          icon: <DatabaseOutlined />,
          isLeaf: true,
        })),
      })),
    ],
    [appModules],
  );

  // moduleId → 模块名（「所属模块」列与新建表单预填共用）
  const moduleIndex = useMemo(() => {
    const labelById = new Map<string, string>();
    appModules.forEach((a) =>
      a.modules.forEach((m) => {
        labelById.set(String(m.id), m.name);
      }));
    return { labelById };
  }, [appModules]);

  // 真实模块下拉选项（IAM 真源，替代原硬编码假选项）
  const realModuleOptions = useMemo(
    () =>
      appModules.flatMap((a) =>
        a.modules.map((m) => ({ value: String(m.id), label: `${a.appName} / ${m.name}` })),
      ),
    [appModules],
  );

  // 树选中语义：all=全部；app-<id>=该应用下所有模块的对象；mod-<id>=该模块平铺列表
  /** 应用级选中时需在客户端按 moduleId 收敛（后端 page 查询只认 mod- 前缀的 moduleId 过滤） */
  const tableData = useMemo(() => {
    if (selectedIsModule) return data;
    if (selectedIsApp) {
      const ids = new Set(
        appModules
          .filter((a) => `app-${a.appId}` === selectedModule)
          .flatMap((a) => a.modules.map((m) => String(m.id))),
      );
      return data.filter((e) => e.moduleId != null && ids.has(String(e.moduleId)));
    }
    return data;
  }, [data, selectedIsModule, selectedIsApp, selectedModule, appModules]);

  // ----- Modal handlers -----
  // 新建双入口（UC-W2）：全新 / 从平台模板（G3 实例化）
  const [createMode, setCreateMode] = useState<'new' | 'template'>('new');
  const [templates, setTemplates] = useState<MetaTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
  /** 当前选中模板（T5：展示「适用场景一句话」，只给 name/code 用户无法判断选哪个场景） */
  const selectedTemplate = templates.find((t) => t.id === selectedTemplateId) ?? null;
  const [tplFields, setTplFields] = useState<MetaTemplateField[]>([]);

  const openCreate = () => {
    setEditing(null);
    setCreateMode('new');
    setSelectedTemplateId(null);
    setTplFields([]);
    form.resetFields();
    // O3：左树已选中某个模块时，把归属自动带入表单——用户点的上下文就是他的意图，
    // 再要求他重选一次是纯粹的重复劳动（此前 openCreate 无条件 resetFields）。
    const preselectModuleId = isScoped
      ? moduleId
      : selectedIsModule
        ? selectedModule.slice('mod-'.length)
        : undefined;
    form.setFieldsValue({
      type: 0,
      deliveryMode: 1,
      ...(preselectModuleId ? { moduleId: preselectModuleId } : {}),
    });
    setModalOpen(true);
    metadataTemplateApi.list().then((res) => {
      if (res.code === 200) setTemplates(res.data.filter((t) => t.status === 1));
    }).catch(() => { /* 模板目录不可用时保留全新入口 */ });
  };

  // 深链自动打开：依赖 openCreate 稳定后执行一次（§3.3.3 工作台落点）
  useEffect(() => {
    if (searchParams.get('new') === '1') {
      openCreate();
      searchParams.delete('new');
      setSearchParams(searchParams, { replace: true });
    } else if (searchParams.get('import') === '1') {
      setTableImportOpen(true);
      searchParams.delete('import');
      setSearchParams(searchParams, { replace: true });
    } else if (searchParams.get('status') !== null) {
      // 工作台「待发布实体 → 查看全部实体」深链：?status=0 落到「草稿」筛选态。
      // 此处**保留** status 参数（与 new/import 不同）——它是可分享的筛选态，
      // 刷新/复制链接后仍应停在同一视图。
      const s = searchParams.get('status');
      setStatusFilter(s === null || s === '' ? null : Number(s));
    }
    // openCreate 为组件内定义的稳定闭包，仅初始化时消费深链参数
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const chooseTemplate = (id: string | null) => {
    setSelectedTemplateId(id);
    setTplFields([]);
    if (!id) return;
    metadataTemplateApi.fields(id).then((res) => {
      if (res.code === 200) setTplFields(res.data);
    });
  };

  const openEdit = (record: MetaEntity) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    // ⚠ 雪花 ID 禁止 Number()（2^53 截断）；后端 Jackson 接受字符串转 Long
    const resolvedModuleId: string | undefined = isScoped
      ? moduleId
      : values.moduleId != null
        ? String(values.moduleId)
        : undefined;
    try {
      // 从模板实例化（UC-W2 主流程 A）
      if (!editing && createMode === 'template' && selectedTemplateId) {
        const res = await metadataTemplateApi.instantiate(selectedTemplateId, {
          name: values.name,
          code: values.code,
          displayName: values.displayName,
          description: values.description,
          tableName: values.tableName,
          moduleId: resolvedModuleId,
          deliveryMode: values.deliveryMode,
        });
        if (res.code === 200 || res.code === 201) {
          message.success(`已从模板「${selectedTemplate?.name || '未知'}」实例化成功（已复制为独立副本，后续编辑与模板互不影响）`);
          setModalOpen(false);
          const newId = res.data;
          // 从模板起盘 → 落「界面」Tab（PRD §0.6.6 定案：模板已带字段，先见结果而非结构）
          navigate(
            appId && moduleId
              ? `/apps/${appId}/modules/${moduleId}/entities/${newId}?tab=ui`
              : `/entities/${newId}?tab=ui`,
          );
        } else {
          message.error(errorMessage(res));
        }
        return;
      }
      if (editing) {
        const body: UpdateMetaEntityReq = {
          name: values.name,
          displayName: values.displayName,
          description: values.description,
          tableName: values.tableName,
          sortOrder: values.sortOrder,
          deliveryMode: values.deliveryMode,
          icon: values.icon,
        };
        const res = await metadataEntityApi.update(editing.id, body);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          load();
        } else {
          message.error(res.message);
        }
      } else {
        const body: CreateMetaEntityReq = {
          ...values,
          moduleId: resolvedModuleId,
        };
        const res = await metadataEntityApi.create(body);
        if (res.code === 200) {
          message.success('创建成功');
          setModalOpen(false);
          const newId = res.data;
          // 落点：实体详情「结构」Tab（W0-7：新建后直接进结构空态，而非停在列表）
          navigate(
            appId && moduleId
              ? `/apps/${appId}/modules/${moduleId}/entities/${newId}?tab=structure`
              : `/entities/${newId}?tab=structure`,
          );
        } else {
          message.error(res.message);
        }
      }
    } catch (e) {
      if (e && typeof e === 'object' && 'errorFields' in e) return; // 表单校验错误已就地提示
      message.error('保存失败');
    }
  };

  const handleDelete = async (id: string) => {
    const res = await metadataEntityApi.delete(id);
    if (res.code === 200) {
      message.success('已删除');
      load();
    } else {
      message.error(res.message);
    }
  };

  /**
   * 行内「更多」菜单项：把复制 / 导出收进下拉，主操作区只留高频动作（详情 / 数据 / 发布 / 删除），
   * 减少操作列换行与视觉噪音（对标 Ant Design Pro 行操作「更多」范式）。
   * 复制按 METADATA_MODEL_WRITE 细粒度判定（避免仅靠角色 canWrite 近似导致越权可见）；
   * 导出无权限限制，所有角色均可见。
   */
  const moreItems = (record: MetaEntity): MenuProps['items'] => {
    const items: NonNullable<MenuProps['items']> = [];
    // 编辑并入「打开」：两者都进对象工作区，两个入口进同一个地方是噪音主源（B1）。
    // 这里保留「编辑」作为工作区直达的快捷方式，供习惯先改定义的老用户使用。
    if (has(BonePermissionCodes.METADATA_MODEL_WRITE)) {
      // 已发布对象原本的「编辑」按钮是 disabled（禁止改已发布结构），
      // 收进下拉后必须**保留**这个约束，否则用户点进去填半天提交却被后端拒。
      items.push(
        record.status === META_ENTITY_PUBLISHED
          ? { key: 'edit', icon: <EditOutlined />, label: '编辑定义（需先下线）', disabled: true }
          : { key: 'edit', icon: <EditOutlined />, label: '编辑定义', onClick: () => openEdit(record) },
      );
    }
    if (record.deliveryMode === META_DELIVERY_RUNTIME && record.status === META_ENTITY_PUBLISHED) {
      items.push({
        key: 'data',
        icon: <DatabaseOutlined />,
        label: '业务数据',
        onClick: () => navigate(`/entities/${record.id}/data`),
      });
    }
    if (has(BonePermissionCodes.METADATA_MODEL_WRITE)) {
      items.push({
        key: 'copy',
        icon: <CopyOutlined />,
        label: '复制',
        onClick: () => openCopy(record),
      });
    }
    items.push({
      key: 'export',
      icon: <DownloadOutlined />,
      label: '导出模型 JSON',
      onClick: () => handleExport(record),
    });
    return items;
  };

  // ----- 表格列定义 -----
  // ⚠ 本表含 ellipsis 列 → antd 自动启用 tableLayout:fixed。容器不足时无 width 的列会被压成 0 宽，
  // 其 nowrap 内容会溢出叠加到相邻列（实测「名称」叠到「编码」）。故名称列必须给 width+ellipsis，
  // 且表格必须配 scroll.x（列宽合计 1160，不足时横向滚动而非塌缩）。
  const columns: ColumnsType<MetaEntity> = [
    {
      title: '名称',
      dataIndex: 'displayName',
      key: 'displayName',
      width: 180,
      ellipsis: true,
      render: (name: string, record) => (
        <Typography.Text
          ellipsis={{ tooltip: name }}
          style={{ color: '#1668dc', cursor: 'pointer', maxWidth: '100%' }}
          onClick={() => navigate(`/entities/${record.id}`)}
        >
          {name}
        </Typography.Text>
      ),
    },
    { title: '编码', dataIndex: 'code', key: 'code', width: 140, ellipsis: true },
    {
      // 模块列：并表后（B3）模块归属必须可见——此前它是卡片分组标题，
      // 改成单表后若不补这一列，用户在「全部模型」视图就不知道对象归属哪个模块。
      title: '所属模块',
      key: 'module',
      width: 130,
      ellipsis: true,
      render: (_, record) => {
        const label = record.moduleId != null ? moduleIndex.labelById.get(String(record.moduleId)) : undefined;
        return label
          ? <Text style={{ fontSize: 12 }}>{label}</Text>
          : <Text type="secondary" style={{ fontSize: 12 }}>未分组</Text>;
      },
    },
    { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 140, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (t: number) => (
        <Tag color={t === 1 ? 'geekblue' : 'default'}>{t === 1 ? '主数据类' : '普通业务'}</Tag>
      ),
    },
    {
      title: '交付模式',
      dataIndex: 'deliveryMode',
      key: 'deliveryMode',
      width: 100,
      render: (m: number, row) => (
        <Tag color={m === 1 ? 'purple' : 'default'}>{row.deliveryModeLabel ?? DELIVERY_MODE[m] ?? m}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (s: number) => (
        <Tag color={s === 1 ? 'green' : s === 2 ? 'default' : 'blue'}>
          {ENTITY_STATUS[s] ?? s}
        </Tag>
      ),
    },
    {
      title: '字段数',
      key: 'fieldCount',
      width: 70,
      render: (_, record) => (
        <Badge
          count={(record as unknown as Record<string, number>).fieldCount ?? 0}
          style={{ backgroundColor: '#1890ff' }}
          showZero
        />
      ),
    },
    {
      // 运维型清单的必备列（B4）：此前「找最近改的/谁动的」只能靠 ⌘K 跳转，
      // 列表本身不支持，而巡检/交接场景恰恰高频问这个问题。
      title: '最近更新',
      key: 'updatedAt',
      width: 110,
      render: (_, record) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {record.updatedAt ? formatDate(record.updatedAt) : '—'}
        </Text>
      ),
    },
    {
      // 操作列收敛为 [打开][发布][删除][更多▾]（B1）：
      // 「编辑」并入「更多」（两者都进对象工作区）；「数据」仅对已发布 RUNTIME 对象出现，
      // 移入「更多」可避免它与「打开」并列造成误点不同页签。删除保留 inline + Popconfirm，
      // 不塞进下拉——antd Dropdown 内嵌 Popconfirm 会在确认时关闭菜单造成竞态。
      title: '操作',
      key: 'action',
      width: 170,
      render: (_, record) => (
        <Space size={2}>
          <Button type="link" size="small" onClick={() => navigate(`/entities/${record.id}`)}>
            打开
          </Button>
          {record.status === 0 && (
            <AuthButton code={BonePermissionCodes.METADATA_PUBLISH} type="link" size="small" icon={<ThunderboltOutlined />} onClick={() => openPublishPreview(record)}>
              发布
            </AuthButton>
          )}
          {canWrite && (
            /* 删除挂在 Popconfirm 下：无权限时必须整体不渲染（<Auth> 包外层），
               换成 AuthButton 会让 null 成为 Popconfirm children，antd cloneElement 抛异常 */
            <Auth code={BonePermissionCodes.METADATA_MODEL_WRITE}>
              <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
                <Button type="link" size="small" danger disabled={record.status === 1}>删除</Button>
              </Popconfirm>
            </Auth>
          )}
          <Dropdown menu={{ items: moreItems(record) }} trigger={['click']}>
            <Button type="link" size="small">更多 <DownOutlined /></Button>
          </Dropdown>
        </Space>
      ),
    },
  ];

  // ----- Render -----
  return (
    <div className="page">
      {/* 面包屑（模块上下文时）。**正常流定位**，不用 absolute ——
          absolute + top:0 会压到 shell 顶栏下方（实测截图：标题与全局搜索框重叠），
          且需要靠「占位 div」硬凑高度。改为正常流后不需要任何占位 hack。 */}
      {isScoped && (
        <Breadcrumb
          style={{ marginBottom: 8 }}
          items={[
            { title: <a onClick={() => navigate('/apps')}>模型设计</a> },
            { title: <a onClick={() => navigate(`/apps/${appId}/modules`)}>模块</a> },
            { title: '业务对象' },
          ]}
        />
      )}

      {/* 页头：声明本页职责边界（消除「模型设计 / 业务对象 / 业务数据三个菜单到底管什么」的困惑） */}
      <div style={{ marginBottom: 12 }}>
        <Space size={8} wrap style={{ marginBottom: 2 }}>
          <Typography.Title level={5} style={{ margin: 0 }}>业务对象</Typography.Title>
          <Tag color="blue" style={{ fontSize: 12 }}>模型清单（唯一真源）</Tag>
          <Text type="secondary" style={{ fontSize: 12 }}>
            共 {total} 个{isScoped ? '（当前模块）' : ''}
          </Text>
          {/* 待发布数做成可点：概览数字若不能下钻，就只是个装饰。
              此前写死成静态文本，用户看到「待发布 7」却要自己去点 Segmented 找「草稿」。 */}
          {draftTotal != null && draftTotal > 0 && (
            <Tag
              color="processing"
              style={{ fontSize: 12, cursor: 'pointer', marginInlineEnd: 0 }}
              onClick={() => { setStatusFilter(0); setPage(1); }}
            >
              待发布 {draftTotal} · 点击筛选
            </Tag>
          )}
        </Space>
        <Text type="secondary" style={{ fontSize: 12 }}>
          在这里检索、筛选、批量发布、复制与维护业务对象。点「打开」进入对象工作区（结构 / 界面 / 数据 / 发布四页签）；
          录入业务数据请用「更多 → 业务数据」或工作区的「数据」页签，它会锁定当前对象、不会看错数据。
        </Text>
      </div>

      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        {/* 左侧模块树（仅非 scoped 模式；数据来自 IAM 应用/模块真源） */}
        {!isScoped && (
          <Card size="small" style={{ width: 220, flexShrink: 0 }} styles={{ body: { padding: 8 } }}>
            <Tree
              showIcon
              defaultExpandAll
              selectedKeys={[selectedModule]}
              onSelect={(keys) => {
                if (keys.length > 0) setSelectedModule(String(keys[0]));
              }}
              treeData={moduleTree}
            />
          </Card>
        )}

        {/* 右侧列表 */}
        {/* minWidth:0 是关键：flex 子项默认 min-width:auto，会被 Table 的 scroll.x=1160 撑破导致页面横向溢出 */}
        <div style={{ flex: 1, minWidth: 0 }}>
          {/* 过滤与操作栏 */}
          {/* gutter 会给 Row 加 -6px 左右负边距，在 flex:1 容器里会把内容顶出视口（实测溢出 10px）
            这里把 Row 自身外边距归零，列间距由 Col 的 padding 承担，视觉间隔不变。 */}
          <Row gutter={[12, 12]} style={{ marginBottom: 16, marginLeft: 0, marginRight: 0 }}>
            <Col>
              <Input.Search
                placeholder="搜索对象名称/编码"
                allowClear
                style={{ width: 220 }}
                onSearch={(v) => { setKeyword(v); setPage(1); }}
              />
            </Col>
            <Col>
              {/* 状态分段：对标 Pro 列表页把高频筛选提到一行，低频的类型/交付模式收进「更多筛选」 */}
              <Segmented
                value={statusFilter ?? -1}
                onChange={(v) => { setStatusFilter(Number(v) === -1 ? null : Number(v)); setPage(1); }}
                options={[
                  { value: -1, label: '全部' },
                  { value: 0, label: '草稿' },
                  { value: 1, label: '已发布' },
                  { value: 2, label: '已下线' },
                ]}
              />
            </Col>
            <Col>
              <Dropdown
                menu={{
                  items: [
                    {
                      key: 'type',
                      label: '类型',
                      children: [
                        { key: 'type-all', label: '全部类型' },
                        { key: 'type-0', label: '普通业务' },
                        { key: 'type-1', label: '主数据类' },
                      ],
                    },
                    {
                      key: 'delivery',
                      label: '交付模式',
                      children: [
                        { key: 'dm-all', label: '全部模式' },
                        { key: 'dm-0', label: '手写界面' },
                        { key: 'dm-1', label: '自动生成界面' },
                      ],
                    },
                  ],
                  onClick: ({ key }) => {
                    if (key === 'type-all') setTypeFilter(null);
                    else if (key === 'type-0') setTypeFilter(0);
                    else if (key === 'type-1') setTypeFilter(1);
                    else if (key === 'dm-all') setDeliveryFilter(null);
                    else if (key === 'dm-0') setDeliveryFilter(0);
                    else if (key === 'dm-1') setDeliveryFilter(1);
                    setPage(1);
                  },
                }}
              >
                <Button>
                  更多筛选
                  {(typeFilter !== null || deliveryFilter !== null) && (
                    <Badge
                      count={(typeFilter !== null ? 1 : 0) + (deliveryFilter !== null ? 1 : 0)}
                      style={{ marginLeft: 4 }}
                    />
                  )}
                </Button>
              </Dropdown>
            </Col>
            <Col>
              {/* ⌘K 快速跳转实体：显式露出提示，避免"藏起来的能力等于不存在" */}
              <Tooltip title="按名称/编码/表名快速定位对象">
                <Button onClick={() => setCmdOpen(true)}>⌘K 定位</Button>
              </Tooltip>
            </Col>
            <Col flex="auto" style={{ textAlign: 'right' }}>
              <Space>
                {/* 导入合并为一个下拉（B2）：此前工具栏有「从存量表导入」和「导入模型」两个按钮，
                    稀释了右侧「新建」这个真正的主行动。两个都是低频的"导入既有资产"动作。 */}
                {canWrite && (
                  <Auth code={BonePermissionCodes.METADATA_MODEL_WRITE}>
                    <Dropdown
                      menu={{
                        items: [
                          { key: 'table', icon: <DatabaseOutlined />, label: '从存量表导入' },
                          { key: 'model', icon: <ImportOutlined />, label: '导入模型 JSON' },
                        ],
                        onClick: ({ key }) => {
                          if (key === 'table') setTableImportOpen(true);
                          else setImportOpen(true);
                        },
                      }}
                    >
                      <Button icon={<ImportOutlined />}>导入</Button>
                    </Dropdown>
                  </Auth>
                )}
                {canWrite && (
                  <AuthButton code={BonePermissionCodes.METADATA_MODEL_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                    新建业务对象
                  </AuthButton>
                )}
              </Space>
            </Col>
          </Row>

          {!canWrite && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message="只读模式"
              description="您在该应用中的角色为「只读（viewer）」，仅可查看模型；建模与发布需要 ADMIN / DEVELOPER 角色（请联系应用管理员在 IAM 授予）。"
            />
          )}

          {/* B2 批量结果条：失败必须可追溯——成功数 + 失败明细 List，替代 message+console 组合 */}
          {batchResult && (
            <Alert
              type={batchResult.errors.length > 0 ? 'warning' : 'success'}
              showIcon
              closable
              onClose={() => setBatchResult(null)}
              style={{ marginBottom: 12 }}
              message={`发布成功 ${batchResult.successCount} 个，失败 ${batchResult.errors.length} 个`}
              description={
                batchResult.errors.length > 0 ? (
                  <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
                    {batchResult.errors.slice(0, 5).map((e, i) => (
                      <li key={i} style={{ fontSize: 12 }}>{String(e)}</li>
                    ))}
                  </ul>
                ) : undefined}
            />
          )}

          {/* 批量操作栏 */}
          {selectedRowKeys.length > 0 && (
            <Space style={{ marginBottom: 12 }}>
              <Text type="secondary">已选 {selectedRowKeys.length} 项</Text>
              {/* 批量操作：整组 Popconfirm 挂 <Auth>，理由同行内删除（AuthButton 返回 null 会炸 antd cloneElement） */}
              <Auth code={BonePermissionCodes.METADATA_PUBLISH}>
                <Popconfirm title={`确认发布选中的 ${selectedRowKeys.length} 个实体？`} onConfirm={async () => {
                  // ⚠ 雪花 ID 必须保持字符串：Number() 会因超出2^53 静默截断，
                  // 导致批量操作命中「另一个」实体。API 签名也是 string[]。
                  const ids = selectedRowKeys.map(String);
                  try {
                    const res = await metadataEntityApi.batchPublish(ids);
                    if (res.code === 200) {
                      const { successCount, failCount, errors } = res.data;
                      if (failCount === 0) {
                        message.success(`已批量发布 ${successCount} 个实体`);
                      } else {
                        // 批量失败必须可追溯（B2）：结果条逐项展示，不留 console-only
                        setBatchResult({ kind: 'publish', successCount, failCount, errors: errors.map((raw) => { const m = String(raw).match(/^id=([^:]+):\s*(.+)$/); return { id: m ? m[1] : String(raw), reason: m ? m[2] : String(raw) }; }) });
                      }
                    } else {
                      message.error(res.message || '批量发布失败');
                    }
                  } catch {
                    message.error('批量发布失败，请确认 bone-metadata-server :9001 已启动');
                  } finally {
                    setSelectedRowKeys([]);
                    load();
                  }
                }}>
                  <Button size="small">批量发布</Button>
                </Popconfirm>
              </Auth>
              <Auth code={BonePermissionCodes.METADATA_MODEL_WRITE}>
                <Popconfirm title={`确认删除选中的 ${selectedRowKeys.length} 个实体？`} onConfirm={async () => {
                  // ⚠ 同上：雪花 ID 不能 Number()，会截断后命中错误实体
                  const ids = selectedRowKeys.map(String);
                  try {
                    const res = await metadataEntityApi.batchDelete(ids);
                    if (res.code === 200) {
                      const { successCount, failCount, errors } = res.data;
                      if (failCount === 0) {
                        message.success(`已批量删除 ${successCount} 个实体`);
                      } else {
                        // 删除失败同样可追溯（与发布同一条结果通道）
                        setBatchResult({ kind: 'delete', successCount, failCount, errors: errors.map((raw) => { const m = String(raw).match(/^id=([^:]+):\s*(.+)$/); return { id: m ? m[1] : String(raw), reason: m ? m[2] : String(raw) }; }) });
                      }
                    } else {
                      message.error(res.message || '批量删除失败');
                    }
                  } catch {
                    message.error('批量删除失败，请确认 bone-metadata-server :9001 已启动');
                  } finally {
                    setSelectedRowKeys([]);
                    load();
                  }
                }}>
                  <Button size="small" danger>批量删除</Button>
                </Popconfirm>
              </Auth>
            </Space>
          )}

          {/* 单表替代分组卡片（B3）：
              此前左树按模块过滤、右又把实体按模块拆成 N 张独立 Table（各自带表头/分页缺失），
              同一维度表达两次且操作列重复渲染。现在左树做模块导航、右一张表 + 「所属模块」列，
              分页/勾选/批量统一，避免「点两处得两个视图」的冗余。 */}
          <Table
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={tableData}
            scroll={{ x: 1300 }}
            rowSelection={{
              selectedRowKeys,
              onChange: setSelectedRowKeys,
            }}
            /* 空态区分「一个对象都没有」与「筛选后没匹配」：
               前者要给建模入口，后者要给清除筛选 —— 同一个默认 Empty 会让用户
               在「我还没建过模型」和「筛选条件写错了」之间反复猜。 */
            locale={{
              emptyText: (
                <Empty description={null}>
                  {hasActiveFilter ? (
                    <Space direction="vertical" size={4}>
                      <Text>没有符合条件的业务对象</Text>
                      <Button
                        onClick={() => {
                          setKeyword('');
                          setTypeFilter(null);
                          setDeliveryFilter(null);
                          setStatusFilter(null);
                          setSelectedModule('all');
                          setPage(1);
                        }}
                      >
                        清除筛选条件
                      </Button>
                    </Space>
                  ) : (
                    <Space direction="vertical" size={4}>
                      <Text>还没有任何业务对象</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        从模型设计页选模板起盘，或从此处空白建模
                      </Text>
                      <Space>
                        {canWrite && (
                          <AuthButton code={BonePermissionCodes.METADATA_MODEL_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                            新建业务对象
                          </AuthButton>
                        )}
                        <Button onClick={() => navigate('/apps')}>去模型设计</Button>
                      </Space>
                    </Space>
                  )}
                </Empty>
              ),
            }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 个业务对象`,
              onChange: (p, ps) => { setPage(p); setPageSize(ps); },
            }}
          />
        </div>
        {/* ↑ 右侧列表结束（flex row：左树 + 右表） */}
      </div>

      {/* 新建/编辑 Modal */}
      <Modal
        title={editing ? '编辑实体' : '新建实体'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
        width={600}
      >
        {!editing && (
          <Segmented
            style={{ marginBottom: 16 }}
            value={createMode}
            onChange={(v) => setCreateMode(v as 'new' | 'template')}
            options={[
              { value: 'new', label: '全新建模' },
              { value: 'template', label: '从平台模板' },
            ]}
          />
        )}
        {!editing && createMode === 'template' && (
          <Card size="small" style={{ marginBottom: 16 }} styles={{ body: { padding: 12 } }}>
            <div style={{ marginBottom: 8 }}>
              <Text strong style={{ fontSize: 13 }}>平台模板</Text>
              <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>默认字段将复制到新实体</Text>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {templates.map((t) => {
                const active = selectedTemplateId === t.id;
                return (
                  <div
                    key={t.id}
                    onClick={() => chooseTemplate(active ? null : t.id)}
                    style={{
                      padding: '8px 10px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      border: active ? '2px solid #1677ff' : '2px solid #d9d9d9',
                      background: active ? '#e6f4ff' : '#fff',
                    }}
                  >
                    <Space size={6} wrap>
                      <Radio checked={active} />
                      <Text strong style={{ fontSize: 13 }}>{t.name}</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>{t.code}{t.currentVersion ? ` · v${t.currentVersion}` : ''}</Text>
                    </Space>
                    {t.description && (
                      <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 4, lineHeight: 1.5 }}>
                        {t.description}
                      </Text>
                    )}
                    {active && tplFields.length > 0 && (
                      <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>
                        默认字段（{tplFields.length} 个）：{tplFields.map((f) => f.displayName || f.code).join('、')}（实例化后可扩展）
                      </Text>
                    )}
                  </div>
                );
              })}
              {templates.length === 0 && (
                <Text type="secondary">暂无平台模板，请直接「全新建模」。</Text>
              )}
            </div>
          </Card>
        )}
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              {!editing ? (
                <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                  <Input disabled={!!editing} />
                </Form.Item>
              ) : (
                <Form.Item name="name" label="名称">
                  <Input disabled />
                </Form.Item>
              )}
            </Col>
          </Row>
          {!editing && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="code" label="编码" rules={[{ required: true }, CODE_RULE]} tooltip="全局唯一，一旦创建不可修改">
                  <Input placeholder="如：user, order" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="tableName" label="数据库表名" rules={[{ required: true }, CODE_RULE]} tooltip="物理表名，建议前缀如 meta_">
                  <Input placeholder="如：sys_user, biz_order" />
                </Form.Item>
              </Col>
            </Row>
          )}
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="实体的业务含义说明" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="界面怎么来？" tooltip="页面产生方式（底层仍存 delivery_mode，1:1 映射见 PRD §0.7）；标准 CRUD 选自动，整页不像 CRUD 选手写">
                <div style={{ display: 'flex', gap: 12, paddingTop: 4 }}>
                  <UiModeCard
                    title="自动生成界面"
                    badge="默认"
                    desc="字段建好即自动列表/表单（catalog 投影 + 可微调列），适合配置型对象"
                    active={uiModeWatch === META_DELIVERY_RUNTIME}
                    disabled={!!editing && editing.status === 1}
                    onSelect={() => form.setFieldsValue({ deliveryMode: META_DELIVERY_RUNTIME })}
                  />
                  <UiModeCard
                    title="手写界面"
                    desc="元数据进代码生成，页面在业务仓手写（或生成后改），适合复杂领域"
                    active={uiModeWatch === 0}
                    disabled={!!editing && editing.status === 1}
                    onSelect={() => form.setFieldsValue({ deliveryMode: META_DELIVERY_GENERATIVE })}
                  />
                </div>
              </Form.Item>
            </Col>
            {!editing && (
              <Col span={8}>
                <Form.Item name="type" label="类型">
                  <Select options={[
                    { value: 0, label: '普通业务实体' },
                    { value: 1, label: '主数据类实体' },
                  ]} />
                </Form.Item>
              </Col>
            )}
            <Col span={8}>
              <Form.Item name="sortOrder" label="排序">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          {/* 模块选择（真实 IAM 模块；scoped 模式由路由注入，禁用） */}
          <Form.Item
            name="moduleId"
            label="所属模块"
            rules={isScoped ? [] : [{ required: true, message: '请选择所属模块' }]}
            extra={(
              /* 上下文提示：左树已选中模块时告诉用户「已按上下文预选」，避免他以为是默认值 */
              !isScoped && selectedIsModule
                ? '已按左侧选中的模块预选，如需改归属可重新选择'
                : undefined
            )}
          >
            <Select
              disabled={isScoped}
              showSearch
              optionFilterProp="label"
              placeholder="选择所属应用模块"
              options={realModuleOptions}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 复制实体 Modal（UC-W2 流程 B） */}
      <Modal
        title={`复制实体：${copySource?.displayName ?? ''}`}
        open={copyOpen}
        onOk={handleCopy}
        onCancel={() => setCopyOpen(false)}
        destroyOnHidden
        width={560}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="将复制实体定义与全部字段为新草稿（关系不复制，需人工重建）"
        />
        <Form form={copyForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="code" label="新编码" rules={[{ required: true }, CODE_RULE]} tooltip="租户内唯一">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="tableName" label="新表名" rules={[{ required: true }, CODE_RULE]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="targetModuleId" label="目标模块" tooltip="缺省保持与源实体同模块；跨应用复制需要目标应用的建模角色">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="保持源模块归属"
              options={realModuleOptions}
            />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 发布摘要预览 Modal（UC-W7） */}
      <PublishPreviewModal
        open={previewOpen}
        entityId={previewEntity?.id ?? null}
        entityName={previewEntity?.displayName}
        onClose={() => setPreviewOpen(false)}
        onPublished={load}
        onViewInterface={previewGoInterface}
        onOpenData={previewGoData}
        onAddField={previewGoAddField}
      />

      {/* 逆向建模 Modal（UC-IMP：存量物理表 → 目录实体草稿） */}
      <ImportTableModal
        open={tableImportOpen}
        onClose={() => setTableImportOpen(false)}
        onImported={(entityId) => {
          setTableImportOpen(false);
          load();
          navigate(`/entities/${entityId}`);
        }}
      />

      {/* 模型导入 Modal（F13） */}
      <ImportModelModal
        open={importOpen}
        moduleId={isScoped ? moduleId : undefined}
        moduleOptions={realModuleOptions}
        onClose={() => setImportOpen(false)}
        onImported={(entityId) => {
          setImportOpen(false);
          load();
          navigate(`/entities/${entityId}`);
        }}
      />

      {/* B2 命令面板：Ctrl+K 只做「跳实体」（PRD §5.4 规则 31；融合方案 §3.3.3）
          键盘完备：↑↓ 移动 / Enter 打开 / Esc 关闭（antd Modal 自带 Esc，这里只需处理前后键）。 */}
      <Modal
        title={<Space><Text strong>跳转对象</Text><Text type="secondary" style={{ fontSize: 12 }}>Ctrl / ⌘ + K · ↑↓ 选择 · Enter 打开</Text></Space>}
        open={cmdOpen}
        onCancel={() => { setCmdOpen(false); setCmdKeyword(''); }}
        footer={null}
        width={520}
        destroyOnHidden
      >
        <Input
          autoFocus
          allowClear
          placeholder="输入对象名称 / 编码 / 表名，回车打开第一个匹配"
          value={cmdKeyword}
          onChange={(e) => { setCmdKeyword(e.target.value); setCmdIndex(0); }}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') {
              e.preventDefault();
              setCmdIndex((i) => (cmdResults.length === 0 ? 0 : (i + 1) % cmdResults.length));
            } else if (e.key === 'ArrowUp') {
              e.preventDefault();
              setCmdIndex((i) => (cmdResults.length === 0 ? 0 : (i - 1 + cmdResults.length) % cmdResults.length));
            } else if (e.key === 'Enter') {
              // 高亮位优先（↑↓ 选中什么就打开什么）；未移动过则等价于第一条
              e.preventDefault();
              const target = cmdResults[cmdIndex] ?? cmdResults[0];
              if (target) {
                navigate(`/entities/${target.id}`);
                setCmdOpen(false);
                setCmdKeyword('');
              }
            }
          }}
        />
        <div style={{ marginTop: 12, maxHeight: 320, overflow: 'auto' }}>
          {cmdResults.length === 0 ? (
            <Text type="secondary" style={{ display: 'block', textAlign: 'center', padding: '24px 0' }}>
              {cmdKeyword.trim() ? `无匹配「${cmdKeyword.trim()}」的对象` : '输入名称 / 编码 / 表名开始搜索'}
            </Text>
          ) : (
            cmdResults.map((e, i) => (
              <div
                key={e.id}
                onClick={() => { navigate(`/entities/${e.id}`); setCmdOpen(false); setCmdKeyword(''); }}
                onMouseEnter={() => setCmdIndex(i)}
                style={{
                  padding: '8px 10px',
                  borderRadius: 6,
                  cursor: 'pointer',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  // 高亮跟随键盘位，鼠标悬停也会同步（业界通例）
                  background: i === cmdIndex ? '#e6f4ff' : 'transparent',
                }}
              >
                <Space>
                  <Text strong>{e.displayName}</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>{e.code}</Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>{e.tableName}</Text>
                </Space>
                <Tag color={e.deliveryMode === META_DELIVERY_RUNTIME ? 'purple' : 'default'} style={{ marginInlineEnd: 0 }}>
                  {e.deliveryMode === META_DELIVERY_RUNTIME ? '自动界面' : '手写界面'}
                </Tag>
              </div>
            ))
          )}
        </div>
        {cmdResults.length > 0 && (
          <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
            共 {cmdResults.length} 条（最多显示 8 条）
          </Text>
        )}
      </Modal>
    </div>
  );
};

export default EntityManagement;
