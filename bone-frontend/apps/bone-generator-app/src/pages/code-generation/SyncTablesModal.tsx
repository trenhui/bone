import { Modal, Form, Select, Button } from 'antd';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
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
    handleTableSearch,
  } = props;

  const isPhysical = metadataSource === 'PHYSICAL_DB';

  return (
    <Modal
      title="同步表结构"
      open={syncModalVisible}
      onCancel={closeSyncModal}
      width={800}
      // 自定义 footer 才能给「同步」加门禁（antd 默认确定按钮由 ModalContext 内部渲染）。
      // 传数组时 antd 原样渲染、不做 cloneElement，AuthButton 安全。
      footer={[
        <AuthButton
          key="ok"
          code={BonePermissionCodes.GENERATOR_DATASOURCES_SYNC}
          type="primary"
          loading={loading}
          onClick={() => void syncTables()}
        >
          同步
        </AuthButton>,
        <Button key="cancel" onClick={closeSyncModal}>
          取消
        </Button>,
      ]}
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
            placeholder="请选择要同步的表（输入表名可服务端搜索）"
            showSearch
            optionFilterProp="children"
            loading={loading}
            onSearch={isPhysical ? (v: string) => handleTableSearch(v) : undefined}
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
