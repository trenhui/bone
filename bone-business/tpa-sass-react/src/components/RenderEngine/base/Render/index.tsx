
import React, { useState, useEffect, useMemo, useCallback, useRef } from "react";
import { Empty } from "antd";
import { isBaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { ScopeProvider, useScopeData } from "../../hooks/useScopeData";
import { treeMapping } from "@/utils/treeUtils";
import { getValueByJsonPath, setValueByJsonPath } from "@/utils/jsonpathUtils";
import { isEmpty } from "lodash-es";
import PKInput from "../Input";
import PKSelectDrop from "../SelectDrop";
import PKDateTime from "../DateTime";
import PKInputNum from "../InputNum";
import PKSelectCtrl from "../SelectCtrl";
import PKDateRange from "../DateRange";
import PKFieldSet from "../FieldSet";
import PKForm from "../Form";
import PKPage from "../Page";
import PKBlock from "../Block";
import PKMainBlock from "../MainBlock";

interface RenderProps {
  schema: any;
  rules?: any;
  displayMode?: DisplayModeEnum;
  pageData?: any;
  data?: any;
  onDataChange?: (data: any) => void;
  onUpdateSchema?: (displayMode: DisplayModeEnum) => void;
}

const componentRegistry = {
  PKInput,
  PKSelectDrop,
  PKDateTime,
  PKInputNum,
  PKSelectCtrl,
  PKDateRange,
  PKFieldSet,
  PKForm,
  PKPage,
  PKBlock,
  PKMainBlock,
};

const RenderInternal: React.FC<RenderProps> = (props) => {
  const [modalVisible, setModalVisible] = useState(false);
  const [currentModalComponent, setCurrentModalComponent] = useState<string | null>(null);
  const [modalParams, setModalParams] = useState<any>({});
  const [modalCallback, setModalCallback] = useState<Function | null>(null);
  
  const componentMapRef = useRef<Map<string, any>>(new Map());
  const tableMapRef = useRef<Map<string, any>>(new Map());
  const validateListRef = useRef<Function[]>([]);
  
  const parentScopeData = useScopeData();
  const [data, setData] = useState<any>(props.data || {});

  useEffect(() => {
    if (props.data && JSON.stringify(props.data) !== JSON.stringify(data)) {
      setData(props.data);
    }
  }, [props.data]);

  const computedSchema = useMemo(() => {
    if (isEmpty(props.schema)) {
      return null;
    }

    try {
      const result = treeMapping(
        props.schema,
        (node: any) => node.body,
        (node: any, subNodes: any[]) => { node.body = subNodes; },
        (node: any) => {
          if (!node?.type) {
            console.warn("节点缺少type属性:", node);
            return node;
          }
          if (isBaseCompType(node.type) || node.type === "Table") {
            componentMapRef.current.set(node.id, node);
          }

          if (node.code === "relateLiability") {
            return { ...node, type: "PKCustom" };
          }

          return { ...node, type: "PK" + node.type };
        }
      );
      return result;
    } catch (error) {
      console.error("Schema映射失败:", error);
      return null;
    }
  }, [props.schema]);

  const getComponent = useCallback((id: string) => {
    return componentMapRef.current.get(id);
  }, []);

  const setComponent = useCallback((id: string, component: any) => {
    componentMapRef.current.set(id, component);
  }, []);

  const pushValidateToValidateList = useCallback((validate: Function) => {
    validateListRef.current.push(validate);
  }, []);

  const validate = useCallback(async () => {
    const allResult = { success: true, messages: [] as string[] };
    for (const validateFn of validateListRef.current) {
      const result = await Promise.resolve(validateFn());
      console.log("result", result);
      allResult.messages.push(...(result.messages || []));
      allResult.success = allResult.success && result.success;
    }
    return allResult;
  }, []);

  const getValueInData = useCallback((path: string) => {
    return path.split(".").reduce((acc, key) => acc?.[key], data);
  }, [data]);

  const setValueInData = useCallback((path: string, value: any) => {
    const parts = path.split(".");
    const last = parts.pop();
    if (!last) return;
    
    const newData = { ...data };
    const target = parts.reduce((acc, key) => {
      if (!acc[key]) {
        acc[key] = {};
      }
      return acc[key];
    }, newData);
    
    target[last] = value;
    setData(newData);
    props.onDataChange?.(newData);
  }, [data, props.onDataChange]);

  const initData = useCallback((initialData: any) => {
    const newData = { ...data, ...initialData };
    setData(newData);
    props.onDataChange?.(newData);
  }, [data, props.onDataChange]);

  const getValueInDataByJsonPath = useCallback((jsonPath: string) => {
    return getValueByJsonPath(data, jsonPath);
  }, [data]);

  const setValueInDataByJsonPath = useCallback((jsonPath: string, value: any) => {
    const newData = setValueByJsonPath(data, jsonPath, value);
    setData(newData);
    props.onDataChange?.(newData);
  }, [data, props.onDataChange]);

  const getTable = useCallback((id: string) => {
    return tableMapRef.current.get(id);
  }, []);

  const setTable = useCallback((id: string, table: any) => {
    tableMapRef.current.set(id, table);
  }, []);

  const getTableRow = useCallback((id: string, index: number) => {
    return tableMapRef.current.get(id)?.[index];
  }, []);

  const setTableRow = useCallback((id: string, index: number, row: any) => {
    const table = tableMapRef.current.get(id);
    if (table) {
      table[index] = row;
    }
  }, []);

  const getTableValueByJsonPath = useCallback((id: string, jsonPath: string) => {
    const table = tableMapRef.current.get(id);
    return getValueByJsonPath(table, jsonPath);
  }, []);

  const setTableValueByJsonPath = useCallback((id: string, jsonPath: string, value: any) => {
    const table = tableMapRef.current.get(id);
    if (table) {
      setValueByJsonPath(table, jsonPath, value);
    }
  }, []);

  const showModal = useCallback((componentName: string, componentParams: any, callback: Function | null = null) => {
    setCurrentModalComponent(componentName);
    setModalParams(componentParams);
    setModalCallback(() => callback);
    setModalVisible(true);
  }, []);

  const closeModal = useCallback((params: any = null) => {
    setModalVisible(false);
    if (modalCallback) {
      modalCallback(params);
    }
    setTimeout(() => {
      setCurrentModalComponent(null);
      setModalParams({});
      setModalCallback(null);
    }, 300);
  }, [modalCallback]);

  const getRules = useCallback((type: number, id: string) => {
    switch (type) {
      case 1:
        return (
          props?.rules?.fieldLinkageRuleList?.filter(
            (rule: any) => rule.fieldId == id
          ) || []
        );
      case 2:
        return props?.rules?.submitRuleList || [];
      case 3:
        return (
          props?.rules?.fieldTableRuleVOList?.filter(
            (rule: any) => rule.currentField.id == id
          ) || []
        );
      case 4:
        return (
          props?.rules?.linkedDisplayRuleVOList?.find(
            (rule: any) => rule.selectFieldId == id
          )?.otherField || []
        );
      case 5:
        return (
          props?.rules?.rowEditRuleVOList?.filter((rule: any) => rule.tableId == id) ||
          []
        );
      case 6:
        return (
          props?.rules?.rowVerifyRuleVOList?.filter(
            (rule: any) => rule.tableId == id
          ) || []
        );
      case 7:
        return (
          props?.rules?.crossTableDataEditVOList?.filter(
            (rule: any) => rule.currentTableId == id
          ) || []
        );
      case 8:
        return (
          props?.rules?.crossTableDataVerifyRuleVOList?.filter(
            (rule: any) => rule.currentTableId == id
          ) || []
        );
      case 9:
        return (
          props?.rules?.groupAggregateRuleVOList?.filter(
            (rule: any) => rule.tableId == id
          ) || []
        );
      case 10:
        return (
          props?.rules?.summaryRuleList?.find((rule: any) => rule.tableId == id)
            ?.dataSummaryRuleList || []
        );
      default:
        return [];
    }
  }, [props.rules]);

  const updateSchema = useCallback((displayMode: DisplayModeEnum = props.displayMode || DisplayModeEnum.EDIT) => {
    props.onUpdateSchema?.(displayMode);
  }, [props.displayMode, props.onUpdateSchema]);

  const renderComponent = useCallback((schemaNode: any): React.ReactNode => {
    if (!schemaNode) return null;

    const Component = componentRegistry[schemaNode.type as keyof typeof componentRegistry];
    
    if (!Component) {
      console.warn(`组件 ${schemaNode.type} 未找到`);
      return null;
    }

    const childComponents = schemaNode.body?.map((child: any) => renderComponent(child));

    return (
      <Component key={schemaNode.id} {...schemaNode}>
        {childComponents}
      </Component>
    );
  }, []);

  useEffect(() => {
    return () => {
      componentMapRef.current.clear();
      tableMapRef.current.clear();
      validateListRef.current = [];
    };
  }, []);

  const scopeValue = useMemo(() => ({
    componentManager: {
      get: getComponent,
      set: setComponent,
    },
    modalManager: {
      showModal,
      closeModal,
    },
    validateManager: {
      push: pushValidateToValidateList,
      validate,
    },
    data,
    dataManager: {
      get: getValueInData,
      set: setValueInData,
      getByJp: getValueInDataByJsonPath,
      setByJp: setValueInDataByJsonPath,
      initData: initData,
    },
    tableMap: tableMapRef.current,
    tableManager: {
      get: getTable,
      set: setTable,
      getRow: getTableRow,
      setRow: setTableRow,
      getByJp: getTableValueByJsonPath,
      setByJp: setTableValueByJsonPath,
    },
    rulesManager: {
      get: getRules,
    },
    displayMode: props.displayMode || DisplayModeEnum.EDIT,
    updateSchema,
    ...props.pageData,
  }), [
    getComponent, setComponent,
    showModal, closeModal,
    pushValidateToValidateList, validate,
    data,
    getValueInData, setValueInData, getValueInDataByJsonPath, setValueInDataByJsonPath, initData,
    getTable, setTable, getTableRow, setTableRow, getTableValueByJsonPath, setTableValueByJsonPath,
    getRules,
    props.displayMode, updateSchema,
    props.pageData,
  ]);

  if (isEmpty(computedSchema)) {
    return <Empty />;
  }

  return (
    <ScopeProvider initialData={scopeValue}>
      <div>
        {renderComponent(computedSchema)}
      </div>
    </ScopeProvider>
  );
};

const Render: React.FC<RenderProps> = (props) => {
  return (
    <ScopeProvider initialData={props.pageData}>
      <RenderInternal {...props} />
    </ScopeProvider>
  );
};

export default Render;
