import React, { useEffect, useState } from "react";
import { Modal, Form, Input } from "antd";
import ClaimAPI, { IPersonInfo } from "@/api/claim";

interface PersonInfoDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
}

const PersonInfoDialog: React.FC<PersonInfoDialogProps> = ({ visible, onClose, claimId }) => {
  const [personInfo, setPersonInfo] = useState<IPersonInfo>({
    name: "",
    identityNo: "",
    gender: "",
    age: "",
    phone: "",
    address: "",
    relationship: "",
  });

  const getPersonInfo = async () => {
    if (claimId) {
      const res = await ClaimAPI.getPersonInfo(claimId);
      setPersonInfo(res);
    }
  };

  useEffect(() => {
    if (visible && claimId) {
      getPersonInfo();
    }
  }, [visible, claimId]);

  return (
    <Modal
      title="人员信息"
      open={visible}
      onCancel={onClose}
      footer={[
        <button key="cancel" onClick={onClose}>
          取消
        </button>
      ]}
      width="30%"
      maskClosable={false}
    >
      <div style={{ maxHeight: "500px", overflowY: "auto", padding: "8px" }}>
        <Form layout="horizontal" labelAlign="left">
          <Form.Item label="姓名">
            <Input value={personInfo.name} disabled />
          </Form.Item>
          <Form.Item label="身份证号">
            <Input value={personInfo.identityNo} disabled />
          </Form.Item>
          <Form.Item label="性别">
            <Input value={personInfo.gender} disabled />
          </Form.Item>
          <Form.Item label="年龄">
            <Input value={personInfo.age} disabled />
          </Form.Item>
          <Form.Item label="联系方式">
            <Input value={personInfo.phone} disabled />
          </Form.Item>
          <Form.Item label="地址">
            <Input value={personInfo.address} disabled />
          </Form.Item>
          <Form.Item label="关系">
            <Input value={personInfo.relationship} disabled />
          </Form.Item>
        </Form>
      </div>
    </Modal>
  );
};

export default PersonInfoDialog;