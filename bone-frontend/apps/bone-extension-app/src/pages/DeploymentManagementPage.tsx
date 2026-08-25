import React, { useCallback, useEffect, useState } from 'react';
import { Card, Empty, List, Select, Spin, Tag, message } from 'antd';
import {
  formatStudioError,
  getDeploymentState,
  getDependencyGraph,
  listPlugins,
  type DeploymentStateView,
  type DependencyGraphView,
} from '@/services/extensionApi';
import DeploymentStateDiagram from './DeploymentStateDiagram';

/**
 * 部署管理容器页。
 * 提供插件选择器，加载真实部署状态与依赖图并渲染，消除原 /deploy 路由的静态空占位。
 */
const DeploymentManagementPage: React.FC = () => {
  const [plugins, setPlugins] = useState<{ id: number; name: string }[]>([]);
  const [pluginId, setPluginId] = useState<number | undefined>();
  const [state, setState] = useState<DeploymentStateView | null>(null);
  const [graph, setGraph] = useState<DependencyGraphView | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    listPlugins()
      .then((list) => {
        const rows = Array.isArray(list) ? list : list.records ?? [];
        setPlugins(rows.map((p) => ({ id: p.id, name: p.name })));
      })
      .catch((e) => message.error(formatStudioError(e, '加载插件列表失败')));
  }, []);

  const loadState = useCallback(async (id: number) => {
    setLoading(true);
    try {
      const [s, g] = await Promise.all([getDeploymentState(id), getDependencyGraph()]);
      setState(s);
      setGraph(g);
    } catch (e) {
      message.error(formatStudioError(e, '加载部署状态失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  return (
    <Card
      title="部署管理"
      extra={
        <Select
          allowClear
          placeholder="选择插件"
          style={{ width: 220 }}
          value={pluginId}
          onChange={(v) => {
            setPluginId(v);
            if (v != null) {
              loadState(v).catch((e) => message.error(formatStudioError(e, '加载部署状态失败')));
            } else {
              setState(null);
              setGraph(null);
            }
          }}
          options={plugins.map((p) => ({ value: p.id, label: p.name }))}
        />
      }
    >
      <Spin spinning={loading}>
        {pluginId == null ? (
          <Empty description="请选择插件查看部署状态" />
        ) : state == null ? (
          <Empty description="暂无部署数据" />
        ) : (
          <>
            <DeploymentStateDiagram state={state} />
            <Card size="small" title="依赖关系（详设 §12.3）" style={{ marginTop: 16 }}>
              {graph && graph.nodes.length > 0 ? (
                <List
                  size="small"
                  dataSource={graph.nodes}
                  renderItem={(n) => (
                    <List.Item>
                      <Tag color={n.enabled ? 'green' : 'default'}>{n.name}</Tag>
                      <span style={{ color: '#999' }}>
                        {n.deploymentStatus ?? '未部署'}
                      </span>
                    </List.Item>
                  )}
                />
              ) : (
                <Empty description="无依赖节点" />
              )}
            </Card>
          </>
        )}
      </Spin>
    </Card>
  );
};

export default DeploymentManagementPage;
