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
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
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
  const [saving, setSaving] = useState(false);

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
      const res = await mappingApi.registerInsurer({
        insurerCode: v.insurerCode.trim(),
        insurerName: v.insurerName.trim(),
        insurerShortName: v.insurerShortName?.trim() || undefined,
        insurerType: v.insurerType,
        creditCode: v.creditCode?.trim() || undefined,
        parentCode: v.parentCode?.trim() || undefined,
      });
      if (res.code !== 200) {
        message.error(resolveErrorMessage(res, '注册失败'));
        return;
      }
      message.success('注册成功');
      setModalOpen(false);
      void load(keyword, page, size);
    } catch (err) {
      message.error(resolveErrorMessage(err, '注册失败'));
    } finally {
      setSaving(false);
    }
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
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v) => {
        const s = v as InsurerStatus;
        return <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{INSURER_STATUS_LABEL[s] ?? s}</Tag>;
      },
    },
  ];

  return (
    <>
      <PageHeader
        title="承保主体管理"
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
        title="注册承保主体"
        open={modalOpen}
        confirmLoading={saving}
        onOk={() => void submit()}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="insurerCode"
            label="保司编码"
            rules={[{ required: true, message: '请输入保司编码' }]}
          >
            <Input maxLength={64} placeholder="如 YCBX / PICC，同租户唯一" />
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
