import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  message,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { CloudDownloadOutlined, ReloadOutlined } from '@ant-design/icons';
import { Auth, AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import {
  type ExtPointRow,
  type MarketplaceItem,
  type StudioPageResult,
  formatStudioError,
  installMarketplaceItem,
  listExtPoints,
  listMarketplaceItems,
} from '@/services/extensionApi';

const Marketplace: React.FC = () => {
  const [items, setItems] = useState<MarketplaceItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<string | undefined>(undefined);
  // ⚠ 扩展点 ID 是雪花 ID（后端 Long→String），下拉 value 必须 string
  const [extPointOptions, setExtPointOptions] = useState<{ label: string; value: string }[]>([]);
  const [installTarget, setInstallTarget] = useState<MarketplaceItem | null>(null);
  const [selectedExtPointId, setSelectedExtPointId] = useState<string | undefined>(undefined);
  const [installAppId, setInstallAppId] = useState<string | undefined>(undefined);
  const [installing, setInstalling] = useState(false);

  const categories = Array.from(
    new Set(items.map((i) => i.category).filter((c): c is string => !!c)),
  );

  const load = async () => {
    setLoading(true);
    try {
      const result = await listMarketplaceItems({ keyword: keyword || undefined, category });
      setItems(result);
    } catch (e) {
      message.error(formatStudioError(e, '加载市场失败'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    listExtPoints()
      .then((result) => {
        const rows: ExtPointRow[] = Array.isArray(result)
          ? result
          : (result as StudioPageResult<ExtPointRow>).records ?? [];
        setExtPointOptions(
          rows.map((p) => ({ value: p.id, label: p.name ?? p.interfaceName ?? `#${p.id}` })),
        );
      })
      .catch(() => undefined);
    load();
  }, []);

  const handleInstallConfirm = async () => {
    if (!installTarget) return;
    setInstalling(true);
    try {
      const result = await installMarketplaceItem(installTarget.id, selectedExtPointId, installAppId);
      message.success(
        `已安装为插件 #${result.pluginId}${installAppId ? `（归属应用 #${installAppId}）` : '（平台通用）'}`,
      );
      setInstallTarget(null);
      setSelectedExtPointId(undefined);
      setInstallAppId(undefined);
    } catch (e) {
      message.error(formatStudioError(e, '安装失败'));
    } finally {
      setInstalling(false);
    }
  };

  return (
    <Card>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="插件市场（详设 §12.2）"
        description="目录由 classpath:marketplace/items.json 提供；安装会把条目落地为扩展实现并绑定到匹配的扩展点。"
      />
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          placeholder="搜索名称/描述"
          value={keyword}
          allowClear
          onChange={(e) => setKeyword(e.target.value)}
          onSearch={() => load()}
          style={{ width: 280 }}
        />
        <Select
          allowClear
          placeholder="按分类筛选"
          style={{ width: 180 }}
          value={category}
          options={categories.map((c) => ({ value: c, label: c }))}
          onChange={(v) => {
            setCategory(v);
          }}
          onClear={() => setCategory(undefined)}
        />
        <Button icon={<ReloadOutlined />} onClick={() => load()}>
          刷新
        </Button>
      </Space>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin />
        </div>
      ) : items.length === 0 ? (
        <Empty description="暂无市场条目" />
      ) : (
        <Row gutter={[16, 16]}>
          {items.map((item) => (
            <Col key={item.id} xs={24} sm={12} md={8} xl={6}>
              <Card
                size="small"
                hoverable
                title={
                  <Space direction="vertical" size={0}>
                    <Typography.Text strong>{item.name}</Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {item.vendor ?? '—'} · v{item.version ?? '1.0.0'}
                    </Typography.Text>
                  </Space>
                }
                extra={
                  item.category ? <Tag color="blue">{item.category}</Tag> : null
                }
                actions={[
                  <AuthButton
                    key="install"
                    code={BonePermissionCodes.EXTENSION_MARKETPLACE_INSTALL}
                    type="link"
                    icon={<CloudDownloadOutlined />}
                    disabled={item.installed}
                    onClick={() => setInstallTarget(item)}
                  >
                    {item.installed ? '已安装' : '安装'}
                  </AuthButton>,
                ]}
              >
                <Typography.Paragraph ellipsis={{ rows: 2 }} style={{ minHeight: 44 }}>
                  {item.description}
                </Typography.Paragraph>
                <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 4 }}>
                  接口：<code>{item.extPointInterface ?? '—'}</code>
                </Typography.Paragraph>
                <Space wrap size={4}>
                  {(item.tags ?? []).map((t) => (
                    <Tag key={t}>{t}</Tag>
                  ))}
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
      )}
      {/* 「确认安装」是 antd 内部渲染的确定按钮，不能替换成 AuthButton；
          用 <Auth> 包住整个 Modal，无权限时弹窗与确定按钮都不渲染。 */}
      <Auth code={BonePermissionCodes.EXTENSION_MARKETPLACE_INSTALL}>
        <Modal
          open={!!installTarget}
          title={installTarget ? `安装 ${installTarget.name}` : '安装'}
          confirmLoading={installing}
          onCancel={() => {
            setInstallTarget(null);
            setSelectedExtPointId(undefined);
          }}
          onOk={handleInstallConfirm}
          okText="确认安装"
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Paragraph>
              将创建一条扩展实现（className=<code>{installTarget?.className}</code>），并将其
              绑定到扩展点。若不显式选择，将按接口名 <code>{installTarget?.extPointInterface}</code>{' '}
              自动匹配。
            </Typography.Paragraph>
            <Select
              allowClear
              placeholder="扩展点（不选则自动匹配接口）"
              options={extPointOptions}
              value={selectedExtPointId}
              onChange={setSelectedExtPointId}
              style={{ width: '100%' }}
            />
            {/* 应用 ID 是雪花 ID（19 位），必须文本输入：InputNumber 走 number 会丢精度 */}
            <Input
              placeholder="归属应用 ID（留空 = 平台通用，5a G5）"
              value={installAppId}
              onChange={(e) => setInstallAppId(e.target.value || undefined)}
              style={{ width: '100%' }}
            />
          </Space>
        </Modal>
      </Auth>
    </Card>
  );
};

export default Marketplace;
