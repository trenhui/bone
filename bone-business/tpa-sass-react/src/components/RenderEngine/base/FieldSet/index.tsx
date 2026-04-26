import { useState, useMemo, useContext } from "react";
import { Row, Col, Button, ConfigProvider } from "antd";
import { ArrowDownOutlined, ArrowUpOutlined } from "@ant-design/icons";
import { ScopeContext } from "../../hooks/useScopeData";
import { useDataBinding } from "../../hooks/useDataBinding";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { FieldSetProps, defaultFieldSetProps } from "./props";

export function FieldSet(props: FieldSetProps) {
  const { id, name, body, children } = { ...defaultFieldSetProps, ...props };

  const scopeContext = useContext(ScopeContext);
  const dataManager = scopeContext?.data?.dataManager;
  const modalManager = scopeContext?.data?.modalManager;
  const displayMode = scopeContext?.data?.displayMode;
  const updateSchema = scopeContext?.data?.updateSchema;

  const [isExpand, setIsExpand] = useState(true);

  const rowGroups = useMemo(() => {
    if (!body || body.length === 0) return [];

    const groupedItems: Record<string, any[]> = {};

    body.forEach((item) => {
      if (!groupedItems[item.row]) {
        groupedItems[item.row] = [];
      }
      groupedItems[item.row].push(item);
    });

    const newGroupedItems: Record<string, any[]> = {};
    Object.entries(groupedItems).forEach(([row, group]) => {
      group.sort((a, b) => a.col - b.col);
      let currentRow = 0;
      let currentRowWidth = 0;

      group.forEach((item) => {
        const itemWidth = item.width || 1;
        if (currentRowWidth + itemWidth > 5) {
          currentRow++;
          currentRowWidth = 0;
        }

        const newRowKey = `${row}-${currentRow}`;
        if (!newGroupedItems[newRowKey]) {
          newGroupedItems[newRowKey] = [];
        }

        newGroupedItems[newRowKey].push(item);
        currentRowWidth += itemWidth;
      });
    });

    const sortedRowKeys = Object.keys(newGroupedItems).sort((a, b) => {
      const [rowA, subRowA] = a.split("-").map(Number);
      const [rowB, subRowB] = b.split("-").map(Number);
      return rowA - rowB || subRowA - subRowB;
    });

    return sortedRowKeys.map((rowKey) => newGroupedItems[rowKey]);
  }, [body]);

  const isSupportConfig = displayMode === DisplayModeEnum.CONFIG;

  const handleEdit = () => {
    modalManager?.showModal(
      "UpdateFieldSetPropsDrawer",
      { id },
      (isChange = false) => {
        if (isChange) {
          updateSchema?.(DisplayModeEnum.CONFIG);
        }
      }
    );
  };

  const handleSort = () => {
    modalManager?.showModal(
      "UpdateFieldSetSortDrawer",
      { id },
      (isChange = false) => {
        if (isChange) {
          updateSchema?.(DisplayModeEnum.CONFIG);
        }
      }
    );
  };

  const setValueInData = (fieldItem: any, value: any) => {
    const { targetDataBinding } = useDataBinding(fieldItem?.dataBinding);
    dataManager?.setByJp(targetDataBinding, value);
  };

  const getFieldTargetProps = (fieldItem: any) => {
    const { targetDataBinding, targetProp } = useDataBinding(
      fieldItem?.dataBinding
    );

    return {
      ...fieldItem,
      targetValue: dataManager?.getByJp(targetDataBinding),
      targetProp: targetProp,
    };
  };

  const handelFieldChange = (newVal: any, item: any) => {
    setValueInData(item, newVal);
  };

  const renderChildComponent = (item: any) => {
    const ComponentName = item.type.startsWith("PK")
      ? item.type
      : `PK${item.type}`;

    const Component =
      require(`../${item.type.startsWith("PK") ? item.type.slice(2) : item.type}`).default;

    if (!Component) {
      console.warn(`组件 ${item.type} 未找到`);
      return null;
    }

    const itemProps = getFieldTargetProps(item);

    return (
      <Component
        {...itemProps}
        key={item.id}
        onChange={(newVal: any) => handelFieldChange(newVal, item)}
      />
    );
  };

  return (
    <div className="field-set" style={{ marginBottom: "16px" }}>
      <div
        className="field-set__header"
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: "8px",
        }}
      >
        <div
          className="field-set__title"
          style={{
            paddingLeft: "5px",
            fontSize: "14px",
            fontWeight: 600,
            color: "var(--el-text-color-regular, #303133)",
            borderLeft: "3px solid var(--el-color-primary, #1890ff)",
          }}
        >
          <span>{name}</span>
        </div>
        <div style={{ display: "flex", alignItems: "center" }}>
          {isSupportConfig && (
            <>
              <Button type="primary" onClick={handleEdit} style={{ marginRight: "8px" }}>
                批量编辑
              </Button>
              <Button type="primary" onClick={handleSort}>
                字段排序
              </Button>
            </>
          )}
          <span
            style={{
              marginLeft: "8px",
              color: "var(--el-text-color-regular, #303133)",
              transition: "transform 0.3s",
              cursor: "pointer",
              transform: isExpand ? "rotate(0deg)" : "rotate(180deg)",
            }}
            onClick={() => setIsExpand(!isExpand)}
          >
            {isExpand ? <ArrowDownOutlined /> : <ArrowUpOutlined />}
          </span>
        </div>
      </div>

      {isExpand && (
        <div>
          {rowGroups.map((rowGroup, index) => (
            <div
              key={index}
              className="field-set__row"
              style={{
                display: "flex",
                flex: 1,
                flexDirection: "column",
                margin: "0 10px",
              }}
            >
              <Row gutter={16} className="custom-row" style={{ display: "flex", flexWrap: "wrap" }}>
                {rowGroup.map((item) => (
                  <Col
                    key={item.id}
                    style={{
                      flexGrow: 0,
                      flexShrink: 0,
                      flexBasis: `${item.width * 20}%`,
                    }}
                  >
                    {renderChildComponent(item)}
                  </Col>
                ))}
              </Row>
            </div>
          ))}
          {children}
        </div>
      )}
    </div>
  );
}

export default FieldSet;
