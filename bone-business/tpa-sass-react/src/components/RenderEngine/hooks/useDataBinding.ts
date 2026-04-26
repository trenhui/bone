
import { useMemo } from "react";

export function useDataBinding(dataBinding?: string, tableIndex = -1) {
  const targetDataBinding = useMemo(() => {
    let result = "";

    if (tableIndex === -1 && dataBinding) {
      result = dataBinding;
    }

    if (tableIndex !== -1 && dataBinding) {
      result = `$[${tableIndex}].${dataBinding.slice(2)}`;
    }

    return result;
  }, [dataBinding, tableIndex]);

  const targetProp = useMemo(() => targetDataBinding.slice(2), [targetDataBinding]);

  return { targetDataBinding, targetProp };
}
