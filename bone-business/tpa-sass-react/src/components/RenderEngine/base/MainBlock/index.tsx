
import React, { useState, useMemo, useEffect, useCallback } from "react";
import { ArrowDownOutlined } from "@ant-design/icons";
import { useScopeData } from "../../hooks/useScopeData";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { MainBlockProps, defaultMainBlockProps } from "./props";
import PKInput from "../Input";
import PKSelectDrop from "../SelectDrop";

const PKMainBlock: React.FC<MainBlockProps> = (props) => {
  const mergedProps = { ...defaultMainBlockProps, ...props };
  const [isExpand, setIsExpand] = useState(false);
  const [processedValues, setProcessedValues] = useState<Map<string, any>>(new Map());
  const scopeData = useScopeData();

  const dataManager = scopeData.getData("dataManager");

  const displayItems = useMemo(() => {
    const firstBlock = mergedProps.body?.[0];
    if (!firstBlock?.body) return [];
    return firstBlock.body.filter(
      (item: any) => item.displayed === DisplayedEnum.show
    );
  }, [mergedProps.body]);

  const handleExpand = useCallback(() => {
    setIsExpand(!isExpand);
  }, [isExpand]);

  const getDataValue = useCallback(async (item: any) => {
    const value = dataManager?.getByJp?.(item.dataBinding);
    return value || "";
  }, [dataManager]);

  const processDisplayItems = useCallback(async () => {
    const newProcessedValues = new Map();
    for (const item of displayItems) {
      try {
        const value = await getDataValue(item);
        newProcessedValues.set(item.id, value);
      } catch (error) {
        console.error(`Error processing item ${item.id}:`, error);
        newProcessedValues.set(item.id, "");
      }
    }
    setProcessedValues(newProcessedValues);
  }, [displayItems, getDataValue]);

  useEffect(() => {
    processDisplayItems();
  }, [processDisplayItems]);

  const getProcessedValue = (item: any) => {
    return processedValues.get(item.id) || "";
  };

  const visibleItems = isExpand ? displayItems : displayItems.slice(0, 6);

  return (
    <div className="py-3 px-5 relative group">
      <div className="flex justify-between items-center">
        <div className="flex-1 flex items-center">
          <div className="grid w-full grid-cols-3 gap-2">
            {visibleItems.map((item: any) => (
              <div key={item.id} className="flex flex-row items-center">
                <span className="flex-shrink-0 text-sm text-gray-600">{item.showName}</span>
                <span className="ml-3 text-sm font-bold text-gray-900">
                  {getProcessedValue(item)}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="flex-shrink-0 w-[30%] flex justify-end items-center">
          {/* PKButtonGroup 组件将在后续添加 */}
        </div>
        <ArrowDownOutlined
          onClick={handleExpand}
          className={`absolute bottom-1 left-1/2 -translate-x-1/2 text-gray-400 opacity-0 group-hover:opacity-100 cursor-pointer active:scale-90 transition-all duration-300 ${
            isExpand ? "rotate-180" : ""
          }`}
        />
      </div>
    </div>
  );
};

export default PKMainBlock;
