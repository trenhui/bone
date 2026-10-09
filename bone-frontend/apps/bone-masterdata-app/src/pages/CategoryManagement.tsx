import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Space, Tree, TreeSelect } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Auth, AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import { categoryApi } from '../services/api';
import type { MasterDataCategory } from '../types/governance';
import { useEntityScope } from '../context/EntityScopeContext';
import EntityScopeSelect from '../components/EntityScopeSelect';
import { useMessage } from '../App';

/**
 * 分类树节点。
 *
 * key 用 string：雪花 ID（如 910000000000000121）超出 2^53，后端为保精度序列化成字符串，
 * 声明成 number 会与实际运行时不符（Map key / === 比较行为不可预期）。
 */
interface CatNode {
  key: string;
  title: string;
  children?: CatNode[];
}

const toTree = (rows: MasterDataCategory[]): CatNode[] => {
  const byParent = new Map<string | null, MasterDataCategory[]>();
  rows.forEach((r) => {
    // 后端 id/parentCategoryId 均为字符串；null/undefined 统一归到根
    const key = r.parentCategoryId === null || r.parentCategoryId === undefined
      ? null
      : String(r.parentCategoryId);
    byParent.set(key, [...(byParent.get(key) ?? []), r]);
  });
  const build = (parent: string | null): CatNode[] =>
    (byParent.get(parent) ?? []).map((r) => {
      const id = String(r.id);
      return {
        key: id,
        title: `${r.code} · ${r.name}（L${r.level}）`,
        children: byParent.has(id) ? build(id) : undefined
      };
    });
  return build(null);
};

