import React, { useState } from 'react';
import { DndProvider, useDrag, useDrop } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import { Card, Layout, Menu, Button, Form, Input, Select, Switch, Slider, Checkbox, Radio, Space, Col, Row } from 'antd';
import './DraggableConfig.css';

const { Sider, Content } = Layout;
const { Option } = Select;

// 可拖拽的配置项组件
const DraggableItem = ({ id, type, title, onDelete }) => {
  const [{ isDragging }, drag] = useDrag({
    type: 'CONFIG_ITEM',
    item: { id, type, title },
    collect: (monitor) => ({
      isDragging: monitor.isDragging(),
    }),
  });

  return (
    <div
      ref={drag}
      className={`draggable-item ${isDragging ? 'dragging' : ''}`}
      style={{
        opacity: isDragging ? 0.5 : 1,
      }}
    >
      <Card title={title} size="small" actions={[
        <Button key="delete" danger onClick={onDelete}>删除</Button>
      ]}>
        <p>类型: {type}</p>
      </Card>
    </div>
  );
};

// 放置区域组件
const DropZone = ({ items, onDrop, onDelete }) => {
  const [{ isOver }, drop] = useDrop({
    accept: 'CONFIG_ITEM',
    drop: (item) => onDrop(item),
    collect: (monitor) => ({
      isOver: monitor.isOver(),
    }),
  });

  return (
    <div
      ref={drop}
      className={`drop-zone ${isOver ? 'over' : ''}`}
    >
      <h3>配置区域</h3>
      {items.length === 0 ? (
        <p>拖拽配置项到这里</p>
      ) : (
        <div className="config-items">
          {items.map((item) => (
            <DraggableItem
              key={item.id}
              id={item.id}
              type={item.type}
              title={item.title}
              onDelete={() => onDelete(item.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
};

// 配置项库组件
const ConfigLibrary = ({ onDragStart }) => {
  const configTypes = [
    { id: 'input', type: 'input', title: '输入框' },
    { id: 'select', type: 'select', title: '下拉选择' },
    { id: 'switch', type: 'switch', title: '开关' },
    { id: 'slider', type: 'slider', title: '滑块' },
    { id: 'checkbox', type: 'checkbox', title: '复选框' },
    { id: 'radio', type: 'radio', title: '单选框' },
  ];

  return (
    <div className="config-library">
      <h3>配置库</h3>
      <div className="library-items">
        {configTypes.map((item) => (
          <DraggableItem
            key={item.id}
            id={item.id}
            type={item.type}
            title={item.title}
            onDelete={() => {}}
          />
        ))}
      </div>
    </div>
  );
};

// 主拖拽配置组件
const DraggableConfig = () => {
  const [configItems, setConfigItems] = useState([]);
  const [nextId, setNextId] = useState(1);

  const handleDrop = (item) => {
    const newItem = {
      id: nextId,
      type: item.type,
      title: `${item.title} ${nextId}`,
    };
    setConfigItems([...configItems, newItem]);
    setNextId(nextId + 1);
  };

  const handleDelete = (id) => {
    setConfigItems(configItems.filter((item) => item.id !== id));
  };

  return (
    <DndProvider backend={HTML5Backend}>
      <Layout style={{ height: '100vh' }}>
        <Sider width={300} theme="light">
          <ConfigLibrary onDragStart={() => {}} />
        </Sider>
        <Layout>
          <Content style={{ padding: '24px' }}>
            <DropZone
              items={configItems}
              onDrop={handleDrop}
              onDelete={handleDelete}
            />
          </Content>
        </Layout>
      </Layout>
    </DndProvider>
  );
};

export default DraggableConfig;
