import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Row, Spin, Statistic, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import {
  formatStudioError,
  getExtensionOverview,
  type SandboxConfig,
} from '@/services/extensionApi';

const ExtensionOverview: React.FC = () => {
  const [stats, setStats] = useState<SandboxConfig | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setStats(await getExtensionOverview());
    } catch (e) {
      message.error(formatStudioError(e, '加载概览失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loading && !stats) {
    return <Spin />;
  }

  return (
    <>
      <Button icon={<ReloadOutlined />} onClick={load} style={{ marginBottom: 16 }}>
        刷新
      </Button>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="扩展引擎控制台"
        description="扩展点定义契约接口，插件为扩展实现；部署与运行时同步由 Studio 编排，详见扩展点 / 插件 / 沙箱菜单。"
      />
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="扩展点" value={stats?.extPointCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="插件（扩展实现）" value={stats?.pluginCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic title="执行总数" value={stats?.executionTotal ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card>
            <Statistic
              title="成功率(%)"
              value={stats?.successRate ?? 100}
              precision={1}
              valueStyle={{
                color: (stats?.successRate ?? 100) >= 95 ? '#3f8600' : '#cf1322',
              }}
            />
          </Card>
        </Col>
        <Col xs={12} md={8}>
          <Card>
            <Statistic title="成功" value={stats?.executionSuccess ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={8}>
          <Card>
            <Statistic title="失败" value={stats?.executionFailed ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={8}>
          <Card>
            <Statistic title="运行中" value={stats?.executionRunning ?? 0} />
          </Card>
        </Col>
        <Col span={24}>
          <Card title="运行时">
            元数据同步：
            {stats?.runtimeSyncEnabled ?? stats?.syncEnabled ? '已启用' : '未启用'}
          </Card>
        </Col>
      </Row>
    </>
  );
};

export default ExtensionOverview;
