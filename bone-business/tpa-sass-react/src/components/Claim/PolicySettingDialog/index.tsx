import React, { useEffect, useState } from "react";
import { Modal, Form, Input, Button, Select, message } from "antd";
import ClaimAPI from "@/api/claim";

interface PolicySettingDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
  onSuccess?: () => void;
}

const { Option } = Select;

const PolicySettingDialog: React.FC<PolicySettingDialogProps> = ({ visible, onClose, claimId, onSuccess }) => {
  const [form] = Form.useForm();

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      await ClaimAPI.setPolicy(claimId, values);
      message.success("保单设置成功");
      onClose();
      if (onSuccess) {
        onSuccess();
      }
    } catch (error) {
      message.error("保单设置失败");
    }
  };

  return (
    <Modal
      title="保单设置"
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
          name="policyNo"
          label="保单号"
          rules={[{ required: true, message: "请输入保单号" }]}
        >
          <Input placeholder="请输入保单号" />
        </Form.Item>
        <Form.Item
          name="policyType"
          label="保单类型"
          rules={[{ required: true, message: "请选择保单类型" }]}
        >
          <Select placeholder="请选择保单类型">
            <Option value="个人">个人</Option>
            <Option value="团体">团体</Option>
          </Select>
        </Form.Item>
        <Form.Item
          name="effectiveDate"
          label="生效日期"
          rules={[{ required: true, message: "请输入生效日期" }]}
        >
          <Input placeholder="请输入生效日期" />
        </Form.Item>
        <Form.Item
          name="expireDate"
          label="失效日期"
          rules={[{ required: true, message: "请输入失效日期" }]}
        >
          <Input placeholder="请输入失效日期" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default PolicySettingDialog;