import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ApartmentOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusSquareOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  App as AntApp,
  Breadcrumb,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Flex,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Spin,
  Tag,
  Tree,
  TreeSelect,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import type { Key } from 'react';
import {
  createDept,
  deleteDept,
  getDeptTree,
  updateDept,
  type DeptNode,
  type DeptReq,
} from '../services/api';
import ModulePage from '../components/ModulePage';
import { ProCard, StatisticCard } from '@ant-design/pro-components';

const statusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' },
];

const statusTag = (status?: number) => {
  if (status === 1) return <Tag color="success">启用</Tag>;
  if (status === 0) return <Tag color="default">禁用</Tag>;
  return <Tag>未知</Tag>;
};

/** 统计整棵树的派生指标 */
function summarize(list: DeptNode[]) {
  let total = 0;
  let enabled = 0;
  let roots = 0;
  let maxDepth = 0;
  const walk = (nodes: DeptNode[], depth: number) => {
    maxDepth = Math.max(maxDepth, depth);
    for (const n of nodes) {
      total += 1;
      if (n.status === 1) enabled += 1;
      if (depth === 1) roots += 1;
      if (n.children?.length) walk(n.children, depth + 1);
    }
  };
  walk(list, 1);
  return { total, enabled, roots, maxDepth };
}

/** 收集某节点下所有后代的 id（用于删除影响范围与 TreeSelect 禁用项） */
function collectDescendantIds(node: DeptNode | null): string[] {
  const ids: string[] = [];
  const walk = (n: DeptNode) => {
    ids.push(n.id);
    n.children?.forEach(walk);
  };
  node?.children?.forEach(walk);
  return ids;
}

/** 递归收集全部节点 id（展开全部用） */
function collectAllKeys(list: DeptNode[]): Key[] {
  const keys: Key[] = [];
  const walk = (nodes: DeptNode[]) => {
    nodes.forEach((n) => {
      keys.push(n.id);
      if (n.children?.length) walk(n.children);
    });
  };
  walk(list);
  return keys;
}

/** 计算从根到目标节点的路径 */
function findPath(list: DeptNode[], id: string, trail: DeptNode[] = []): DeptNode[] | null {
  for (const n of list) {
    const next = [...trail, n];
    if (n.id === id) return next;
    if (n.children) {
      const r = findPath(n.children, id, next);
      if (r) return r;
    }
  }
  return null;
}

