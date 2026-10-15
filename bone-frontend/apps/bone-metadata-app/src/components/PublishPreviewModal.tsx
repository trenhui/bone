import { Button, Modal, Space } from 'antd';
import PublishPreviewPanel from './PublishPreviewPanel';

/**
 * 发布摘要预览 Modal（顶栏发布入口复用）。
 * 内容抽至 PublishPreviewPanel，本组件仅提供 Modal 外壳。
 */
interface Props {
  open: boolean;
  entityId: string | null;
  entityName?: string;
  onClose: () => void;
  onPublished: () => void;
  onViewInterface?: () => void;
  onOpenData?: () => void;
  onAddField?: () => void;
  /** 界面协议存在阻断错误时「去修复」回调（跳界面设计器 Tab） */
  onFixUiSchema?: () => void;
}

const PublishPreviewModal: React.FC<Props> = ({
  open,
  entityId,
  entityName,
  onClose,
  onPublished,
  onViewInterface,
  onOpenData,
  onAddField,
  onFixUiSchema,
}) => {
  return (
    <Modal
      title={`发布预览${entityName ? `：${entityName}` : ''}`}
      open={open}
      onCancel={onClose}
      width={720}
      destroyOnHidden
      footer={<Space><Button onClick={onClose}>关闭</Button></Space>}
    >
      {open && entityId && (
        <PublishPreviewPanel
          entityId={entityId}
          entityName={entityName}
          onPublished={onPublished}
          onViewInterface={onViewInterface}
          onOpenData={onOpenData}
          onAddField={onAddField}
          onFixUiSchema={onFixUiSchema}
        />
      )}
    </Modal>
  );
};

export default PublishPreviewModal;
