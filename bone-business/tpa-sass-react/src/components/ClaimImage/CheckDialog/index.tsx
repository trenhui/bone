import React, { useEffect, useState } from "react";
import { Modal, Form, Input, Button, Table } from "antd";
import ClaimImageAPI from "@/api/claimImage";

interface CheckDialogProps {
  visible: boolean;
  onClose: () => void;
  imageId: string;
}

interface CheckItem {
  id: string;
  checkItem: string;
  checkResult: string;
  checkTime: string;
  checker: string;
}

const CheckDialog: React.FC<CheckDialogProps> = ({ visible, onClose, imageId }) => {
  const [checkList, setCheckList] = useState<CheckItem[]>([]);

  const getCheckList = async () => {
    if (imageId) {
      const res = await ClaimImageAPI.getCheckList(imageId);
      setCheckList(res);
    }
  };

  useEffect(() => {
    if (visible && imageId) {
      getCheckList();
    }
  }, [visible, imageId]);

  const columns = [
    {
      title: "检查项",
      dataIndex: "checkItem",
      key: "checkItem",
    },
    {
      title: "检查结果",
      dataIndex: "checkResult",
      key: "checkResult",
    },
    {
      title: "检查时间",
      dataIndex: "checkTime",
      key: "checkTime",
    },
    {
      title: "检查人",
      dataIndex: "checker",
      key: "checker",
    },
  ];

  return (
    <Modal
      title="影像检查记录"
      open={visible}
      onCancel={onClose}
      footer={null}
      width={800}
    >
      <Table
        dataSource={checkList}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 10 }}
      />
    </Modal>
  );
};

export default CheckDialog;