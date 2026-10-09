import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import {
  errorMessage,
  metadataEntityApi,
  metadataFieldApi,
  runtimeRecordApi,
} from '../services/metadataApi';
import type { MetaEntity, MetaField, RuntimeRecord } from '../types';
import {
  DELIVERY_MODE,
  ENTITY_STATUS,
  META_DELIVERY_RUNTIME,
  META_ENTITY_PUBLISHED,
  parseEnumValues,
  RUNTIME_READONLY_FIELDS,
} from '../types';
import { Auth, AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import {
  enumOptionsOf,
  isEnumField,
  isRenderSupported,
  loadOverlay,
  projectColumns,
  tableSizeOf,
} from '../utils/columnProjection';

const { Text } = Typography;

function recordId(row: RuntimeRecord): string {
  const id = row.id;
  return id != null ? String(id) : '';
}

function formatCell(value: unknown): string {
  if (value == null) return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

/** ENUM 单元格：码值 → 展示名 */
function renderEnumCell(value: unknown, field: MetaField): string {
  if (value == null || value === '') return '—';
  const opt = enumOptionsOf(field).find((o) => o.code === String(value));
  return opt ? `${opt.label}（${value}）` : String(value);
}

export interface RuntimeDataManagementProps {
  /** 嵌入实体详情「数据」Tab 时传入实体 id；为空则走独立全局「运行时数据」路由（带实体选择器） */
  embeddedEntityId?: string | null;
}

const RuntimeDataManagement: React.FC<RuntimeDataManagementProps> = ({ embeddedEntityId = null }) => {
  const { id: pathId } = useParams<{ id?: string }>();
  const resolvedId = embeddedEntityId ?? pathId ?? null;
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  /** 数据 Tab 空态 CTA：由 /…/data 回溯到实体详情并定位「结构」Tab（B1） */
  const structurePath = `${location.pathname.replace(/\/data\/?$/, '')}?tab=structure`;

  const [form] = Form.useForm();
  const [entities, setEntities] = useState<MetaEntity[]>([]);
  const [entityCode, setEntityCode] = useState<string | null>(null);
  const [detailEntity, setDetailEntity] = useState<MetaEntity | null>(null);
  const [fields, setFields] = useState<MetaField[]>([]);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<RuntimeRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<RuntimeRecord | null>(null);
  const [editingEtag, setEditingEtag] = useState<string | undefined>(undefined);
  const [conflict, setConflict] = useState(false);
  /** W0a2：q/sort 接线 —— 单条件过滤（field:value）*/
  const [searchField, setSearchField] = useState<string | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  /** sort 契约：逗号分隔列名，`-` 前缀降序（RuntimePageQuery.parseSort） */
  const [sortField, setSortField] = useState<string | undefined>(undefined);
  const [sortDesc, setSortDesc] = useState(false);

  /** 当前选中实体：嵌入模式来自详情接口，独立模式来自列表选择 */
  const selectedEntity = useMemo(
    () => detailEntity ?? entities.find((e) => e.code === entityCode) ?? null,
    [detailEntity, entities, entityCode],
  );

  const writableFields = useMemo(
    () => fields.filter((f) => !RUNTIME_READONLY_FIELDS.has(f.code)),
    [fields],
  );

  /** 列投影（预览 ≡ 数据 单一真源）：始终应用本地覆盖 */
  const overlay = useMemo(
    () => (entityCode ? loadOverlay(entityCode) : null),
    // overlay 随选中实体变化重新读取（localStorage 变更由界面 Tab 写入后重载）
    [entityCode, fields],
  );
  const projectedFields = useMemo(
    () => projectColumns(writableFields, overlay),
    [writableFields, overlay],
  );

  // 独立全局路由（/runtime、无 id）：加载 RUNTIME 已发布实体供选择器（W0-2：size 50→500，避免第 51 个后不可选）
  useEffect(() => {
    if (resolvedId) return;
    metadataEntityApi
      .page({ page: 1, size: 500, deliveryMode: META_DELIVERY_RUNTIME, status: META_ENTITY_PUBLISHED })
      .then((res) => {
        if (res.code !== 200) {
          message.error(errorMessage(res));
          return;
        }
        const runtimePublished = normalizeRuntimePublished(res.data.records);
        setEntities(runtimePublished);
        const fromQuery = searchParams.get('entity');
        // W0-1 / PRD D12：无 :id 且无 ?entity= 时**不替用户决策**——保持空态由用户显式选择。
        // 旧实现会 setEntityCode(runtimePublished[0].code)，即「/runtime 静默选中第一个实体」：
        // 用户以为在看自己选的实体，实际看到的是列表首项的数据（静默串实体）。
        if (fromQuery && runtimePublished.some((e) => e.code === fromQuery)) {
          setEntityCode(fromQuery);
        }
      });
    // entityCode 仅用于首次为空时初始化，不应作为依赖
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, resolvedId]);

  // 嵌入模式：按实体 id 拉详情得到 code（W0-1：/…/:id/data 绑定 :id，不再静默看错实体）
  useEffect(() => {
    if (!resolvedId) return;
    setDetailEntity(null);
    metadataEntityApi.detail(resolvedId).then((res) => {
      if (res.code === 200) {
        setDetailEntity(res.data);
        setEntityCode(res.data.code);
      } else {
        message.error(errorMessage(res));
      }
    });
  }, [resolvedId]);

  // 字段随选中实体变化加载
  useEffect(() => {
    const eid = selectedEntity?.id;
    if (!eid) {
      setFields([]);
      return;
    }
    metadataFieldApi.page(eid, { page: 1, size: 500 }).then((res) => {
      if (res.code === 200) setFields(normalizePageRecords(res.data));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedEntity?.id]);

  /**
   * 请求参数由列投影产出（W0a2 + §0.6.3）：
   * `fields` = 当前可见列的 code ⇒ 界面 Tab 关掉的列不会出现在响应里，
   * 与「预览列 ≡ 数据列」同源，杜绝两边各算一遍导致的漂移。
   */
  const load = useCallback(async () => {
    if (!entityCode) return;
    setLoading(true);
    try {
      const fieldsParam = projectedFields.length
        ? projectedFields.map((f) => f.code).join(',')
        : undefined;
      const res = await runtimeRecordApi.page(entityCode, {
        page,
        size: pageSize,
        fields: fieldsParam,
        // q 契约：field:value（后端 RuntimePageQuery.parseFilter）
        q: searchField && keyword ? `${searchField}:${keyword}` : undefined,
        // sort 契约：`-` 前缀降序（后端 RuntimePageQuery.parseSort）
        sort: sortField ? `${sortDesc ? '-' : ''}${sortField}` : undefined,
      });
      if (res.code === 200) {
        const pageData = normalizePage(res.data);
        setData(pageData.records);
        setTotal(pageData.total);
      } else {
        message.error(errorMessage(res));
      }
    } catch {
      message.error('加载运行时数据失败，请确认实体已发布且物理表存在');
    } finally {
      setLoading(false);
    }
  }, [entityCode, page, pageSize, projectedFields, searchField, keyword, sortField, sortDesc]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setEditingEtag(undefined);
    setConflict(false);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = async (record: RuntimeRecord) => {
    if (!entityCode) return;
    setConflict(false);
    const rid = recordId(record);
    if (!rid) {
      message.error('记录缺少主键 id');
      return;
    }
    // 拉取最新记录以捕获 ETag（W0-4：乐观并发，编辑提交带 If-Match）
    try {
      const res = await runtimeRecordApi.get(entityCode, rid);
      if (res.code !== 200) {
        message.error(errorMessage(res));
        return;
      }
      const latest = res.data;
      setEditing(latest);
      setEditingEtag(res.etag);
      const values: Record<string, unknown> = {};
      writableFields.forEach((f) => {
        const v = latest[f.code];
        if ((f.type === 'DATE' || f.type === 'DATETIME') && v != null) {
          const d = dayjs(String(v));
          values[f.code] = d.isValid() ? d : undefined;
        } else {
          values[f.code] = v;
        }
      });
      form.setFieldsValue(values);
      setModalOpen(true);
    } catch {
      message.error('加载记录失败');
    }
  };

  const handleDelete = async (record: RuntimeRecord) => {
    if (!entityCode) return;
    const id = recordId(record);
    if (!id) {
      message.error('记录缺少主键 id');
      return;
    }
    try {
      const res = await runtimeRecordApi.delete(entityCode, id);
      if (res.code === 200) {
        message.success('已删除');
        load();
      } else {
        message.error(errorMessage(res));
      }
    } catch {
      message.error('删除失败');
    }
  };

  /** 把表单值按字段类型规整（DATE/DATETIME 的 dayjs → 字符串） */
  const buildBody = (values: Record<string, unknown>): RuntimeRecord => {
    const body: RuntimeRecord = {};
    writableFields.forEach((f) => {
      const v = values[f.code];
      if ((f.type === 'DATE' || f.type === 'DATETIME') && v != null && dayjs.isDayjs(v)) {
        body[f.code] = (v as Dayjs).format(f.type === 'DATE' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss');
      } else {
        body[f.code] = v as unknown;
      }
    });
    return body;
  };

  const handleSubmit = async () => {
    if (!entityCode) return;
    const values = await form.validateFields();
    const body = buildBody(values);
    try {
      if (editing) {
        const id = recordId(editing);
        const res = await runtimeRecordApi.update(entityCode, id, body, editingEtag);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          setEditing(null);
          setEditingEtag(undefined);
          load();
        } else {
          message.error(errorMessage(res));
        }
      } else {
        const res = await runtimeRecordApi.create(entityCode, body);
        if (res.code === 200) {
          message.success('创建成功');
          setModalOpen(false);
          load();
        } else {
          message.error(errorMessage(res));
        }
      }
    } catch (err) {
      // W0-4：412 并发冲突 —— 提示重新加载，而非静默失败
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 412) {
        setConflict(true);
        message.warning('数据已被他人修改（412 并发冲突），请重新加载后再编辑');
        if (editing) {
          const rid = recordId(editing);
          const res = await runtimeRecordApi.get(entityCode, rid);
          if (res.code === 200) {
            setEditing(res.data);
            setEditingEtag(res.etag);
          }
        }
      } else {
        message.error(editing ? '更新失败' : '创建失败');
      }
    }
  };

  const tableColumns: ColumnsType<RuntimeRecord> = useMemo(() => {
    const cols: ColumnsType<RuntimeRecord> = projectedFields.map((f) => ({
      title: f.displayName || f.code,
      dataIndex: f.code,
      key: f.code,
      ellipsis: true,
      sorter: true,
      sortOrder: sortField === f.code ? (sortDesc ? 'descend' : 'ascend') : null,
      render: (_: unknown, row) => {
        if (isEnumField(f)) return renderEnumCell(row[f.code], f);
        return formatCell(row[f.code]);
      },
    }));
    if (cols.length === 0) {
      cols.push({
        title: 'id',
        dataIndex: 'id',
        key: 'id',
        render: (_: unknown, row) => formatCell(row.id),
      });
    }
    cols.push({
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 160,
      sorter: false,
      sortOrder: null,
      render: (_: unknown, row) => (
        <Space>
          <AuthButton code={BonePermissionCodes.METADATA_RUNTIME_WRITE} type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </AuthButton>
          <Auth code={BonePermissionCodes.METADATA_RUNTIME_WRITE}>
            <Popconfirm title="确认删除？" onConfirm={() => handleDelete(row)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </Auth>
        </Space>
      ),
    });
    return cols;
    // handleDelete/openEdit 为稳定的页面级函数引用，加入会导致循环
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectedFields, entityCode, sortField, sortDesc]);

  const renderFieldInput = (field: MetaField) => {
    const type = (field.type || 'STRING').toUpperCase();
    // W0-5 / PRD §5.5：未支持类型必须显式降级提示 + 字段名，禁止静默渲染成裸 Input
    if (!isRenderSupported(field)) {
      return (
        <Alert
          key={field.id}
          type="warning"
                    showIcon
          style={{ marginBottom: 12 }}
          message={`字段「${field.displayName || field.code}」的类型 ${field.type} 暂不支持录入控件`}
          description="该类型在运行时 API 中可读写，但建模界面暂无对应控件；如需界面录入请改用受支持类型。"
        />
      );
    }
    if (type === 'BOOLEAN') {
      return (
        <Form.Item name={field.code} label={field.displayName} valuePropName="checked">
          <Switch />
        </Form.Item>
      );
    }
    if (type === 'INTEGER' || type === 'LONG') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <InputNumber style={{ width: '100%' }} />
        </Form.Item>
      );
    }
    if (type === 'DECIMAL') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <InputNumber style={{ width: '100%' }} step={0.01} />
        </Form.Item>
      );
    }
    if (type === 'DATE' || type === 'DATETIME') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <DatePicker
            showTime={type === 'DATETIME'}
            format={type === 'DATE' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'}
            style={{ width: '100%' }}
          />
        </Form.Item>
      );
    }
    if (type === 'ENUM') {
      const options = parseEnumValues(field.enumValues);
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <Select
            allowClear
            placeholder={options.length ? '请选择' : '该枚举未配置码表'}
            options={options.map((o) => ({ value: o.code, label: `${o.label}（${o.code}）` }))}
          />
        </Form.Item>
      );
    }
    if (type === 'TEXT') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <Input.TextArea rows={3} />
        </Form.Item>
      );
    }
    return (
      <Form.Item
        name={field.code}
        label={field.displayName}
        rules={field.required ? [{ required: true, message: `请填写${field.displayName}` }] : []}
      >
        <Input />
      </Form.Item>
    );
  };

  // 嵌入模式且实体非 RUNTIME / 未发布：给出明确指引，而非静默错误
  const embeddedBlocked = resolvedId != null && selectedEntity != null
    && (selectedEntity.deliveryMode !== META_DELIVERY_RUNTIME || selectedEntity.status !== META_ENTITY_PUBLISHED);

  return (
    <div className="page">
      {/* 页头：声明本页职责边界。
          「运行时数据」与实体列表的「数据」按钮、实体工作区的「数据」页签是**同一份数据**，
          区别只在**上下文来源**：本页是跨实体运营台（需自己选实体），后两者已锁定实体。
          此前页面只说「模式 B 是什么」，没说「我什么时候该来这里」，用户因此把它
          当成第三个数据入口，在三个入口间反复试错。 */}
      {!resolvedId && (
        <Space direction="vertical" size={0} style={{ marginBottom: 16, width: '100%' }}>
          <Space size={8} wrap>
            <Typography.Title level={5} style={{ margin: 0 }}>运行时数据</Typography.Title>
            <Tag color="purple" style={{ fontSize: 12 }}>跨实体数据运营台</Tag>
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            这里是<Text strong>跨实体</Text>的数据入口，适合你不确定数据在哪个模型、或者要跨多个模型巡检记录时使用。
            若你已知要操作哪个实体，请从「实体」列表点行内「数据」按钮，或在实体工作区打开「数据」页签——
            那两条路径会自动锁定当前实体，不会看错数据。
          </Text>
        </Space>
      )}
      {!resolvedId && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="仅「运行时 (B) + 已发布」的实体有物理表"
          description={
            <>
              经 <Text code>/api/v1/runtime/entities/&#123;code&#125;/records</Text>{' '}
              直接读写物理表，无需代码生成。生成式 (A) 实体请到「代码生成」查看。
            </>
          }
        />
      )}

      {/* 独立全局路由：实体选择器；嵌入模式不显示 */}
      {!resolvedId && (
        <Space style={{ marginBottom: 16 }} wrap>
          <span>实体：</span>
          <Select
            style={{ minWidth: 280 }}
            placeholder="选择 RUNTIME 已发布实体"
            value={entityCode ?? undefined}
            onChange={(code) => {
              setEntityCode(code);
              setPage(1);
            }}
            options={entities.map((e) => ({
              value: e.code,
              label: `${e.displayName} (${e.code}) → ${e.tableName}`,
            }))}
            notFoundContent="暂无符合条件的模型，请先在模型管理中创建并发布 RUNTIME 模型"
            /* W0-2：size 50→500 后候选可达数百个，选择器必须可搜索——
               否则「去截断」只是把不可达项从「第 51 个之后」推迟到「第 501 个之后」。 */
            showSearch
            optionFilterProp="label"
            filterOption={(input, option) =>
              String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
            }
          />
          <Button icon={<ReloadOutlined />} onClick={load} disabled={!entityCode}>
            刷新
          </Button>
          <AuthButton code={BonePermissionCodes.METADATA_RUNTIME_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate} disabled={!entityCode}>
            新建记录
          </AuthButton>
        </Space>
      )}

      {selectedEntity && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
          物理表：<Text code>{selectedEntity.tableName}</Text> · 交付模式：
          {DELIVERY_MODE[selectedEntity.deliveryMode ?? 0]} · 状态：
          {ENTITY_STATUS[selectedEntity.status]}
        </Text>
      )}

      {embeddedBlocked && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="该实体暂不支持运行时数据管理"
          description={
            selectedEntity?.deliveryMode !== META_DELIVERY_RUNTIME
              ? '仅「运行时 (B)」交付模式的实体发布后才提供动态 CRUD 数据管理，请改用生成式(A)实体的业务应用录入数据。'
              : '请先在工作区「发布」Tab 完成发布，物理表建表后即可在此管理数据。'
          }
        />
      )}

      {!embeddedBlocked && selectedEntity && (
        <>
          {/* B1：数据 Tab 空态 —— 无字段时给「补齐字段并发布」结构建议，
              而不是渲染一张零列的空表格（用户会以为「没数据」而非「没字段」） */}
          {writableFields.length === 0 ? (
            <Empty
              style={{ margin: '32px 0' }}
              description={
                <Space direction="vertical" size={2}>
                  <Text>该实体还没有可录入的字段</Text>
                  <Text type="secondary">
                    请先在「结构」页签补齐字段并发布，物理表建表后即可在此录入数据
                  </Text>
                </Space>
              }
            >
              <Button type="primary" onClick={() => navigate(structurePath)}>
                去结构补齐字段
              </Button>
            </Empty>
          ) : (
            <>
          {resolvedId && (
            <Space style={{ marginBottom: 16 }} wrap>
              <AuthButton code={BonePermissionCodes.METADATA_RUNTIME_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                新建记录
              </AuthButton>
              <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
              {/* W0a2：q 接线（契约 = field:value） */}
              <Select
                allowClear
                style={{ minWidth: 160 }}
                placeholder="搜索字段"
                value={searchField}
                onChange={(v) => { setSearchField(v); setPage(1); }}
                options={projectedFields.map((f) => ({ value: f.code, label: f.displayName || f.code }))}
              />
              <Input.Search
                allowClear
                style={{ width: 240 }}
                placeholder="搜索值（精确/模糊取决于后端规则）"
                value={keyword}
                disabled={!searchField}
                onChange={(e) => setKeyword(e.target.value)}
                onSearch={() => setPage(1)}
              />
            </Space>
          )}

          <Table<RuntimeRecord>
            rowKey={(row) => recordId(row) || JSON.stringify(row)}
            loading={loading}
            columns={tableColumns}
            dataSource={data}
            size={tableSizeOf(overlay?.density)}
            scroll={{ x: true }}
            /* 排序交给后端：列排序变化 → 受控状态 → sort 契约（- 前缀降序） */
            onChange={(_pagination, _filters, sorter) => {
              const s = Array.isArray(sorter) ? sorter[0] : sorter;
              const field = s?.field ?? s?.columnKey;
              if (s?.order && field) {
                setSortField(String(field));
                setSortDesc(s.order === 'descend');
              } else {
                setSortField(undefined);
                setSortDesc(false);
              }
            }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              onChange: (p, ps) => {
                setPage(p);
                setPageSize(ps);
              },
            }}
          />
            </>
          )}
        </>
      )}

      <Modal
        title={editing ? '编辑记录' : '新建记录'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        width={560}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          {conflict && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              message="记录已被他人修改"
              description="下方表单已重新加载最新数据，请确认后再次提交。"
            />
          )}
          {writableFields.length === 0 ? (
            <Alert
              type="warning"
              message="该模型尚未配置建模字段，请先在模型详情的「结构」页签中维护"
            />
          ) : (
            writableFields.map((f) => <div key={f.id}>{renderFieldInput(f)}</div>)
          )}
        </Form>
      </Modal>
    </div>
  );
};

// ----- 内部工具 -----
function normalizeRuntimePublished(list: MetaEntity[]): MetaEntity[] {
  return list.filter(
    (e) => e.deliveryMode === META_DELIVERY_RUNTIME && e.status === META_ENTITY_PUBLISHED,
  );
}

function normalizePageRecords(page: { records?: MetaEntity[] | MetaField[] }): MetaField[] {
  return (page.records as MetaField[]) || [];
}

function normalizePage<T>(raw: { records?: T[]; total?: string | number; page?: number; size?: number }) {
  return {
    records: raw.records ?? [],
    total: typeof raw.total === 'string' ? Number(raw.total) : (raw.total ?? 0),
    page: raw.page ?? 1,
    size: raw.size ?? 20,
  };
}

export default RuntimeDataManagement;
