import React, { useEffect, useState } from "react";
import { Modal, Table } from "antd";
import ClaimAPI from "@/api/claim";

interface OutEntryRecordDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
}

interface OutEntryRecord {
  id: string;
  outEntryType: string;
  outEntryTime: string;
  outEntryUser: string;
  entryTime: string;
  entryUser: string;
  remark: string;
}

const OutEntryRecordDialog: React.FC<OutEntryRecordDialogProps> = ({ visible, onClose, claimId }) => {
  const [outEntryRecords, setOutEntryRecords] = useState<OutEntryRecord[]>([]);

  const getOutEntryRecords = async () => {
    if (claimId) {
      const res = await ClaimAPI.getOutEntryRecords(claimId);
      setOutEntryRecords(res);
    }
  };

  useEffect(() => {
    if (visible && claimId) {
      getOutEntryRecords();
    }
  }, [visible, claimId]);

  const columns = [
    {
      title: "外出类型",
      dataIndex: "outEntryType",
      key: "outEntryType",
    },
    {
      title: "外出时间",
      dataIndex: "outEntryTime",
      key: "outEntryTime",
    },
    {
      title: "外出人",
      dataIndex: "outEntryUser",
      key: "outEntryUser",
    },
    {
      title: "归岗时间",
      dataIndex: "entryTime",
      key: "entryTime",
    },
    {
      title: "归岗人",
      dataIndex: "entryUser",
      key: "entryUser",
    },
    {
      title: "备注",
      dataIndex: "remark",
      key: "remark",
    },
  ];

  return (
    <Modal
      title="外出记录"
      open={visible}
      onCancel={onClose}
      footer={null}
      width={800}
    >
      <Table
        dataSource={outEntryRecords}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 10 }}
      />
    </Modal>
  );
};

export default OutEntryRecordDialog;