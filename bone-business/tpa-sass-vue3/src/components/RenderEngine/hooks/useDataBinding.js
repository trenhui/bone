import { computed } from "vue";

export function useDataBinding(dataBinding, tableIndex = -1) {
  const targetDataBinding = computed(() => {
    let result = "";

    if (tableIndex === -1 && dataBinding) {
      result = dataBinding;
    }

    if (tableIndex !== -1 && dataBinding) {
      result = `$[${tableIndex}].${dataBinding.slice(2)}`;
    }

    return result;
  });

  const targetProp = computed(() => targetDataBinding.value.slice(2));

  return { targetDataBinding, targetProp };
}
