import { Card, Typography, Select, Button, Table, Space } from 'antd';
import { ReloadOutlined, PlusOutlined, DownloadOutlined } from '@ant-design/icons';
import type { UseCodeGeneration } from './useCodeGeneration';

const { Title, Text } = Typography;

export default function MainPanel(props: UseCodeGeneration): JSX.Element {
  const {
    dataSources,
    syncedTables,
    selectedTables,
    activeDataSourceId,
    metadataSource,
    loadingSyncedTables,
    refreshTables,
    selectDataSource,
    handleTableSelect,
    openSyncModal,
    openConfigModal,
    tableColumns,
  } = props;

  const isPhysical = metadataSource === 'PHYSICAL_DB';

  return (
    <Card>
      <Title level={4}>代码生成</Title>

      <div
        style={{
          marginBottom: '24px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}
      >
        <Text strong>
          {isPhysical ? '已同步表列表' : '已选元数据实体'}
        </Text>
        {isPhysical && (
          <Select
            placeholder="选择数据源以加载已同步表"
            style={{ minWidth: 240 }}
            value={activeDataSourceId || undefined}
            onChange={selectDataSource}
            options={dataSources.map((ds) => ({
              value: ds.id,
              label: `${ds.name} (${ds.type})`,
            }))}
          />
        )}
        <Button
          type="primary"
          icon={<ReloadOutlined />}
          onClick={refreshTables}
          loading={loadingSyncedTables}
          disabled={isPhysical && !activeDataSourceId}
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
        <div
          style={{
            textAlign: 'center',
            padding: '40px',
            marginBottom: '24px',
            border: '1px dashed #d9d9d9',
          }}
        >
          <Text>暂无已同步的表</Text>
        </div>
      )}

      <Space style={{ marginBottom: '24px' }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openSyncModal}>
          同步表结构
        </Button>
        <Button
          type="primary"
          icon={<DownloadOutlined />}
          onClick={openConfigModal}
          disabled={selectedTables.length === 0}
        >
          生成代码
        </Button>
      </Space>
    </Card>
  );
}
