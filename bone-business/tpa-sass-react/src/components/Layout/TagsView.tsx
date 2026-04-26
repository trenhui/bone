import React, { useState, useEffect, useRef } from 'react';
import { Tag, Dropdown, MenuProps } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTagsViewStore, useSettingsStore } from '@/store';
import { LayoutEnum } from '@/enums/LayoutEnum';
import './TagsView.css';

// 创建路由标题映射
const routeTitleMap: Record<string, string> = {
  '/dashboard': 'dashboard',
  '/myJob/myPrecheck': '我的初审',
  '/myJob/myEntry': '我的录入',
  '/myJob/myQualityCheck': '我的质检',
  '/myJob/myAudit': '我的审核',
  '/myJob/myReview': '我的复核',
  '/jobConfig/jobBaseConfig': '标准作业配置',
  '/jobConfig/bizIdentityList': '主体专属列表',
  '/jobConfig/bizIdentityConfig': '主体专属配置',
  '/policyConfig/groupPolicyList': '团险保单管理',
  '/systemManage/modelManage': '数据模型管理',
  '/systemManage/optionConfig': '系统选项配置',
  '/systemManage/eventManage': '事件管理',
  '/systemManage/groupIndividualConfig': '团单个险配置',
  '/codeTools/codeGeneration': '代码生成',
  '/codeTools/dataSourceConfiguration': '数据源配置',
  '/claimManage/pushFail': '推送失败',
  '/claimManage/copyClaim': '复制赔案',
  '/jobManage/groupInsuranceSignList': '团险签收',
  '/jobManage/claimHandOver': '转交赔案',
  '/jobManage/claimDistribute': '分配赔案',
  '/jobManage/uploadRecord': '导入记录'
};

