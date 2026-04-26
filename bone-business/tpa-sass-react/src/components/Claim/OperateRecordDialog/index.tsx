import React, { useEffect, useState } from "react";
import { Modal, Table } from "antd";
import ClaimAPI from "@/api/claim";

interface OperateRecordDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
}

interface OperateRecord {
  id: string;
  operateType: string;
  operateContent: string;
  operateTime: string;
  operator: string;
}

const OperateRecordDialog: React.FC<OperateRecordDialogProps> = ({ visible, onClose, claimId }) => {
  const [operateRecords, setOperateRecords] = useState<OperateRecord[]>([]);

  const getOperateRecords = async () => {
    if (claimId) {
      const res = await ClaimAPI.getOperateRecords(claimId);
      setOperateRecords(res);
    }
  };

  useEffect(() => {
    if (visible && claimId) {
      getOperateRecords();
    }
  }, [visible, claimId]);

  const columns = [
    {
      title: "操作类型",
      dataIndex: "operateType",
      key: "operateType",
    },
    {
      title: "操作内容",
      dataIndex: "operateContent",
      key: "operateContent",
    },
    {
      title: "操作时间",
      dataIndex: "operateTime",
      key: "operateTime",
    },
    {
      title: "操作人",
      dataIndex: "operator",
      key: "operator",
    },
  ];

  return (
    <Modal
      title="操作记录"
      open={visible}
      onCancel={onClose}
      footer={null}
      width={800}
    >
      <Table
        dataSource={operateRecords}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 10 }}
      />
    </Modal>
  );
};

export default OperateRecordDialog;