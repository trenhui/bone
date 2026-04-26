import React, { useState } from "react";
import { Modal, Form, Input, Button, message } from "antd";
import ClaimAPI from "@/api/claim";

interface ReturnDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
  onSuccess?: () => void;
}

const ReturnDialog: React.FC<ReturnDialogProps> = ({ visible, onClose, claimId, onSuccess }) => {
  const [form] = Form.useForm();

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await ClaimAPI.returnClaim(claimId, values);
      message.success("退回成功");
      onClose();
      if (onSuccess) {
        onSuccess();
      }
    } catch (error) {
      message.error("退回失败");
    }
  };

  return (
    <Modal
      title="退回赔案"
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
          name="returnReason"
          label="退回原因"
          rules={[{ required: true, message: "请输入退回原因" }]}
        >
          <Input.TextArea rows={4} placeholder="请输入退回原因" />
        </Form.Item>
        <Form.Item
          name="returnTo"
          label="退回对象"
          rules={[{ required: true, message: "请选择退回对象" }]}
        >
          <select style={{ width: "100%", padding: "8px" }}>
            <option value="上一环节">上一环节</option>
            <option value="录入环节">录入环节</option>
            <option value="审核环节">审核环节</option>
          </select>
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ReturnDialog;