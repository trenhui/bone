import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Descriptions, Empty, Form, Input, List, Modal, Space, Table, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import { supplierApi, resolveTenantId } from '../services/supplierApi';
import type { SupplierStateDTO, SupplierRegistrationResultDTO } from '../types/supplier';
import {
  allowedSupplierEvents,
  allowedVendorEvents,
  degradeMessage,
  fromApiError,
  pageCount,
  safeStateProjection,
  formatHistory,
  type TransitionEventOption,
} from './supplierView';
import { useMessage } from '../App';

const { Title, Text, Paragraph } = Typography;

/**
 * 采购供应商治理控制台。
 *
 * 双角色（Supplier 合作资格 / Vendor 交易付款资格）分区呈现、显式迁移事件触发、分页列表。
 * 所有守卫裁决在后端：页面只按当前状态显示「允许的事件入口」，失败一律按后端 message 呈现，
 * 不在本地推进状态。PII 通过 safeStateProjection 白名单过滤，不落入 DOM。
 *
 * 错误处理：apiClient 对 success=false 与 HTTP 403/404/5xx 一律 Promise.reject，
 * 故所有调用必须 try/catch，错误经 fromApiError → degradeMessage 呈现（见 supplierView）。
 */
const SupplierManagement: React.FC = () => {
  const message = useMessage();
  const [records, setRecords] = useState<SupplierStateDTO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const size = 10;
  const [current, setCurrent] = useState<SupplierStateDTO | null>(null);
  const [degraded, setDegraded] = useState<string | null>(null);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [form] = Form.useForm();

  /** 统一失败呈现：返回展示文案，供调用方决定是否进入降级空态。 */
  const showFailure = useCallback(
    (error: unknown): string => {
      const info = fromApiError(error);
      const text = degradeMessage(info.code, info.message);
      message.error(text);
      return text;
    },
    [message],
  );

  const loadPage = useCallback(async () => {
    try {
      const resp = await supplierApi.page({ page, size });
      setDegraded(null);
      setRecords(resp.data.records.map(safeStateProjection));
      setTotal(resp.data.total);
    } catch (error) {
      const text = showFailure(error);
      setDegraded(text);
      setRecords([]);
      setTotal(0);
    }
  }, [page, showFailure]);

  const loadCurrent = useCallback(
    async (partyId: string) => {
      try {
        const resp = await supplierApi.current(partyId);
        setCurrent(resp.data ? safeStateProjection(resp.data) : null);
      } catch (error) {
        showFailure(error);
      }
    },
    [showFailure],
  );

  useEffect(() => {
    void loadPage();
  }, [loadPage]);

  const doTransition = useCallback(
    async (role: 'SUPPLIER' | 'VENDOR', opt: TransitionEventOption) => {
      if (!current) return;
      const partyId = current.partyId;
      try {
        const resp =
          role === 'SUPPLIER'
            ? await supplierApi.transitionSupplier(partyId, { tenantId: resolveTenantId(), event: opt.event })
            : await supplierApi.transitionVendor(partyId, {
              tenantId: resolveTenantId(),
              event: opt.event,
              source: opt.source ?? 'DOMAIN',
            });
        // apiClient 已把 success=false 转成 reject，能走到这里即成功；仍以 data 为准防御。
        message.success(`已提交：${resp.data?.toState ?? opt.label}`);
        await Promise.all([loadCurrent(partyId), loadPage()]);
      } catch (error) {
        showFailure(error); // 失败不本地推进状态
      }
    },
    [current, loadCurrent, loadPage, message, showFailure],
  );

  const doRegister = useCallback(
    async (
      values: { mergeId: string; partyId?: string; sourceSystem?: string; localId?: string; uscc?: string },
    ) => {
      // 身份数据（uscc 等）只作为候选源值提交，提交后不保留本地副本、不在 UI 回显。
      const tenantId = resolveTenantId();
      try {
        const resp = await supplierApi.register({
          mergeId: values.mergeId,
          tenantId,
          partyId: values.partyId,
          sourceSystem: values.sourceSystem,
          localId: values.localId,
          sources: [
            {
              recordId: `${values.mergeId}-src-1`,
              sourceSystem: values.sourceSystem || 'MANUAL',
              values: values.uscc ? { uscc: values.uscc } : {},
            },
          ],
        });
        const result: SupplierRegistrationResultDTO = resp.data;
        // 成功才关闭弹窗与清空表单；失败保留用户输入以便重试。
        setRegisterOpen(false);
        form.resetFields();
        if (result?.outcome === 'MANUAL_REVIEW_REQUIRED') {
          message.warning('登记已转人工复核（身份冲突或裁决不完整），未产生落库副作用');
        } else {
          message.success(`登记成立：${result?.supplierState} / ${result?.vendorState}`);
        }
        await loadPage();
      } catch (error) {
        showFailure(error); // 弹窗保持打开、表单不清空
      }
    },
    [form, loadPage, message, showFailure],
  );

  const columns = [
    { title: '主体标识', dataIndex: 'partyId', key: 'partyId' },
    {
      title: '合作资格',
      dataIndex: 'supplierState',
      key: 'supplierState',
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>,
    },
    {
      title: '交易资格',
      dataIndex: 'vendorState',
      key: 'vendorState',
      render: (s: string) => <Tag color={s === 'ACTIVE_VENDOR' ? 'green' : 'default'}>{s}</Tag>,
    },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: SupplierStateDTO) => (
        <Button size="small" onClick={() => void loadCurrent(r.partyId)}>
          查看
        </Button>
      ),
    },
  ];

  const supplierOptions = current ? allowedSupplierEvents(current.supplierState) : [];
  const vendorOptions = current ? allowedVendorEvents(current.vendorState) : [];
  const pages = pageCount(total, size);

  return (
    <div style={{ maxWidth: 1100 }}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Title level={4} style={{ margin: 0 }}>
            供应商治理
          </Title>
          <Space>
            <AuthButton
              code={BonePermissionCodes.MASTERDATA_GOVERNANCE_WRITE}
              type="primary"
              onClick={() => setRegisterOpen(true)}
            >
              登记供应商
            </AuthButton>
            <Button icon={<ReloadOutlined />} onClick={() => void loadPage()}>
              刷新
            </Button>
          </Space>
        </div>

        {degraded && (
          <Card>
            <Empty description={degraded} />
          </Card>
        )}

        <Card title="供应商列表">
          <Table
            rowKey="partyId"
            columns={columns}
            dataSource={records}
            pagination={{
              current: page,
              pageSize: size,
              total,
              showTotal: () => `共 ${total} 条 / 共 ${pages} 页`,
              onChange: (p) => setPage(p),
            }}
            locale={{ emptyText: '暂无供应商（列表为空）' }}
          />
        </Card>

        <Card title="当前供应商 · 双角色状态">
          {!current ? (
            <Empty description="请先查询供应商" />
          ) : (
            <Space direction="vertical" size="large" style={{ width: '100%' }}>
              <Descriptions bordered size="small" column={2}>
                <Descriptions.Item label="主体标识">{current.partyId}</Descriptions.Item>
                <Descriptions.Item label="上游状态">—</Descriptions.Item>
              </Descriptions>
              <div style={{ display: 'flex', gap: 16 }}>
                <Card title="Supplier · 合作资格" size="small" style={{ flex: 1 }}>
                  <Paragraph>
                    <Text>状态：</Text>
                    <Tag color="blue">{current.supplierState}</Tag>
                    <Text type="secondary"> v{String(current.supplierVersion)}</Text>
                  </Paragraph>
                  <Text type="secondary">迁移历史（最新在后）：</Text>
                  <List
                    size="small"
                    dataSource={formatHistory(current.supplierHistory)}
                    renderItem={(h) => <List.Item>{h}</List.Item>}
                    locale={{ emptyText: '无迁移记录' }}
                  />
                  <Space wrap style={{ marginTop: 8 }}>
                    {supplierOptions.map((opt) => (
                      <AuthButton
                        key={opt.event}
                        code={BonePermissionCodes.MASTERDATA_GOVERNANCE_WRITE}
                        size="small"
                        onClick={() => void doTransition('SUPPLIER', opt)}
                      >
                        {opt.label}
                      </AuthButton>
                    ))}
                  </Space>
                </Card>
                <Card title="Vendor · 交易付款资格" size="small" style={{ flex: 1 }}>
                  <Paragraph>
                    <Text>状态：</Text>
                    <Tag color="gold">{current.vendorState}</Tag>
                    <Text type="secondary"> v{String(current.vendorVersion)}</Text>
                  </Paragraph>
                  <Text type="secondary">迁移历史（最新在后）：</Text>
                  <List
                    size="small"
                    dataSource={formatHistory(current.vendorHistory)}
                    renderItem={(h) => <List.Item>{h}</List.Item>}
                    locale={{ emptyText: '无迁移记录' }}
                  />
                  <Space wrap style={{ marginTop: 8 }}>
                    {vendorOptions.map((opt) => (
                      <AuthButton
                        key={`${opt.event}-${opt.source}`}
                        code={BonePermissionCodes.MASTERDATA_GOVERNANCE_WRITE}
                        size="small"
                        onClick={() => void doTransition('VENDOR', opt)}
                      >
                        {opt.label}
                      </AuthButton>
                    ))}
                  </Space>
                </Card>
              </div>
            </Space>
          )}
        </Card>
      </Space>

      <Modal
        title="登记供应商"
        open={registerOpen}
        onCancel={() => {
          setRegisterOpen(false);
          form.resetFields();
        }}
        onOk={() => void form.validateFields().then(doRegister)}
        okText="提交登记"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="mergeId" label="合并批次号" rules={[{ required: true, message: '请输入合并批次号' }]}>
            <Input placeholder="如 merge-2026-001" />
          </Form.Item>
          <Form.Item name="partyId" label="统一主体标识（可选）">
            <Input placeholder="由上游治理流程分配" />
          </Form.Item>
          <Form.Item name="sourceSystem" label="来源系统">
            <Input placeholder="如 ERP / SRM" />
          </Form.Item>
          <Form.Item name="localId" label="本系统记录号（可选）">
            <Input />
          </Form.Item>
          <Form.Item name="uscc" label="统一社会信用代码（仅用于身份解析，不在界面回显）">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default SupplierManagement;
