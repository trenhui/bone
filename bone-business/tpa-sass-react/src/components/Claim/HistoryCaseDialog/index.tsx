import React, { useEffect, useState } from "react";
import { Modal, Table } from "antd";
import ClaimAPI from "@/api/claim";

interface HistoryCaseDialogProps {
  visible: boolean;
  onClose: () => void;
  claimId: string;
}

interface HistoryCase {
  id: string;
  caseNo: string;
  caseType: string;
  createTime: string;
  status: string;
  handler: string;
}

const HistoryCaseDialog: React.FC<HistoryCaseDialogProps> = ({ visible, onClose, claimId }) => {
  const [historyCases, setHistoryCases] = useState<HistoryCase[]>([]);

  const getHistoryCases = async () => {
    if (claimId) {
      const res = await ClaimAPI.getHistoryCases(claimId);
      setHistoryCases(res);
    }
  };

  useEffect(() => {
    if (visible && claimId) {
      getHistoryCases();
    }
  }, [visible, claimId]);

  const columns = [
    {
      title: "案号",
      dataIndex: "caseNo",
      key: "caseNo",
    },
    {
      title: "案件类型",
      dataIndex: "caseType",
      key: "caseType",
    },
    {
      title: "创建时间",
      dataIndex: "createTime",
      key: "createTime",
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
    },
    {
      title: "处理人",
      dataIndex: "handler",
      key: "handler",
    },
  ];

  return (
    <Modal
      title="历史案件"
      open={visible}
      onCancel={onClose}
      footer={null}
      width={800}
    >
      <Table
        dataSource={historyCases}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 10 }}
      />
    </Modal>
  );
};

export default HistoryCaseDialog;