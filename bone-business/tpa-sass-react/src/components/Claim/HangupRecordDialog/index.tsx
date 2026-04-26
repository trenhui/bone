import React, { useEffect, useState } from "react";
import { Modal, Table } from "antd";
import ClaimAPI from "@/api/claim";

interface HangupRecordDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
}

interface HangupRecord {
  id: string;
  hangupReason: string;
  hangupRemark: string;
  hangupTime: string;
  hangupUser: string;
  resumeTime: string;
  resumeUser: string;
  resumeReason: string;
}

const HangupRecordDialog: React.FC<HangupRecordDialogProps> = ({ visible, onClose, claimId }) => {
  const [hangupRecords, setHangupRecords] = useState<HangupRecord[]>([]);

  const getHangupRecords = async () => {
    if (claimId) {
      const res = await ClaimAPI.getHangupRecords(claimId);
      setHangupRecords(res);
    }
  };

  useEffect(() => {
    if (visible && claimId) {
      getHangupRecords();
    }
  }, [visible, claimId]);

  const columns = [
    {
      title: "挂起原因",
      dataIndex: "hangupReason",
      key: "hangupReason",
    },
    {
      title: "挂起备注",
      dataIndex: "hangupRemark",
      key: "hangupRemark",
    },
    {
      title: "挂起时间",
      dataIndex: "hangupTime",
      key: "hangupTime",
    },
    {
      title: "挂起人",
      dataIndex: "hangupUser",
      key: "hangupUser",
    },
    {
      title: "恢复时间",
      dataIndex: "resumeTime",
      key: "resumeTime",
    },
    {
      title: "恢复人",
      dataIndex: "resumeUser",
      key: "resumeUser",
    },
    {
      title: "恢复原因",
      dataIndex: "resumeReason",
      key: "resumeReason",
    },
  ];

  return (
    <Modal
      title="挂起记录"
      open={visible}
      onCancel={onClose}
      footer={null}
      width={800}
    >
      <Table
        dataSource={hangupRecords}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 10 }}
      />
    </Modal>
  );
};

export default HangupRecordDialog;