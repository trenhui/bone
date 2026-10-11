import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Input, Modal, Space, Tag, Typography } from 'antd';

const { Text } = Typography;
import { metadataEntityApi } from '../services/metadataApi';
import {
  META_DELIVERY_RUNTIME,
  type MetaEntity,
} from '../types';

/**
 * 全局命令面板（⌘K / Ctrl+K）——
 * 跨「模型设计 / 业务对象 / 业务数据」三入口一致的快速跳转对象能力（业界低代码平台通用范式）。
 *
 * 组件自包含：内部管理 open/keyword/index 状态，并监听全局 ⌘K 开关；
 * 选中即跳转到对象详情。业务对象页（EntityManagement）已有等价的本地命令面板，
 * 该组件只挂在另外两个入口（模型设计 / 业务数据），避免同页双 ⌘K 监听冲突。
 */
const CommandPalette: React.FC = () => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [index, setIndex] = useState(0);
  const [results, setResults] = useState<MetaEntity[]>([]);
  const [loading, setLoading] = useState(false);

  // 全局 ⌘K / Ctrl+K 开关
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        setOpen((v) => !v);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // 关键字变化 → 拉取匹配对象（最多 8 条）
  useEffect(() => {
    if (!open) return;
    const kw = keyword.trim();
    let cancelled = false;
    setLoading(true);
    metadataEntityApi
      .page({ keyword: kw || undefined, page: 1, size: 8 })
      .then((res) => {
        if (!cancelled && res.code === 200) {
          setResults(res.data.records ?? []);
          setIndex(0);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, keyword]);

  const openEntity = (e: MetaEntity) => {
    navigate(`/entities/${e.id}`);
    setOpen(false);
    setKeyword('');
  };

  const current = useMemo(
    () => results[index] ?? results[0],
    [results, index],
  );

  return (
    <Modal
      title={
        <Space>
          <Text strong>跳转对象</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Ctrl / ⌘ + K · ↑↓ 选择 · Enter 打开
          </Text>
        </Space>
      }
      open={open}
      onCancel={() => {
        setOpen(false);
        setKeyword('');
      }}
      footer={null}
      width={520}
      destroyOnHidden
    >
      <Input
        autoFocus
        allowClear
        placeholder="输入对象名称 / 编码 / 表名，回车打开第一个匹配"
        value={keyword}
        onChange={(e) => {
          setKeyword(e.target.value);
          setIndex(0);
        }}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') {
            e.preventDefault();
            setIndex((i) => (results.length === 0 ? 0 : (i + 1) % results.length));
          } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setIndex((i) =>
              results.length === 0 ? 0 : (i - 1 + results.length) % results.length,
            );
          } else if (e.key === 'Enter') {
            e.preventDefault();
            if (current) openEntity(current);
          }
        }}
      />
      <div style={{ marginTop: 12, maxHeight: 320, overflow: 'auto' }}>
        {results.length === 0 ? (
          <Text
            type="secondary"
            style={{ display: 'block', textAlign: 'center', padding: '24px 0' }}
          >
            {keyword.trim()
              ? loading
                ? '搜索中…'
                : `无匹配「${keyword.trim()}」的对象`
              : '输入名称 / 编码 / 表名开始搜索'}
          </Text>
        ) : (
          results.map((e, i) => (
            <div
              key={e.id}
              onClick={() => openEntity(e)}
              onMouseEnter={() => setIndex(i)}
              style={{
                padding: '8px 10px',
                borderRadius: 6,
                cursor: 'pointer',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                background: i === index ? '#e6f4ff' : 'transparent',
              }}
            >
              <Space>
                <Text strong>{e.displayName}</Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {e.code}
                </Text>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {e.tableName}
                </Text>
              </Space>
              <Tag
                color={e.deliveryMode === META_DELIVERY_RUNTIME ? 'purple' : 'default'}
                style={{ marginInlineEnd: 0 }}
              >
                {e.deliveryMode === META_DELIVERY_RUNTIME ? '自动界面' : '手写界面'}
              </Tag>
            </div>
          ))
        )}
      </div>
      {results.length > 0 && (
        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
          共 {results.length} 条（最多显示 8 条）
        </Text>
      )}
    </Modal>
  );
};

export default CommandPalette;
