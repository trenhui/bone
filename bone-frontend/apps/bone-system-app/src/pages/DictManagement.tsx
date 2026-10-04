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
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';

const CATEGORY_COLOR: Record<string, string> = {
  ENUM: 'purple',
  LIST: 'blue',
  CASCADE: 'cyan',
};

const TAG_COLOR: Record<string, string> = {
  default: 'default',
  info: 'blue',
  success: 'green',
  warning: 'orange',
  error: 'red',
};

/** 生效状态标记：Oracle 时间有效性口径——历史数据按旧口径展示、新单据用新口径。 */
const effectiveTag = (item: DictItem, t: TFunction) => {
  const now = dayjs();
  const from = item.effectiveFrom ? dayjs(item.effectiveFrom) : null;
  const to = item.effectiveTo ? dayjs(item.effectiveTo) : null;
  if (from && now.isBefore(from)) return <Tag color="blue">{t('system.dictManagement.effectivePending')}</Tag>;
  if (to && now.isAfter(to)) return <Tag color="red">{t('system.dictManagement.effectiveExpired')}</Tag>;
  if (!from && !to) return <Typography.Text type="secondary">{t('system.dictManagement.effectiveLongTerm')}</Typography.Text>;
  return <Tag color="green">{t('system.dictManagement.effectiveActive')}</Tag>;
};

const toLocalIso = (v?: unknown) =>
  v && (v as { format?: (s: string) => string }).format
    ? (v as { format: (s: string) => string }).format('YYYY-MM-DDTHH:mm:ss')
    : undefined;

