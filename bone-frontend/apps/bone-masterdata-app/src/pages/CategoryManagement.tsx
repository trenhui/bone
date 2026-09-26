import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Space, Tree, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { categoryApi } from '../services/api';
import type { MasterDataCategory } from '../types/governance';

interface CatNode {
  key: number;
  title: string;
  children?: CatNode[];
}

const toTree = (rows: MasterDataCategory[]): CatNode[] => {
  const byParent = new Map<number | null, MasterDataCategory[]>();
  rows.forEach((r) => {
    const key = r.parentCategoryId ?? null;
    byParent.set(key, [...(byParent.get(key) ?? []), r]);
  });
  const build = (parent: number | null): CatNode[] =>
    (byParent.get(parent) ?? []).map((r) => ({
      key: r.id,
      title: `${r.code} · ${r.name}（L${r.level}）`,
      children: byParent.has(r.id) ? build(r.id) : undefined
    }));
  return build(null);
};

/** 分类体系（G4 / UC-T3）：模型内树形分类维护。 */
const CategoryManagement: React.FC = () => {
  const [entityId, setEntityId] = useState<number | undefined>();
  const [rows, setRows] = useState<MasterDataCategory[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async (eid?: number) => {
    if (!eid) return;
    const res = await categoryApi.tree(eid);
    setRows(res.data ?? []);
  }, []);

  useEffect(() => {
    if (entityId) void load(entityId);
  }, [entityId, load]);

  return (
    <Card
      title="分类体系"
      extra={
        <Space>
          <Input.Search
            placeholder="输入主数据模型ID"
            enterButton="加载"
            style={{ width: 240 }}
            onSearch={(v) => {
              const n = Number(v);
              if (Number.isFinite(n) && n > 0) setEntityId(n);
              else message.warning('请输入有效模型ID');
            }}
          />
          <Button type="primary" icon={<PlusOutlined />} disabled={!entityId} onClick={() => setCreateOpen(true)}>
            新建分类
          </Button>
        </Space>
      }
    >
      {rows.length === 0 ? (
        <Card size="small" type="inner">输入模型 ID 加载分类树；树形分类用于记录归类与管家授权范围（CATEGORY）。</Card>
      ) : (
        <Tree
          treeData={toTree(rows)}
          defaultExpandAll
          titleRender={(node) => (
            <Space>
              <span>{node.title}</span>
              <Popconfirm
                title="删除该分类？（有子分类时不可删）"
                onConfirm={async () => {
                  try {
                    await categoryApi.delete(node.key as number);
                    message.success('已删除');
                    void load(entityId);
                  } catch {
                    message.error('删除失败（可能存在子分类）');
                  }
                }}
              >
                <Button size="small" type="text" danger>删除</Button>
              </Popconfirm>
            </Space>
          )}
        />
      )}

      <Modal
        title="新建分类"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        destroyOnClose
        onOk={async () => {
          const v = await form.validateFields();
          await categoryApi.create({
            ...v,
            masterDataEntityId: entityId,
            parentCategoryId: v.parentCategoryId ? Number(v.parentCategoryId) : undefined
          });
          message.success('已创建');
          setCreateOpen(false);
          form.resetFields();
          void load(entityId);
        }}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="code" label="分类编码" rules={[{ required: true, message: '编码不能为空' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="name" label="分类名称" rules={[{ required: true, message: '名称不能为空' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="parentCategoryId" label="父分类ID（留空=根节点）">
            <InputNumber style={{ width: '100%' }} min={1} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default CategoryManagement;
