import { useEffect, useState } from 'react';
import Editor from '@monaco-editor/react';
import { Modal, Form, Input, Select, Checkbox, Progress, Divider, Button } from 'antd';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import { templateApi } from '../../services/api';
import type { UseCodeGeneration } from './useCodeGeneration';

export default function GenerateConfigModal(props: UseCodeGeneration): JSX.Element {
  const {
    configModalVisible,
    closeConfigModal,
    generateCode,
    loadingGenerate,
    generateProgress,
    loadingTemplates,
    templates,
    metadataSource,
    activeDataSourceId,
    dataSources,
    generateForm,
    syncedTables,
    selectedTables,
  } = props;

  const isPhysical = metadataSource === 'PHYSICAL_DB';
  const [previewTemplateId, setPreviewTemplateId] = useState<string | undefined>();
  const [previewContent, setPreviewContent] = useState('');
  const [loadingPreview, setLoadingPreview] = useState(false);

  useEffect(() => {
    if (!previewTemplateId) {
      setPreviewContent('');
      return;
    }
    let cancelled = false;
    setLoadingPreview(true);
    templateApi
      .getById(previewTemplateId)
      .then((res) => {
        if (cancelled) return;
        const data = (res.data ?? {}) as Record<string, unknown>;
        setPreviewContent(String(data.content ?? data.templateContent ?? ''));
      })
      .catch(() => {
        if (!cancelled) setPreviewContent('');
      })
      .finally(() => {
        if (!cancelled) setLoadingPreview(false);
      });
    return () => {
      cancelled = true;
    };
  }, [previewTemplateId, configModalVisible]);

  return (
    <Modal
      title="生成配置"
      open={configModalVisible}
      onCancel={closeConfigModal}
      width={800}
      // 自定义 footer 才能给「生成」加门禁（antd 默认确定按钮由 ModalContext 内部渲染）。
      // 传数组时 antd 原样渲染、不做 cloneElement，AuthButton 安全。
      footer={[
        <AuthButton
          key="ok"
          code={BonePermissionCodes.GENERATOR_CODEGEN_WRITE}
          type="primary"
          loading={loadingGenerate}
          onClick={() => void generateCode()}
        >
          生成
        </AuthButton>,
        <Button key="cancel" onClick={closeConfigModal}>
          取消
        </Button>,
      ]}
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
        {isPhysical && (
          <Form.Item
            name="dataSourceId"
            label="数据源"
            rules={[{ required: true, message: '请选择数据源' }]}
            initialValue={activeDataSourceId || undefined}
          >
            <Select
              placeholder="请选择数据源"
              showSearch
              optionFilterProp="children"
              options={dataSources.map((ds) => ({
                value: ds.id,
                label: `${ds.name} (${ds.type})`,
              }))}
            />
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
            onChange={(ids) =>
              setPreviewTemplateId(
                Array.isArray(ids) && ids.length > 0 ? String(ids[0]) : undefined,
              )
            }
            options={templates.map((template) => ({
              value: String(template.id),
              label: `${String(template.name)} (${String(template.type)})`,
            }))}
          />
        </Form.Item>

        {isPhysical && (
          <>
            <Form.Item
              name="childTable"
              label="子表（主子聚合，可选）"
              extra={
                selectedTables.length === 1
                  ? '选择子表后额外生成「一次事务创建主表 + 明细」的聚合应用服务'
                  : '主子聚合仅支持单张主表：请只勾选一张表后再配置'
              }
            >
              <Select
                placeholder="不选 = 只生成单表代码"
                allowClear
                disabled={selectedTables.length !== 1}
                options={syncedTables
                  .filter((t) => !selectedTables.includes(t.tableName))
                  .map((t) => ({
                    value: t.tableName,
                    label: `${t.tableName}${t.tableComment ? ` (${t.tableComment})` : ''}`,
                  }))}
              />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(prev, cur) => prev.childTable !== cur.childTable}>
              {({ getFieldValue }) => {
                const childTable = getFieldValue('childTable') as string | undefined;
                const childColumns =
                  syncedTables.find((t) => t.tableName === childTable)?.columns ?? [];
                if (!childTable) {
                  return null;
                }
                return (
                  <Form.Item
                    name="childFkColumn"
                    label="子表外键列"
                    rules={[{ required: true, message: '主子聚合必须指定子表外键列' }]}
                    extra={`子表 ${childTable} 中指向主表 id 的列，聚合服务会用主表 id 填充它`}
                  >
                    <Select
                      placeholder="选择外键列，如 order_id"
                      allowClear
                      options={childColumns.map((c) => ({
                        value: c.columnName,
                        label: `${c.columnName}${c.columnComment ? ` (${c.columnComment})` : ''}`,
                      }))}
                    />
                  </Form.Item>
                );
              }}
            </Form.Item>
          </>
        )}

        <Form.Item name="includeTests" valuePropName="checked">
          <Checkbox>包含测试代码</Checkbox>
        </Form.Item>

        <Form.Item name="includeDocumentation" valuePropName="checked">
          <Checkbox>包含文档</Checkbox>
        </Form.Item>
      </Form>

      <Divider orientation="left">模板内容预览（只读）</Divider>
      <Editor
        height="260px"
        language="handlebars"
        theme="light"
        value={previewContent}
        loading={loadingPreview ? <div>加载模板内容…</div> : undefined}
        options={{
          readOnly: true,
          minimap: { enabled: false },
          fontSize: 12,
          scrollBeyondLastLine: false,
        }}
      />
    </Modal>
  );
}
