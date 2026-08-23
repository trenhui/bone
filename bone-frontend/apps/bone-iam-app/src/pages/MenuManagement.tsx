import React, { useEffect, useMemo, useState } from 'react';
import { Button, Drawer, Form, Input, InputNumber, message, Modal, Space, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  createMenu,
  deleteMenu,
  getMenuTree,
  updateMenu,
  type MenuNodeItem,
  type MenuReq,
} from '../services/api';

const MenuManagement: React.FC = () => {
  const [treeData, setTreeData] = useState<MenuNodeItem[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<MenuNodeItem | null>(null);
  const [parentId, setParentId] = useState<number | null>(null);
  const [form] = Form.useForm<MenuReq>();

  const load = async () => {
    const res = await getMenuTree();
    setTreeData(res.data ?? []);
  };

  useEffect(() => {
    load();
  }, []);

  const toTreeNodes = (list: MenuNodeItem[]): DataNode[] =>
    list.map((m) => ({
      key: m.id,
      title: m.name,
      children: m.children && m.children.length ? toTreeNodes(m.children) : undefined,
    }));

  const antdTree = useMemo(() => toTreeNodes(treeData), [treeData]);

  const openCreate = (pid: number | null) => {
    setEditing(null);
    setParentId(pid);
    form.resetFields();
    form.setFieldsValue({ parentId: pid ?? null, type: 1 });
    setDrawerOpen(true);
  };

  const openEdit = (node: MenuNodeItem) => {
    setEditing(node);
    setParentId(node.parentId);
    form.setFieldsValue({
      name: node.name,
      parentId: node.parentId ?? null,
      path: node.path,
      icon: node.icon,
      orderNo: node.orderNo,
      permission: node.permission,
      type: node.type ?? 1,
    });
    setDrawerOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    if (editing) {
      await updateMenu(editing.id, values);
      message.success('菜单已更新');
    } else {
      await createMenu({ ...values, parentId });
      message.success('菜单已创建');
    }
    setDrawerOpen(false);
    load();
  };

  const remove = (node: MenuNodeItem) => {
    Modal.confirm({
      title: `确认删除「${node.name}」？`,
      content: '该菜单下的子菜单也会一并移除。',
      onOk: async () => {
        await deleteMenu(node.id);
        message.success('菜单已删除');
        load();
      },
    });
  };

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => openCreate(null)}>
          新建根菜单
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
        title={editing ? '编辑菜单' : '新建菜单'}
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
          <Form.Item name="name" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
            <Input placeholder="如：用户管理" />
          </Form.Item>
          <Form.Item name="parentId" label="上级菜单">
            <InputNumber style={{ width: '100%' }} placeholder="留空表示顶级菜单" />
          </Form.Item>
          <Form.Item name="path" label="路由路径">
            <Input placeholder="如：/iam/users" />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Input placeholder="如：UserOutlined" />
          </Form.Item>
          <Form.Item name="permission" label="权限码（留空表示登录可见）">
            <Input placeholder="如：iam:users:read" />
          </Form.Item>
          <Form.Item name="orderNo" label="排序号">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="type" label="类型" initialValue={1}>
            <InputNumber style={{ width: '100%' }} min={1} max={2} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
};

function findNode(list: MenuNodeItem[], id: number): MenuNodeItem | null {
  for (const n of list) {
    if (n.id === id) return n;
    if (n.children) {
      const r = findNode(n.children, id);
      if (r) return r;
    }
  }
  return null;
}

export default MenuManagement;
