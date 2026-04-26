import React, { useState } from "react";
import { Modal, Button, message } from "antd";
import ClaimImageAPI from "@/api/claimImage";

interface ShortCutDialogProps {
  visible: boolean;
  onClose: () => void;
  imageId: string;
  onSuccess?: () => void;
}

const ShortCutDialog: React.FC<ShortCutDialogProps> = ({ visible, onClose, imageId, onSuccess }) => {
  const handleShortcut = async (action: string) => {
    try {
      await ClaimImageAPI.shortcutAction(imageId, action);
      message.success(`${action}成功`);
      onClose();
      if (onSuccess) {
        onSuccess();
      }
    } catch (error) {
      message.error(`${action}失败`);
    }
  };

  return (
    <Modal
      title="快捷操作"
      open={visible}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>
      ]}
      width={400}
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
        <Button type="primary" onClick={() => handleShortcut("通过")}>
          快速通过
        </Button>
        <Button type="default" onClick={() => handleShortcut("拒绝")}>
          快速拒绝
        </Button>
        <Button type="default" onClick={() => handleShortcut("转人工")}>
          转人工处理
        </Button>
        <Button type="default" onClick={() => handleShortcut("需要补充")}>
          需要补充材料
        </Button>
      </div>
    </Modal>
  );
};

export default ShortCutDialog;