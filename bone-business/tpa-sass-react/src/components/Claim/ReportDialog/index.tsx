import React, { useState } from "react";
import { Modal, Form, Input, Button, message } from "antd";
import ClaimAPI from "@/api/claim";

interface ReportDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
  onSuccess?: () => void;
}

const ReportDialog: React.FC<ReportDialogProps> = ({ visible, onClose, claimId, onSuccess }) => {
  const [form] = Form.useForm();

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await ClaimAPI.reportClaim(claimId, values);
      message.success("上报成功");
      onClose();
      if (onSuccess) {
        onSuccess();
      }
    } catch (error) {
      message.error("上报失败");
    }
  };

  return (
    <Modal
      title="上报赔案"
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
          name="reportReason"
          label="上报原因"
          rules={[{ required: true, message: "请输入上报原因" }]}
        >
          <Input.TextArea rows={4} placeholder="请输入上报原因" />
        </Form.Item>
        <Form.Item
          name="reportLevel"
          label="上报级别"
          rules={[{ required: true, message: "请选择上报级别" }]}
        >
          <select style={{ width: "100%", padding: "8px" }}>
            <option value="一般">一般</option>
            <option value="重要">重要</option>
            <option value="紧急">紧急</option>
          </select>
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ReportDialog;