import React, { useState } from 'react';
import { Modal, Upload, Button, message, Space, Alert } from 'antd';
import { InboxOutlined } from '@ant-design/icons';

const { Dragger } = Upload;

interface UploadComponent {
  id: string;
  dataType: number;
  title: string;
}

interface UploadData {
  [key: string]: any;
}

interface UploadImgDocCheckDialogProps {
  visible: boolean;
  uploadComponent: UploadComponent;
  uploadData: UploadData;
  uploadUrl?: string;
  onClose: () => void;
  onResult: (result: boolean) => void;
}

const UploadImgDocCheckDialog: React.FC<UploadImgDocCheckDialogProps> = ({
  visible,
  uploadComponent,
  uploadData,
  uploadUrl,
  onClose,
  onResult
}) => {
  const [fileList, setFileList] = useState<any[]>([]);
  const [uploading, setUploading] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleUpload = async () => {
    setUploading(true);
    // 模拟上传过程
    setTimeout(() => {
      setUploading(false);
      setSuccess(true);
      message.success('上传成功');
      onResult(true);
    }, 1000);
  };

  const handleChange = (info: any) => {
    const { status } = info.file;
    if (status !== 'uploading') {
      console.log(info.file, info.fileList);
    }
    if (status === 'done') {
      message.success(`${info.file.name} 文件上传成功`);
    } else if (status === 'error') {
      message.error(`${info.file.name} 文件上传失败`);
    }
    setFileList(info.fileList);
  };

  const uploadProps = {
    name: 'file',
    multiple: true,
    action: uploadUrl || '/api/upload',
    onChange: handleChange,
    fileList,
    accept: '.jpg,.jpeg,.png,.pdf,.doc,.docx',
  };

  return (
    <Modal
      title={`${uploadComponent?.title} - 上传校验`}
      open={visible}
      onCancel={onClose}
      footer={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button
            type="primary"
            loading={uploading}
            onClick={handleUpload}
            disabled={fileList.length === 0}
          >
            开始校验
          </Button>
        </Space>
      }
      width={800}
    >
      <Dragger {...uploadProps}>
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
        <p className="ant-upload-hint">
          支持上传图片和文档文件，单个文件不超过10MB
        </p>
      </Dragger>

      {success && (
        <Alert
          message="文件校验通过，可以继续操作"
          type="success"
          showIcon
          style={{ marginTop: '16px' }}
        />
      )}
    </Modal>
  );
};

export default UploadImgDocCheckDialog;