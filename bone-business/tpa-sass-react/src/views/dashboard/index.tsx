import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Progress, List, Avatar, Badge } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, UserOutlined, FileOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import './Dashboard.css';

interface DashboardData {
  totalClaims: number;
  pendingClaims: number;
  processedClaims: number;
  successRate: number;
  recentClaims: Array<{
    id: string;
    name: string;
    status: string;
    time: string;
  }>;
}

const Dashboard: React.FC = () => {
  const [dashboardData, setDashboardData] = useState<DashboardData>({
    totalClaims: 0,
    pendingClaims: 0,
    processedClaims: 0,
    successRate: 0,
    recentClaims: [],
  });

  useEffect(() => {
    // 模拟获取仪表盘数据
    const fetchData = async () => {
      // 实际项目中这里会调用API获取数据
      setDashboardData({
        totalClaims: 1280,
        pendingClaims: 320,
        processedClaims: 960,
        successRate: 92,
        recentClaims: [
          { id: '1', name: '张三', status: '已处理', time: '2026-04-25 10:30' },
          { id: '2', name: '李四', status: '处理中', time: '2026-04-25 09:15' },
          { id: '3', name: '王五', status: '已拒绝', time: '2026-04-24 16:45' },
          { id: '4', name: '赵六', status: '已处理', time: '2026-04-24 14:20' },
          { id: '5', name: '钱七', status: '处理中', time: '2026-04-24 11:10' },
        ],
      });
    };

    fetchData();
  }, []);

  return (
    <div className="dashboard">
      <h1>仪表盘</h1>
      <Row gutter={[16, 16]}>
        <Col span={6}>
          <Card>
            <Statistic
              title="总赔案数"
              value={dashboardData.totalClaims}
              valueStyle={{ color: '#1890ff' }}
              prefix={<FileOutlined />}
              suffix="件"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="待处理"
              value={dashboardData.pendingClaims}
              valueStyle={{ color: '#faad14' }}
              prefix={<CloseCircleOutlined />}
              suffix="件"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="已处理"
              value={dashboardData.processedClaims}
              valueStyle={{ color: '#52c41a' }}
              prefix={<CheckCircleOutlined />}
              suffix="件"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="成功率"
              value={dashboardData.successRate}
              valueStyle={{ color: '#52c41a' }}
              prefix={<ArrowUpOutlined />}
              suffix="%"
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col span={12}>
          <Card title="处理进度">
            <Progress percent={75} status="active" />
          </Card>
        </Col>
        <Col span={12}>
          <Card title="最近赔案">
            <List
              dataSource={dashboardData.recentClaims}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    avatar={<Avatar icon={<UserOutlined />} />}
                    title={
                      <div>
                        {item.name}
                        <Badge 
                          status={item.status === '已处理' ? 'success' : item.status === '处理中' ? 'processing' : 'error'} 
                          text={item.status} 
                          style={{ marginLeft: 8 }}
                        />
                      </div>
                    }
                    description={item.time}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;