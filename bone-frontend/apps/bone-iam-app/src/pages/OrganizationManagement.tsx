import React, { useEffect, useMemo, useState } from 'react';
import { Button, Drawer, Form, Input, InputNumber, message, Modal, Space, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  createDept,
  deleteDept,
  getDeptTree,
  updateDept,
  type DeptNode,
  type DeptReq,
} from '../services/api';

const OrganizationManagement: React.FC = () => {
  const [treeData, setTreeData] = useState<DeptNode[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<DeptNode | null>(null);
  const [parentId, setParentId] = useState<number | null>(null);
  const [form] = Form.useForm<DeptReq>();

  const load = async () => {
    const res = await getDeptTree();
    setTreeData(res.data ?? []);
  };

  useEffect(() => {
    load();
  }, []);

  const toTreeNodes = (list: DeptNode[]): DataNode[] =>
    list.map((d) => ({
      key: d.id,
      title: d.name,
      children: d.children && d.children.length ? toTreeNodes(d.children) : undefined,
    }));

  const antdTree = useMemo(() => toTreeNodes(treeData), [treeData]);

  const openCreate = (pid: number | null) => {
    setEditing(null);
    setParentId(pid);
    form.resetFields();
    form.setFieldsValue({ parentId: pid ?? null, status: 1 });
    setDrawerOpen(true);
  };

  const openEdit = (node: DeptNode) => {
    setEditing(node);
    setParentId(node.parentId);
    form.setFieldsValue({
      name: node.name,
      parentId: node.parentId ?? null,
      orderNo: node.orderNo,
      status: node.status ?? 1,
    });
    setDrawerOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    if (editing) {
      await updateDept(editing.id, values);
      message.success('部门已更新');
    } else {
      await createDept({ ...values, parentId });
      message.success('部门已创建');
    }
    setDrawerOpen(false);
    load();
  };

  const remove = (node: DeptNode) => {
    Modal.confirm({
      title: `确认删除「${node.name}」？`,
      content: '该部门下的子节点也会一并移除。',
      onOk: async () => {
        await deleteDept(node.id);
        message.success('部门已删除');
        load();
      },
    });
  };

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => openCreate(null)}>
          新建根部门
        </Button>
      </Space>
      <Tree<DataNode>
        treeData={antdTree}
        defaultExpandAll
        titleRender={(node) => {
          const id = Number(node.key);
          const found = findNode(treeData, id);
          return (
            <Space>
              <span>{node.title as string}</span>
              <Button type="link" size="small" onClick={() => openCreate(id)}>
                加子级
              </Button>
              <Button type="link" size="small" onClick={() => found && openEdit(found)}>
                编辑
              </Button>
              <Button type="link" size="small" danger onClick={() => found && remove(found)}>
                删除
              </Button>
            </Space>
          );
        }}
      />
      <Drawer
        title={editing ? '编辑部门' : '新建部门'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={420}
        footer={
          <Space style={{ float: 'right' }}>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" onClick={submit}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="部门名称" rules={[{ required: true, message: '请输入部门名称' }]}>
            <Input placeholder="如：研发中心" />
          </Form.Item>
          <Form.Item name="parentId" label="上级部门">
            <InputNumber style={{ width: '100%' }} placeholder="留空表示根部门" />
          </Form.Item>
          <Form.Item name="orderNo" label="排序号">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue={1}>
            <InputNumber style={{ width: '100%' }} min={0} max={1} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
};

function findNode(list: DeptNode[], id: number): DeptNode | null {
  for (const n of list) {
    if (n.id === id) return n;
    if (n.children) {
      const r = findNode(n.children, id);
      if (r) return r;
    }
  }
  return null;
}

export default OrganizationManagement;