/** 分类体系（G4 / UC-T3）：模型内树形分类维护，支持新建 / 编辑 / 删除。 */
const CategoryManagement: React.FC = () => {
  const message = useMessage();
  // 模型来自全局作用域：与字段 / 记录 / 治理 / 工单页面共享，无需手输 ID
  const { entityId, currentEntity } = useEntityScope();
  const [rows, setRows] = useState<MasterDataCategory[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  /** 受控展开：数据异步到达后自动全展开（defaultExpandAll 对异步树无效）。 */
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  /** 非空表示编辑态：后端 UpdateMasterDataCategoryCommand 只接受 name/description/sortOrder。 */
  const [editing, setEditing] = useState<MasterDataCategory | null>(null);
  const [form] = Form.useForm();

  const load = useCallback(
    async (eid?: string) => {
      if (!eid) {
        setRows([]);
        return;
      }
      setLoading(true);
      try {
        const res = await categoryApi.tree(eid);
        const list = res.data ?? [];
        setRows(list);
        // 切模型后默认全展开：父节点 key 收集自「被别人当作 parentCategoryId 的那些行」
        const parents = new Set(
          list
            .map((r) => r.parentCategoryId)
            .filter((p): p is NonNullable<typeof p> => p !== null && p !== undefined)
            .map(String)
        );
        setExpandedKeys([...parents]);
      } catch {
        message.error('加载分类树失败');
      } finally {
        setLoading(false);
      }
    },
    [message]
  );

  useEffect(() => {
    void load(entityId);
  }, [entityId, load]);

  const treeData = useMemo(() => toTree(rows), [rows]);

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (cat: MasterDataCategory) => {
    setEditing(cat);
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditing(null);
  };

  const submit = async () => {
    let v: Record<string, unknown>;
    try {
      v = await form.validateFields();
    } catch {
      return; // 字段级校验错误由 Form.Item 就地展示
    }
    try {
      if (editing) {
        await categoryApi.update(editing.id, {
          name: v.name,
          description: v.description,
          sortOrder: v.sortOrder
        });
        message.success('已保存');
      } else {
        await categoryApi.create({
          ...v,
          masterDataEntityId: entityId,
          parentCategoryId: v.parentCategoryId ?? undefined
        });
        message.success('已创建');
      }
      closeModal();
      void load(entityId);
    } catch (e: unknown) {
      // 提交失败必须让用户看到原因并保持弹窗开启，否则表现为「点了没反应」
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      message.error(msg ? `保存失败：${msg}` : '保存失败，请检查分类编码是否重复');
    }
  };

  const emptyNode = !entityId ? (
    <Card size="small" type="inner">
      请先在上方选择主数据模型；分类是「模型内」的树（表 mdm_category.mdm_entity_id 必填），
      未选模型时没有可展示的数据。
    </Card>
  ) : (
    <Card size="small" type="inner">
      模型「{currentEntity?.name ?? entityId}」下暂无分类。点右上角「新建分类」创建根节点，
      之后即可在新建时选择父分类继续扩展层级。
    </Card>
  );

  return (
    <Card
      title="分类体系"
      extra={
        <Space>
          <EntityScopeSelect width={260} />
          <AuthButton code={BonePermissionCodes.MASTERDATA_CATEGORIES_WRITE} type="primary" icon={<PlusOutlined />} disabled={!entityId} onClick={openCreate}>
            新建分类
          </AuthButton>
          <Button icon={<ReloadOutlined />} disabled={!entityId} onClick={() => void load(entityId)}>
            刷新
          </Button>
        </Space>
      }
    >
      {rows.length === 0 ? (
        emptyNode
      ) : (
        <Tree
          treeData={treeData}
          // defaultExpandAll 只在首次挂载时生效，而 treeData 是异步到达的（挂载时为空数组），
          // 直接用会导致「进来是折叠的、每次都要手点」。改用受控 expandedKeys + 数据到达后展开。
          expandedKeys={expandedKeys}
          onExpand={(keys) => setExpandedKeys(keys.map(String))}
          autoExpandParent
          titleRender={(node) => {
            const cat = rows.find((r) => String(r.id) === node.key);
            return (
              <Space>
                <span>{node.title}</span>
                {cat && (
                  <AuthButton code={BonePermissionCodes.MASTERDATA_CATEGORIES_WRITE} size="small" type="text" onClick={() => openEdit(cat)}>
                    编辑
                  </AuthButton>
                )}
                {/* Popconfirm 会对 children 做 cloneElement：用 <Auth> 包外层，不能把内部 Button 换成 AuthButton */}
                <Auth code={BonePermissionCodes.MASTERDATA_CATEGORIES_WRITE}>
                  <Popconfirm
                    title="删除该分类？（有子分类时不可删）"
                    onConfirm={async () => {
                      try {
                        await categoryApi.delete(node.key);
                        message.success('已删除');
                        void load(entityId);
                      } catch {
                        message.error('删除失败（可能存在子分类）');
                      }
                    }}
                  >
                    <Button size="small" type="text" danger>
                      删除
                    </Button>
                  </Popconfirm>
                </Auth>
              </Space>
            );
          }}
        />
      )}
      {loading && <div style={{ padding: '12px 0', color: 'rgba(0,0,0,0.45)' }}>加载中…</div>}

      <Modal
        title={editing ? `编辑分类 · ${editing.code}` : '新建分类'}
        open={modalOpen}
        onCancel={closeModal}
        destroyOnHidden
        onOk={submit}
      >
        <Form
          key={editing ? `edit-${editing.id}` : 'create'}
          form={form}
          layout="vertical"
          preserve={false}
          initialValues={
            editing
              ? {
                  code: editing.code,
                  name: editing.name,
                  description: editing.description,
                  sortOrder: editing.sortOrder
                }
              : { sortOrder: 0 }
          }
        >
          <Form.Item
            name="code"
            label="分类编码"
            rules={[{ required: true, message: '编码不能为空' }]}
            extra={editing ? '编码创建后不可修改（实体内唯一）' : undefined}
          >
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item name="name" label="分类名称" rules={[{ required: true, message: '名称不能为空' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号" extra="同级按升序排列，默认 0">
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
          {!editing && (
            <Form.Item name="parentCategoryId" label="父分类（留空=根节点）">
              <TreeSelect
                allowClear
                treeDefaultExpandAll
                placeholder="留空则创建根节点"
                treeData={treeData}
                fieldNames={{ label: 'title', value: 'key', children: 'children' }}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
};

export default CategoryManagement;
