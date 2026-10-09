import { useEffect, useMemo, useState } from 'react';
import Editor from '@monaco-editor/react';
import { Modal, Button, Typography, List, Spin, Alert, Tag } from 'antd';
import { DownloadOutlined, FileTextOutlined } from '@ant-design/icons';
import { codeGenerationApi, getGeneratorOperation } from '../../services/api';
import type { GeneratedFileItem } from '../../services/types';
import type { UseCodeGeneration } from './useCodeGeneration';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';

const { Text } = Typography;

/**
 * 代码生成结果弹窗（状态驱动）。
 *
 * <p>旧版是硬编码的「任务已创建，正在处理中」死文案——前端 generate() 实际已轮询到终态才返回，
 * 弹窗打开时任务必然已 SUCCESS/FAILED，旧文案纯属误导，用户会对着「处理中」狂点下载。
 *
 * <p>新版：打开即查任务状态与产物清单；SUCCESS 展示文件列表 + Monaco 在线预览（下载前先看代码），
 * FAILED 展示后端错误详情。
 */

/** 按扩展名推断 Monaco 语言，仅影响高亮，不影响功能 */
function languageOf(filePath: string): string {
  if (filePath.endsWith('.java')) return 'java';
  if (filePath.endsWith('.md')) return 'markdown';
  if (filePath.endsWith('.xml')) return 'xml';
  if (filePath.endsWith('.yml') || filePath.endsWith('.yaml')) return 'yaml';
  if (filePath.endsWith('.json')) return 'json';
  return 'plaintext';
}

export default function ResultModal(props: UseCodeGeneration): JSX.Element {
  const { resultModalVisible, closeResultModal, downloadCode, taskId } = props;
  const [status, setStatus] = useState<'LOADING' | 'SUCCESS' | 'FAILED' | 'PROCESSING'>('LOADING');
  const [errorMessage, setErrorMessage] = useState('');
  const [files, setFiles] = useState<GeneratedFileItem[]>([]);
  const [activeFile, setActiveFile] = useState<GeneratedFileItem | null>(null);
  const [loadingFiles, setLoadingFiles] = useState(false);

  useEffect(() => {
    if (!resultModalVisible || !taskId) return;
    let cancelled = false;

    const load = async () => {
      setStatus('LOADING');
      setErrorMessage('');
      setFiles([]);
      setActiveFile(null);
      try {
        const statusRes = await codeGenerationApi.getTaskStatus(taskId);
        // 后端 data 即状态字符串（非对象）
        const taskStatus = String(statusRes.data ?? 'UNKNOWN').toUpperCase();
        if (cancelled) return;
        if (taskStatus === 'SUCCESS') {
          setStatus('SUCCESS');
          setLoadingFiles(true);
          const filesRes = await codeGenerationApi.listGeneratedFiles(taskId, true);
          if (cancelled) return;
          const list = filesRes.data ?? [];
          setFiles(list);
          setActiveFile(list[0] ?? null);
          setLoadingFiles(false);
        } else if (taskStatus === 'FAILED') {
          setStatus('FAILED');
          // 错误详情从 operation 视图取（status 端点只有状态字符串）
          try {
            const op = await getGeneratorOperation(taskId);
            if (!cancelled) setErrorMessage(op.error?.detail ?? '代码生成失败');
          } catch {
            if (!cancelled) setErrorMessage('代码生成失败');
          }
        } else {
          setStatus('PROCESSING');
        }
      } catch (error) {
        if (!cancelled) {
          setStatus('FAILED');
          setErrorMessage('查询任务状态失败');
          console.error('查询任务状态失败:', error);
        }
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [resultModalVisible, taskId]);

  const editorLanguage = useMemo(
    () => (activeFile?.filePath ? languageOf(activeFile.filePath) : 'plaintext'),
    [activeFile],
  );

  return (
    <Modal
      title="代码生成结果"
      open={resultModalVisible}
      onCancel={closeResultModal}
      footer={[
        <Button key="close" onClick={closeResultModal}>
          关闭
        </Button>,
        <AuthButton
          key="download"
          code={BonePermissionCodes.GENERATOR_CODEGEN_WRITE}
          type="primary"
          icon={<DownloadOutlined />}
          onClick={downloadCode}
          disabled={status !== 'SUCCESS'}
        >
          下载代码
        </AuthButton>,
      ]}
      width={960}
    >
      <div style={{ marginBottom: 12 }}>
        <Text strong>任务 ID：</Text>
        <Text copyable>{taskId}</Text>
        {status === 'SUCCESS' && (
          <Tag color="success" style={{ marginLeft: 8 }}>
            生成成功 · 共 {files.length} 个文件
          </Tag>
        )}
        {status === 'FAILED' && (
          <Tag color="error" style={{ marginLeft: 8 }}>
            生成失败
          </Tag>
        )}
        {status === 'PROCESSING' && (
          <Tag color="processing" style={{ marginLeft: 8 }}>
            处理中
          </Tag>
        )}
      </div>

      {status === 'LOADING' && (
        <div style={{ textAlign: 'center', padding: '32px 0' }}>
          <Spin tip="正在查询生成结果…" />
        </div>
      )}

      {status === 'FAILED' && <Alert type="error" showIcon message="代码生成失败" description={errorMessage} />}

      {status === 'PROCESSING' && (
        <Alert
          type="info"
          showIcon
          message="任务处理中"
          description="代码生成仍在后台执行，稍后在「生成历史」页可查看结果并下载。"
        />
      )}

      {status === 'SUCCESS' && (
        <div style={{ display: 'flex', gap: 12 }}>
          <div style={{ width: 320, flexShrink: 0 }}>
            <Spin spinning={loadingFiles}>
              <List
                size="small"
                dataSource={files}
                bordered
                style={{ maxHeight: 460, overflow: 'auto' }}
                renderItem={(item) => (
                  <List.Item
                    onClick={() => setActiveFile(item)}
                    style={{
                      cursor: 'pointer',
                      background: activeFile?.filePath === item.filePath ? '#e6f4ff' : undefined,
                      padding: '4px 12px',
                    }}
                  >
                    <FileTextOutlined style={{ marginRight: 6, color: '#8c8c8c' }} />
                    <Text style={{ fontSize: 12 }} ellipsis={{ tooltip: item.filePath }}>
                      {item.filePath || item.fileName}
                    </Text>
                  </List.Item>
                )}
              />
            </Spin>
          </div>
          <div style={{ flex: 1, minWidth: 0, border: '1px solid #f0f0f0' }}>
            <Editor
              height="460px"
              language={editorLanguage}
              theme="light"
              value={activeFile?.content ?? ''}
              loading={<div style={{ padding: 12 }}>加载文件内容…</div>}
              options={{
                readOnly: true,
                minimap: { enabled: false },
                fontSize: 12,
                scrollBeyondLastLine: false,
                automaticLayout: true,
              }}
            />
          </div>
        </div>
      )}
    </Modal>
  );
}
