import React from 'react';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';

interface PlaceholderProps {
  title?: string;
  subTitle?: string;
  backUrl?: string;
}

const Placeholder: React.FC<PlaceholderProps> = ({
  title = '页面开发中',
  subTitle = '该页面正在开发中，敬请期待...',
  backUrl = '/dashboard',
}) => {
  const navigate = useNavigate();

  return (
    <Result
      status="info"
      title={title}
      subTitle={subTitle}
      extra={[
        <Button type="primary" key="back" onClick={() => navigate(backUrl)}>
          返回首页
        </Button>,
      ]}
    />
  );
};

export default Placeholder;
