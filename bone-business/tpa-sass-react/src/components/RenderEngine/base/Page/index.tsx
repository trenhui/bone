
import React, { useState, useEffect, useMemo } from "react";
import { Tooltip } from "antd";
import { FlagOutlined } from "@ant-design/icons";
import { useScopeData } from "../../hooks/useScopeData";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { PageProps, defaultPageProps } from "./props";
import PKForm from "../Form";
import PKBlock from "../Block";
import PKMainBlock from "../MainBlock";

const componentRegistry = {
  PKForm,
  PKBlock,
  PKMainBlock,
};

const PKPage: React.FC<PageProps> = (props) => {
  const mergedProps = { ...defaultPageProps, ...props };
  const [isAffix, setIsAffix] = useState(false);
  const scopeData = useScopeData();

  const dataManager = scopeData.getData("dataManager");

  const isExclusivePage = useMemo(() => mergedProps.pageType === 1, [mergedProps.pageType]);

  useEffect(() => {
    scopeData.setDatas({
      isExclusivePage,
      pageCode: mergedProps.code,
      pageId: mergedProps.id,
      bizIdentityId: mergedProps.bizInfo?.id,
      bizIdentityCode: mergedProps.bizInfo?.code,
    });
  }, [scopeData, isExclusivePage, mergedProps]);

  const handleAffixChange = () => {
    setIsAffix(!isAffix);
  };

  const getOperatorName = () => {
    const operatorNames = dataManager?.get("operatorNames") || [];
    return `初审人员：${operatorNames[0] || ""}<br>录入人员：${operatorNames[1] || ""}<br>质检人员：${operatorNames[2] || ""}<br>审核人员：${operatorNames[3] || ""}<br>复核人员：${operatorNames[4] || ""}`;
  };

  const getReturnReason = () => {
    const rejectReason = dataManager?.get("rejectReason") || "";
    const returnReason = dataManager?.get("returnReason") || "";
    if (rejectReason && returnReason) {
      return `复核驳回原因：${rejectReason}<br>退回原因：${returnReason}`;
    } else if (rejectReason) {
      return `复核驳回原因：${rejectReason}`;
    } else if (returnReason) {
      return `退回原因：${returnReason}`;
    }
    return "";
  };

  const renderComponent = (schemaNode: any): React.ReactNode => {
    if (!schemaNode) return null;

    const Component = componentRegistry[schemaNode.type as keyof typeof componentRegistry];
    
    if (!Component) {
      console.warn(`组件 ${schemaNode.type} 未找到`);
      return null;
    }

    return <Component key={schemaNode.id} {...schemaNode} isAffix={isAffix} />;
  };

  return (
    <div>
      <div className="sticky top-0 z-10 bg-white px-6 py-3 border-b border-gray-200 flex">
        <div className="flex-1 flex-shrink-0">
          <span className="text-lg font-bold text-gray-900">
            {mergedProps.name}
            {mergedProps.displayMode === DisplayModeEnum.PREVIEW ? " - 预览状态" : ""}
          </span>
          {mergedProps.bizInfo && (
            <span className="text-sm ml-2 text-gray-600">
              <span className="mr-2">主体类型</span>
              <span>{mergedProps.bizInfo.bizType}</span>
              <span className="ml-4 mr-2">主体名称</span>
              <span>{mergedProps.bizInfo.name}</span>
            </span>
          )}
        </div>
        <div className="flex-1 text-xs text-gray-600 flex gap-4 justify-end items-start min-w-0">
          <Tooltip title={getReturnReason()}>
            <div className="flex flex-col min-w-0 flex-1 max-w-lg text-red-500">
              {dataManager?.get("rejectReason") && (
                <span className="truncate">
                  复核驳回原因：{dataManager.get("rejectReason")}
                </span>
              )}
              {dataManager?.get("returnReason") && (
                <span className="truncate">
                  退回原因：{dataManager.get("returnReason")}
                </span>
              )}
            </div>
          </Tooltip>
          <div className="flex flex-col flex-shrink-0 whitespace-nowrap">
            <span>时效：{dataManager?.get("limitHour")}</span>
            <span>其中挂起时长：{dataManager?.get("hangUpTotalHour")}</span>
          </div>
          <div className="flex flex-col flex-shrink-0 whitespace-nowrap">
            <span>登录账号：{dataManager?.get("loginAccount")}</span>
            <Tooltip title={getOperatorName()}>
              <span>当前作业：{dataManager?.get("currentOperatorName")}</span>
            </Tooltip>
          </div>
        </div>
        {mergedProps.displayMode !== DisplayModeEnum.CONFIG && (
          <div className="flex-shrink-0 ml-3 flex items-center">
            <FlagOutlined
              onClick={handleAffixChange}
              style={{ 
                color: isAffix ? "#409eff" : "#909399",
                cursor: "pointer"
              }}
            />
          </div>
        )}
      </div>

      {mergedProps.body?.map((item: any) => renderComponent(item))}
    </div>
  );
};

export default PKPage;
