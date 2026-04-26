import React, { useEffect, useState } from "react";
import { Modal, Form, Input } from "antd";
import ClaimAPI, { IApplyInfo } from "@/api/claim";

interface ApplyInfoDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
}

const ApplyInfoDialog: React.FC<ApplyInfoDialogProps> = ({ visible, onClose, claimId }) => {
  const [applyInfo, setApplyInfo] = useState<IApplyInfo>({
    applyName: "",
    applyIdentityNo: "",
    applyType: "",
    applyTime: "",
    applyPhone: "",
    outInsureName: "",
    outInsureIdentityNo: "",
    outInsureTime: "",
    policyNo: "",
    collectName: "",
    accountNo: "",
    bankName: "",
    bankAddress: "",
  });

  const getApplyInfo = async () => {
    if (claimId) {
      const res = await ClaimAPI.applyInfo(claimId);
      setApplyInfo(res);
    }
  };

  useEffect(() => {
    if (visible && claimId) {
      getApplyInfo();
    }
  }, [visible, claimId]);

  return (
    <Modal
      title="查看报案信息"
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
          <p>申请信息</p>
          <Form.Item label="申请人姓名">
            <Input value={applyInfo.applyName} disabled />
          </Form.Item>
          <Form.Item label="申请人身份证">
            <Input value={applyInfo.applyIdentityNo} disabled />
          </Form.Item>
          <Form.Item label="申请类型">
            <Input value={applyInfo.applyType} disabled />
          </Form.Item>
          <Form.Item label="申请时间">
            <Input value={applyInfo.applyTime} disabled />
          </Form.Item>
          <Form.Item label="联系方式">
            <Input value={applyInfo.applyPhone} disabled />
          </Form.Item>
          <p>报案信息</p>
          <Form.Item label="出险人姓名">
            <Input value={applyInfo.outInsureName} disabled />
          </Form.Item>
          <Form.Item label="出险人证件号">
            <Input value={applyInfo.outInsureIdentityNo} disabled />
          </Form.Item>
          <Form.Item label="出险时间">
            <Input value={applyInfo.outInsureTime} disabled />
          </Form.Item>
          <Form.Item label="保单号">
            <Input value={applyInfo.policyNo} disabled />
          </Form.Item>
          <Form.Item label="领款人姓名">
            <Input value={applyInfo.collectName} disabled />
          </Form.Item>
          <Form.Item label="银行卡号">
            <Input value={applyInfo.accountNo} disabled />
          </Form.Item>
          <Form.Item label="开户行">
            <Input value={applyInfo.bankName} disabled />
          </Form.Item>
          <Form.Item label="开户行地址">
            <Input value={applyInfo.bankAddress} disabled />
          </Form.Item>
        </Form>
      </div>
    </Modal>
  );
};

export default ApplyInfoDialog;
