import { Modal, Button, Typography } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { UseCodeGeneration } from './useCodeGeneration';

const { Text } = Typography;

export default function ResultModal(props: UseCodeGeneration): JSX.Element {
  const { resultModalVisible, closeResultModal, downloadCode, taskId } = props;

  return (
    <Modal
      title="代码生成结果"
      open={resultModalVisible}
      onCancel={closeResultModal}
      footer={[
        <Button key="close" onClick={closeResultModal}>
          关闭
        </Button>,
        <Button key="download" type="primary" icon={<DownloadOutlined />} onClick={downloadCode}>
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
  );
}
