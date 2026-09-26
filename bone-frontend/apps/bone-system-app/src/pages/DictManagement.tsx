import React, { useEffect, useMemo, useState } from 'react';
import {
  Row,
  Col,
  Card,
  Table,
  Button,
  Space,
  Input,
  Select,
  Tag,
  Switch,
  Modal,
  Form,
  InputNumber,
  TreeSelect,
  Drawer,
  Upload,
  Typography,
  Descriptions,
  Popconfirm,
  Empty,
  Alert,
  DatePicker,
  message,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UploadOutlined,
  DownloadOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import type {
  DictType,
  DictItem,
  DictItemText,
  DictEnumDiff,
  DictExport,
  DictCategory,
} from '@/types';
import { dictApi } from '@/services/api';
import { invalidateDictCache } from '@/hooks/useDict';

const CATEGORY_COLOR: Record<string, string> = {
  ENUM: 'purple',
  LIST: 'blue',
  CASCADE: 'cyan',
};

const TAG_TYPE_OPTIONS = [
  { value: 'default', label: '默认' },
  { value: 'info', label: '信息' },
  { value: 'success', label: '成功' },
  { value: 'warning', label: '警告' },
  { value: 'error', label: '危险' },
];

const VALUE_TYPE_OPTIONS = [
  { value: 'STRING', label: 'STRING 文本' },
  { value: 'INT', label: 'INT 整数' },
  { value: 'DECIMAL', label: 'DECIMAL 小数' },
  { value: 'BOOLEAN', label: 'BOOLEAN 布尔' },
];

const LANGUAGE_OPTIONS = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' },
  { value: 'zh-TW', label: '繁體中文' },
];

const TAG_COLOR: Record<string, string> = {
  default: 'default',
  info: 'blue',
  success: 'green',
  warning: 'orange',
  error: 'red',
};

/** 生效状态标记：Oracle 时间有效性口径——历史数据按旧口径展示、新单据用新口径。 */
const effectiveTag = (item: DictItem) => {
  const now = dayjs();
  const from = item.effectiveFrom ? dayjs(item.effectiveFrom) : null;
  const to = item.effectiveTo ? dayjs(item.effectiveTo) : null;
  if (from && now.isBefore(from)) return <Tag color="blue">未生效</Tag>;
  if (to && now.isAfter(to)) return <Tag color="red">已过期</Tag>;
  if (!from && !to) return <Typography.Text type="secondary">长期有效</Typography.Text>;
  return <Tag color="green">生效中</Tag>;
};

const toLocalIso = (v?: unknown) =>
  v && (v as { format?: (s: string) => string }).format
    ? (v as { format: (s: string) => string }).format('YYYY-MM-DDTHH:mm:ss')
    : undefined;