const TagsView: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const tagsViewStore = useTagsViewStore();
  const settingsStore = useSettingsStore();
  const containerRef = useRef<HTMLDivElement>(null);

  // 右键菜单状态
  const [contextMenu, setContextMenu] = useState({
    visible: false,
    left: 0,
    top: 0
  });
  const [selectedTag, setSelectedTag] = useState<TagView | null>(null);

  // 初始化标签 - 简单处理
  useEffect(() => {
    // 添加默认标签
    tagsViewStore.addVisitedView({
      name: 'Dashboard',
      title: 'dashboard',
      path: '/dashboard',
      fullPath: '/dashboard',
      affix: true,
      keepAlive: true
    });
  }, []);

  // 路由变化时添加标签
  useEffect(() => {
    if (location.pathname) {
      const title = routeTitleMap[location.pathname] || location.pathname.split('/').pop() || '页面';
      const tag: TagView = {
        name: location.pathname.replace(/\//g, '-').slice(1) || 'page',
        title,
        path: location.pathname,
        fullPath: location.pathname + location.search,
        affix: location.pathname === '/dashboard',
        keepAlive: true,
        query: Object.fromEntries(new URLSearchParams(location.search))
      };
      tagsViewStore.addView(tag);
    }
  }, [location.pathname, location.search]);

  // 关闭右键菜单
  useEffect(() => {
    const closeMenu = () => setContextMenu(prev => ({ ...prev, visible: false }));
    if (contextMenu.visible) {
      document.body.addEventListener('click', closeMenu);
    }
    return () => document.body.removeEventListener('click', closeMenu);
  }, [contextMenu.visible]);

  const handleTagClick = (path: string) => {
    navigate(path);
  };

  const handleTagClose = (e: React.MouseEvent, tag: TagView) => {
    e.preventDefault();
    e.stopPropagation();
    tagsViewStore.delView(tag).then((res) => {
      if (selectedTag && selectedTag.path === location.pathname) {
        const latestView = res.visitedViews[res.visitedViews.length - 1];
        if (latestView) {
          navigate(latestView.fullPath);
        }
      }
    });
  };

  const openContextMenu = (e: React.MouseEvent, tag: TagView) => {
    e.preventDefault();
    setSelectedTag(tag);

    const menuMinWidth = 105;
    const containerRect = containerRef.current?.getBoundingClientRect();
    if (!containerRect) return;

    const offsetLeft = containerRect.left;
    const offsetWidth = containerRect.width;
    const maxLeft = offsetWidth - menuMinWidth;
    let left = e.clientX - offsetLeft + 15;

    if (left > maxLeft) {
      left = maxLeft;
    }

    let top = e.clientY;
    if (settingsStore.layout === LayoutEnum.MIX) {
      top -= 50;
    }

    setContextMenu({ visible: true, left, top });
  };

  // 右键菜单处理函数
  const refreshSelectedTag = () => {
    if (!selectedTag) return;
    tagsViewStore.delCachedView(selectedTag);
    const { fullPath } = selectedTag;
    navigate('/redirect' + fullPath);
    setContextMenu(prev => ({ ...prev, visible: false }));
  };

  const closeSelectedTag = () => {
    if (!selectedTag) return;
    handleTagClose({ preventDefault: () => {}, stopPropagation: () => {} } as React.MouseEvent, selectedTag);
    setContextMenu(prev => ({ ...prev, visible: false }));
  };

  const closeOtherTags = () => {
    if (!selectedTag) return;
    navigate(selectedTag.path);
    tagsViewStore.delOtherViews(selectedTag);
    setContextMenu(prev => ({ ...prev, visible: false }));
  };

  const closeLeftTags = () => {
    if (!selectedTag) return;
    tagsViewStore.delLeftViews(selectedTag).then((res) => {
      if (!res.visitedViews.find((item: TagView) => item.path === location.pathname)) {
        const latestView = res.visitedViews[res.visitedViews.length - 1];
        if (latestView) {
          navigate(latestView.fullPath);
        }
      }
    });
    setContextMenu(prev => ({ ...prev, visible: false }));
  };

  const closeRightTags = () => {
    if (!selectedTag) return;
    tagsViewStore.delRightViews(selectedTag).then((res) => {
      if (!res.visitedViews.find((item: TagView) => item.path === location.pathname)) {
        const latestView = res.visitedViews[res.visitedViews.length - 1];
        if (latestView) {
          navigate(latestView.fullPath);
        }
      }
    });
    setContextMenu(prev => ({ ...prev, visible: false }));
  };

  const closeAllTags = () => {
    if (!selectedTag) return;
    tagsViewStore.delAllViews().then((res) => {
      const latestView = res.visitedViews[res.visitedViews.length - 1];
      if (latestView) {
        navigate(latestView.fullPath);
      }
    });
    setContextMenu(prev => ({ ...prev, visible: false }));
  };

  const isFirstView = () => {
    if (!selectedTag) return false;
    return selectedTag.path === '/dashboard' || 
           (tagsViewStore.visitedViews.length > 1 && selectedTag.fullPath === tagsViewStore.visitedViews[1].fullPath);
  };

  const isLastView = () => {
    if (!selectedTag) return false;
    return selectedTag.fullPath === tagsViewStore.visitedViews[tagsViewStore.visitedViews.length - 1].fullPath;
  };

  const menuItems = [
    {
      key: 'refresh',
      label: '刷新',
      onClick: refreshSelectedTag,
      disabled: false
    },
    {
      key: 'close',
      label: '关闭',
      disabled: selectedTag?.affix || false,
      onClick: closeSelectedTag,
    },
    {
      key: 'closeOther',
      label: '关闭其它',
      onClick: closeOtherTags,
      disabled: false
    },
    {
      key: 'closeLeft',
      label: '关闭左侧',
      disabled: isFirstView(),
      onClick: closeLeftTags,
    },
    {
      key: 'closeRight',
      label: '关闭右侧',
      disabled: isLastView(),
      onClick: closeRightTags,
    },
    {
      key: 'closeAll',
      label: '关闭所有',
      onClick: closeAllTags,
      disabled: false
    },
  ];

  return (
    <div className="tags-container" ref={containerRef}>
      <div className="scroll-container" style={{ position: 'relative', width: '100%', overflow: 'hidden', whiteSpace: 'nowrap' }}>
        {tagsViewStore.visitedViews.map((tag: TagView) => (
          <Tag
            key={tag.fullPath}
            color={tag.path === location.pathname ? 'blue' : ''}
            closable={!tag.affix}
            onClose={(e) => handleTagClose(e, tag)}
            onClick={() => handleTagClick(tag.path)}
            onContextMenu={(e) => openContextMenu(e, tag)}
            className={`tags-item ${tag.path === location.pathname ? 'active' : ''}`}
          >
            {tag.title}
          </Tag>
        ))}
      </div>

      {/* 右键菜单 */}
      {contextMenu.visible && (
        <div
          className="contextmenu"
          style={{
            position: 'absolute',
            zIndex: 99,
            fontSize: 12,
            background: '#fff',
            borderRadius: 4,
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
            left: contextMenu.left,
            top: contextMenu.top,
          }}
        >
          <div style={{ listStyle: 'none', padding: '4px 0', margin: 0 }}>
            {menuItems.map((item) => {
              if (!item) return null;
              const isDisabled = !!item.disabled;
              return (
                <div
                  key={item.key}
                  style={{
                    padding: '8px 16px',
                    cursor: isDisabled ? 'not-allowed' : 'pointer',
                    color: isDisabled ? 'rgba(0,0,0,0.25)' : 'inherit',
                  }}
                  onClick={!isDisabled ? item.onClick : undefined}
                >
                  {item.label}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

export default TagsView;
