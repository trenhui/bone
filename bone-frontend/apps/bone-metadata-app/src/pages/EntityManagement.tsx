import { useEffect, useState, useMemo, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Button, Breadcrumb, Card, Form, Input, InputNumber, Modal, Popconfirm, Segmented, Select,
  Space, Table, Tag, Tree, message, Badge, Typography, Row, Col,
} from 'antd';
import {
  PlusOutlined, AppstoreOutlined, CopyOutlined, DatabaseOutlined, FolderOutlined, SearchOutlined,
  ThunderboltOutlined, DownloadOutlined, ImportOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { DataNode } from 'antd/es/tree';
import { errorMessage, metadataEntityApi, metadataTemplateApi } from '../services/metadataApi';
import PublishPreviewModal from '../components/PublishPreviewModal';
import ImportModelModal from '../components/ImportModelModal';
import { exportEntityModel } from '../utils/modelTransfer';
import { appApi, moduleApi, type BoneModule } from '../services/appModuleApi';
import type {
  CopyEntityReq, CreateMetaEntityReq, MetaEntity, MetaTemplate, MetaTemplateField, UpdateMetaEntityReq,
} from '../types';
import { DELIVERY_MODE, ENTITY_STATUS, META_DELIVERY_RUNTIME, META_ENTITY_PUBLISHED } from '../types';

const { Text } = Typography;

/** 标识符白名单（与后端 2a UC-MT2 / validate 规则一致） */
const CODE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/;
const CODE_RULE = { pattern: CODE_PATTERN, message: '以字母开头，仅字母/数字/下划线' };

// ----- types -----

interface EntityGroup {
  key: string;
  label: string;
  entities: MetaEntity[];
}

// ----- Component -----

const EntityManagement: React.FC = () => {
  const navigate = useNavigate();
  const { appId, moduleId } = useParams<{ appId: string; moduleId: string }>();
  const isScoped = !!appId && !!moduleId;

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<MetaEntity[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<number | null>(null);
  const [deliveryFilter, setDeliveryFilter] = useState<number | null>(null);
  const [statusFilter, setStatusFilter] = useState<number | null>(null);
  const [selectedModule, setSelectedModule] = useState<string>(isScoped ? moduleId! : 'all');
  // 树选中语义（提前声明，供 load 依赖）：all=全部分组视图；app-<id>=该应用下模块；mod-<id>=该模块平铺列表
  const selectedIsModule = selectedModule.startsWith('mod-');
  const selectedIsApp = selectedModule.startsWith('app-');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetaEntity | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  // ----- 权限形态（UC-W8 / 2b F2）：scoped 模式下按应用角色收敛为只读；后端 403 永远兜底 -----
  const [myRole, setMyRole] = useState<string | undefined>(undefined);
  const canWrite = myRole !== 'viewer'; // undefined（平台管理员 / 非 scoped 视图）视为可写

  useEffect(() => {
    if (!appId) { setMyRole(undefined); return; }
    let cancelled = false;
    appApi.listMine({ page: 1, size: 100 }).then((res) => {
      if (!cancelled && res.code === 200) {
        const app = (res.data.list ?? []).find((a) => String(a.id) === String(appId));
        setMyRole(app?.myRole);
      }
    }).catch(() => { /* 角色获取失败时不收敛 UI，交由后端 403 兜底 */ });
    return () => { cancelled = true; };
  }, [appId]);

  // ----- 发布预览（UC-W7 摘要级） -----
  const [previewEntity, setPreviewEntity] = useState<MetaEntity | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const openPublishPreview = (record: MetaEntity) => {
    setPreviewEntity(record);
    setPreviewOpen(true);
  };

  // ----- 实体复制（UC-W2 流程 B） -----
  const [copyOpen, setCopyOpen] = useState(false);
  const [copySource, setCopySource] = useState<MetaEntity | null>(null);
  const [copyForm] = Form.useForm();

  // ----- 模型导入导出（F13） -----
  const [importOpen, setImportOpen] = useState(false);
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

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { pageNum: page, pageSize };
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
        setData(res.data.list);
        setTotal(res.data.total);
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

  // ----- 真实应用/模块树（IAM 真源；仅非 scoped 模式加载）-----
  const [appModules, setAppModules] = useState<{ appId: string; appName: string; modules: BoneModule[] }[]>([]);
  useEffect(() => {
    if (isScoped) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await appApi.listMine({ page: 1, size: 100 });
        const apps = res.code === 200 ? res.data.list ?? [] : [];
        const pairs = await Promise.all(
          apps.map(async (a) => {
            try {
              const mr = await moduleApi.listByApp(a.id, { pageNum: 1, pageSize: 100 });
              return { appId: a.id, appName: a.name, modules: mr.code === 200 ? mr.data.list ?? [] : [] };
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

  // moduleId → 模块名（分组标题）与 moduleId → 应用树键（应用级过滤）
  const moduleIndex = useMemo(() => {
    const labelById = new Map<string, string>();
    const appKeyById = new Map<string, string>();
    appModules.forEach((a) =>
      a.modules.forEach((m) => {
        labelById.set(String(m.id), m.name);
        appKeyById.set(String(m.id), `app-${a.appId}`);
      }));
    return { labelById, appKeyById };
  }, [appModules]);

  // 真实模块下拉选项（IAM 真源，替代原硬编码假选项）
  const realModuleOptions = useMemo(
    () =>
      appModules.flatMap((a) =>
        a.modules.map((m) => ({ value: String(m.id), label: `${a.appName} / ${m.name}` })),
      ),
    [appModules],
  );

  // ----- 模块分组（按实体真实 moduleId 归属；未归属进「未分组」）-----
  const grouped = useMemo<EntityGroup[]>(() => {
    const groups: Record<string, EntityGroup> = {
      uncategorized: { key: 'uncategorized', label: '未分组', entities: [] },
    };
    moduleIndex.labelById.forEach((label, id) => {
      groups[`mod-${id}`] = { key: `mod-${id}`, label, entities: [] };
    });
    data.forEach((entity) => {
      const key = entity.moduleId != null ? `mod-${entity.moduleId}` : 'uncategorized';
      (groups[key] ?? groups.uncategorized).entities.push(entity);
    });
    return Object.values(groups).filter((g) => g.entities.length > 0);
  }, [data, moduleIndex]);

  // 树选中语义：all=全部分组视图；app-<id>=该应用下模块的分组视图；mod-<id>=该模块平铺列表
  const groupsToRender = useMemo(() => {
    if (!selectedIsApp) return grouped;
    return grouped.filter(
      (g) => g.key === 'uncategorized' || moduleIndex.appKeyById.get(g.key.slice(4)) === selectedModule,
    );
  }, [grouped, selectedIsApp, selectedModule, moduleIndex]);
  const moduleScopedData = useMemo(
    () => (selectedIsModule ? data.filter((e) => `mod-${e.moduleId}` === selectedModule) : data),
    [data, selectedIsModule, selectedModule],
  );

  // ----- Modal handlers -----
  // 新建双入口（UC-W2）：全新 / 从平台模板（G3 实例化）
  const [createMode, setCreateMode] = useState<'new' | 'template'>('new');
  const [templates, setTemplates] = useState<MetaTemplate[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [tplFields, setTplFields] = useState<MetaTemplateField[]>([]);

  const openCreate = () => {
    setEditing(null);
    setCreateMode('new');
    setSelectedTemplateId(null);
    setTplFields([]);
    form.resetFields();
    form.setFieldsValue({ type: 0, deliveryMode: 0 });
    setModalOpen(true);
    metadataTemplateApi.list().then((res) => {
      if (res.code === 200) setTemplates(res.data.filter((t) => t.status === 1));
    }).catch(() => { /* 模板目录不可用时保留全新入口 */ });
  };

  const chooseTemplate = (id: number | null) => {
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
    const resolvedModuleId: string | number | undefined = isScoped
      ? moduleId
      : values.moduleId != null
        ? values.moduleId
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
          message.success('模板实例化成功，默认字段已复制');
          setModalOpen(false);
          const newId = res.data;
          navigate(
            appId && moduleId
              ? `/apps/${appId}/modules/${moduleId}/entities/${newId}`
              : `/entities/${newId}`,
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
          load();
        } else {
          message.error(res.message);
        }
      }
    } catch (e) {
      if (e && typeof e === 'object' && 'errorFields' in e) return; // 表单校验错误已就地提示
      message.error('保存失败');
    }
  };

  const handleDelete = async (id: number) => {
    const res = await metadataEntityApi.delete(id);
    if (res.code === 200) {
      message.success('已删除');
      load();
    } else {
      message.error(res.message);
    }
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
      title: '操作',
      key: 'action',
      width: 340,
      render: (_, record) =>
        canWrite ? (
          <Space size={2} wrap>
            <Button type="link" size="small" onClick={() => openEdit(record)} disabled={record.status === 1}>
              编辑
            </Button>
            <Button type="link" size="small" onClick={() => navigate(`/entities/${record.id}`)}>
              字段
            </Button>
            {record.deliveryMode === META_DELIVERY_RUNTIME && record.status === META_ENTITY_PUBLISHED && (
              <Button type="link" size="small" onClick={() => navigate(`/entities/${record.id}/data`)}>
                数据
              </Button>
            )}
            <Button
              type="link"
              size="small"
              icon={<CopyOutlined />}
              onClick={() => openCopy(record)}
              title="复制定义与字段为新草稿"
            >
              复制
            </Button>
            {record.status === 0 && (
              <Button type="link" size="small" icon={<ThunderboltOutlined />} onClick={() => openPublishPreview(record)}>
                发布
              </Button>
            )}
            <Button
              type="link"
              size="small"
              icon={<DownloadOutlined />}
              onClick={() => handleExport(record)}
              title="导出模型 JSON（定义+字段+关系）"
            >
              导出
            </Button>
            <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
              <Button type="link" size="small" danger disabled={record.status === 1}>删除</Button>
            </Popconfirm>
          </Space>
        ) : (
          <Space size={2} wrap>
            <Button type="link" size="small" onClick={() => navigate(`/entities/${record.id}`)}>
              查看
            </Button>
            <Button
              type="link"
              size="small"
              icon={<DownloadOutlined />}
              onClick={() => handleExport(record)}
              title="导出模型 JSON（定义+字段+关系）"
            >
              导出
            </Button>
          </Space>
        ),
    },
  ];

  // ----- Render -----
  return (
    <div className="page" style={{ display: 'flex', gap: 16 }}>
      {/* 面包屑（模块上下文时） */}
      {isScoped && (
        <div style={{ position: 'absolute', top: 0, left: 0, right: 0 }}>
          <Breadcrumb
            items={[
              { title: <a onClick={() => navigate('/apps')}>应用管理</a> },
              { title: <a onClick={() => navigate(`/apps/${appId}/modules`)}>模块</a> },
              { title: '领域建模' },
            ]}
          />
        </div>
      )}

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
        {isScoped && <div style={{ height: 36 }} /> /* 面包屑占位 */}
        {/* 过滤与操作栏 */}
        {/* gutter 会给 Row 加 -6px 左右负边距，在 flex:1 容器里会把内容顶出视口（实测溢出 10px）
            这里把 Row 自身外边距归零，列间距由 Col 的 padding 承担，视觉间隔不变。 */}
        <Row gutter={[12, 12]} style={{ marginBottom: 16, marginLeft: 0, marginRight: 0 }}>
          <Col>
            <Input.Search
              placeholder="搜索实体名称/编码"
              allowClear
              style={{ width: 220 }}
              onSearch={(v) => { setKeyword(v); setPage(1); }}
            />
          </Col>
          <Col>
            <Select
              style={{ width: 130 }}
              placeholder="类型"
              allowClear
              value={typeFilter}
              onChange={(v) => { setTypeFilter(v ?? null); setPage(1); }}
              options={[
                { value: 0, label: '普通业务' },
                { value: 1, label: '主数据类' },
              ]}
            />
          </Col>
          <Col>
            <Select
              style={{ width: 120 }}
              placeholder="交付模式"
              allowClear
              value={deliveryFilter}
              onChange={(v) => { setDeliveryFilter(v ?? null); setPage(1); }}
              options={[
                { value: 0, label: '生成式(A)' },
                { value: 1, label: '运行时(B)' },
              ]}
            />
          </Col>
          <Col>
            <Select
              style={{ width: 100 }}
              placeholder="状态"
              allowClear
              value={statusFilter}
              onChange={(v) => { setStatusFilter(v ?? null); setPage(1); }}
              options={[
                { value: 0, label: '草稿' },
                { value: 1, label: '已发布' },
                { value: 2, label: '已下线' },
              ]}
            />
          </Col>
          <Col flex="auto" style={{ textAlign: 'right' }}>
            <Space>
              <Button icon={<SearchOutlined />} onClick={load}>刷新</Button>
              {canWrite && (
                <Button icon={<ImportOutlined />} onClick={() => setImportOpen(true)}>导入模型</Button>
              )}
              {canWrite && (
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建实体</Button>
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

        {/* 批量操作栏 */}
        {selectedRowKeys.length > 0 && (
          <Space style={{ marginBottom: 12 }}>
            <Text type="secondary">已选 {selectedRowKeys.length} 项</Text>
            <Popconfirm title={`确认发布选中的 ${selectedRowKeys.length} 个实体？`} onConfirm={async () => {
              const ids = selectedRowKeys.map(Number);
              try {
                const res = await metadataEntityApi.batchPublish(ids);
                if (res.code === 200) {
                  const { successCount, failCount, errors } = res.data;
                  if (failCount === 0) {
                    message.success(`已批量发布 ${successCount} 个实体`);
                  } else {
                    message.warning(`发布成功 ${successCount} 个，失败 ${failCount} 个`);
                    errors.forEach((e) => console.warn('[batch-publish]', e));
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
            <Popconfirm title={`确认删除选中的 ${selectedRowKeys.length} 个实体？`} onConfirm={async () => {
              const ids = selectedRowKeys.map(Number);
              try {
                const res = await metadataEntityApi.batchDelete(ids);
                if (res.code === 200) {
                  const { successCount, failCount, errors } = res.data;
                  if (failCount === 0) {
                    message.success(`已批量删除 ${successCount} 个实体`);
                  } else {
                    message.warning(`删除成功 ${successCount} 个，失败 ${failCount} 个`);
                    errors.forEach((e) => console.warn('[batch-delete]', e));
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
          </Space>
        )}

        {/* 模块分组展示（模块级选中时为客户端过滤的平铺列表） */}
        {!selectedIsModule && groupsToRender.length > 0 ? (
          groupsToRender.map((group) => (
            <Card
              key={group.key}
              size="small"
              title={
                <Space>
                  <FolderOutlined />
                  <span>{group.label}</span>
                  <Tag>{group.entities.length}</Tag>
                </Space>
              }
              style={{ marginBottom: 12 }}
              styles={{ body: { padding: 0 } }}
            >
              <Table
                rowKey="id"
                loading={loading}
                columns={columns}
                dataSource={group.entities}
                pagination={false}
                size="small"
                scroll={{ x: 1160 }}
                rowSelection={{
                  selectedRowKeys,
                  onChange: setSelectedRowKeys,
                }}
              />
            </Card>
          ))
        ) : (
          <Table
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={moduleScopedData}
            scroll={{ x: 1160 }}
            rowSelection={{
              selectedRowKeys,
              onChange: setSelectedRowKeys,
            }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              onChange: (p, ps) => { setPage(p); setPageSize(ps); },
            }}
          />
        )}
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
            <Select
              style={{ width: '100%' }}
              placeholder="选择平台预置模板"
              value={selectedTemplateId}
              onChange={(v) => chooseTemplate(v ?? null)}
              options={templates.map((t) => ({ value: t.id, label: `${t.name}（${t.code}${t.currentVersion ? ' · ' + t.currentVersion : ''}）` }))}
              allowClear
            />
            {tplFields.length > 0 && (
              <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
                默认字段：{tplFields.map((f) => f.code).join('、')}（实例化后可在实体详情中扩展）
              </Text>
            )}
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
              <Form.Item name="deliveryMode" label="交付模式" tooltip="生成式走代码生成；运行时由 metadata-engine 提供动态 API">
                <Select
                  disabled={!!editing && editing.status === 1}
                  options={[
                    { value: 0, label: '生成式 (A)' },
                    { value: 1, label: '运行时 (B)' },
                  ]}
                />
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
    </div>
  );
};

export default EntityManagement;
