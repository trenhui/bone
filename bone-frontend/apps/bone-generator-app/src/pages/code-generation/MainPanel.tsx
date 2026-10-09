import { Alert, Button, Card, Typography, Select, Table, Space } from 'antd';
import { ReloadOutlined, PlusOutlined, DownloadOutlined, ToolOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
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
    zeroColumnCount,
    repairing,
    repairColumns,
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

      {isPhysical && zeroColumnCount > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message={`检测到 ${zeroColumnCount} 张已同步表缺少列元数据`}
          description="这些表是历史版本同步的存量数据，基于它们生成的实体只有 id 字段。建议一键按物理库回填列信息（幂等，不影响已正确同步的表）。"
          action={
            // 回填列元数据会按物理库改写已同步表的列信息，属同步类写操作，与
            // 「同步表结构」同码（GENERATOR_DATASOURCES_SYNC），非普通维护。
            <AuthButton
              code={BonePermissionCodes.GENERATOR_DATASOURCES_SYNC}
              size="small"
              icon={<ToolOutlined />}
              onClick={repairColumns}
              loading={repairing}
            >
              回填列元数据
            </AuthButton>
          }
        />
      )}

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
        {/* 同步表结构会写入元数据表（改表结构语义），故用 SYNC 码而非维护码 */}
        <AuthButton
          code={BonePermissionCodes.GENERATOR_DATASOURCES_SYNC}
          type="primary"
          icon={<PlusOutlined />}
          onClick={openSyncModal}
        >
          同步表结构
        </AuthButton>
        <AuthButton
          code={BonePermissionCodes.GENERATOR_CODEGEN_WRITE}
          type="primary"
          icon={<DownloadOutlined />}
          onClick={openConfigModal}
          disabled={selectedTables.length === 0}
        >
          生成代码
        </AuthButton>
      </Space>
    </Card>
  );
}
