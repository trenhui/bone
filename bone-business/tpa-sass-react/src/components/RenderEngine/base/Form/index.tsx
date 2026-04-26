
import React, { useCallback, useMemo, useEffect, useRef } from "react";
import { Form as AntForm } from "antd";
import { useScopeData } from "../../hooks/useScopeData";
import { isEmpty } from "lodash-es";
import { FormProps, defaultFormProps } from "./props";
import PKInput from "../Input";
import PKSelectDrop from "../SelectDrop";
import PKBlock from "../Block";
import PKMainBlock from "../MainBlock";

const componentRegistry = {
  PKInput,
  PKSelectDrop,
  PKBlock,
  PKMainBlock,
};

const PKForm: React.FC<FormProps> = (props) => {
  const mergedProps = { ...defaultFormProps, ...props };
  const [form] = AntForm.useForm();
  const scopeData = useScopeData();

  const validateManager = scopeData.getData("validateManager");
  const rulesManager = scopeData.getData("rulesManager");

  const submitRuleList = useMemo(() => rulesManager?.get(2, "Form") || [], [rulesManager]);

  const validateFields = useCallback(async () => {
    try {
      await form.validateFields();
      return { success: true };
    } catch (error: any) {
      const messages = error.errorFields?.map((item: any) => item.errors[0]) || [];
      return { success: false, messages };
    }
  }, [form]);

  const validate = useCallback(async () => {
    const formValidResult = await validateFields();

    if (isEmpty(submitRuleList)) {
      return formValidResult;
    }

    return {
      success: formValidResult.success,
      messages: formValidResult.messages || [],
    };
  }, [validateFields, submitRuleList]);

  useEffect(() => {
    validateManager?.push?.(validate);
  }, [validateManager, validate]);

  const renderComponent = useCallback((schemaNode: any): React.ReactNode => {
    if (!schemaNode) return null;

    const Component = componentRegistry[schemaNode.type as keyof typeof componentRegistry];
    
    if (!Component) {
      console.warn(`组件 ${schemaNode.type} 未找到`);
      return null;
    }

    return <Component key={schemaNode.id} {...schemaNode} />;
  }, []);

  const stickyItems = mergedProps.body?.slice(0, 2);
  const regularItems = mergedProps.body?.slice(2);

  return (
    <div className={`w-full bg-gray-50 ${mergedProps.body?.length > 0 ? "pt-1" : ""}`}>
      <AntForm form={form} labelPosition="top">
        <div className="bg-white mb-2 mx-2 rounded-lg">
          {mergedProps.isAffix && (
            <div className="sticky top-0 z-50 bg-white shadow-lg">
              {stickyItems?.map((item: any) => (
                <div key={item.id} className="border-b border-gray-200">
                  {renderComponent(item)}
                </div>
              ))}
            </div>
          )}
          
          {!mergedProps.isAffix && stickyItems?.map((item: any) => (
            <div key={item.id} className="border-b border-gray-200">
              {renderComponent(item)}
            </div>
          ))}

          {regularItems?.map((item: any, index: number) => (
            <div 
              key={item.id} 
              className={index < regularItems.length - 1 ? "border-b border-gray-200" : ""}
            >
              {renderComponent(item)}
            </div>
          ))}
        </div>

        <div className="sticky bottom-0 z-10">
          <div className="flex justify-center w-full bg-white border-t border-gray-200 py-3">
            {/* PKButtonGroup 组件将在后续添加 */}
          </div>
        </div>
      </AntForm>
    </div>
  );
};

export default PKForm;
