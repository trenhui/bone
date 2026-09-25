import { Modal, Form, Select } from 'antd';
import type { UseCodeGeneration, MetadataSource } from './useCodeGeneration';

export default function SyncTablesModal(props: UseCodeGeneration): JSX.Element {
  const {
    syncModalVisible,
    closeSyncModal,
    syncTables,
    loading,
    metadataSource,
    dataSources,
    dataSourceTables,
    syncForm,
    handleMetadataSourceChange,
    handleDataSourceChange,
  } = props;

  const isPhysical = metadataSource === 'PHYSICAL_DB';

  return (
    <Modal
      title="同步表结构"
      open={syncModalVisible}
      onCancel={closeSyncModal}
      onOk={syncTables}
      okText="同步"
      cancelText="取消"
      width={800}
      confirmLoading={loading}
    >
      <Form form={syncForm} layout="vertical" requiredMark={false}>
        <Form.Item label="元数据来源">
          <Select
            value={metadataSource}
            onChange={(v: MetadataSource) => handleMetadataSourceChange(v)}
            options={[
              { value: 'PHYSICAL_DB', label: '物理数据源' },
              { value: 'CATALOG_SNAPSHOT', label: '元数据目录（已发布 meta_*）' },
            ]}
          />
        </Form.Item>

        <Form.Item
          name="dataSourceId"
          label="数据源"
          rules={[{ required: isPhysical, message: '请选择数据源' }]}
        >
          <Select
            placeholder="请选择数据源"
            showSearch
            optionFilterProp="children"
            disabled={!isPhysical}
            onChange={(id: string) => void handleDataSourceChange(id)}
            options={dataSources.map((ds) => ({
              value: ds.id,
              label: `${ds.name} (${ds.type})`,
            }))}
          />
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
            options={dataSourceTables.map((table) => {
              const tableName = table.tableName || '';
              const tableComment = table.tableComment || '';
              return {
                value: tableName,
                label: `${tableName}${tableComment ? ` (${tableComment})` : ''}`,
              };
            })}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