const OrganizationManagement: React.FC = () => {
  const { message: msg, modal } = AntApp.useApp();
  const [treeData, setTreeData] = useState<DeptNode[]>([]);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<DeptNode | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<DeptReq>();

  const load = useCallback(
    async (kw?: string) => {
      setLoading(true);
      try {
        const res = await getDeptTree(kw || undefined);
        const data = res.data ?? [];
        setTreeData(data);
        // 默认展开到第二层，方便直接看到结构
        const expand: Key[] = [];
        const walk = (nodes: DeptNode[], depth: number) => {
          if (depth <= 2) nodes.forEach((n) => (expand.push(n.id), n.children && walk(n.children, depth + 1)));
        };
        walk(data, 1);
        setExpandedKeys(expand);
      } catch {
        msg.error('加载组织机构失败');
      } finally {
        setLoading(false);
      }
    },
    [msg],
  );

  useEffect(() => {
    void load();
  }, []);

  // 搜索防抖
  useEffect(() => {
    const t = setTimeout(() => void load(keyword), 300);
    return () => clearTimeout(t);
  }, [keyword, load]);

  // 真正含 children 的树
  const renderTree = useMemo<DataNode[]>(() => {
    const map = (list: DeptNode[]): DataNode[] =>
      list.map((d) => ({
        key: d.id,
        title: (
          <Flex justify="space-between" align="center" style={{ paddingInlineEnd: 8 }}>
            <Space size={6}>
              <ApartmentOutlined style={{ color: '#1677ff' }} />
              <span>{d.name}</span>
              {statusTag(d.status)}
            </Space>
            <Space size={0} className="org-node-actions">
              <Button
                type="link"
                size="small"
                icon={<PlusSquareOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  openCreate(d.id);
                }}
                title="添加子部门"
              />
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  openEdit(d);
                }}
                title="编辑"
              />
              <Button
                type="link"
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  remove(d);
                }}
                title="删除"
              />
            </Space>
          </Flex>
        ),
        children: d.children && d.children.length ? map(d.children) : undefined,
      }));
    return map(treeData);
  }, [treeData]);

  const selectedNode = useMemo(
    () => (selectedId == null ? null : findPath(treeData, selectedId)?.pop() ?? null),
    [treeData, selectedId],
  );
  const selectedPath = useMemo(
    () => (selectedId == null ? [] : findPath(treeData, selectedId) ?? []),
    [treeData, selectedId],
  );

  const stats = useMemo(() => summarize(treeData), [treeData]);

  const openCreate = (pid: string | null) => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ parentId: pid && pid !== '0' ? pid : '0', status: 1, orderNo: 1 });
    setDrawerOpen(true);
  };

  const openEdit = (node: DeptNode) => {
    setEditing(node);
    form.setFieldsValue({
      name: node.name,
      parentId: node.parentId && node.parentId !== '0' ? node.parentId : '0',
      orderNo: node.orderNo ?? 1,
      status: node.status ?? 1,
    });
    setDrawerOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const parentId = values.parentId && values.parentId !== '0' ? values.parentId : null;
      const payload = { ...values, parentId };
      if (editing) {
        await updateDept(editing.id, payload);
        msg.success('部门已更新');
      } else {
        await createDept(payload);
        msg.success('部门已创建');
      }
      setDrawerOpen(false);
      await load();
    } catch {
      msg.error('保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  const remove = (node: DeptNode) => {
    const childCount = collectDescendantIds(node).length;
    // 后端仅删除单节点不级联：有子节点时前端拦截，避免产生孤儿数据
    if (childCount > 0) {
      modal.warning({
        title: `无法删除「${node.name}」`,
        content: `该部门下还有 ${childCount} 个子部门，请先删除子部门后再操作。`,
        okText: '知道了',
      });
      return;
    }
    modal.confirm({
      title: `确认删除「${node.name}」？`,
      content: '删除后将不可恢复。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await deleteDept(node.id);
        msg.success('部门已删除');
        if (selectedId === node.id) setSelectedId(null);
        await load();
      },
    });
  };

  // TreeSelect 选项：编辑时禁用自身及其后代
  const parentTreeData = useMemo<DataNode[]>(() => {
    const disabledIds = editing ? new Set([editing.id, ...collectDescendantIds(editing)]) : new Set<string>();
    const map = (list: DeptNode[]): DataNode[] =>
      list.map(
        (d) =>
          ({
            key: d.id,
            value: d.id,
            title: d.name,
            disabled: disabledIds.has(d.id),
            children: d.children && d.children.length ? map(d.children) : undefined,
          }) as DataNode,
      );
    return [{ key: '0', value: '0', title: '根部门（顶级）' } as DataNode, ...map(treeData)];
  }, [treeData, editing]);

  const toolbar = (
    <Space>
      <Input
        allowClear
        prefix={<SearchOutlined />}
        placeholder="搜索部门名称"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        style={{ width: 220 }}
      />
      <Button icon={<ReloadOutlined />} onClick={() => load(keyword)} title="刷新" />
      <Button
        onClick={() => setExpandedKeys(collectAllKeys(treeData))}
        title="展开全部"
      >
        展开
      </Button>
      <Button onClick={() => setExpandedKeys([])} title="折叠全部">
        折叠
      </Button>
      <Button type="primary" icon={<PlusSquareOutlined />} onClick={() => openCreate(null)}>
        新建根部门
      </Button>
    </Space>
  );

  const statistics = (
    <StatisticCard.Group direction="row" style={{ width: '100%' }}>
      <StatisticCard
        statistic={{ title: '部门总数', value: stats.total, prefix: <ApartmentOutlined /> }}
      />
      <StatisticCard
        statistic={{ title: '启用中', value: stats.enabled, valueStyle: { color: '#52c41a' } }}
      />
      <StatisticCard statistic={{ title: '根部门', value: stats.roots }} />
      <StatisticCard statistic={{ title: '最大层级', value: stats.maxDepth, suffix: '级' }} />
    </StatisticCard.Group>
  );

  return (
    <ModulePage title="组织机构管理" description="以树形结构维护企业部门层级，支持搜索、层级浏览与影响范围确认删除。" extra={toolbar} statistics={statistics}>
      <Spin spinning={loading}>
        <Flex gap={16} align="flex-start" style={{ padding: 16 }}>
        <ProCard
          title="部门结构"
          style={{ flex: '0 0 420px', minWidth: 360 }}
          bordered
          bodyStyle={{ padding: 0, maxHeight: 'calc(100vh - 320px)', overflow: 'auto' }}
        >
          <Tree<DataNode>
            treeData={renderTree}
            expandedKeys={expandedKeys}
            onExpand={(keys) => setExpandedKeys(keys)}
            selectedKeys={selectedId == null ? [] : [selectedId]}
            onSelect={(keys) => setSelectedId(keys.length ? String(keys[0]) : null)}
            blockNode
            style={{ padding: 12 }}
          />
        </ProCard>

        <ProCard
          title={selectedNode ? selectedNode.name : '部门详情'}
          style={{ flex: 1, minWidth: 0 }}
          bordered
          headerBordered
          bodyStyle={{ padding: 16 }}
          extra={
            selectedNode && (
              <Space>
                <Button size="small" icon={<PlusSquareOutlined />} onClick={() => openCreate(selectedNode.id)}>
                  子部门
                </Button>
                <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(selectedNode)}>
                  编辑
                </Button>
                <Button size="small" danger icon={<DeleteOutlined />} onClick={() => remove(selectedNode)}>
                  删除
                </Button>
              </Space>
            )
          }
        >
          {selectedNode ? (
            <>
              <Breadcrumb
                items={selectedPath.map((n) => ({ title: n.name }))}
                style={{ marginBlockEnd: 16 }}
              />
              <Descriptions column={2} size="small" bordered>
                <Descriptions.Item label="部门 ID">{selectedNode.id}</Descriptions.Item>
                <Descriptions.Item label="状态">{statusTag(selectedNode.status)}</Descriptions.Item>
                <Descriptions.Item label="上级部门">
                  {selectedNode.parentId ? findPath(treeData, selectedNode.parentId)?.pop()?.name ?? selectedNode.parentId : '根部门'}
                </Descriptions.Item>
                <Descriptions.Item label="排序号">{selectedNode.orderNo ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="子部门数">{selectedNode.children?.length ?? 0}</Descriptions.Item>
                <Descriptions.Item label="层级">第 {(selectedPath.findIndex((n) => n.id === selectedNode.id) + 1)} 级</Descriptions.Item>
              </Descriptions>
            </>
          ) : (
            <Empty description="从左侧选择一个部门查看详情，或点击「新建根部门」" style={{ padding: '48px 0' }} />
          )}
        </ProCard>
      </Flex>
      </Spin>

      <Drawer
        title={editing ? `编辑部门 · ${editing.name}` : '新建部门'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={440}
        destroyOnClose
        footer={
          <Flex justify="end" gap={8}>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={submit}>
              保存
            </Button>
          </Flex>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="部门名称" rules={[{ required: true, message: '请输入部门名称' }]}>
            <Input placeholder="如：研发中心" />
          </Form.Item>
          <Form.Item name="parentId" label="上级部门" tooltip="选择所属上级；选「根部门」即为顶级部门">
            <TreeSelect
              treeData={parentTreeData}
              treeDefaultExpandAll
              placeholder="请选择上级部门"
              allowClear
              onChange={(v) => form.setFieldsValue({ parentId: v ?? undefined })}
            />
          </Form.Item>
          <Form.Item name="orderNo" label="排序号" tooltip="同层级下的展示顺序，数字越小越靠前">
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <Select options={statusOptions} />
          </Form.Item>
        </Form>
      </Drawer>
    </ModulePage>
  );
};

export default OrganizationManagement;
