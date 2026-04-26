
import { useComponentPropertyStore } from "@/store";
import { useMemo, useEffect } from "react";

interface UseBaseComponentPropertyResult {
  currentDisplayed: any;
  currentInputStatus: any;
  currentRequired: any;
  currentValueType: any;
  currentDisplay: any;
}

export const useBaseComponentProperty = (props: { id: string }): UseBaseComponentPropertyResult => {
  const store = useComponentPropertyStore();

  useEffect(() => {
    store.initComponent(props.id, {
      displayed: props.displayed,
      inputStatus: props.inputStatus,
      required: props.required,
      valueType: props.valueType,
      display: props.display,
    });

    return () => {
      store.removeComponent(props.id);
    };
  }, [store, props.id, props.displayed, props.inputStatus, props.required, props.valueType, props.display]);

  const componentState = useMemo(() => store.components[props.id], [store.components, props.id]);

  return {
    currentDisplayed: componentState?.displayed,
    currentInputStatus: componentState?.inputStatus,
    currentRequired: componentState?.required,
    currentValueType: componentState?.valueType,
    currentDisplay: componentState?.display,
  };
};
