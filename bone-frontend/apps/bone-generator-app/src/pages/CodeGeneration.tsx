import React, { useEffect, useState } from 'react';
import {
  Form,
  Select,
  Input,
  Checkbox,
  Button,
  message,
  Card,
  Typography,
  Table,
  Modal,
  Space,
  Progress,
} from 'antd';
import { ReloadOutlined, DownloadOutlined, PlusOutlined } from '@ant-design/icons';
import {
  codeGenerationApi,
  dataSourceApi,
  generationTaskApi,
  metadataEntitySnapshotApi,
  pageRecords,
  tableMetadataApi,
  templateApi,
} from '../services/api';
import { DatabaseTable } from '../services/types';
import { useGeneratorStore } from '../store';

const { Title, Text } = Typography;
const { Option } = Select;

const CodeGeneration: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [loadingSyncedTables, setLoadingSyncedTables] = useState(false);
  const [loadingGenerate, setLoadingGenerate] = useState(false);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [resultModalVisible, setResultModalVisible] = useState(false);
  const [syncModalVisible, setSyncModalVisible] = useState(false);
  const [configModalVisible, setConfigModalVisible] = useState(false);
  const [templates, setTemplates] = useState<any[]>([]);
  const [taskId, setTaskId] = useState<string>('');
  const [generateProgress, setGenerateProgress] = useState(0);
  const [syncForm] = Form.useForm();
  const [generateForm] = Form.useForm();
  const [dataSourceTables, setDataSourceTables] = useState<any[]>([]);

  const {
    dataSources,
    setDataSources,
    setLoadingDataSources,
    tables: syncedTables,
    setTables: setSyncedTables,
    selectedTables,
    setSelectedTables,
    activeDataSourceId,
    setActiveDataSourceId,
    metadataSource,
    setMetadataSource,
  } = useGeneratorStore();

  // 加载数据源列表
  const loadDataSources = async () => {
    try {
      setLoadingDataSources(true);
      const response = await dataSourceApi.getList({ page: 1, size: 100 });
      setDataSources(pageRecords(response.data.data));
    } catch (error) {
      message.error('加载数据源失败');
      console.error('加载数据源失败:', error);
    } finally {
      setLoadingDataSources(false);
    }
  };

  const loadSyncedTables = async (dataSourceId?: string) => {
    if (metadataSource === 'CATALOG_SNAPSHOT') {
      return;
    }
    const dsId = dataSourceId ?? activeDataSourceId;
    if (!dsId) {
      setSyncedTables([]);
      return;
    }
    try {
      setLoadingSyncedTables(true);
      const response = await dataSourceApi.listSyncedTables(dsId);
      const tables = (response.data.data ?? []) as DatabaseTable[];
      setSyncedTables(tables);
    } catch (error) {
      message.error('加载已同步表失败');
      console.error('加载已同步表失败:', error);
      setSyncedTables([]);
    } finally {
      setLoadingSyncedTables(false);
    }
  };

  // 加载模板列表
  const loadTemplates = async () => {
    try {
      setLoadingTemplates(true);
      const response = await templateApi.getList({ page: 1, size: 100 });
      setTemplates(pageRecords(response.data.data));
    } catch (error) {
      message.error('加载模板失败');
      console.error('加载模板失败:', error);
    } finally {
      setLoadingTemplates(false);
    }
  };

  // 组件挂载时加载数据
  useEffect(() => {
    loadDataSources();
    loadTemplates();
  }, []);

  useEffect(() => {
    if (metadataSource === 'PHYSICAL_DB' && activeDataSourceId) {
      void loadSyncedTables(activeDataSourceId);
    }
  }, [activeDataSourceId, metadataSource]);

  // 处理表选择
  const handleTableSelect = (selectedKeys: React.Key[]) => {
    const selectedTableNames = selectedKeys.map(key => key.toString());
    setSelectedTables(selectedTableNames);
  };

  // 打开同步表结构模态框
  const handleOpenSyncModal = () => {
    syncForm.resetFields();
    setSyncModalVisible(true);
  };

  // 同步表结构
  const handleSyncTables = async () => {
    try {
      // 先获取表单值，避免直接操作可能有循环引用的数据
      const values = await syncForm.validateFields();
      // 创建新对象，确保没有循环引用
      const syncData = {
        dataSourceId: values.dataSourceId,
        tableNames: values.tableNames ? [...values.tableNames] : []
      };
      
      if (!syncData.tableNames || syncData.tableNames.length === 0) {
        message.error('请选择要同步的表');
        return;
      }
      
      setLoading(true);
      if (metadataSource === 'CATALOG_SNAPSHOT') {
        const picked = dataSourceTables.filter((t) =>
          syncData.tableNames.includes(t.tableName),
        );
        setSyncedTables(
          picked.map((t) => ({
            tableName: t.tableName,
            tableComment: t.tableComment ?? '',
            columns: [],
          })),
        );
        setMetadataSource('CATALOG_SNAPSHOT');
        message.success('已选用元数据目录实体');
      } else {
        await tableMetadataApi.sync(syncData);
        message.success('表结构同步成功');
        setActiveDataSourceId(syncData.dataSourceId);
        await loadSyncedTables(syncData.dataSourceId);
      }
      setSyncModalVisible(false);
    } catch (error) {
      message.error('同步表结构失败');
      console.error('同步表结构失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 加载数据源的表列表
  const handleLoadDataSourceTables = async (dataSourceId: string) => {
    try {
      const response = await tableMetadataApi.getDataSourceTables(dataSourceId);
      return response.data.data;
    } catch (error) {
      message.error('加载表列表失败');
      console.error('加载表列表失败:', error);
      return [];
    }
  };

  const handleLoadCatalogEntities = async () => {
    try {
      setLoading(true);
      const response = await metadataEntitySnapshotApi.list({
        page: 1,
        size: 500,
        tenantId: 0,
      });
      const page = response.data.data;
      const tables: DatabaseTable[] = pageRecords(page);
      setDataSourceTables(
        tables.map((t) => ({ tableName: t.tableName, tableComment: t.tableComment })),
      );
      syncForm.setFieldValue('tableNames', []);
    } catch (error) {
      message.error('加载元数据目录失败');
      console.error('加载元数据目录失败:', error);
      setDataSourceTables([]);
    } finally {
      setLoading(false);
    }
  };

  // 打开生成配置模态框
  const handleOpenConfigModal = () => {
    if (selectedTables.length === 0) {
      message.error('请选择要生成代码的表');
      return;
    }
    generateForm.resetFields();
    setConfigModalVisible(true);
  };

  // 生成代码
  const handleGenerateCode = async () => {
    try {
      const values = await generateForm.validateFields();
      setLoadingGenerate(true);
      
      setGenerateProgress(0);

      if (metadataSource === 'CATALOG_SNAPSHOT') {
        const templateId = Array.isArray(values.templateIds)
          ? String(values.templateIds[0])
          : String(values.templateIds);
        const syncRes = await generationTaskApi.create({
          templateId,
          name: values.projectName,
          basePackage: values.basePackage,
          moduleName: values.moduleName,
          metadataSource: 'CATALOG_SNAPSHOT',
          tenantId: 0,
          entityCodes: selectedTables,
          tableNames: selectedTables,
          includeTests: values.includeTests,
          includeDocumentation: values.includeDocumentation,
        });
        const body = syncRes.data?.data as { generationId?: string; status?: string; message?: string };
        setTaskId(body?.generationId ?? '');
        message.success(body?.message ?? '代码生成完成');
      } else {
        const request = {
          projectName: values.projectName,
          basePackage: values.basePackage,
          moduleName: values.moduleName,
          dataSourceId: values.dataSourceId,
          tableNames: selectedTables,
          templateIds: values.templateIds,
          genConfig: JSON.stringify({
            includeTests: values.includeTests,
            includeDocumentation: values.includeDocumentation,
          }),
        };
        const response = await codeGenerationApi.generate(request, {
          onProgress: setGenerateProgress,
        });
        const newTaskId = response.data.data as string;
        setTaskId(newTaskId);
        message.success('代码生成任务已提交');
      }
      setConfigModalVisible(false);
      setResultModalVisible(true);
    } catch (error) {
      message.error('代码生成失败');
      console.error('代码生成失败:', error);
    } finally {
      setLoadingGenerate(false);
    }
  };

  // 下载生成的代码
  const handleDownloadCode = async () => {
    if (taskId) {
      try {
        const response = await codeGenerationApi.downloadCode(taskId);
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `generated-code-${taskId}.zip`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
      } catch (error) {
        message.error('下载代码失败');
        console.error('下载代码失败:', error);
      }
    }
  };

  // 表格列定义
  const tableColumns = [
    {
      title: '表名',
      dataIndex: 'tableName',
      key: 'tableName',
    },
    {
      title: '注释',
      dataIndex: 'tableComment',
      key: 'tableComment',
    },
    {
      title: '列数',
      dataIndex: 'columns',
      key: 'columns',
      render: (columns: any[]) => columns.length,
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Card>
        <Title level={4}>代码生成</Title>
        
        <div style={{ marginBottom: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text strong>
            {metadataSource === 'CATALOG_SNAPSHOT' ? '已选元数据实体' : '已同步表列表'}
          </Text>
          {metadataSource === 'PHYSICAL_DB' && (
            <Select
              placeholder="选择数据源以加载已同步表"
              style={{ minWidth: 240 }}
              value={activeDataSourceId || undefined}
              onChange={(id) => {
                setActiveDataSourceId(id);
                setSelectedTables([]);
              }}
              options={dataSources.map((ds) => ({
                value: ds.id,
                label: `${ds.name} (${ds.type})`,
              }))}
            />
          )}
          <Button
            type="primary"
            icon={<ReloadOutlined />}
            onClick={() => loadSyncedTables()}
            loading={loadingSyncedTables}
            disabled={metadataSource === 'PHYSICAL_DB' && !activeDataSourceId}
          >
            刷新列表
          </Button>
        </div>
        
        {syncedTables.length > 0 ? (
          <Table
            columns={tableColumns}
            dataSource={syncedTables}
            rowKey="tableName"
            pagination={false}
            rowSelection={{
              type: 'checkbox',
              selectedRowKeys: selectedTables,
              onChange: handleTableSelect,
            }}
            style={{ marginBottom: '24px' }}
          />
        ) : (
          <div style={{ textAlign: 'center', padding: '40px', marginBottom: '24px', border: '1px dashed #d9d9d9' }}>
            <Text>暂无已同步的表</Text>
          </div>
        )}
        
        <Space style={{ marginBottom: '24px' }}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleOpenSyncModal}
          >
            同步表结构
          </Button>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            onClick={handleOpenConfigModal}
            disabled={selectedTables.length === 0}
          >
            生成代码
          </Button>
        </Space>
      </Card>

      {/* 同步表结构模态框 */}
      <Modal
        title="同步表结构"
        open={syncModalVisible}
        onCancel={() => setSyncModalVisible(false)}
        onOk={handleSyncTables}
        okText="同步"
        cancelText="取消"
        width={800}
        confirmLoading={loading}
      >
        <Form
          form={syncForm}
          layout="vertical"
          requiredMark={false}
        >
          <Form.Item label="元数据来源">
            <Select
              value={metadataSource}
              onChange={(v: 'PHYSICAL_DB' | 'CATALOG_SNAPSHOT') => {
                setMetadataSource(v);
                setDataSourceTables([]);
                setSyncedTables([]);
                setSelectedTables([]);
                syncForm.setFieldValue('tableNames', []);
                if (v === 'CATALOG_SNAPSHOT') {
                  void handleLoadCatalogEntities();
                }
              }}
              options={[
                { value: 'PHYSICAL_DB', label: '物理数据源' },
                { value: 'CATALOG_SNAPSHOT', label: '元数据目录（已发布 meta_*）' },
              ]}
            />
          </Form.Item>

          <Form.Item
            name="dataSourceId"
            label="数据源"
            rules={[{ required: metadataSource === 'PHYSICAL_DB', message: '请选择数据源' }]}
          >
            <Select
              placeholder="请选择数据源"
              showSearch
              optionFilterProp="children"
              disabled={metadataSource === 'CATALOG_SNAPSHOT'}
              onChange={async (dataSourceId) => {
                if (metadataSource !== 'PHYSICAL_DB' || !dataSourceId) {
                  setDataSourceTables([]);
                  return;
                }
                if (dataSourceId) {
                  try {
                    setLoading(true);
                    const tables = await handleLoadDataSourceTables(dataSourceId);
                    // 只保留必要字段，彻底消除循环引用
                    const processedTables = (tables as DatabaseTable[]).map((table) => ({
                      tableName: table.tableName,
                      tableComment: table.tableComment,
                    }));
                    setDataSourceTables(processedTables);
                    syncForm.setFieldValue('tableNames', []);
                  } catch (error) {
                    console.error('加载表列表失败:', error);
                    setDataSourceTables([]);
                  } finally {
                    setLoading(false);
                  }
                } else {
                  setDataSourceTables([]);
                }
              }}
            >
              {dataSources && dataSources.map((dataSource) => (
                <Option key={dataSource.id} value={dataSource.id}>
                  {dataSource.name} ({dataSource.type})
                </Option>
              ))}
            </Select>
          </Form.Item>
          
          <Form.Item
            name="tableNames"
            label="选择表"
            rules={[{ required: true, message: '请选择要同步的表' }]}
          >
            <Select
              mode="multiple"
              placeholder="请选择要同步的表"
              showSearch
              optionFilterProp="children"
              loading={loading}
              options={dataSourceTables.map(table => {
                // 创建全新的纯数据对象，彻底避免循环引用
                const tableName = table.tableName || '';
                const tableComment = table.tableComment || '';
                return {
                  value: tableName,
                  label: `${tableName}${tableComment ? ` (${tableComment})` : ''}`
                };
              })}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 生成配置模态框 */}
      <Modal
        title="生成配置"
        open={configModalVisible}
        onCancel={() => setConfigModalVisible(false)}
        onOk={handleGenerateCode}
        okText="生成"
        cancelText="取消"
        width={800}
        confirmLoading={loadingGenerate}
      >
        {loadingGenerate && (
          <Progress percent={generateProgress} status="active" style={{ marginBottom: 16 }} />
        )}
        <Form
          form={generateForm}
          layout="vertical"
          requiredMark={false}
          initialValues={{
            basePackage: 'com.example',
            moduleName: 'demo',
            includeTests: true,
            includeDocumentation: true,
          }}
        >
          {metadataSource === 'PHYSICAL_DB' && (
            <Form.Item
              name="dataSourceId"
              label="数据源"
              rules={[{ required: true, message: '请选择数据源' }]}
              initialValue={activeDataSourceId || undefined}
            >
              <Select placeholder="请选择数据源" showSearch optionFilterProp="children">
                {dataSources?.map((ds) => (
                  <Option key={ds.id} value={ds.id}>
                    {ds.name} ({ds.type})
                  </Option>
                ))}
              </Select>
            </Form.Item>
          )}

          <Form.Item
            name="projectName"
            label="项目名称"
            rules={[{ required: true, message: '请输入项目名称' }]}
          >
            <Input placeholder="请输入项目名称" />
          </Form.Item>
          
          <Form.Item
            name="basePackage"
            label="基础包路径"
            rules={[{ required: true, message: '请输入基础包路径' }]}
          >
            <Input placeholder="请输入基础包路径，如 com.example" />
          </Form.Item>
          
          <Form.Item
            name="moduleName"
            label="模块名称"
            rules={[{ required: true, message: '请输入模块名称' }]}
          >
            <Input placeholder="请输入模块名称，如 demo" />
          </Form.Item>
          
          <Form.Item
            name="templateIds"
            label="模板"
            rules={[{ required: true, message: '请选择模板' }]}
          >
            <Select 
              placeholder="请选择模板" 
              mode="multiple"
              loading={loadingTemplates}
            >
              {templates && templates.map((template) => (
                <Option key={template.id} value={template.id}>
                  {template.name} ({template.type})
                </Option>
              ))}
            </Select>
          </Form.Item>
          
          <Form.Item
            name="includeTests"
            valuePropName="checked"
          >
            <Checkbox>包含测试代码</Checkbox>
          </Form.Item>
          
          <Form.Item
            name="includeDocumentation"
            valuePropName="checked"
          >
            <Checkbox>包含文档</Checkbox>
          </Form.Item>
        </Form>
      </Modal>

      {/* 生成结果模态框 */}
      <Modal
        title="代码生成结果"
        open={resultModalVisible}
        onCancel={() => setResultModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setResultModalVisible(false)}>
            关闭
          </Button>,
          <Button
            key="download"
            type="primary"
            icon={<DownloadOutlined />}
            onClick={handleDownloadCode}
          >
            下载代码
          </Button>,
        ]}
        width={800}
      >
        <div>
          <div style={{ marginBottom: '16px' }}>
            <Text strong>任务 ID：</Text>
            <Text>{taskId}</Text>
          </div>
          <div style={{ marginBottom: '16px' }}>
            <Text strong>状态：</Text>
            <Text style={{ color: 'green' }}>任务已创建，正在处理中</Text>
          </div>
          <div style={{ marginBottom: '16px' }}>
            <Text strong>提示：</Text>
            <Text>代码生成正在后台执行，请稍候点击下载按钮获取生成的代码。</Text>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default CodeGeneration;