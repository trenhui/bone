import React, { useState } from "react";
import { Modal, Form, Input, Button, message } from "antd";
import ClaimAPI from "@/api/claim";

interface ReviewRejectionDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
  onSuccess?: () => void;
}

const ReviewRejectionDialog: React.FC<ReviewRejectionDialogProps> = ({ visible, onClose, claimId, onSuccess }) => {
  const [form] = Form.useForm();

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await ClaimAPI.rejectClaim(claimId, values);
      message.success("拒赔成功");
      onClose();
      if (onSuccess) {
        onSuccess();
      }
    } catch (error) {
      message.error("拒赔失败");
    }
  };

  return (
    <Modal
      title="拒赔处理"
      open={visible}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button key="ok" type="primary" onClick={handleSubmit}>
          确定
        </Button>,
      ]}
      width={400}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="rejectReason"
          label="拒赔原因"
          rules={[{ required: true, message: "请输入拒赔原因" }]}
        >
          <Input.TextArea rows={4} placeholder="请输入拒赔原因" />
        </Form.Item>
        <Form.Item
          name="rejectType"
          label="拒赔类型"
          rules={[{ required: true, message: "请选择拒赔类型" }]}
        >
          <select style={{ width: "100%", padding: "8px" }}>
            <option value="不属于保险责任">不属于保险责任</option>
            <option value="材料不全">材料不全</option>
            <option value="虚假报案">虚假报案</option>
            <option value="其他">其他</option>
          </select>
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ReviewRejectionDialog;