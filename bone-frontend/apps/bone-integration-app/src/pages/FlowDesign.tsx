import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Card, Button, Modal, Form, Input, message, Tabs, Select } from 'antd';
import { PlusOutlined, PlayCircleOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { Graph, Shape, type Node } from '@antv/x6';
import { Snapline } from '@antv/x6-plugin-snapline';
import { flowApi, connectorApi } from '../services/api';
import type { IntegrationFlow, CreateFlowReq, UpdateFlowReq, Connector } from '../types';

const { TextArea } = Input;
const { TabPane } = Tabs;
const { Option } = Select;

// 注册自定义节点
Shape.Rect.define({
  shape: 'custom-rect',
  width: 120,
  height: 60,
  attrs: {
    body: {
      fill: '#f5f5f5',
      stroke: '#d9d9d9',
      strokeWidth: 1,
    },
    label: {
      text: '节点',
      fill: '#333',
      fontSize: 12,
      textWrap: {
        width: -10,
        height: -10,
      },
    },
  },
});

export const FlowDesign: React.FC = () => {
  const [flows, setFlows] = useState<IntegrationFlow[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [isEdit, setIsEdit] = useState(false);
  const [currentFlow, setCurrentFlow] = useState<IntegrationFlow | null>(null);
  const [form] = Form.useForm();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [graph, setGraph] = useState<Graph | null>(null);
  const graphRef = useRef<HTMLDivElement>(null);
  const [connectors, setConnectors] = useState<Connector[]>([]);
  const [nodeConfigVisible, setNodeConfigVisible] = useState(false);
  const [editingNode, setEditingNode] = useState<Node | null>(null);
  const [nodeForm] = Form.useForm();

  // 加载连接器列表（供节点配置面板绑定）
  useEffect(() => {
    connectorApi
      .getConnectors({ pageNum: 1, pageSize: 100 })
      .then((res) => setConnectors(res.data.list ?? []))
      .catch(() => setConnectors([]));
  }, []);

  const nodeTypes = [
    { value: 'START', label: '开始' },
    { value: 'END', label: '结束' },
    { value: 'HTTP', label: 'HTTP 请求' },
    { value: 'JDBC', label: '数据库操作' },
    { value: 'FTP', label: 'FTP' },
    { value: 'MQ', label: '消息队列' },
    { value: 'TRANSFORM', label: '转换' },
    { value: 'DECISION', label: '条件分支' },
    { value: 'PARALLEL', label: '并行' },
    { value: 'SCRIPT', label: '脚本' },
    { value: 'LOG', label: '日志' },
    { value: 'WAIT', label: '等待' },
    { value: 'LOOP', label: '循环' },
  ];

  const fetchFlows = useCallback(async () => {
    setLoading(true);
    try {
      const response = await flowApi.getFlows({ pageNum: page, pageSize });
      setFlows(response.data.list);
      setTotal(response.data.total);
    } catch {
      message.error('获取流程列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    void fetchFlows();
  }, [fetchFlows]);

  useEffect(() => {
    if (graphRef.current) {
      const newGraph = new Graph({
        container: graphRef.current,
        grid: { size: 10, visible: true },
        connecting: {
          router: { name: 'manhattan' },
          connector: { name: 'rounded' },
          anchor: { name: 'center' },
          allowBlank: false,
          snap: true,
        },
      });

      // 添加网格和对齐线插件
      newGraph.use(new Snapline());

      // 双击节点打开配置面板
      newGraph.on('node:dblclick', ({ node }) => {
        openNodeConfig(node);
      });

      setGraph(newGraph);

      return () => {
        newGraph.dispose();
      };
    }
  }, []);

  const handleAdd = () => {
    setIsEdit(false);
    setCurrentFlow(null);
    form.resetFields();
    setModalVisible(true);
  };

  // ===== 节点配置面板 =====

  const openNodeConfig = (node: Node) => {
    const data = (node.getData() ?? {}) as {
      id?: number;
      type?: string;
      config?: Record<string, unknown>;
    };
    setEditingNode(node);
    nodeForm.setFieldsValue({
      name: String(node.getAttrByPath('label/text') ?? ''),
      type: data.type ?? 'HTTP',
      connectorId: data.config?.connectorId ?? undefined,
      configJson: data.config ? JSON.stringify(data.config, null, 2) : '{}',
    });
    setNodeConfigVisible(true);
  };

  const saveNodeConfig = async () => {
    try {
      const values = await nodeForm.validateFields();
      if (!editingNode) return;
      const data = (editingNode.getData() ?? {}) as {
        id?: number;
        type?: string;
        config?: Record<string, unknown>;
      };
      const config: Record<string, unknown> = { ...(data.config ?? {}) };
      if (values.connectorId) {
        config.connectorId = values.connectorId;
      }
      try {
        const parsed = JSON.parse(values.configJson || '{}');
        Object.assign(config, parsed);
      } catch {
        // 忽略非法 JSON，保留已填写的字段
      }
      editingNode.setData({ ...data, type: values.type, config });
      editingNode.setAttrByPath('label/text', values.name);
      message.success('节点配置已保存');
      setNodeConfigVisible(false);
    } catch {
      // 校验未通过，保持打开
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (!graph) return;
    const nodeType = e.dataTransfer.getData('nodeType') || 'HTTP';
    const nodeLabel = e.dataTransfer.getData('nodeLabel') || nodeType;
    const point = graph.clientToLocal({ x: e.clientX, y: e.clientY });
    const added = graph.addNode({
      shape: 'custom-rect',
      x: point.x - 60,
      y: point.y - 30,
      width: 120,
      height: 60,
      attrs: {
        label: { text: nodeLabel },
      },
      data: { id: undefined, type: nodeType, config: {} },
    });
    openNodeConfig(added);
  };

  const handleEdit = async (flow: IntegrationFlow) => {
    setIsEdit(true);
    setCurrentFlow(flow);
    form.setFieldsValue({
      name: flow.name,
      description: flow.description,
    });
    
    // 加载流程到画布
    if (graph) {
      graph.clearCells();
      
      // 添加节点
      const nodeMap = new Map<number, string>();
      flow.nodes.forEach(node => {
        const x6Node = graph.addNode({
          shape: 'custom-rect',
          x: node.positionX,
          y: node.positionY,
          width: 120,
          height: 60,
          attrs: {
            label: { text: node.name },
          },
          data: { id: node.id, type: node.type, config: node.config },
        });
        nodeMap.set(node.id, x6Node.id);
      });
      
      // 添加连接
      flow.connections.forEach(connection => {
        const sourceId = nodeMap.get(connection.sourceNodeId);
        const targetId = nodeMap.get(connection.targetNodeId);
        if (sourceId && targetId) {
          graph.addEdge({
            source: sourceId,
            target: targetId,
            attrs: {
              label: { text: connection.condition || '' },
            },
            data: { id: connection.id, condition: connection.condition },
          });
        }
      });
    }
    
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await flowApi.deleteFlow(id);
      message.success('删除成功');
      fetchFlows();
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleTest = async (id: number) => {
    try {
      const response = await flowApi.testFlow(id, { test: 'data' });
      if (response.data.success) {
        message.success('测试成功');
      } else {
        message.error(`测试失败: ${response.data.error ?? '未知错误'}`);
      }
    } catch (error) {
      message.error('测试失败');
    }
  };

  const handleActivate = async (id: number) => {
    try {
      await flowApi.activateFlow(id);
      message.success('激活成功');
      fetchFlows();
    } catch (error) {
      message.error('激活失败');
    }
  };

  const handleDeactivate = async (id: number) => {
    try {
      await flowApi.deactivateFlow(id);
      message.success('停用成功');
      fetchFlows();
    } catch (error) {
      message.error('停用失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      
      if (!graph) return;
      
      // 从画布中获取节点和连接
      const cells = graph.getCells();
      const nodes = cells
        .filter((cell) => cell.isNode())
        .map((cell) => {
          const node = cell as Node;
          const pos = node.getPosition();
          const data = (node.getData() ?? {}) as {
            id?: number;
            type?: string;
            config?: Record<string, unknown>;
          };
          return {
            id: data.id,
            name: String(node.getAttrByPath('label/text') ?? ''),
            type: data.type ?? 'HTTP',
            config: data.config ?? {},
            positionX: pos.x,
            positionY: pos.y,
          };
        });
      
      const connections = cells
        .filter(cell => cell.isEdge())
        .map(edge => {
          const sourceNode = graph.getCellById(edge.getSourceCellId());
          const targetNode = graph.getCellById(edge.getTargetCellId());
          return {
            id: edge.data.id,
            sourceNodeId: sourceNode?.data.id,
            targetNodeId: targetNode?.data.id,
            condition: edge.data.condition || '',
          };
        });
      
      if (isEdit && currentFlow) {
        const updateData: UpdateFlowReq = {
          name: values.name,
          description: values.description,
          nodes: nodes.map((n) => ({
            id: n.id,
            name: n.name,
            type: n.type,
            config: n.config,
            positionX: n.positionX,
            positionY: n.positionY,
          })),
          connections,
        };
        await flowApi.updateFlow(currentFlow.id, updateData);
        message.success('更新成功');
      } else {
        const createData: CreateFlowReq = {
          name: values.name,
          description: values.description,
          nodes: nodes.map(({ name, type, config, positionX, positionY }) => ({
            name,
            type,
            config,
            positionX,
            positionY,
          })),
          connections: connections.map(({ sourceNodeId, targetNodeId, condition }) => ({
            sourceNodeId,
            targetNodeId,
            condition,
          })),
        };
        await flowApi.createFlow(createData);
        message.success('创建成功');
      }
      
      setModalVisible(false);
      fetchFlows();
    } catch (error) {
      message.error('操作失败');
    }
  };

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <span style={{ color: status === 'ACTIVE' ? '#52c41a' : '#ff4d4f' }}>
          {status === 'ACTIVE' ? '激活' : '停用'}
        </span>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: IntegrationFlow) => (
        <div>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
            style={{ marginRight: 8 }}
          >
            编辑
          </Button>
          <Button
            type="link"
            icon={<PlayCircleOutlined />}
            onClick={() => handleTest(record.id)}
            style={{ marginRight: 8 }}
          >
            测试
          </Button>
          {record.status === 'ACTIVE' ? (
            <Button
              type="link"
              danger
              onClick={() => handleDeactivate(record.id)}
              style={{ marginRight: 8 }}
            >
              停用
            </Button>
          ) : (
            <Button
              type="link"
              onClick={() => handleActivate(record.id)}
              style={{ marginRight: 8 }}
            >
              激活
            </Button>
          )}
          <Button
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.id)}
          >
            删除
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <Card
        title="流程设计"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新建流程
          </Button>
        }
      >
        <Table
          columns={columns}
          dataSource={flows}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            onChange: (page) => setPage(page),
            onShowSizeChange: (_, size) => setPageSize(size),
          }}
        />
      </Card>

      <Modal
        title={isEdit ? '编辑流程' : '新建流程'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={1000}
        height={600}
      >
        <Tabs defaultActiveKey="design">
          <TabPane tab="流程设计" key="design">
            <div style={{ display: 'flex', height: 500 }}>
              {/* 节点库 */}
              <div style={{ width: 150, padding: 10, borderRight: '1px solid #e8e8e8' }}>
                <h4 style={{ marginBottom: 16 }}>节点库</h4>
                {nodeTypes.map(type => (
                  <div
                    key={type.value}
                    style={{
                      padding: 8,
                      marginBottom: 8,
                      background: '#f0f2f5',
                      borderRadius: 4,
                      textAlign: 'center',
                      cursor: 'move',
                    }}
                    draggable
                    onDragStart={(e) => {
                      e.dataTransfer.setData('nodeType', type.value);
                      e.dataTransfer.setData('nodeLabel', type.label);
                    }}
                  >
                    {type.label}
                  </div>
                ))}
              </div>
              {/* 画布 */}
              <div
                style={{ flex: 1, position: 'relative' }}
                onDrop={handleDrop}
                onDragOver={(e) => e.preventDefault()}
              >
                <div
                  ref={graphRef}
                  style={{ width: '100%', height: '100%', background: '#fff' }}
                />
              </div>
            </div>
          </TabPane>
          <TabPane tab="流程信息" key="info">
            <Form form={form} layout="vertical">
              <Form.Item
                name="name"
                label="流程名称"
                rules={[{ required: true, message: '请输入流程名称' }]}
              >
                <Input placeholder="请输入流程名称" />
              </Form.Item>
              <Form.Item
                name="description"
                label="流程描述"
              >
                <TextArea rows={4} placeholder="请输入流程描述" />
              </Form.Item>
            </Form>
          </TabPane>
        </Tabs>
      </Modal>

      {/* 节点配置面板 */}
      <Modal
        title="节点配置"
        open={nodeConfigVisible}
        onOk={saveNodeConfig}
        onCancel={() => setNodeConfigVisible(false)}
        width={480}
      >
        <Form form={nodeForm} layout="vertical">
          <Form.Item name="name" label="节点名称" rules={[{ required: true, message: '请输入节点名称' }]}>
            <Input placeholder="请输入节点名称" />
          </Form.Item>
          <Form.Item name="type" label="节点类型" rules={[{ required: true, message: '请选择节点类型' }]}>
            <Select placeholder="请选择节点类型">
              {nodeTypes.map((t) => (
                <Option key={t.value} value={t.value}>
                  {t.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="connectorId" label="绑定连接器（可选）">
            <Select placeholder="请选择要绑定的连接器" allowClear>
              {connectors.map((c) => (
                <Option key={c.id} value={c.id}>
                  {c.name}（{c.type}）
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="configJson" label="节点参数（JSON）">
            <TextArea rows={6} placeholder='{"url": "https://example.com", "method": "POST"}' />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

// 修复 Table 组件的导入
import { Table } from 'antd';