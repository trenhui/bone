import { useEffect, useState } from 'react';
import Editor from '@monaco-editor/react';
import { Modal, Form, Input, Select, Checkbox, Progress, Divider } from 'antd';
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
      .getById(Number(previewTemplateId))
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
      onOk={generateCode}
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
