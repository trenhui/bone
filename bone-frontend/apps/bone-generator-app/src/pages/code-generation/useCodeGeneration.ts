import { useCallback, useEffect, useState, type Key } from 'react';
import { Form, message, type TableColumnsType } from 'antd';
import {
  codeGenerationApi,
  dataSourceApi,
  metadataEntitySnapshotApi,
  pageRecords,
  tableMetadataApi,
  templateApi,
} from '../../services/api';
import { DatabaseTable } from '../../services/types';
import { useGeneratorStore } from '../../store';
import { useDataSources } from '../../hooks/useDataSources';

export type MetadataSource = 'PHYSICAL_DB' | 'CATALOG_SNAPSHOT';

/**
 * 代码生成页的聚合状态与行为（从 CodeGeneration.tsx 抽离，便于拆组件）。
 * 保持与原实现完全一致的副作用与分支逻辑。
 */
// 返回类型由下方 UseCodeGeneration = ReturnType<...> 派生，避免循环标注
// eslint-disable-next-line @typescript-eslint/explicit-module-boundary-types
export function useCodeGeneration() {
  const [loading, setLoading] = useState(false);
  const [loadingSyncedTables, setLoadingSyncedTables] = useState(false);
  const [loadingGenerate, setLoadingGenerate] = useState(false);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [resultModalVisible, setResultModalVisible] = useState(false);
  const [syncModalVisible, setSyncModalVisible] = useState(false);
  const [configModalVisible, setConfigModalVisible] = useState(false);
  const [templates, setTemplates] = useState<Array<Record<string, unknown>>>([]);
  const [taskId, setTaskId] = useState<string>('');
  const [generateProgress, setGenerateProgress] = useState(0);
  const [syncForm] = Form.useForm();
  const [generateForm] = Form.useForm();
  const [dataSourceTables, setDataSourceTables] = useState<DatabaseTable[]>([]);

  const {
    tables: syncedTables,
    setTables: setSyncedTables,
    selectedTables,
    setSelectedTables,
    activeDataSourceId,
    setActiveDataSourceId,
    metadataSource,
    setMetadataSource,
  } = useGeneratorStore();
  const { data: dataSources = [] } = useDataSources();

  const loadSyncedTables = useCallback(
    async (dataSourceId?: string) => {
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
        setSyncedTables(response.data ?? []);
      } catch (error) {
        message.error('加载已同步表失败');
        console.error('加载已同步表失败:', error);
        setSyncedTables([]);
      } finally {
        setLoadingSyncedTables(false);
      }
    },
    [metadataSource, activeDataSourceId, setSyncedTables],
  );

  const loadTemplates = useCallback(async () => {
    try {
      setLoadingTemplates(true);
      const response = await templateApi.getList({ page: 1, size: 100 });
      setTemplates(pageRecords(response.data));
    } catch (error) {
      message.error('加载模板失败');
      console.error('加载模板失败:', error);
    } finally {
      setLoadingTemplates(false);
    }
  }, []);

  useEffect(() => {
    void loadTemplates();
  }, [loadTemplates]);

  useEffect(() => {
    if (metadataSource === 'PHYSICAL_DB' && activeDataSourceId) {
      void loadSyncedTables(activeDataSourceId);
    }
  }, [activeDataSourceId, metadataSource, loadSyncedTables]);

  const handleTableSelect = (selectedKeys: Key[]) => {
    setSelectedTables(selectedKeys.map((key) => key.toString()));
  };

  const openSyncModal = () => {
    syncForm.resetFields();
    setSyncModalVisible(true);
  };

  const handleLoadDataSourceTables = async (dataSourceId: string): Promise<DatabaseTable[]> => {
    try {
      const response = await tableMetadataApi.getDataSourceTables(dataSourceId);
      return response.data;
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
      const page = response.data;
      const records = pageRecords(page) as Array<{ tableName?: string; tableComment?: string }>;
      setDataSourceTables(
        records.map((t) => ({
          tableName: String(t.tableName ?? ''),
          tableComment: String(t.tableComment ?? ''),
        })),
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

  const handleMetadataSourceChange = (value: MetadataSource) => {
    setMetadataSource(value);
    setDataSourceTables([]);
    setSyncedTables([]);
    setSelectedTables([]);
    syncForm.setFieldValue('tableNames', []);
    if (value === 'CATALOG_SNAPSHOT') {
      void handleLoadCatalogEntities();
    }
  };

  const handleDataSourceChange = async (dataSourceId: string) => {
    if (metadataSource !== 'PHYSICAL_DB' || !dataSourceId) {
      setDataSourceTables([]);
      return;
    }
    try {
      setLoading(true);
      const tables = (await handleLoadDataSourceTables(dataSourceId)) ?? [];
      const processedTables: DatabaseTable[] = tables.map((table) => ({
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
  };

  const syncTables = async () => {
    try {
      const values = await syncForm.validateFields();
      const syncData = {
        dataSourceId: values.dataSourceId,
        tableNames: values.tableNames ? [...values.tableNames] : [],
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

  const openConfigModal = () => {
    if (selectedTables.length === 0) {
      message.error('请选择要生成代码的表');
      return;
    }
    generateForm.resetFields();
    setConfigModalVisible(true);
  };

  const generateCode = async () => {
    try {
      const values = await generateForm.validateFields();
      setLoadingGenerate(true);
      setGenerateProgress(0);

      // 统一入口：PHYSICAL_DB 与 CATALOG_SNAPSHOT 都走 /code-generation，
      // 后端按 metadataSource 分流（catalog 复用 CodeGeneratorService 引擎）。
      const request = {
        projectName: values.projectName,
        basePackage: values.basePackage,
        moduleName: values.moduleName,
        dataSourceId: metadataSource === 'PHYSICAL_DB' ? values.dataSourceId : undefined,
        tableNames: selectedTables,
        templateIds: values.templateIds,
        genConfig: JSON.stringify({
          includeTests: values.includeTests,
          includeDocumentation: values.includeDocumentation,
        }),
        metadataSource,
        entityCodes: selectedTables,
        tenantId: 0,
      };

      const response = await codeGenerationApi.generate(request, {
        onProgress: setGenerateProgress,
      });
      const data = response.data as
        | string
        | { taskId?: string; operationId?: string }
        | undefined;
      const newTaskId =
        typeof data === 'string' ? data : data?.taskId ?? data?.operationId ?? '';
      setTaskId(newTaskId);
      message.success(
        metadataSource === 'CATALOG_SNAPSHOT'
          ? '代码生成完成'
          : '代码生成任务已提交',
      );
      setConfigModalVisible(false);
      setResultModalVisible(true);
    } catch (error) {
      message.error('代码生成失败');
      console.error('代码生成失败:', error);
    } finally {
      setLoadingGenerate(false);
    }
  };

  const downloadCode = async () => {
    if (!taskId) {
      return;
    }
    try {
      const response = await codeGenerationApi.downloadCode(taskId);
      const url = window.URL.createObjectURL(new Blob([response as unknown as BlobPart]));
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
  };

  const selectDataSource = (id: string) => {
    setActiveDataSourceId(id);
    setSelectedTables([]);
  };

  const refreshTables = () => {
    void loadSyncedTables();
  };

  const tableColumns: TableColumnsType<DatabaseTable> = [
    { title: '表名', dataIndex: 'tableName', key: 'tableName' },
    { title: '注释', dataIndex: 'tableComment', key: 'tableComment' },
    {
      title: '列数',
      dataIndex: 'columns',
      key: 'columns',
      render: (value) => (value as unknown[]).length,
    },
  ];

  return {
    // 状态
    loading,
    loadingSyncedTables,
    loadingGenerate,
    loadingTemplates,
    templates,
    taskId,
    generateProgress,
    dataSourceTables,
    syncedTables,
    selectedTables,
    activeDataSourceId,
    metadataSource,
    dataSources,
    syncModalVisible,
    configModalVisible,
    resultModalVisible,
    syncForm,
    generateForm,
    tableColumns,
    // 行为
    refreshTables,
    selectDataSource,
    handleTableSelect,
    openSyncModal,
    syncTables,
    handleMetadataSourceChange,
    handleDataSourceChange,
    openConfigModal,
    generateCode,
    downloadCode,
    closeSyncModal: () => setSyncModalVisible(false),
    closeConfigModal: () => setConfigModalVisible(false),
    closeResultModal: () => setResultModalVisible(false),
  };
}

export type UseCodeGeneration = ReturnType<typeof useCodeGeneration>;