const DictManagement: React.FC = () => {
  const { t } = useTranslation();

  // 标签类型 / 值类型 / 语言 选项（含用户可见文案，置于 useTranslation 作用域内）
  const tagTypeOptions = [
    { value: 'default', label: t('system.dictManagement.default') },
    { value: 'info', label: t('system.dictManagement.tagTypeInfo') },
    { value: 'success', label: t('system.dictManagement.tagTypeSuccess') },
    { value: 'warning', label: t('system.dictManagement.tagTypeWarning') },
    { value: 'error', label: t('system.dictManagement.tagTypeDanger') },
  ];

  const valueTypeOptions = [
    { value: 'STRING', label: t('system.dictManagement.valueTypeString') },
    { value: 'INT', label: t('system.dictManagement.valueTypeInt') },
    { value: 'DECIMAL', label: t('system.dictManagement.valueTypeDecimal') },
    { value: 'BOOLEAN', label: t('system.dictManagement.valueTypeBoolean') },
  ];

  const languageOptions = [
    { value: 'zh-CN', label: t('system.dictManagement.langZhCN') },
    { value: 'en-US', label: t('system.dictManagement.langEnUS') },
    { value: 'zh-TW', label: t('system.dictManagement.langZhTW') },
  ];

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
      const list = res.data?.records ?? [];
      setTypes(list);
      setSelectedType((cur) => (cur ? list.find((tt) => tt.code === cur.code) ?? cur : list[0] ?? null));
    } catch {
      message.error(t('system.dictManagement.fetchTypesFailed'));
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
        raw = res.data?.records ?? [];
      }
      setItems(filterTree(raw));
    } catch {
      message.error(t('system.dictManagement.fetchItemsFailed'));
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
        message.success(t('system.dictManagement.typeUpdated'));
      } else {
        await dictApi.createType(values);
        message.success(t('system.dictManagement.typeCreated'));
      }
      setTypeModalOpen(false);
      invalidateDictCache(values.code);
      fetchTypes();
    } catch {
      message.error(t('system.dictManagement.saveFailed'));
    }
  };

  const removeType = async (record: DictType) => {
    try {
      await dictApi.deleteType(record.id!);
      message.success(t('system.dictManagement.typeDeleted'));
      if (selectedType?.code === record.code) setSelectedType(null);
      invalidateDictCache(record.code);
      fetchTypes();
    } catch {
      message.error(t('system.dictManagement.deleteTypeFailed'));
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
        (res.data ?? []).map((tx) => ({ language: tx.language, label: tx.label, description: tx.description })),
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
        message.success(t('system.dictManagement.itemUpdated'));
      } else {
        await dictApi.createItem(payload);
        message.success(t('system.dictManagement.itemCreated'));
      }
      const code = editingItem?.code ?? payload.code;
      const texts: DictItemText[] = (values.texts ?? []).filter(
        (tx: DictItemText) => tx?.language && tx?.label,
      );
      if (code && texts.length) {
        await dictApi.saveTexts(payload.typeCode ?? selectedType?.code ?? '', code, texts);
      }
      setItemModalOpen(false);
      invalidateDictCache(selectedType?.code);
      fetchItems();
    } catch {
      message.error(t('system.dictManagement.saveItemFailed'));
    }
  };

  const removeItem = async (record: DictItem) => {
    try {
      await dictApi.deleteItem(record.id!);
      message.success(t('system.dictManagement.itemDeleted'));
      invalidateDictCache(selectedType?.code);
      fetchItems();
    } catch {
      message.error(t('system.dictManagement.deleteItemFailed'));
    }
  };

  const toggleItemStatus = async (record: DictItem, checked: boolean) => {
    await dictApi.updateItem(record.id!, { status: checked ? 1 : 0 });
    invalidateDictCache(selectedType?.code);
    fetchItems();
  };

  const markDefault = async (record: DictItem) => {
    await dictApi.markDefault(record.id!);
    message.success(t('system.dictManagement.markedDefault'));
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
      message.error(t('system.dictManagement.enumDriftReadFailed'));
    }
  };

  const runSync = async () => {
    if (!selectedType) return;
    const res = await dictApi.syncEnum(selectedType.code);
    message.success(t('system.dictManagement.syncedCount', { count: res.data ?? 0 }));
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
      message.success(t('system.dictManagement.importedCount', { count: res.data ?? 0 }));
      invalidateDictCache(selectedType.code);
      fetchItems();
      fetchHierarchies(selectedType.code);
    } catch {
      message.error(t('system.dictManagement.importFailed'));
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
      title: t('system.dictManagement.colScope'),
      dataIndex: 'code',
      render: (v: string, r: DictType) => (
        <Space size={4}>
          <span>{v}</span>
          {r.builtin === 1 && <Tag color="gold">{t('system.dictManagement.builtinTag')}</Tag>}
        </Space>
      ),
    },
    { title: t('system.dictManagement.colName'), dataIndex: 'name', ellipsis: true },
    {
      title: t('system.dictManagement.colCategory'),
      dataIndex: 'category',
      width: 90,
      render: (v: string) => <Tag color={CATEGORY_COLOR[v] ?? 'default'}>{v}</Tag>,
    },
  ];

  const itemColumns = [
    { title: t('system.dictManagement.colCode'), dataIndex: 'code', width: 140 },
    {
      title: t('system.dictManagement.colLabel'),
      dataIndex: 'label',
      render: (v: string, r: DictItem) => (
        <Space size={4}>
          <span>{v}</span>
          {(r.isDefault ?? 0) === 1 && <Tag color="green">{t('system.dictManagement.default')}</Tag>}
          {r.tenantId ? <Tag color="blue">{t('system.dictManagement.colTenantOverride')}</Tag> : null}
        </Space>
      ),
    },
    { title: t('system.dictManagement.colValue'), dataIndex: 'value', width: 110 },
    { title: t('system.dictManagement.colExternalCode'), dataIndex: 'externalCode', width: 110 },
    {
      title: t('system.dictManagement.colDisplay'),
      dataIndex: 'tagType',
      width: 90,
      render: (v: string) => <Tag color={TAG_COLOR[v ?? 'default']}>{v ?? 'default'}</Tag>,
    },
    {
      title: t('system.dictManagement.colEffectiveStatus'),
      key: 'effective',
      width: 110,
      render: (_: unknown, r: DictItem) => effectiveTag(r, t),
    },
    { title: t('system.dictManagement.colSort'), dataIndex: 'sort', width: 70 },
    {
      title: t('system.dictManagement.colStatus'),
      dataIndex: 'status',
      width: 90,
      render: (v: number, r: DictItem) => (
        <Switch size="small" checked={v === 1} onChange={(c) => toggleItemStatus(r, c)} />
      ),
    },
    {
      title: t('system.dictManagement.colAction'),
      width: 230,
      render: (_: unknown, r: DictItem) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => openEditItem(r)}>
            {t('system.dictManagement.edit')}
          </Button>
          {isCascade && (
            <Button type="link" size="small" onClick={() => openCreateItem(r.code)}>
              {t('system.dictManagement.addChild')}
            </Button>
          )}
          {(r.isDefault ?? 0) !== 1 && (
            <Button type="link" size="small" onClick={() => markDefault(r)}>
              {t('system.dictManagement.default')}
            </Button>
          )}
          <Popconfirm
            title={t('system.dictManagement.deleteItemConfirm', { label: r.label })}
            onConfirm={() => removeItem(r)}
          >
            <Button type="link" size="small" danger>
              {t('system.dictManagement.delete')}
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
          title={t('system.dictManagement.dictTypeTitle')}
          extra={
            <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreateType}>
              {t('system.dictManagement.create')}
            </Button>
          }
        >
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            <Input
              placeholder={t('system.dictManagement.searchTypePlaceholder')}
              prefix={<SearchOutlined />}
              allowClear
              value={typeKeyword}
              onChange={(e) => setTypeKeyword(e.target.value)}
              onPressEnter={fetchTypes}
            />
            <Select
              allowClear
              placeholder={t('system.dictManagement.filterByCategory')}
              style={{ width: '100%' }}
              value={typeCategory}
              onChange={setTypeCategory}
              options={[
                { value: 'ENUM', label: t('system.dictManagement.catEnum') },
                { value: 'LIST', label: t('system.dictManagement.catList') },
                { value: 'CASCADE', label: t('system.dictManagement.catCascade') },
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
          title={selectedType ? `${selectedType.name}（${selectedType.code}）` : t('system.dictManagement.dictItemTitle')}
          extra={
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={() => fetchItems()} disabled={!selectedType}>
                {t('system.dictManagement.refresh')}
              </Button>
              {isEnum && (
                <Button icon={<SyncOutlined />} onClick={openEnumDrawer} disabled={!selectedType}>
                  {t('system.dictManagement.enumSync')}
                </Button>
              )}
              <Button icon={<DownloadOutlined />} onClick={exportType} disabled={!selectedType}>
                {t('system.dictManagement.export')}
              </Button>
              <Upload beforeUpload={importType} showUploadList={false} accept=".json">
                <Button icon={<UploadOutlined />} disabled={!selectedType}>
                  {t('system.dictManagement.import')}
                </Button>
              </Upload>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => openCreateItem()}
                disabled={!selectedType}
              >
                {t('system.dictManagement.createItem')}
              </Button>
            </Space>
          }
        >
          {!selectedType ? (
            <Empty description={t('system.dictManagement.selectTypeHint')} />
          ) : (
            <>
              <Descriptions size="small" column={4} style={{ marginBottom: 12 }}>
                <Descriptions.Item label={t('system.dictManagement.descCategory')}>
                  <Tag color={CATEGORY_COLOR[selectedType.category ?? 'LIST']}>{selectedType.category}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t('system.dictManagement.descValueType')}>
                  {selectedType.valueType ?? 'STRING'}
                  {selectedType.valueRegex ? ` / ${selectedType.valueRegex}` : ''}
                </Descriptions.Item>
                <Descriptions.Item label={t('system.dictManagement.descCodeSegments')}>
                  {selectedType.codeSegments || '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('system.dictManagement.descOperation')}>
                  <Space size={0}>
                    <Button type="link" size="small" onClick={() => openEditType(selectedType)}>
                      {t('system.dictManagement.editType')}
                    </Button>
                    <Popconfirm
                      title={t('system.dictManagement.deleteTypeConfirm', { name: selectedType.name })}
                      onConfirm={() => removeType(selectedType)}
                    >
                      <Button type="link" size="small" danger disabled={selectedType.builtin === 1}>
                        {t('system.dictManagement.deleteType')}
                      </Button>
                    </Popconfirm>
                  </Space>
                </Descriptions.Item>
              </Descriptions>

              <Space style={{ marginBottom: 12 }} wrap>
                <Input
                  placeholder={t('system.dictManagement.searchItemPlaceholder')}
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
                    options={hierarchies.map((h) => ({
                      value: h,
                      label: t('system.dictManagement.hierarchyView', { h }),
                    }))}
                  />
                )}
                <Space size={4}>
                  <Switch size="small" checked={onlyEnabled} onChange={setOnlyEnabled} />
                  <Typography.Text type="secondary">{t('system.dictManagement.onlyEnabled')}</Typography.Text>
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
        title={editingType ? t('system.dictManagement.editDictType') : t('system.dictManagement.createDictType')}
        open={typeModalOpen}
        onCancel={() => setTypeModalOpen(false)}
        onOk={submitType}
        width={560}
        destroyOnHidden
      >
        <Form form={typeForm} layout="vertical">
          <Form.Item
            name="code"
            label={t('system.dictManagement.fieldScopeCode')}
            rules={[{ required: true, message: t('system.dictManagement.scopeCodeRequired') }]}
          >
            <Input placeholder={t('system.dictManagement.scopeCodePlaceholder')} disabled={!!editingType} />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('system.dictManagement.fieldScopeName')}
            rules={[{ required: true, message: t('system.dictManagement.scopeNameRequired') }]}
          >
            <Input placeholder={t('system.dictManagement.scopeNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="category" label={t('system.dictManagement.fieldScopeCategory')} rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'ENUM', label: t('system.dictManagement.catEnumDesc') },
                { value: 'LIST', label: t('system.dictManagement.catListDesc') },
                { value: 'CASCADE', label: t('system.dictManagement.catCascadeDesc') },
              ]}
            />
          </Form.Item>
          <Form.Item name="moduleCode" label={t('system.dictManagement.fieldModule')}>
            <Input placeholder={t('system.dictManagement.modulePlaceholder')} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.category !== cur.category}>
            {({ getFieldValue }) =>
              getFieldValue('category') === 'ENUM' ? (
                <Form.Item
                  name="enumClass"
                  label={t('system.dictManagement.fieldEnumClass')}
                  rules={[{ required: true, message: t('system.dictManagement.enumClassRequired') }]}
                >
                  <Input placeholder={t('system.dictManagement.enumClassPlaceholder')} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.category !== cur.category}>
            {({ getFieldValue }) =>
              getFieldValue('category') === 'CASCADE' ? (
                <Form.Item name="maxDepth" label={t('system.dictManagement.fieldMaxDepth')}>
                  <InputNumber min={0} max={10} style={{ width: '100%' }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Space size={16} align="start">
            <Form.Item name="valueType" label={t('system.dictManagement.fieldValueType')}>
              <Select style={{ width: 160 }} options={valueTypeOptions} />
            </Form.Item>
            <Form.Item name="valueRegex" label={t('system.dictManagement.fieldValueRegex')}>
              <Input style={{ width: 200 }} placeholder={t('system.dictManagement.valueRegexPlaceholder')} />
            </Form.Item>
          </Space>
          <Form.Item
            name="codeSegments"
            label={t('system.dictManagement.fieldCodeSegments')}
            tooltip={t('system.dictManagement.codeSegmentsTooltip')}
          >
            <Input placeholder={t('system.dictManagement.codeSegmentsPlaceholder')} />
          </Form.Item>
          <Form.Item name="description" label={t('system.dictManagement.fieldDescription')}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space size={16}>
            <Form.Item name="sort" label={t('system.dictManagement.fieldSort')} initialValue={0}>
              <InputNumber />
            </Form.Item>
            <Form.Item
              name="status"
              label={t('system.dictManagement.fieldEnabled')}
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
        title={editingItem ? t('system.dictManagement.editDictItem') : t('system.dictManagement.createDictItem')}
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
          <Form.Item name="typeCode" label={t('system.dictManagement.fieldOwnerScope')}>
            <Input disabled />
          </Form.Item>
          {isCascade && (
            <Space size={16} align="start">
              <Form.Item name="hierarchyCode" label={t('system.dictManagement.fieldHierarchyView')}>
                <Select
                  style={{ width: 160 }}
                  options={hierarchies.map((h) => ({ value: h, label: h }))}
                />
              </Form.Item>
              <Form.Item name="parentCode" label={t('system.dictManagement.fieldParentItem')}>
                <TreeSelect
                  allowClear
                  showSearch
                  treeDefaultExpandAll
                  style={{ width: 240 }}
                  placeholder={t('system.dictManagement.parentPlaceholder')}
                  treeNodeFilterProp="title"
                  treeData={parentOptions}
                />
              </Form.Item>
            </Space>
          )}
          <Form.Item
            name="code"
            label={t('system.dictManagement.fieldCode')}
            rules={[{ required: true, message: t('system.dictManagement.codeRequired') }]}
          >
            <Input placeholder={t('system.dictManagement.codePlaceholder')} disabled={!!editingItem} />
          </Form.Item>
          <Form.Item
            name="label"
            label={t('system.dictManagement.fieldLabel')}
            rules={[{ required: true, message: t('system.dictManagement.labelRequired') }]}
          >
            <Input placeholder={t('system.dictManagement.labelPlaceholder')} />
          </Form.Item>
          <Form.Item
            name="value"
            label={t('system.dictManagement.fieldValue')}
            tooltip={t('system.dictManagement.valueTooltip', {
              constraint: `${selectedType?.valueType ?? 'STRING'}${
                selectedType?.valueRegex ? ' / ' + selectedType.valueRegex : ''
              }`,
            })}
          >
            <Input placeholder={t('system.dictManagement.valuePlaceholder')} />
          </Form.Item>
          <Form.Item
            name="externalCode"
            label={t('system.dictManagement.fieldExternalCode')}
            tooltip={t('system.dictManagement.externalCodeTooltip')}
          >
            <Input placeholder={t('system.dictManagement.externalCodePlaceholder')} />
          </Form.Item>
          <Form.Item
            label={t('system.dictManagement.fieldEffectiveRange')}
            tooltip={t('system.dictManagement.effectiveRangeTooltip')}
          >
            <Space>
              <Form.Item name="effectiveFrom" noStyle>
                <DatePicker showTime placeholder={t('system.dictManagement.effectiveFromPlaceholder')} />
              </Form.Item>
              <Form.Item name="effectiveTo" noStyle>
                <DatePicker showTime placeholder={t('system.dictManagement.effectiveToPlaceholder')} />
              </Form.Item>
            </Space>
          </Form.Item>
          <Form.Item name="tagType" label={t('system.dictManagement.fieldTagType')}>
            <Select options={tagTypeOptions} />
          </Form.Item>
          <Form.Item
            name="i18nKey"
            label={t('system.dictManagement.fieldI18nKey')}
            tooltip={t('system.dictManagement.i18nKeyTooltip')}
          >
            <Input placeholder={t('system.dictManagement.i18nKeyPlaceholder')} />
          </Form.Item>
          <Form.Item label={t('system.dictManagement.fieldTranslations')}>
            <Form.List name="texts" initialValue={itemTexts}>
              {(fields, { add, remove }) => (
                <Space direction="vertical" style={{ width: '100%' }}>
                  {fields.map((field) => (
                    <Space key={field.key} align="baseline">
                      <Form.Item {...field} name={[field.name, 'language']} rules={[{ required: true }]}>
                        <Select
                          style={{ width: 130 }}
                          options={languageOptions}
                          placeholder={t('system.dictManagement.langPlaceholder')}
                        />
                      </Form.Item>
                      <Form.Item {...field} name={[field.name, 'label']} rules={[{ required: true }]}>
                        <Input
                          style={{ width: 200 }}
                          placeholder={t('system.dictManagement.translationLabelPlaceholder')}
                        />
                      </Form.Item>
                      <Button type="link" danger onClick={() => remove(field.name)}>
                        {t('system.dictManagement.remove')}
                      </Button>
                    </Space>
                  ))}
                  <Button type="dashed" block onClick={() => add({ language: 'en-US' })}>
                    {t('system.dictManagement.addTranslation')}
                  </Button>
                </Space>
              )}
            </Form.List>
          </Form.Item>
          <Form.Item name="description" label={t('system.dictManagement.fieldRemark')}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Space size={16}>
            <Form.Item name="sort" label={t('system.dictManagement.fieldSort')} initialValue={0}>
              <InputNumber />
            </Form.Item>
            <Form.Item
              name="status"
              label={t('system.dictManagement.fieldEnabled')}
              valuePropName="checked"
              getValueProps={(v) => ({ checked: v !== 0 })}
              normalize={(v) => (v ? 1 : 0)}
            >
              <Switch />
            </Form.Item>
            <Form.Item name="isDefault" label={t('system.dictManagement.default')} valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* 枚举漂移抽屉 */}
      <Drawer
        title={t('system.dictManagement.enumSyncTitle', { code: selectedType?.code ?? '' })}
        open={enumDrawerOpen}
        onClose={() => setEnumDrawerOpen(false)}
        width={560}
        extra={
          <Button type="primary" onClick={runSync}>
            {t('system.dictManagement.syncToDict')}
          </Button>
        }
      >
        {!enumDiff ? (
          <Empty description={t('system.dictManagement.noDriftData')} />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Alert
              type={enumDiff.consistent ? 'success' : 'warning'}
              showIcon
              message={
                enumDiff.consistent
                  ? t('system.dictManagement.enumConsistent')
                  : t('system.dictManagement.enumDrift')
              }
              description={t('system.dictManagement.enumClassDesc', { enumClass: enumDiff.enumClass ?? '-' })}
            />
            <div>
              <Typography.Text strong>{t('system.dictManagement.missingInDictTitle')}</Typography.Text>
              <div style={{ marginTop: 6 }}>
                {enumDiff.missingInDict.length ? (
                  enumDiff.missingInDict.map((c) => (
                    <Tag key={c} color="blue">
                      {c}
                    </Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">{t('system.dictManagement.none')}</Typography.Text>
                )}
              </div>
            </div>
            <div>
              <Typography.Text strong type="danger">
                {t('system.dictManagement.missingInEnumTitle')}
              </Typography.Text>
              <div style={{ marginTop: 6 }}>
                {enumDiff.missingInEnum.length ? (
                  enumDiff.missingInEnum.map((c) => (
                    <Tag key={c} color="red">
                      {c}
                    </Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">{t('system.dictManagement.none')}</Typography.Text>
                )}
              </div>
            </div>
            <div>
              <Typography.Text strong>{t('system.dictManagement.valueDriftTitle')}</Typography.Text>
              <div style={{ marginTop: 6 }}>
                {enumDiff.valueDrift.length ? (
                  enumDiff.valueDrift.map((c) => (
                    <Tag key={c} color="orange">
                      {c}
                    </Tag>
                  ))
                ) : (
                  <Typography.Text type="secondary">{t('system.dictManagement.none')}</Typography.Text>
                )}
              </div>
            </div>
            <Typography.Text type="secondary">
              {t('system.dictManagement.syncIdempotent')}
            </Typography.Text>
          </Space>
        )}
      </Drawer>
    </Row>
  );
};

export default DictManagement;
