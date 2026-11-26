import type { ReactElement } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button, Tooltip } from 'antd';
import { SearchOutlined } from '@ant-design/icons';

/**
 * 全局案件速查入口：在智能理赔所有菜单下常驻，用户不必先猜「该去哪个工作台找案件」。
 *
 * <p>不做成一个悬浮的查询结果面板——那会让每个页面各自持有查询状态，且与 URL 真源冲突。
 * 这里只负责跳转到统一查询页，筛选、分页、保存视图全部收敛在 CaseQueryPage。
 */
export function GlobalCaseFinder(): ReactElement | null {
  const navigate = useNavigate();
  const location = useLocation();
  if (location.pathname.endsWith('/case-query')) {
    return null;
  }
  return (
    <Tooltip title="按案件号 / 状态 / 时间 / 金额等条件查找案件">
      <Button
        type="primary"
        icon={<SearchOutlined />}
        aria-label="案件速查"
        onClick={() => navigate('/case-query')}
        style={{
          position: 'fixed',
          right: 24,
          bottom: 24,
          zIndex: 1000,
          boxShadow: '0 6px 16px rgba(0,0,0,0.16)',
        }}
      >
        案件速查
      </Button>
    </Tooltip>
  );
}
