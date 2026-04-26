import React, { useState } from "react";
import { Modal, Form, Select, Input, Button, message } from "antd";
import ClaimAPI from "@/api/claim";

interface HangupDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
  onSuccess?: () => void;
}

const { Option } = Select;

const HangupDialog: React.FC<HangupDialogProps> = ({ visible, onClose, claimId, onSuccess }) => {
  const [form] = Form.useForm();

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await ClaimAPI.hangupClaim(claimId, values);
      message.success("挂起成功");
      onClose();
      if (onSuccess) {
        onSuccess();
      }
    } catch (error) {
      message.error("挂起失败");
    }
  };

  return (
    <Modal
      title="挂起赔案"
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
          name="hangupReason"
          label="挂起原因"
          rules={[{ required: true, message: "请选择挂起原因" }]}
        >
          <Select placeholder="请选择挂起原因">
            <Option value="等待补充材料">等待补充材料</Option>
            <Option value="等待调查结果">等待调查结果</Option>
            <Option value="其他">其他</Option>
          </Select>
        </Form.Item>
        <Form.Item
          name="hangupRemark"
          label="备注"
        >
          <Input.TextArea rows={4} placeholder="请输入备注信息" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default HangupDialog;