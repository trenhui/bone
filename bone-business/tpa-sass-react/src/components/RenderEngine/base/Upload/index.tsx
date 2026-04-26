import React, { useState, useMemo } from 'react';
import { Button, Form, message, Alert } from 'antd';
import { DownloadOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import FileAPI from '@/api/pk-file';
import { saveAs } from 'file-saver';

// Components
import UploadDataCheckDialog from './DataCheckDialog';
import UploadImgDocCheckDialog from './ImgDocCheckDialog';
import UploadImageCheckDialog from './ImageCheckDialog';

interface UploadComponent {
  id: string;
  dataType: number;
  title: string;
  templateFileName?: string;
  importDescription?: string;
}

interface UploadData {
  [key: string]: any;
}

interface UploadProps {
  uploadData: UploadData;
  uploadComponent: UploadComponent;
  uploadUrl?: string;
  onResult?: (result: boolean) => void;
}

const UploadComponent: React.FC<UploadProps> = ({
  uploadData,
  uploadComponent,
  uploadUrl,
  onResult
}) => {
  const [isUploaded, setIsUploaded] = useState(false);
  const [uploadResult, setUploadResult] = useState(false);
  const [downloadLoading, setDownloadLoading] = useState(false);
  const [dialogVisible, setDialogVisible] = useState(false);

  const ComputedComponent = useMemo(() => {
    switch (uploadComponent?.dataType) {
      case 1:
        return UploadDataCheckDialog;
      case 2:
        return UploadImgDocCheckDialog;
      case 3:
        return UploadImageCheckDialog;
      default:
        return null;
    }
  }, [uploadComponent?.dataType]);

  const isUploadDataComponent = (component: UploadComponent): boolean => {
    return component.dataType === 1;
  };

  const handleCheck = () => {
    if (!uploadComponent) {
      return;
    }
    setDialogVisible(true);
  };

  const handleDownloadTemplate = async () => {
    if (!uploadComponent || !isUploadDataComponent(uploadComponent)) {
      return;
    }

    try {
      setDownloadLoading(true);
      const res = await FileAPI.getTemplate(uploadComponent.id);

      // 将base64转换为blob
      const byteArray = Uint8Array.from(atob(res), (c) => c.charCodeAt(0));
      const blob = new Blob([byteArray], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });

      saveAs(
        blob,
        uploadComponent.templateFileName
          ? uploadComponent.templateFileName + '.xlsx'
          : 'template.xlsx'
      );
    } catch (error) {
      console.error(error);
      message.error('下载模板失败');
    } finally {
      setDownloadLoading(false);
    }
  };

  const handleResult = (result: boolean) => {
    setIsUploaded(true);
    setUploadResult(result);
    setDialogVisible(false);
    if (onResult) {
      onResult(result);
    }
  };

  const DialogComponent = ComputedComponent;

  return (
    <div>
      <Form.Item label={uploadComponent?.title}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Button type="primary" onClick={handleCheck}>
            点击上传
          </Button>

          {isUploadDataComponent(uploadComponent) && (
            <Button
              type="link"
              icon={<DownloadOutlined />}
              loading={downloadLoading}
              onClick={handleDownloadTemplate}
            >
              下载模板文件
            </Button>
          )}
        </div>
      </Form.Item>

      {isUploaded && uploadResult && (
        <Alert
          message="上传文件已预校验完成，请继续操作"
          type="success"
          showIcon
          style={{ marginBottom: '8px' }}
        />
      )}

      {isUploaded && !uploadResult && (
        <Alert
          message="上传文件出现问题，请重新上传"
          type="error"
          showIcon
          style={{ marginBottom: '8px' }}
        />
      )}

      {uploadComponent?.importDescription && (
        <div style={{
          marginTop: '8px',
          padding: '12px 20px',
          backgroundColor: '#f7f7f7',
          border: '1px solid #e8e8e8',
          borderRadius: '4px',
          fontSize: '14px',
          color: '#666',
          whiteSpace: 'pre-wrap'
        }}>
          {uploadComponent.importDescription}
        </div>
      )}

      {DialogComponent && (
        <DialogComponent
          visible={dialogVisible}
          uploadComponent={uploadComponent}
          uploadData={uploadData}
          uploadUrl={uploadUrl}
          onClose={() => setDialogVisible(false)}
          onResult={handleResult}
        />
      )}
    </div>
  );
};

export default UploadComponent;