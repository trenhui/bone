import React, { useState, useEffect, useMemo } from 'react';
import { Button, Space, Popconfirm, message } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useScopeData } from '../../hooks/useScopeData';
import EventAPI from '@/api/event';
import eventRouter from '@/event/eventRouter';
import { BUTTON_NEED_LOADING_EVENT_CODE } from '@/constants';

// Enums
import { DisplayModeEnum } from '@/enums/DisplayModeEnum';
import { DisplayTypeEnum } from '@/enums/event/DisplayTypeEnum';
import { getDisplayLevel } from '@/enums/event/DisplayLevelEnum';

interface ButtonItem {
  id: string;
  eventCode: string;
  label: string;
  style: number;
  displayType: DisplayTypeEnum;
}

interface ButtonGroupProps {
  buttonList?: ButtonItem[];
  ownerId: string;
  owner: string;
  align?: string;
  isTableRow?: boolean;
  tableRow?: any;
  tableIndex?: number;
  children?: React.ReactNode;
}

const ButtonGroup: React.FC<ButtonGroupProps> = ({
  buttonList = [],
  ownerId,
  owner,
  align = 'left',
  isTableRow = false,
  tableRow,
  tableIndex,
  children
}) => {
  const scopeData = useScopeData();
  const modalManager = scopeData.getData('modalManager');
  const displayMode = scopeData.getData('displayMode') as DisplayModeEnum;
  const updateSchema = scopeData.getData('updateSchema') as Function;
  const [loading, setLoading] = useState<string[]>([]);

  // 处理表格行数据
  useEffect(() => {
    if (isTableRow) {
      scopeData.setDatas({
        tableRow,
        tableIndex,
      });
    }
  }, [tableRow, tableIndex, isTableRow, scopeData]);

  // 去重按钮列表
  const uniqueButtonList = useMemo(() => {
    const uniqueMap = new Map<string, ButtonItem>();
    buttonList.forEach((button) => {
      if (button.eventCode && !uniqueMap.has(button.eventCode)) {
        uniqueMap.set(button.eventCode, button);
      }
    });
    return Array.from(uniqueMap.values());
  }, [buttonList]);

  const isConfig = useMemo(() => {
    return displayMode === DisplayModeEnum.CONFIG;
  }, [displayMode]);

  const handleDeleteButton = async (id: string) => {
    try {
      await EventAPI.deleteEventTriggerById(id);
      message.success('删除成功');
      if (updateSchema) {
        updateSchema(DisplayModeEnum.CONFIG);
      }
    } catch (error) {
      console.error(error);
      message.error('删除失败');
    }
  };

  const handleCreateButton = () => {
    if (modalManager) {
      modalManager.showModal(
        'CreateEventButtonDialog',
        {
          id: ownerId,
          owner: owner,
        },
        (isChange = false) => {
          if (isChange && updateSchema) {
            updateSchema(DisplayModeEnum.CONFIG);
          }
        }
      );
    }
  };

  const addLoading = (eventCode: string) => {
    if (BUTTON_NEED_LOADING_EVENT_CODE.includes(eventCode)) {
      setLoading(prev => [...prev, eventCode]);
    }
  };

  const removeLoading = (eventCode: string) => {
    setLoading(prev => prev.filter(code => code !== eventCode));
  };

  const isLoading = (eventCode: string) => {
    return loading.includes(eventCode);
  };

  // 设置加载状态方法到scopeData
  useEffect(() => {
    scopeData.setDatas({
      setLoading: addLoading,
      removeLoading: removeLoading,
    });
  }, [scopeData, addLoading, removeLoading]);

  const handleButtonsClick = (item: ButtonItem) => {
    eventRouter(item.eventCode, scopeData.getData);
  };

  const getButtonType = (style: number) => {
    const level = getDisplayLevel(style);
    switch (level) {
      case 'primary':
        return 'primary';
      case 'success':
        return 'success';
      case 'warning':
        return 'warning';
      case 'danger':
        return 'danger';
      default:
        return 'default';
    }
  };

  const justifyContent = useMemo(() => {
    switch (align) {
      case 'right':
        return 'flex-end';
      case 'center':
        return 'center';
      default:
        return 'flex-start';
    }
  }, [align]);

  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '8px', justifyContent }}>
      {/* 预设事件 */}
      {children}

      {/* 自定义事件 */}
      {uniqueButtonList.map((item) => (
        <div key={item.id} style={{ position: 'relative' }}>
          <Button
            size={isTableRow ? 'small' : 'default'}
            disabled={isConfig}
            type={getButtonType(item.style)}
            ghost={item.displayType === DisplayTypeEnum.LINK}
            onClick={() => handleButtonsClick(item)}
            loading={isLoading(item.eventCode)}
          >
            {item.label}
          </Button>

          {/* 删除按钮 */}
          {isConfig && (
            <div style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: 'rgba(0, 0, 0, 0.3)',
              borderRadius: '4px',
              opacity: 0,
              transition: 'opacity 0.2s',
              '&:hover': {
                opacity: 1
              }
            }} onMouseEnter={(e) => {
              e.currentTarget.style.opacity = '1';
            }} onMouseLeave={(e) => {
              e.currentTarget.style.opacity = '0';
            }}>
              <Popconfirm
                title="请确认删除该按钮吗？"
                onConfirm={() => handleDeleteButton(item.id)}
                okText="确定"
                cancelText="取消"
              >
                <DeleteOutlined style={{ color: '#fff', cursor: 'pointer' }} />
              </Popconfirm>
            </div>
          )}
        </div>
      ))}

      {/* 添加按钮 */}
      {isConfig && (
        <Button
          type="default"
          size="small"
          shape="circle"
          icon={<PlusOutlined />}
          onClick={handleCreateButton}
        />
      )}
    </div>
  );
};

export default ButtonGroup;