import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import { EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { resolveErrorMessage } from '@bone/shared-utils';
import type { ColumnsType } from 'antd/es/table';
import { PERMISSIONS } from '../constants/permissions';
import { PageHeader } from '../components/PageHeader';
import { toTotal, mappingApi, INSURER_TYPE_LABEL, INSURER_STATUS_LABEL } from '../services/claimConfigApi';
import type { InsurerType, InsurerStatus, InsurerView } from '../services/claimConfigApi';

const INSURER_TYPE_OPTIONS = Object.entries(INSURER_TYPE_LABEL).map(([value, label]) => ({
  value,
  label,
}));

interface InsurerFormValues {
  insurerCode: string;
  insurerName: string;
  insurerShortName?: string;
  insurerType?: InsurerType;
  creditCode?: string;
  parentCode?: string;
}

/**
 * 承保主体管理（多保司主数据真源）——替代保单上的保险公司自由文本。
 *
 * <p>保单通过 {@code bizIdentityCode} 引用这里的 {@code insurer_code}；跨保司码值映射也以 {@code insurer_code} 为主键。
 * 分公司用 {@code parent_code} 挂在法人主体下，不另建实体。
 *
 * <p><b>不物理删除</b>：主体被保单/映射引用，删行产生孤儿——停用（DISABLED）与终止合作（TERMINATED）
 * 是状态流转而非删除；「关联保单数」让运营在停用前先看清影响面。
 */
export function InsurerManagementPage(): ReactElement {
  const { message } = AntApp.useApp();
  const [form] = Form.useForm<InsurerFormValues>();

  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [rows, setRows] = useState<InsurerView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  /** 非空 = 编辑模式（编码锁定不可改）。 */
  const [editing, setEditing] = useState<InsurerView | null>(null);
  const [saving, setSaving] = useState(false);
  /** 行级处理中（启停/终止防重复提交）。 */
  const [processingCode, setProcessingCode] = useState('');

  const load = useCallback(
    async (kw: string, p: number, s: number) => {
      setLoading(true);
      try {
        const res = await mappingApi.listInsurers({ keyword: kw || undefined, page: p, size: s });
        if (res.code !== 200) {
          message.error(resolveErrorMessage(res, '承保主体加载失败'));
          return;
        }
        setRows(res.data.records);
        setTotal(toTotal(res.data));
      } catch (err) {
        message.error(resolveErrorMessage(err, '承保主体加载失败'));
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    void load(keyword, page, size);
  }, [load, keyword, page, size]);

  const submit = async () => {
    try {
      const v = await form.validateFields();
      setSaving(true);
      const req = {
        insurerCode: v.insurerCode.trim(),
        insurerName: v.insurerName.trim(),
        insurerShortName: v.insurerShortName?.trim() || undefined,
        insurerType: v.insurerType,
        creditCode: v.creditCode?.trim() || undefined,
        parentCode: v.parentCode?.trim() || undefined,
      };
      const res = editing
        ? await mappingApi.updateInsurer(editing.insurerCode, { ...req, insurerCode: editing.insurerCode })
        : await mappingApi.registerInsurer(req);
      if (res.code !== 200) {
        message.error(resolveErrorMessage(res, editing ? '保存失败' : '注册失败'));
        return;
      }
      message.success(editing ? '已保存' : '注册成功');
      setModalOpen(false);
      setEditing(null);
      void load(keyword, page, size);
    } catch (err) {
      message.error(resolveErrorMessage(err, editing ? '保存失败' : '注册失败'));
    } finally {
      setSaving(false);
    }
  };

  const changeStatus = (row: InsurerView, target: InsurerStatus) => {
    const meta: Record<InsurerStatus, { title: string; content: string; danger: boolean }> = {
      ACTIVE: {
        title: '启用主体',
        content: `确认启用「${row.insurerName}」？启用后可选为保单承保主体。`,
        danger: false,
      },
      DISABLED: {
        title: '停用主体',
        content: `确认停用「${row.insurerName}」？停用后不可再选为承保主体，存量保单与映射不受影响。`,
        danger: true,
      },
      TERMINATED: {
        title: '终止合作',
        content: `确认终止与「${row.insurerName}」的合作？终止后不可恢复为启用（仍保留历史单据可见）。`,
        danger: true,
      },
    };
    const m = meta[target];
    Modal.confirm({
      title: m.title,
      content: m.content,
      okText: '确认',
      okButtonProps: { danger: m.danger },
      onOk: () => {
        setProcessingCode(row.insurerCode);
        return mappingApi
          .changeInsurerStatus(row.insurerCode, target)
          .then(() => {
            message.success('状态已更新');
            return load(keyword, page, size);
          })
          .catch((err) => {
            message.error(resolveErrorMessage(err, '状态更新失败'));
            throw err;
          })
          .finally(() => setProcessingCode(''));
      },
    });
  };

  const columns: ColumnsType<InsurerView> = [
    { title: '保司编码', dataIndex: 'insurerCode', width: 140 },
    { title: '保司全称', dataIndex: 'insurerName' },
    { title: '简称', dataIndex: 'insurerShortName', width: 140, render: (v) => v || '—' },
    {
      title: '类型',
      dataIndex: 'insurerType',
      width: 90,
      render: (v) => (v ? INSURER_TYPE_LABEL[v as InsurerType] : '—'),
    },
    { title: '统一信用代码', dataIndex: 'creditCode', width: 180, render: (v) => v || '—' },
    { title: '上级主体编码', dataIndex: 'parentCode', width: 160, render: (v) => v || '—' },
    {
      title: '关联保单',
      dataIndex: 'policyCount',
      width: 90,
      render: (v: number | undefined) => (v === undefined ? '—' : v),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v) => {
        const s = v as InsurerStatus;
        const color = s === 'ACTIVE' ? 'green' : s === 'TERMINATED' ? 'red' : 'default';
        return <Tag color={color}>{INSURER_STATUS_LABEL[s] ?? s}</Tag>;
      },
    },
    {
      title: '操作',
      width: 200,
      render: (_: unknown, row) => {
        const s = (row.status ?? 'ACTIVE') as InsurerStatus;
        return (
          <Space size={4}>
            <AuthButton
              code={PERMISSIONS.POLICY_WRITE}
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                setEditing(row);
                // 常驻 Modal 不再随关闭销毁表单：先 reset 再回填，避免上一条记录残留
                form.resetFields();
                form.setFieldsValue({
                  insurerCode: row.insurerCode,
                  insurerName: row.insurerName,
                  insurerShortName: row.insurerShortName,
                  insurerType: row.insurerType,
                  creditCode: row.creditCode,
                  parentCode: row.parentCode,
                });
                setModalOpen(true);
              }}
            >
              编辑
            </AuthButton>
            {s === 'ACTIVE' && (
              <>
                <AuthButton
                  code={PERMISSIONS.POLICY_WRITE}
                  type="link"
                  size="small"
                  danger
                  loading={processingCode === row.insurerCode}
                  onClick={() => changeStatus(row, 'DISABLED')}
                >
                  停用
                </AuthButton>
                <AuthButton
                  code={PERMISSIONS.POLICY_WRITE}
                  type="link"
                  size="small"
                  danger
                  loading={processingCode === row.insurerCode}
                  onClick={() => changeStatus(row, 'TERMINATED')}
                >
                  终止合作
                </AuthButton>
              </>
            )}
            {(s === 'DISABLED' || s === 'TERMINATED') && (
              <AuthButton
                code={PERMISSIONS.POLICY_WRITE}
                type="link"
                size="small"
                loading={processingCode === row.insurerCode}
                onClick={() => changeStatus(row, 'ACTIVE')}
              >
                启用
              </AuthButton>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <>
      <PageHeader
        title="承保主体"
        description="受控的保险公司主数据（直保/经纪/代理），替代保单上的保险公司自由文本；保单通过业务主体码引用，跨保司码值映射也以它为主键。"
      />
      <Card>
        <Space wrap style={{ marginBottom: 12 }}>
          <Input.Search
            allowClear
            placeholder="编码 / 名称"
            style={{ width: 240 }}
            onSearch={(v) => {
              setPage(1);
              setKeyword(v.trim());
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load(keyword, page, size)}>
            刷新
          </Button>
          <AuthButton
            type="primary"
            icon={<PlusOutlined />}
            code={PERMISSIONS.POLICY_WRITE}
            onClick={() => {
              form.resetFields();
              setEditing(null);
              setModalOpen(true);
            }}
          >
            注册承保主体
          </AuthButton>
        </Space>
        <Table<InsurerView>
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setSize(s);
            },
          }}
        />
      </Card>
      <Modal
        title={editing ? `编辑承保主体（${editing.insurerCode}）` : '注册承保主体'}
        open={modalOpen}
        confirmLoading={saving}
        onOk={() => void submit()}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        // 不用 destroyOnHidden：它在关闭时销毁 Form，导致 useForm 实例在页面挂载时
        // 没有可连接的 Form 元素（antd 告警 + 首次提交前 setFieldsValue 静默失效）。
        // 改为常驻渲染，打开前显式 resetFields 保证每次都是干净表单。
        forceRender
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="insurerCode"
            label="保司编码"
            tooltip={editing ? '编码是保单/映射的外键锚点，创建后不可修改' : undefined}
            rules={[{ required: true, message: '请输入保司编码' }]}
          >
            <Input maxLength={64} placeholder="如 YCBX / PICC，同租户唯一" disabled={!!editing} />
          </Form.Item>
          <Form.Item
            name="insurerName"
            label="保司全称"
            rules={[{ required: true, message: '请输入保司全称' }]}
          >
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="insurerShortName" label="简称">
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="insurerType" label="类型" initialValue="DIRECT">
            <Select options={INSURER_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="creditCode" label="统一社会信用代码">
            <Input maxLength={32} placeholder="外部桥锚点，保司码对不上时用它兜底" />
          </Form.Item>
          <Form.Item name="parentCode" label="上级主体编码" tooltip="分公司的法人主体编码；法人为空">
            <Input maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
