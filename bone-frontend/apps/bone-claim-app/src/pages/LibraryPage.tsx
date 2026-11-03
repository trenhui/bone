import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  App as AntApp,
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
  Tabs,
} from 'antd';
import { EditOutlined, LinkOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { resolveErrorMessage } from '@bone/shared-utils';
import type { ColumnsType } from 'antd/es/table';
import { PERMISSIONS } from '../constants/permissions';
import { useNavigate } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { toTotal } from '../services/claimConfigApi';
import {
  claimConfigApi,
  type DiseaseView,
  type DrugView,
  type FeeCategoryView,
  type HospitalView,
} from '../services/claimConfigApi';

/**
 * 基础目录库（0094）—— 医院 / 疾病 / 药品 / 费用类别 四库的租户级主数据维护。
 *
 * <p><b>为什么目录库独立成页</b>：责任配置里的「适用限定名单 / 费用类别规则」都从这些目录
 * <b>选编码</b>（而非手填），配置面与主数据是两种变更节奏——目录由运营日常维护、责任随保单
 * 版本化发布。混在责任表单里会导致"改一个医院等级要进责任版本链"，业界形态（含参考站）
 * 都是独立的字典/目录管理页。
 *
 * <p>四库均为单表 CRUD、同租户同编码唯一（UK），后端写入前显式校验给出可读错误。
 * 目录项编辑<b>不校验是否被责任引用</b>：责任存的是编码快照，理算只认编码串，
 * 改目录不影响已配置责任（脏编码由理算前校验兜底）。
 */

/** 目录标签页标识（与后端 API 路径一致）。 */
type LibraryKey = 'hospitals' | 'diseases' | 'drugs' | 'fee-categories';

/** 四库行的联合类型（按 activeKey 收窄后取字段）。 */
type LibraryRow = HospitalView | DiseaseView | DrugView | FeeCategoryView;

/** 编辑表单值（各库字段的并集；提交时按库取子集）。 */
interface LibraryFormValues {
  code: string;
  name: string;
  hospitalLevel?: string;
  socialFlag?: number;
  province?: string;
  city?: string;
  icdCategory?: string;
  medicalType?: string;
  sortNo?: number;
}

const SOCIAL_FLAG_OPTIONS = [
  { label: '社保定点', value: 1 },
  { label: '非社保定点', value: 0 },
];

const socialTag = (flag?: number): ReactElement =>
  flag === 1 ? <Tag color="green">社保定点</Tag> : <Tag>非社保定点</Tag>;

/** 医院编码回显兜底：编辑弹窗按库映射表单字段 → 命令对象。 */
function toCommand(key: LibraryKey, values: LibraryFormValues) {
  switch (key) {
  case 'hospitals':
    return {
      code: values.code,
      name: values.name,
      level: values.hospitalLevel,
      socialFlag: values.socialFlag,
      province: values.province,
      city: values.city,
    };
  case 'diseases':
    return { code: values.code, name: values.name, icdCategory: values.icdCategory };
  case 'drugs':
    return {
      code: values.code,
      name: values.name,
      socialFlag: values.socialFlag,
      medicalType: values.medicalType,
    };
  case 'fee-categories':
    return { code: values.code, name: values.name, sortNo: values.sortNo };
  }
}

const TAB_ITEMS: { key: LibraryKey; label: string }[] = [
  { key: 'hospitals', label: '医院库' },
  { key: 'diseases', label: '疾病库' },
  { key: 'drugs', label: '药品库' },
  { key: 'fee-categories', label: '费用类别库' },
];

/** 目录库分类 → 映射域（供「配置跨保司映射」跳转到映射页并预选域）。 */
const DOMAIN_BY_KEY: Record<LibraryKey, string> = {
  hospitals: 'HOSPITAL',
  diseases: 'DISEASE',
  drugs: 'DRUG',
  'fee-categories': 'FEE_CATEGORY',
};

export function LibraryPage(): ReactElement {
  const { message } = AntApp.useApp();
  const [form] = Form.useForm<LibraryFormValues>();

  const [activeKey, setActiveKey] = useState<LibraryKey>('hospitals');
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [rows, setRows] = useState<LibraryRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<LibraryRow | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(
    async (key: LibraryKey, kw: string, p: number, s: number) => {
      setLoading(true);
      try {
        const params = { keyword: kw || undefined, page: p, size: s };
        const res =
          key === 'hospitals'
            ? await claimConfigApi.listHospitals(params)
            : key === 'diseases'
              ? await claimConfigApi.listDiseases(params)
              : key === 'drugs'
                ? await claimConfigApi.listDrugs(params)
                : await claimConfigApi.listFeeCategories(params);
        if (res.code !== 200) {
          message.error(resolveErrorMessage(res, '目录列表加载失败'));
          return;
        }
        setRows(res.data.records);
        setTotal(toTotal(res.data));
      } catch (err) {
        message.error(resolveErrorMessage(err, '目录列表加载失败'));
      } finally {
        setLoading(false);
      }
    },
    [message],
  );

  useEffect(() => {
    void load(activeKey, keyword, page, size);
  }, [load, activeKey, keyword, page, size]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (row: LibraryRow) => {
    setEditing(row);
    form.resetFields();
    // 按库把行字段映射回统一表单字段
    if ('hospitalCode' in row) {
      form.setFieldsValue({
        code: row.hospitalCode,
        name: row.hospitalName,
        hospitalLevel: row.hospitalLevel,
        socialFlag: row.socialFlag,
        province: row.province,
        city: row.city,
      });
    } else if ('diseaseCode' in row) {
      form.setFieldsValue({
        code: row.diseaseCode,
        name: row.diseaseName,
        icdCategory: row.icdCategory,
      });
    } else if ('drugCode' in row) {
      form.setFieldsValue({
        code: row.drugCode,
        name: row.drugName,
        socialFlag: row.socialFlag,
        medicalType: row.medicalType,
      });
    } else {
      form.setFieldsValue({
        code: row.feeCode,
        name: row.feeName,
        sortNo: row.sortNo,
      });
    }
    setModalOpen(true);
  };

  const submit = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const command = toCommand(activeKey, values);
      const isNew = editing === null;
      const res =
        activeKey === 'hospitals'
          ? isNew
            ? await claimConfigApi.createHospital(command)
            : await claimConfigApi.updateHospital(editing.id, command)
          : activeKey === 'diseases'
            ? isNew
              ? await claimConfigApi.createDisease(command)
              : await claimConfigApi.updateDisease(editing.id, command)
            : activeKey === 'drugs'
              ? isNew
                ? await claimConfigApi.createDrug(command)
                : await claimConfigApi.updateDrug(editing.id, command)
              : isNew
                ? await claimConfigApi.createFeeCategory(command)
                : await claimConfigApi.updateFeeCategory(editing.id, command);
      if (res.code !== 200) {
        message.error(resolveErrorMessage(res, '目录保存失败'));
        return;
      }
      message.success('保存成功');
      setModalOpen(false);
      void load(activeKey, keyword, page, size);
    } catch (err) {
      message.error(resolveErrorMessage(err, '目录保存失败'));
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<LibraryRow> = (() => {
    switch (activeKey) {
    case 'hospitals':
      return [
        { title: '医院编码', dataIndex: 'hospitalCode' },
        { title: '医院名称', dataIndex: 'hospitalName' },
        { title: '等级', dataIndex: 'hospitalLevel', width: 100 },
        {
          title: '医保属性',
          dataIndex: 'socialFlag',
          width: 110,
          render: (_: unknown, row) => socialTag((row as HospitalView).socialFlag),
        },
        {
          title: '省市',
          width: 160,
          render: (_: unknown, row) =>
            [(row as HospitalView).province, (row as HospitalView).city]
              .filter(Boolean)
              .join(' / '),
        },
      ];
    case 'diseases':
      return [
        { title: '疾病编码', dataIndex: 'diseaseCode' },
        { title: '疾病名称', dataIndex: 'diseaseName' },
        { title: 'ICD 大类', dataIndex: 'icdCategory', width: 140 },
      ];
    case 'drugs':
      return [
        { title: '药品编码', dataIndex: 'drugCode' },
        { title: '药品名称', dataIndex: 'drugName' },
        {
          title: '医保属性',
          dataIndex: 'socialFlag',
          width: 110,
          render: (_: unknown, row) => socialTag((row as DrugView).socialFlag),
        },
        { title: '医保分类', dataIndex: 'medicalType', width: 100 },
      ];
    case 'fee-categories':
      return [
        { title: '类别编码', dataIndex: 'feeCode' },
        { title: '类别名称', dataIndex: 'feeName' },
        { title: '排序', dataIndex: 'sortNo', width: 80 },
      ];
    }
  })();

  return (
    <>
      <PageHeader
        title="基础目录库"
        description="医院 / 疾病 / 药品 / 费用类别 租户级主数据；责任配置的适用名单与费用规则从这里选编码。"
      />
      <Card>
        <Tabs
          activeKey={activeKey}
          onChange={(k) => {
            setActiveKey(k as LibraryKey);
            setPage(1);
            setKeyword('');
          }}
          items={TAB_ITEMS.map((tab) => ({
            key: tab.key,
            label: tab.label,
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                <Space wrap>
                  <Input.Search
                    allowClear
                    placeholder="编码 / 名称"
                    style={{ width: 240 }}
                    onSearch={(v) => {
                      setPage(1);
                      setKeyword(v.trim());
                    }}
                  />
                  <Button
                    icon={<ReloadOutlined />}
                    onClick={() => void load(activeKey, keyword, page, size)}
                  >
                    刷新
                  </Button>
                  <AuthButton
                    type="primary"
                    icon={<PlusOutlined />}
                    code={PERMISSIONS.POLICY_WRITE}
                    onClick={openCreate}
                  >
                    新建
                  </AuthButton>
                  <Button
                    icon={<LinkOutlined />}
                    onClick={() =>
                      navigate(`/claim/mappings?domain=${DOMAIN_BY_KEY[activeKey]}`)
                    }
                  >
                    配置跨保司映射
                  </Button>
                </Space>
                <Table<LibraryRow>
                  rowKey="id"
                  size="small"
                  loading={loading}
                  columns={[
                    ...columns,
                    {
                      title: '操作',
                      width: 90,
                      render: (_: unknown, row) => (
                        <AuthButton
                          type="link"
                          size="small"
                          icon={<EditOutlined />}
                          code={PERMISSIONS.POLICY_WRITE}
                          onClick={() => openEdit(row)}
                        >
                          编辑
                        </AuthButton>
                      ),
                    },
                  ]}
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
              </Space>
            ),
          }))}
        />
      </Card>
      <Modal
        title={editing ? '编辑目录项' : `${TAB_ITEMS.find((t) => t.key === activeKey)?.label}新建`}
        open={modalOpen}
        confirmLoading={saving}
        onOk={() => void submit()}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="code"
            label="编码"
            rules={[{ required: true, message: '请输入编码' }]}
          >
            <Input maxLength={64} placeholder="同租户唯一" />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={128} />
          </Form.Item>
          {activeKey === 'hospitals' && (
            <>
              <Form.Item name="hospitalLevel" label="医院等级">
                <Input maxLength={32} placeholder="如：三甲" />
              </Form.Item>
              <Form.Item name="socialFlag" label="医保属性" initialValue={0}>
                <Select options={SOCIAL_FLAG_OPTIONS} />
              </Form.Item>
              <Form.Item name="province" label="省份">
                <Input maxLength={32} />
              </Form.Item>
              <Form.Item name="city" label="城市">
                <Input maxLength={32} />
              </Form.Item>
            </>
          )}
          {activeKey === 'diseases' && (
            <Form.Item name="icdCategory" label="ICD 大类">
              <Input maxLength={32} placeholder="如：ICD-10 章节" />
            </Form.Item>
          )}
          {activeKey === 'drugs' && (
            <>
              <Form.Item name="socialFlag" label="医保属性" initialValue={0}>
                <Select options={SOCIAL_FLAG_OPTIONS} />
              </Form.Item>
              <Form.Item name="medicalType" label="医保分类">
                <Input maxLength={32} placeholder="如：甲类" />
              </Form.Item>
            </>
          )}
          {activeKey === 'fee-categories' && (
            <Form.Item name="sortNo" label="排序" initialValue={0}>
              <InputNumber min={0} max={9999} style={{ width: '100%' }} />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
}
