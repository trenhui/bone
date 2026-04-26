import React, { useEffect, useState } from "react";
import { Modal, Form, Input, Button, Table } from "antd";
import ClaimAPI from "@/api/claim";

interface BatchLiabilityDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
}

interface LiabilityItem {
  id: string;
  liabilityType: string;
  amount: number;
  status: string;
}

const BatchLiabilityDialog: React.FC<BatchLiabilityDialogProps> = ({ visible, onClose, claimId }) => {
  const [liabilityList, setLiabilityList] = useState<LiabilityItem[]>([]);

  const getLiabilityList = async () => {
    if (claimId) {
      const res = await ClaimAPI.getLiabilityList(claimId);
      setLiabilityList(res);
    }
  };

  useEffect(() => {
    if (visible && claimId) {
      getLiabilityList();
    }
  }, [visible, claimId]);

  const columns = [
    {
      title: "责任类型",
      dataIndex: "liabilityType",
      key: "liabilityType",
    },
    {
      title: "金额",
      dataIndex: "amount",
      key: "amount",
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
    },
  ];

  return (
    <Modal
      title="批量责任设置"
      open={visible}
      onCancel={onClose}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button key="ok" type="primary">
          确定
        </Button>,
      ]}
      width={600}
    >
      <Table
        dataSource={liabilityList}
        columns={columns}
        rowKey="id"
        pagination={false}
      />
    </Modal>
  );
};

export default BatchLiabilityDialog;