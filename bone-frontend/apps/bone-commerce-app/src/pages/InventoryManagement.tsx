import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  message,
} from 'antd';
import { InboxOutlined, ReloadOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import { normalizeTotal } from '@bone/shared-utils';
import { inventoryApi } from '../services/api';
import { errMsg } from '../utils/error';
import type { InventorySummary } from '../types';

type Action = 'receive' | 'deduct' | 'reserve' | 'confirm' | 'release' | 'safety-stock';

const ACTION_META: Record<Action, { label: string; danger?: boolean }> = {
  receive: { label: '入库' },
  deduct: { label: '扣减', danger: true },
  reserve: { label: '预留' },
  confirm: { label: '确认出库' },
  release: { label: '释放预留' },
  'safety-stock': { label: '安全库存' },
};

/**
 * 库存管理（多渠道共享库存真源）。
 *
 * <b>为何库存要在这里可见可调</b>：渠道订单与站内订单共享同一份实物库存，
 * 库存不足是超卖的唯一拦截点。运营必须能直接看到可用量/预留量，
 * 否则「下单提示库存不足」时无从判断是数据问题还是真的卖完了。
 */
export const InventoryManagement: React.FC = () => {
  const [rows, setRows] = useState<InventorySummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [lowStockOnly, setLowStockOnly] = useState<boolean>(false);

  const [modalAction, setModalAction] = useState<Action | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchRows = useCallback(async () => {
    setLoading(true);
    try {
      const res = await inventoryApi.page({
        page,
        size: pageSize,
        lowStockOnly: lowStockOnly || undefined,
      });
      setRows(res.data.records ?? []);
      setTotal(normalizeTotal(res.data.total));
    } catch (error) {
      message.error(errMsg(error, '获取库存列表失败'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, lowStockOnly]);

  useEffect(() => {
    void fetchRows();
  }, [fetchRows]);

  const submitAction = async (values: {
    productId: string;
    productName?: string;
    warehouseCode?: string;
    quantity: number;
  }) => {
    if (!modalAction) {
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        productId: values.productId,
        productName: values.productName,
        warehouseCode: values.warehouseCode || 'DEFAULT',
        quantity: values.quantity,
      };
      const res =
        modalAction === 'receive'
          ? await inventoryApi.receive(payload)
          : modalAction === 'deduct'
            ? await inventoryApi.deduct(payload)
            : modalAction === 'reserve'
              ? await inventoryApi.reserve(payload)
              : modalAction === 'confirm'
                ? await inventoryApi.confirm(payload)
                : modalAction === 'release'
                  ? await inventoryApi.release(payload)
                  : await inventoryApi.setSafetyStock(payload);
      message.success(
        `${ACTION_META[modalAction].label}成功，可用 ${res.data.availableQty} / 预留 ${res.data.reservedQty}`,
      );
      setModalAction(null);
      form.resetFields();
      void fetchRows();
    } catch (error) {
      message.error(errMsg(error, `${ACTION_META[modalAction].label}失败`));
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { title: '商品ID', dataIndex: 'productId', key: 'productId', width: 160 },
    {
      title: '商品名称',
      dataIndex: 'productName',
      key: 'productName',
      ellipsis: true,
      render: (v: string | null) => v || '—',
    },
    { title: '仓库', dataIndex: 'warehouseCode', key: 'warehouseCode', width: 110 },
    {
      title: '可用库存',
      dataIndex: 'availableQty',
      key: 'availableQty',
      width: 110,
      render: (v: number, r: InventorySummary) => (
        <span style={{ color: r.lowStock ? '#cf1322' : undefined, fontWeight: r.lowStock ? 600 : 400 }}>
          {v}
        </span>
      ),
    },
    { title: '已预留', dataIndex: 'reservedQty', key: 'reservedQty', width: 100 },
    { title: '安全库存', dataIndex: 'safetyStock', key: 'safetyStock', width: 100 },
    {
      title: '预警',
      key: 'lowStock',
      width: 90,
      render: (_: unknown, r: InventorySummary) =>
        r.lowStock ? <Tag color="error">低于安全库存</Tag> : <Tag color="success">正常</Tag>,
    },
  ];

  return (
    <Card
      title="库存管理"
      extra={
        <Space>
          <Select
            placeholder="筛选"
            style={{ width: 150 }}
            value={lowStockOnly ? 'low' : 'all'}
            onChange={(v) => {
              setLowStockOnly(v === 'low');
              setPage(1);
            }}
            options={[
              { value: 'all', label: '全部库存' },
              { value: 'low', label: '仅低库存预警' },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void fetchRows()}>
            刷新
          </Button>
          <AuthButton
            code={BonePermissionCodes.COMMERCE_INVENTORY_WRITE}
            type="primary"
            icon={<InboxOutlined />}
            onClick={() => setModalAction('receive')}
          >
            入库
          </AuthButton>
        </Space>
      }
    >
      <Space style={{ marginBottom: 16 }} size="large" wrap>
        <Statistic title="库存行数" value={total} />
        <Statistic
          title="可用总量"
          value={rows.reduce((s, r) => s + r.availableQty, 0)}
        />
        <Statistic
          title="预留总量"
          value={rows.reduce((s, r) => s + r.reservedQty, 0)}
        />
      </Space>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (p, s) => {
            setPage(p);
            setPageSize(s);
          },
        }}
      />

      <Modal
        title={modalAction ? `库存 · ${ACTION_META[modalAction].label}` : ''}
        open={modalAction !== null}
        onCancel={() => setModalAction(null)}
        footer={null}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={(v) => void submitAction(v)}>
          <Form.Item
            name="productId"
            label="商品ID"
            rules={[{ required: true, message: '请输入商品ID' }]}
            extra="雪花 ID 为字符串，请勿用数字输入控件。"
          >
            <Input placeholder="如 900001" />
          </Form.Item>
          {modalAction === 'receive' && (
            <Form.Item name="productName" label="商品名称">
              <Input placeholder="首次建库存时建议填写" />
            </Form.Item>
          )}
          <Form.Item name="warehouseCode" label="仓库编码" initialValue="DEFAULT">
            <Input placeholder="DEFAULT" />
          </Form.Item>
          <Form.Item
            name="quantity"
            label="数量"
            rules={[{ required: true, message: '请输入数量' }]}
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setModalAction(null)}>取消</Button>
              <AuthButton
                code={BonePermissionCodes.COMMERCE_INVENTORY_WRITE}
                type="primary"
                htmlType="submit"
                loading={submitting}
                danger={modalAction ? ACTION_META[modalAction].danger : false}
              >
                提交
              </AuthButton>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
