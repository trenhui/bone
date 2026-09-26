import React, { useCallback, useEffect, useState } from 'react';
import {
  Card,
  Button,
  Col,
  Modal,
  Form,
  Input,
  Select,
  Popconfirm,
  Row,
  Space,
  Statistic,
  Tag,
  Descriptions,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, RocketOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import type {
  MasterDataEntity,
  CreateMasterDataEntityReq,
  MasterDataEntityPageQry
} from '../types';
import { masterDataEntityApi } from '../services/api';
import { useMessage } from '../App';

const { Option } = Select;
const { TextArea } = Input;

const EntityManagement: React.FC = () => {
  const message = useMessage();
  const [form] = Form.useForm();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isViewModalOpen, setIsViewModalOpen] = useState(false);
  const [currentEntity, setCurrentEntity] = useState<MasterDataEntity | null>(null);
  const [isEditMode, setIsEditMode] = useState(false);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<MasterDataEntity[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchParams, setSearchParams] = useState<MasterDataEntityPageQry>({});
  const [stats, setStats] = useState({ total: 0, published: 0, draft: 0 });

  useEffect(() => {
    // 统计卡片：独立拉取（不受列表筛选/分页影响）
    void masterDataEntityApi.page({ pageNum: 1, pageSize: 200 }).then((res) => {
      if (res.code === 200) {
        const list = res.data.list;
        setStats({
          total: res.data.total,
          published: list.filter((e) => e.status === 'PUBLISHED').length,
          draft: list.filter((e) => e.status === 'DRAFT').length
        });
      }
    });
  }, [data]);

  const fetchEntities = useCallback(async () => {
    setLoading(true);
    try {
      const response = await masterDataEntityApi.page({
        ...searchParams,
        pageNum: page,
        pageSize: pageSize
      });
      if (response.code === 200) {
        setData(response.data.list);
        setTotal(response.data.total);
      } else {
        message.error(response.message);
      }
    } catch {
      message.error('获取模型列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, searchParams]);

  useEffect(() => {
    void fetchEntities();
  }, [fetchEntities]);

  // 打开创建模态框
  const handleAdd = () => {
    setIsEditMode(false);
    setCurrentEntity(null);
    form.resetFields();
    setIsModalOpen(true);
  };

  // 打开编辑模态框
  const handleEdit = (record: MasterDataEntity) => {
    setIsEditMode(true);
    setCurrentEntity(record);
    form.setFieldsValue({
      name: record.name,
      description: record.description,
      category: record.category
    });
    setIsModalOpen(true);
  };

  // 打开查看模态框
  const handleView = (record: MasterDataEntity) => {
    setCurrentEntity(record);
    setIsViewModalOpen(true);
  };

  // 删除模型
  const handleDelete = async (id: number) => {
    try {
      const response = await masterDataEntityApi.delete(id);
      if (response.code === 200) {
        message.success('删除成功');
        fetchEntities();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('删除失败');
    }
  };

  // 发布模型
  const handlePublish = async (id: number) => {
    try {
      const response = await masterDataEntityApi.publish(id);
      if (response.code === 200) {
        message.success('发布成功');
        fetchEntities();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('发布失败');
    }
  };

  // 停用模型
  const handleDisable = async (id: number) => {
    try {
      const response = await masterDataEntityApi.disable(id);
      if (response.code === 200) {
        message.success('停用成功');
        fetchEntities();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      message.error('停用失败');
    }
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      let response;
      if (isEditMode && currentEntity) {
        response = await masterDataEntityApi.update(currentEntity.id, values);
      } else {
        response = await masterDataEntityApi.create(values as CreateMasterDataEntityReq);
      }
      if (response.code === 200) {
        message.success(isEditMode ? '更新成功' : '创建成功');
        setIsModalOpen(false);
        fetchEntities();
      } else {
        message.error(response.message);
      }
    } catch (error) {
      console.error('提交失败:', error);
    }
  };

  // 状态标签
  const getStatusTag = (status: string) => {
    switch (status) {
    case 'DRAFT':
      return <Tag color="blue">草稿</Tag>;
    case 'PUBLISHED':
      return <Tag color="green">已发布</Tag>;
    default:
      return <Tag>{status}</Tag>;
    }
  };

  // 表格列定义
  const columns = [
    {
      title: '模型名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: MasterDataEntity) => (
        <a onClick={() => handleView(record)}>{text}</a>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => getStatusTag(status)
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt'
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: MasterDataEntity) => (
        <Space size="middle">
          <Button
            type="primary"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要删除吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
          {record.status === 'DRAFT' && (
            <Button
              icon={<RocketOutlined />}
              onClick={() => handlePublish(record.id)}
            >
              发布
            </Button>
          )}
          {record.status === 'PUBLISHED' && (
            <Button
              icon={<CloseCircleOutlined />}
              onClick={() => handleDisable(record.id)}
            >
              停用
            </Button>
          )}
        </Space>
      )
    }
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* 页头 */}
      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 500, color: 'rgba(0,0,0,0.88)' }}>主数据模型管理</div>
          <div style={{ marginTop: 4, color: 'rgba(0,0,0,0.55)' }}>
            定义主数据域的结构与发布状态；全流程治理（质检/订阅/漂移/反馈）请进入「域工作台」
          </div>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          创建模型
        </Button>
      </div>

      {/* 统计卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={8} sm={8} lg={4}>
          <Card styles={{ body: { padding: '16px 20px' } }}>
            <Statistic title="模型总数" value={stats.total} valueStyle={{ fontSize: 24, color: '#185FA5' }} />
          </Card>
        </Col>
        <Col xs={8} sm={8} lg={4}>
          <Card styles={{ body: { padding: '16px 20px' } }}>
            <Statistic title="已发布" value={stats.published} valueStyle={{ fontSize: 24, color: '#0F6E56' }} />
          </Card>
        </Col>
        <Col xs={8} sm={8} lg={4}>
          <Card styles={{ body: { padding: '16px 20px' } }}>
            <Statistic title="草稿" value={stats.draft} valueStyle={{ fontSize: 24, color: '#854F0B' }} />
          </Card>
        </Col>
      </Row>

      <Card>
        {/* 搜索表单 */}
        <Form
          layout="inline"
          style={{ marginBottom: 16 }}
          onFinish={(values) => {
            setSearchParams(values);
            setPage(1);
          }}
        >
          <Form.Item name="name" label="模型名称">
            <Input placeholder="请输入模型名称" />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Input placeholder="请输入分类" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select placeholder="请选择状态">
              <Option value="DRAFT">草稿</Option>
              <Option value="PUBLISHED">已发布</Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit">搜索</Button>
          </Form.Item>
          <Form.Item>
            <Button onClick={() => setSearchParams({})}>重置</Button>
          </Form.Item>
        </Form>

        {/* 模型列表 */}
        <ProTable
          options={false}
          columns={columns as ProColumns<MasterDataEntity>[]}
          dataSource={data}
          loading={loading}
          scroll={{ x: 'max-content' }}
          pagination={{
            total,
            pageSize,
            current: page,
            onChange: (current, size) => {
              setPage(current);
              setPageSize(size);
            }
          }}
        />
      </Card>

      {/* 创建/编辑模态框 */}
      <Modal
        title={isEditMode ? '编辑模型' : '创建模型'}
        open={isModalOpen}
        onOk={handleSubmit}
        onCancel={() => setIsModalOpen(false)}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="模型名称"
            rules={[{ required: true, message: '请输入模型名称' }]}
          >
            <Input placeholder="请输入模型名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={4} placeholder="请输入模型描述" />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Input placeholder="请输入分类" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 查看详情模态框 */}
      <Modal
        title="模型详情"
        open={isViewModalOpen}
        onCancel={() => setIsViewModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsViewModalOpen(false)}>关闭</Button>
        ]}
      >
        {currentEntity && (
          <Descriptions column={2}>
            <Descriptions.Item label="模型名称">{currentEntity.name}</Descriptions.Item>
            <Descriptions.Item label="分类">{currentEntity.category || '-'}</Descriptions.Item>
            <Descriptions.Item label="描述" span={2}>{currentEntity.description || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">{getStatusTag(currentEntity.status)}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{currentEntity.createdAt}</Descriptions.Item>
            <Descriptions.Item label="更新时间" span={2}>{currentEntity.updatedAt}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default EntityManagement;
