import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import { ReloadOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import { normalizeTotal } from '@bone/shared-utils';
import { replenishmentApi } from '../services/api';
import { errMsg } from '../utils/error';
import type { ReplenishmentOrderSummary, ReplenishmentSuggestion } from '../types';

const STATUS_META: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  SUBMITTED: { color: 'processing', label: '已提交' },
  APPROVED: { color: 'warning', label: '已批准' },
  RECEIVED: { color: 'success', label: '已入库' },
  CANCELLED: { color: 'error', label: '已取消' },
};

/**
 * 供应链补货管理（新场景页 · 不改 InventoryManagement）。
 *
 * 闭环：低库存建议 → 建补货单 → 提交 → 审批 → 到货入库（后端调 Inventory.receive）。
 */
export const ReplenishmentManagement: React.FC = () => {
  const [rows, setRows] = useState<ReplenishmentOrderSummary[]>([]);
  const [tips, setTips] = useState<ReplenishmentSuggestion[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [filterStatus, setFilterStatus] = useState<string>('');
  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchRows = useCallback(async () => {
    setLoading(true);
    try {
      const res = await replenishmentApi.page({
        page,
        size: pageSize,
        status: filterStatus || undefined,
      });
      setRows(res.data.records ?? []);
      setTotal(normalizeTotal(res.data.total));
    } catch (error) {
      message.error(errMsg(error, '获取补货单失败'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, filterStatus]);

  const fetchTips = useCallback(async () => {
    try {
      const res = await replenishmentApi.suggestions({ page: 1, size: 20 });
      setTips(res.data ?? []);
    } catch {
      /* 建议区失败不挡主列表 */
    }
  }, []);

  useEffect(() => {
    void fetchRows();
  }, [fetchRows]);

  useEffect(() => {
    void fetchTips();
  }, [fetchTips]);

  const runAction = async (
    id: string,
    action: 'submit' | 'approve' | 'receive' | 'cancel',
  ) => {
    setSubmitting(true);
    try {
      const api = replenishmentApi[action];
      await api(id);
      message.success(
        action === 'receive' ? '已到货入库，库存已增加' : '操作成功',
      );
      void fetchRows();
      void fetchTips();
    } catch (error) {
      message.error(errMsg(error, '操作失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const submitCreate = async (values: {
    productId: string;
    productName?: string;
    warehouseCode?: string;
    quantity?: number;
    supplierCode?: string;
    remark?: string;
  }) => {
    setSubmitting(true);
    try {
      const res = await replenishmentApi.create({
        productId: values.productId,
        productName: values.productName,
        warehouseCode: values.warehouseCode,
        quantity: values.quantity,
        supplierCode: values.supplierCode,
        remark: values.remark,
      });
      message.success(`补货单已创建：${res.data.replenishNo}`);
      setCreateOpen(false);
      form.resetFields();
      setPage(1);
      void fetchRows();
      void fetchTips();
    } catch (error) {
      message.error(errMsg(error, '创建失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const batchFromTips = async () => {
    setSubmitting(true);
    try {
      const res = await replenishmentApi.fromSuggestions({ supplierCode: 'SUP-DEMO' });
      message.success(`已生成 ${res.data?.length ?? 0} 张草稿`);
      void fetchRows();
      void fetchTips();
    } catch (error) {
      message.error(errMsg(error, '批量创建失败'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Alert
        type="info"
        showIcon
        message="供应链补货闭环"
        description="低于安全库存 → 建补货单 → 提交/审批 → 到货入库。入库走库存真源，不改「库存管理」页。"
      />

      <Card
        title={
          <Space>
            <ShoppingCartOutlined />
            低库存建议
          </Space>
        }
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => void fetchTips()}>
              刷新
            </Button>
            <AuthButton
              code={BonePermissionCodes.COMMERCE_INVENTORY_WRITE}
              type="primary"
              loading={submitting}
              disabled={tips.filter((t) => !t.hasOpenOrder).length === 0}
              onClick={() => void batchFromTips()}
            >
              按建议建草稿
            </AuthButton>
          </Space>
        }
        size="small"
      >
        <Table
          rowKey={(r) => `${r.productId}-${r.warehouseCode}`}
          size="small"
          pagination={false}
          dataSource={tips}
          locale={{ emptyText: '暂无低于安全库存的 SKU' }}
          columns={[
            { title: '商品', dataIndex: 'productName', render: (v, r) => v || r.productId },
            { title: '仓库', dataIndex: 'warehouseCode', width: 100 },
            { title: '可用', dataIndex: 'availableQty', width: 80 },
            { title: '安全库存', dataIndex: 'safetyStock', width: 90 },
            { title: '建议补货', dataIndex: 'suggestedQty', width: 90 },
            {
              title: '未完结单',
              dataIndex: 'hasOpenOrder',
              width: 90,
              render: (v: boolean) => (v ? <Tag color="orange">有</Tag> : <Tag>无</Tag>),
            },
          ]}
        />
      </Card>

      <Card
        title="补货单"
        extra={
          <Space>
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 140 }}
              value={filterStatus || undefined}
              onChange={(v) => {
                setFilterStatus(v || '');
                setPage(1);
              }}
              options={Object.entries(STATUS_META).map(([k, m]) => ({
                value: k,
                label: m.label,
              }))}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void fetchRows()}>
              刷新
            </Button>
            <AuthButton
              code={BonePermissionCodes.COMMERCE_INVENTORY_WRITE}
              type="primary"
              onClick={() => setCreateOpen(true)}
            >
              手工建单
            </AuthButton>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={rows}
          pagination={{
            current: page,
            pageSize,
            total,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
            },
          }}
          columns={[
            { title: '单号', dataIndex: 'replenishNo', width: 180 },
            {
              title: '商品',
              dataIndex: 'productName',
              render: (v, r) => v || r.productId,
            },
            { title: '仓库', dataIndex: 'warehouseCode', width: 90 },
            { title: '数量', dataIndex: 'quantity', width: 70 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (s: string) => {
                const m = STATUS_META[s] || { color: 'default', label: s };
                return <Tag color={m.color}>{m.label}</Tag>;
              },
            },
            { title: '供应商', dataIndex: 'supplierCode', width: 100 },
            {
              title: '操作',
              key: 'op',
              width: 280,
              render: (_, row) => (
                <Space size={4} wrap>
                  {row.status === 'DRAFT' && (
                    <AuthButton
                      code={BonePermissionCodes.COMMERCE_INVENTORY_WRITE}
                      size="small"
                      loading={submitting}
                      onClick={() => void runAction(row.id, 'submit')}
                    >
                      提交
                    </AuthButton>
                  )}
                  {row.status === 'SUBMITTED' && (
                    <AuthButton
                      code={BonePermissionCodes.COMMERCE_INVENTORY_WRITE}
                      size="small"
                      type="primary"
                      loading={submitting}
                      onClick={() => void runAction(row.id, 'approve')}
                    >
                      审批
                    </AuthButton>
                  )}
                  {row.status === 'APPROVED' && (
                    <AuthButton
                      code={BonePermissionCodes.COMMERCE_INVENTORY_WRITE}
                      size="small"
                      type="primary"
                      loading={submitting}
                      onClick={() => void runAction(row.id, 'receive')}
                    >
                      到货入库
                    </AuthButton>
                  )}
                  {(row.status === 'DRAFT' || row.status === 'SUBMITTED') && (
                    <AuthButton
                      code={BonePermissionCodes.COMMERCE_INVENTORY_WRITE}
                      size="small"
                      danger
                      loading={submitting}
                      onClick={() => void runAction(row.id, 'cancel')}
                    >
                      取消
                    </AuthButton>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      <Modal
        title="手工创建补货单"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={submitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={submitCreate}>
          <Form.Item name="productId" label="商品 ID" rules={[{ required: true }]}>
            <Input placeholder="如 900001" />
          </Form.Item>
          <Form.Item name="productName" label="商品名称">
            <Input />
          </Form.Item>
          <Form.Item name="warehouseCode" label="仓库" initialValue="DEFAULT">
            <Input />
          </Form.Item>
          <Form.Item name="quantity" label="补货数量（空则用建议量）">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="supplierCode" label="供应商" initialValue="SUP-DEMO">
            <Input />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
};
