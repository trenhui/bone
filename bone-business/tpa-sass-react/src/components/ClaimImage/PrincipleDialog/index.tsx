import React, { useEffect, useState } from "react";
import { Modal, Form, Input } from "antd";
import ClaimImageAPI from "@/api/claimImage";

interface PrincipleDialogProps {
  visible: boolean;
  onClose: () => void;
  imageId: string;
}

interface PrincipleInfo {
  id: string;
  principleName: string;
  principleContent: string;
  createTime: string;
  creator: string;
}

const PrincipleDialog: React.FC<PrincipleDialogProps> = ({ visible, onClose, imageId }) => {
  const [principleInfo, setPrincipleInfo] = useState<PrincipleInfo>({
    id: "",
    principleName: "",
    principleContent: "",
    createTime: "",
    creator: "",
  });

  const getPrincipleInfo = async () => {
    if (imageId) {
      const res = await ClaimImageAPI.getPrincipleInfo(imageId);
      setPrincipleInfo(res);
    }
  };

  useEffect(() => {
    if (visible && imageId) {
      getPrincipleInfo();
    }
  }, [visible, imageId]);

  return (
    <Modal
      title="影像处理原则"
      open={visible}
      onCancel={onClose}
      footer={[
        <button key="cancel" onClick={onClose}>
          取消
        </button>
      ]}
      width="50%"
      maskClosable={false}
    >
      <div style={{ maxHeight: "500px", overflowY: "auto", padding: "8px" }}>
        <Form layout="vertical">
          <Form.Item label="原则名称">
            <Input value={principleInfo.principleName} disabled />
          </Form.Item>
          <Form.Item label="原则内容">
            <Input.TextArea rows={8} value={principleInfo.principleContent} disabled />
          </Form.Item>
          <Form.Item label="创建时间">
            <Input value={principleInfo.createTime} disabled />
          </Form.Item>
          <Form.Item label="创建人">
            <Input value={principleInfo.creator} disabled />
          </Form.Item>
        </Form>
      </div>
    </Modal>
  );
};

export default PrincipleDialog;