const DictManagement: React.FC = () => {
  // ---------- 字典类型（左） ----------
  const [types, setTypes] = useState<DictType[]>([]);
  const [typeKeyword, setTypeKeyword] = useState('');
  const [typeCategory, setTypeCategory] = useState<DictCategory | undefined>();
  const [selectedType, setSelectedType] = useState<DictType | null>(null);
  const [typeLoading, setTypeLoading] = useState(false);
  const [typeModalOpen, setTypeModalOpen] = useState(false);
  const [editingType, setEditingType] = useState<DictType | null>(null);
  const [typeForm] = Form.useForm();

  // ---------- 层级视图 ----------
  const [hierarchies, setHierarchies] = useState<string[]>(['DEFAULT']);
  const [hierarchyCode, setHierarchyCode] = useState<string>('DEFAULT');

  // ---------- 字典项（右） ----------
  const [items, setItems] = useState<DictItem[]>([]);
  const [itemLoading, setItemLoading] = useState(false);
  const [itemKeyword, setItemKeyword] = useState('');
  const [onlyEnabled, setOnlyEnabled] = useState(false);
  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<DictItem | null>(null);
  const [itemTexts, setItemTexts] = useState<DictItemText[]>([]);
  const [itemForm] = Form.useForm();

  // ---------- 枚举同步 ----------
  const [enumDrawerOpen, setEnumDrawerOpen] = useState(false);
  const [enumDiff, setEnumDiff] = useState<DictEnumDiff | null>(null);

  const isCascade = selectedType?.category === 'CASCADE';
  const isEnum = selectedType?.category === 'ENUM';

  const fetchTypes = async () => {
    setTypeLoading(true);
    try {
      const res = await dictApi.getTypePage({
        keyword: typeKeyword,
        category: typeCategory,
        pageSize: 500,
      });
      const list = res.data?.list ?? [];
      setTypes(list);
      setSelectedType((cur) => (cur ? list.find((t) => t.code === cur.code) ?? cur : list[0] ?? null));
    } catch {
      message.error('获取字典类型失败');
    } finally {
      setTypeLoading(false);
    }
  };

  useEffect(() => {
    fetchTypes();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [typeCategory]);

  const fetchHierarchies = async (code: string) => {
    try {
      const res = await dictApi.getHierarchies(code);
      const list = res.data ?? [];
      setHierarchies(list.length ? list : ['DEFAULT']);
      setHierarchyCode((cur) => (list.includes(cur) ? cur : 'DEFAULT'));
    } catch {
      setHierarchies(['DEFAULT']);
    }
  };

  const fetchItems = async (typeCode?: string) => {
    const code = typeCode ?? selectedType?.code;
    if (!code) {
      setItems([]);
      return;
    }
    setItemLoading(true);
    try {
      // 级联值域按层级视图取树；列表/枚举值域走分页（关键字下推后端，不做前端全量过滤）
      let raw: DictItem[];
      if (selectedType?.category === 'CASCADE') {
        const res = await dictApi.getItemTree(code, hierarchyCode);
        raw = res.data ?? [];
      } else {
        const res = await dictApi.getItemPage({
          typeCode: code,
          keyword: itemKeyword,
          status: onlyEnabled ? 1 : undefined,
          pageSize: 500,
        });
        raw = res.data?.list ?? [];
      }
      setItems(filterTree(raw));
    } catch {
      message.error('获取字典项失败');
    } finally {
      setItemLoading(false);
    }
  };

  /** 树形数据也支持关键字/启停过滤：命中自身或子孙即保留整条路径。 */
  const filterTree = (nodes: DictItem[]): DictItem[] => {
    const kw = itemKeyword.trim().toLowerCase();
    const match = (n: DictItem) =>
      (!kw || [n.code, n.label, n.value].some((v) => (v ?? '').toLowerCase().includes(kw))) &&
      (!onlyEnabled || n.status === 1);
    return nodes
      .map((n) => ({ ...n, children: n.children ? filterTree(n.children) : undefined }))
      .filter((n) => match(n) || (n.children?.length ?? 0) > 0);
  };

  useEffect(() => {
    if (selectedType) {
      fetchHierarchies(selectedType.code);
    }
    fetchItems();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedType?.code, hierarchyCode, itemKeyword, onlyEnabled]);

  // ---------- 类型表单 ----------
  const openCreateType = () => {
    setEditingType(null);
    typeForm.resetFields();
    typeForm.setFieldsValue({ category: 'LIST', valueType: 'STRING', status: 1, sort: 0, maxDepth: 0 });
    setTypeModalOpen(true);
  };

  const openEditType = (record: DictType) => {
    setEditingType(record);
    typeForm.setFieldsValue({ ...record, status: record.status ?? 1 });
    setTypeModalOpen(true);
  };

  const submitType = async () => {
    const values = await typeForm.validateFields();
    try {
      if (editingType?.id) {
        await dictApi.updateType(editingType.id, values);
        message.success('字典类型已更新');
      } else {
        await dictApi.createType(values);
        message.success('字典类型已创建');
      }
      setTypeModalOpen(false);
      invalidateDictCache(values.code);
      fetchTypes();
    } catch {
      message.error('保存失败');
    }
  };

  const removeType = async (record: DictType) => {
    try {
      await dictApi.deleteType(record.id!);
      message.success('字典类型已删除');
      if (selectedType?.code === record.code) setSelectedType(null);
      invalidateDictCache(record.code);
      fetchTypes();
    } catch {
      message.error('删除失败（内置类型或仍有字典项时不可删除）');
    }
  };

  // ---------- 项表单 ----------
  const openCreateItem = (parentCode?: string) => {
    setEditingItem(null);
    setItemTexts([]);
    itemForm.resetFields();
    itemForm.setFieldsValue({
      typeCode: selectedType?.code,
      hierarchyCode,
      parentCode,
      tagType: 'default',
      status: 1,
      sort: 0,
      isDefault: false,
    });
    setItemModalOpen(true);
  };

  const openEditItem = async (record: DictItem) => {
    setEditingItem(record);
    itemForm.setFieldsValue({
      ...record,
      hierarchyCode: record.hierarchyCode ?? hierarchyCode,
      status: record.status ?? 1,
      isDefault: (record.isDefault ?? 0) === 1,
      effectiveFrom: record.effectiveFrom ? dayjs(record.effectiveFrom) : undefined,
      effectiveTo: record.effectiveTo ? dayjs(record.effectiveTo) : undefined,
    });
    try {
      const res = await dictApi.getTexts(record.typeCode, record.code);
      setItemTexts(
        (res.data ?? []).map((t) => ({ language: t.language, label: t.label, description: t.description })),
      );
    } catch {
      setItemTexts([]);
    }
    setItemModalOpen(true);
  };

  const submitItem = async () => {
    const values = await itemForm.validateFields();
    const payload = {
      ...values,
      isDefault: values.isDefault ? 1 : 0,
      effectiveFrom: toLocalIso(values.effectiveFrom),
      effectiveTo: toLocalIso(values.effectiveTo),
    };
    delete payload.texts;
    try {
      if (editingItem?.id) {
        await dictApi.updateItem(editingItem.id, payload);
        message.success('字典项已更新');
      } else {
        await dictApi.createItem(payload);
        message.success('字典项已创建');
      }
      const code = editingItem?.code ?? payload.code;
      const texts: DictItemText[] = (values.texts ?? []).filter(
        (t: DictItemText) => t?.language && t?.label,
      );
      if (code && texts.length) {
        await dictApi.saveTexts(payload.typeCode ?? selectedType?.code ?? '', code, texts);
      }
      setItemModalOpen(false);
      invalidateDictCache(selectedType?.code);
      fetchItems();
    } catch {
      message.error('保存失败（编码可能重复、值不符合值域格式或父项非法）');
    }
  };

  const removeItem = async (record: DictItem) => {
    try {
      await dictApi.deleteItem(record.id!);
      message.success('字典项已删除');
      invalidateDictCache(selectedType?.code);
      fetchItems();
    } catch {
      message.error('删除失败（任一层级视图中仍有子节点时不可删除）');
    }
  };

  const toggleItemStatus = async (record: DictItem, checked: boolean) => {
    await dictApi.updateItem(record.id!, { status: checked ? 1 : 0 });
    invalidateDictCache(selectedType?.code);
    fetchItems();
  };

  const markDefault = async (record: DictItem) => {
    await dictApi.markDefault(record.id!);
    message.success('已设为默认项');
    invalidateDictCache(selectedType?.code);
    fetchItems();
  };

  // ---------- 枚举同步 ----------
  const openEnumDrawer = async () => {
    if (!selectedType) return;
    try {
      const res = await dictApi.getEnumDiff(selectedType.code);
      setEnumDiff(res.data ?? null);
      setEnumDrawerOpen(true);
    } catch {
      message.error('读取枚举漂移失败');
    }
  };

  const runSync = async () => {
    if (!selectedType) return;
    const res = await dictApi.syncEnum(selectedType.code);
    message.success(`已同步 ${res.data ?? 0} 项`);
    setEnumDrawerOpen(false);
    invalidateDictCache(selectedType.code);
    fetchItems();
  };

  // ---------- 导入导出 ----------
  const exportType = async () => {
    if (!selectedType) return;
    const res = await dictApi.exportType(selectedType.code);
    const snapshot: DictExport = res.data ?? { items: [] };
    const blob = new Blob([JSON.stringify(snapshot, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `dict-${selectedType.code}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const importType = async (file: File) => {
    if (!selectedType) return false;
    try {
      const payload = JSON.parse(await file.text()) as DictExport;
      const res = await dictApi.importType(selectedType.code, payload);
      message.success(`已导入 ${res.data ?? 0} 项`);
      invalidateDictCache(selectedType.code);
      fetchItems();
      fetchHierarchies(selectedType.code);
    } catch {
      message.error('导入失败（文件格式应为导出的值域快照）');
    }
    return false;
  };

  // ---------- 表格定义 ----------
  const parentOptions = useMemo(() => {
    const flat: DictItem[] = [];
    const walk = (nodes: DictItem[]) =>
      nodes.forEach((n) => {
        flat.push(n);
        if (n.children) walk(n.children);
      });
    walk(items);
    return flat.map((n) => ({ value: n.code, title: n.label }));
  }, [items]);

  const typeColumns = [
    {
      title: '值域',
      dataIndex: 'code',
      render: (v: string, r: DictType) => (
        <Space size={4}>
          <span>{v}</span>
          {r.builtin === 1 && <Tag color="gold">内置</Tag>}
        </Space>
      ),
    },
    { title: '名称', dataIndex: 'name', ellipsis: true },
    {
      title: '分类',
      dataIndex: 'category',
      width: 90,
      render: (v: string) => <Tag color={CATEGORY_COLOR[v] ?? 'default'}>{v}</Tag>,
    },
  ];

  const itemColumns = [
    { title: '编码', dataIndex: 'code', width: 140 },
    {
      title: '标签',
      dataIndex: 'label',
      render: (v: string, r: DictItem) => (
        <Space size={4}>
          <span>{v}</span>
          {(r.isDefault ?? 0) === 1 && <Tag color="green">默认</Tag>}
          {r.tenantId ? <Tag color="blue">租户覆盖</Tag> : null}
        </Space>
      ),
    },
    { title: '值', dataIndex: 'value', width: 110 },
    { title: '外部码', dataIndex: 'externalCode', width: 110 },
    {
      title: '展示',
      dataIndex: 'tagType',
      width: 90,
      render: (v: string) => <Tag color={TAG_COLOR[v ?? 'default']}>{v ?? 'default'}</Tag>,
    },
    { title: '生效状态', key: 'effective', width: 110, render: (_: unknown, r: DictItem) => effectiveTag(r) },
    { title: '排序', dataIndex: 'sort', width: 70 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: number, r: DictItem) => (
        <Switch size="small" checked={v === 1} onChange={(c) => toggleItemStatus(r, c)} />
      ),
    },
    {
      title: '操作',
      width: 230,
      render: (_: unknown, r: DictItem) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => openEditItem(r)}>
            编辑
          </Button>
          {isCascade && (
            <Button type="link" size="small" onClick={() => openCreateItem(r.code)}>
              加子级
            </Button>
          )}
          {(r.isDefault ?? 0) !== 1 && (
            <Button type="link" size="small" onClick={() => markDefault(r)}>
              默认
            </Button>
          )}
          <Popconfirm title={`删除「${r.label}」？`} onConfirm={() => removeItem(r)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Row gutter={16}>
      <Col span={7}>
        <Card
          title="字典类型"
          extra={
            <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreateType}>
              新建
            </Button>
          }
        >
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            <Input
              placeholder="搜索编码 / 名称"
              prefix={<SearchOutlined />}
              allowClear
              value={typeKeyword}
              onChange={(e) => setTypeKeyword(e.target.value)}
              onPressEnter={fetchTypes}
            />
            <Select
              allowClear
              placeholder="按分类筛选"
              style={{ width: '100%' }}
              value={typeCategory}
              onChange={setTypeCategory}
              options={[
                { value: 'ENUM', label: 'ENUM 枚举绑定' },
                { value: 'LIST', label: 'LIST 扁平列表' },
                { value: 'CASCADE', label: 'CASCADE 可挂层级' },
              ]}
            />
            <Table
              rowKey="code"
              size="small"
              loading={typeLoading}
              columns={typeColumns}
              dataSource={types}
              pagination={false}
              scroll={{ y: 520 }}
              onRow={(r) => ({ onClick: () => setSelectedType(r) })}
            />
          </Space>
        </Card>
      </Col>

      <Col span={17}>
        <Card
          title={selectedType ? `${selectedType.name}（${selectedType.code}）` : '字典项'}
          extra={
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={() => fetchItems()} disabled={!selectedType}>
                刷新
              </Button>
              {isEnum && (
                <Button icon={<SyncOutlined />} onClick={openEnumDrawer} disabled={!selectedType}>
                  枚举同步
                </Button>
              )}
              <Button icon={<DownloadOutlined />} onClick={exportType} disabled={!selectedType}>
                导出
              </Button>
              <Upload beforeUpload={importType} showUploadList={false} accept=".json">
                <Button icon={<UploadOutlined />} disabled={!selectedType}>
                  导入
                </Button>
              </Upload>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => openCreateItem()}
                disabled={!selectedType}
              >
                新建项
              </Button>
            </Space>
          }
        >
          {!selectedType ? (
            <Empty description="请从左侧选择一个字典类型" />
          ) : (
            <>
              <Descriptions size="small" column={4} style={{ marginBottom: 12 }}>
                <Descriptions.Item label="分类">
                  <Tag color={CATEGORY_COLOR[selectedType.category ?? 'LIST']}>{selectedType.category}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="值类型">
                  {selectedType.valueType ?? 'STRING'}
                  {selectedType.valueRegex ? ` / ${selectedType.valueRegex}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label="编码分段">{selectedType.codeSegments || '-'}</Descriptions.Item>
                <Descriptions.Item label="操作">
                  <Space size={0}>
                    <Button type="link" size="small" onClick={() => openEditType(selectedType)}>
                      编辑类型
                    </Button>
                    <Popconfirm
                      title={`删除类型「${selectedType.name}」？`}
                      onConfirm={() => removeType(selectedType)}
                    >
                      <Button type="link" size="small" danger disabled={selectedType.builtin === 1}>
                        删除类型
                      </Button>
                    </Popconfirm>
                  </Space>
                </Descriptions.Item>
              </Descriptions>

              <Space style={{ marginBottom: 12 }} wrap>
                <Input
                  placeholder="搜索编码 / 标签 / 值"
                  prefix={<SearchOutlined />}
                  allowClear
                  style={{ width: 220 }}
                  value={itemKeyword}
                  onChange={(e) => setItemKeyword(e.target.value)}
                />
                {isCascade && (
                  <Select
                    style={{ width: 200 }}
                    value={hierarchyCode}
                    onChange={setHierarchyCode}
                    options={hierarchies.map((h) => ({ value: h, label: `层级视图：${h}` }))}
                  />
                )}
                <Space size={4}>
                  <Switch size="small" checked={onlyEnabled} onChange={setOnlyEnabled} />
                  <Typography.Text type="secondary">只看启用</Typography.Text>
                </Space>
              </Space>

              <Table
                rowKey="id"
                size="small"
                loading={itemLoading}
                columns={itemColumns}
                dataSource={items}
                pagination={false}
                scroll={{ x: 1080 }}
              />
            </>
          )}
        </Card>
      </Col>

      {/* 类型表单 */}
      <Modal
        title={editingType ? '编辑字典类型' : '新建字典类型'}
        open={typeModalOpen}
        onCancel={() => setTypeModalOpen(false)}
        onOk={submitType}
        width={560}
        destroyOnHidden
      >
        <Form form={typeForm} layout="vertical">
          <Form.Item name="code" label="值域编码" rules={[{ required: true, message: '请输入值域编码' }]}>
            <Input placeholder="如 order_status" disabled={!!editingType} />
          </Form.Item>
          <Form.Item name="name" label="值域名称" rules={[{ required: true, message: '请输入值域名称' }]}>
            <Input placeholder="如 订单状态" />
          </Form.Item>
          <Form.Item name="category" label="值域分类" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ENUM', label: 'ENUM —— 绑定 Java 枚举，可同步/校验' },
                { value: 'LIST', label: 'LIST —— 扁平列表' },
                { value: 'CASCADE', label: 'CASCADE —— 可挂多套层级视图' },
              ]}
            />
          </Form.Item>
          <Form.Item name="moduleCode" label="归属模块">
            <Input placeholder="如 system / masterdata" />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.category !== cur.category}>
            {({ getFieldValue }) =>
              getFieldValue('category') === 'ENUM' ? (
                <Form.Item
                  name="enumClass"
                  label="绑定枚举类"
                  rules={[{ required: true, message: 'ENUM 类值域必须绑定枚举类' }]}
                >
                  <Input placeholder="如 com.bone.blueprint.domain.model.order.OrderStatus" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.category !== cur.category}>
            {({ getFieldValue }) =>
              getFieldValue('category') === 'CASCADE' ? (
                <Form.Item name="maxDepth" label="层级上限（0=不限）">
                  <InputNumber min={0} max={10} style={{ width: '100%' }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Space size={16} align="start">
            <Form.Item name="valueType" label="值类型">
              <Select style={{ width: 160 }} options={VALUE_TYPE_OPTIONS} />
            </Form.Item>
            <Form.Item name="valueRegex" label="值格式正则">
              <Input style={{ width: 200 }} placeholder="如 ^\d{6}$" />
            </Form.Item>
          </Space>
          <Form.Item
            name="codeSegments"
            label="编码分段"
            tooltip="如 2,2,2（GB/T 2260 风格）：导入时按编码前缀自动推导父级"
          >
            <Input placeholder="如 2,2,2" />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space size={16}>
            <Form.Item name="sort" label="排序" initialValue={0}>
              <InputNumber />
            </Form.Item>
            <Form.Item
              name="status"
              label="启用"
              valuePropName="checked"
              getValueProps={(v) => ({ checked: v !== 0 })}
              normalize={(v) => (v ? 1 : 0)}
            >
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 项表单 */}
      <Modal
        title={editingItem ? '编辑字典项' : '新建字典项'}
        open={itemModalOpen}
        onCancel={() => setItemModalOpen(false)}
        onOk={submitItem}
        width={620}
        destroyOnHidden
      >
        <Form
          form={itemForm}
          layout="vertical"
          initialValues={{ tagType: 'default', status: 1, sort: 0, isDefault: false }}
        >
          <Form.Item name="typeCode" label="所属值域">
            <Input disabled />
          </Form.Item>
          {isCascade && (
            <Space size={16} align="start">
              <Form.Item name="hierarchyCode" label="层级视图">
                <Select
                  style={{ width: 160 }}
                  options={hierarchies.map((h) => ({ value: h, label: h }))}
                />
              </Form.Item>
              <Form.Item name="parentCode" label="父级项">
                <TreeSelect
                  allowClear
                  showSearch
                  treeDefaultExpandAll
                  style={{ width: 240 }}
                  placeholder="顶层留空"
                  treeNodeFilterProp="title"
                  treeData={parentOptions}
                />
              </Form.Item>
            </Space>
          )}
          <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }]}>
            <Input placeholder="如 ACTIVE（ENUM 类须与枚举常量名一致）" disabled={!!editingItem} />
          </Form.Item>
          <Form.Item name="label" label="标签" rules={[{ required: true, message: '请输入标签' }]}>
            <Input placeholder="如 已启用" />
          </Form.Item>
          <Form.Item
            name="value"
            label="值"
            tooltip={`受值域「${selectedType?.valueType ?? 'STRING'}${selectedType?.valueRegex ? ' / ' + selectedType.valueRegex : ''}」约束`}
          >
            <Input placeholder="如 1（ENUM 类由同步写入 ordinal）" />
          </Form.Item>
          <Form.Item name="externalCode" label="外部标准码" tooltip="如 GB/T 2260、ISO 4217；仅用于对接">
            <Input placeholder="如 440100" />
          </Form.Item>
          <Form.Item label="生效区间" tooltip="区间内才出现在下拉数据源；留空表示长期有效">
            <Space>
              <Form.Item name="effectiveFrom" noStyle>
                <DatePicker showTime placeholder="开始" />
              </Form.Item>
              <Form.Item name="effectiveTo" noStyle>
                <DatePicker showTime placeholder="结束" />
              </Form.Item>
            </Space>
          </Form.Item>
          <Form.Item name="tagType" label="展示语义">
            <Select options={TAG_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="i18nKey" label="国际化键" tooltip="前端静态语言包键；服务端译文见下方">
            <Input placeholder="如 dict.orderStatus.active" />
          </Form.Item>
          <Form.Item label="多语言译文（服务端下发，运营可改）">
            <Form.List name="texts" initialValue={itemTexts}>
              {(fields, { add, remove }) => (
                <Space direction="vertical" style={{ width: '100%' }}>
                  {fields.map((field) => (
                    <Space key={field.key} align="baseline">
                      <Form.Item {...field} name={[field.name, 'language']} rules={[{ required: true }]}>
                        <Select style={{ width: 130 }} options={LANGUAGE_OPTIONS} placeholder="语言" />
                      </Form.Item>
                      <Form.Item {...field} name={[field.name, 'label']} rules={[{ required: true }]}>
                        <Input style={{ width: 200 }} placeholder="该语言下的显示名" />
                      </Form.Item>
                      <Button type="link" danger onClick={() => remove(field.name)}>
                        移除
                      </Button>
                    </Space>
                  ))}
                  <Button type="dashed" block onClick={() => add({ language: 'en-US' })}>
                    + 增加译文
                  </Button>
                </Space>
              )}
            </Form.List>
          </Form.Item>
          <Form.Item name="description" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space size={16}>
            <Form.Item name="sort" label="排序" initialValue={0}>
              <InputNumber />
            </Form.Item>
            <Form.Item
              name="status"
              label="启用"
              valuePropName="checked"
              getValueProps={(v) => ({ checked: v !== 0 })}
              normalize={(v) => (v ? 1 : 0)}
            >
              <Switch />
            </Form.Item>
            <Form.Item name="isDefault" label="默认项" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 枚举漂移抽屉 */}
      <Drawer
        title={`枚举同步 —— ${selectedType?.code ?? ''}`}
        open={enumDrawerOpen}
        onClose={() => setEnumDrawerOpen(false)}
        width={560}
        extra={
          <Button type="primary" onClick={runSync}>
            同步到字典
          </Button>
        }
      >
        {!enumDiff ? (
          <Empty description="暂无漂移数据" />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Alert
              type={enumDiff.consistent ? 'success' : 'warning'}
              showIcon
              message={enumDiff.consistent ? '枚举与字典一致' : '枚举与字典存在漂移'}
              description={`绑定枚举类：${enumDiff.enumClass ?? '-'}`}
            />
            <div>
              <Typography.Text strong>枚举有、字典无（同步即可）</Typography.Text>
              <div style={{ marginTop: 6 }}>
                {enumDiff.missingInDict.length ? (
                  enumDiff.missingInDict.map((c) => (
                    <Tag key={c} color="blue">
                      {c}
                    </Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">无</Typography.Text>
                )}
              </div>
            </div>
            <div>
              <Typography.Text strong type="danger">
                字典有、枚举无（代码已删而字典未清）
              </Typography.Text>
              <div style={{ marginTop: 6 }}>
                {enumDiff.missingInEnum.length ? (
                  enumDiff.missingInEnum.map((c) => (
                    <Tag key={c} color="red">
                      {c}
                    </Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">无</Typography.Text>
                )}
              </div>
            </div>
            <div>
              <Typography.Text strong>value 与 ordinal 不一致</Typography.Text>
              <div style={{ marginTop: 6 }}>
                {enumDiff.valueDrift.length ? (
                  enumDiff.valueDrift.map((c) => (
                    <Tag key={c} color="orange">
                      {c}
                    </Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">无</Typography.Text>
                )}
              </div>
            </div>
            <Typography.Text type="secondary">
              同步是幂等的：只补齐缺失项、刷新 value，不会覆盖人工润色过的标签。
            </Typography.Text>
          </Space>
        )}
      </Drawer>
    </Row>
  );
};

export default DictManagement;
