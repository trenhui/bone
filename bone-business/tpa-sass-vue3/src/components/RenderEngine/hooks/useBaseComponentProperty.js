import { useComponentPropertyStore } from "@/store";

export const useBaseComponentProperty = (props) => {
  const store = useComponentPropertyStore();

  // 初始化组件的状态（只在第一次 setup 时做一次）
  store.initComponent(props.id, {
    displayed: props.displayed,
    inputStatus: props.inputStatus,
    required: props.required,
    valueType: props.valueType,
    display: props.display,
  });

  onBeforeUnmount(() => {
    store.removeComponent(props.id);
  });

  // 响应式获取这个组件的状态
  const componentState = computed(() => store.components[props.id]);

  return {
    currentDisplayed: computed(() => componentState.value?.displayed),
    currentInputStatus: computed(() => componentState.value?.inputStatus),
    currentRequired: computed(() => componentState.value?.required),
    currentValueType: computed(() => componentState.value?.valueType),
    currentDisplay: computed(() => componentState.value?.display),
  };
};
