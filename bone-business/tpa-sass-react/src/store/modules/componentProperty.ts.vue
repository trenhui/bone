// stores/componentProperty.ts
import { defineStore } from "pinia";
import { ref } from "vue";
import { store } from "@/store";
import {
  PropertyTypeEnum,
  TablePropertyTypeEnum,
} from "@/enums/rule/PropertyTypeEnum";

export const useComponentPropertyStore = defineStore(
  "componentProperty",
  () => {
    const components = ref<Record<string, any>>({});

    function initComponent(id: string, props: any) {
      if (!components.value[id]) {
        components.value[id] = { ...props };
      } else {
        components.value[id] = { ...props, ...components.value[id] };
      }
    }

    function updateProperty(
      id: string,
      property: PropertyTypeEnum | TablePropertyTypeEnum,
      value: any
    ) {
      if (!components.value[id]) {
        components.value[id] = {};
      }

      const comp = components.value[id];
      switch (property) {
        case PropertyTypeEnum.displayed:
          comp.displayed = value;
          break;
        case PropertyTypeEnum.inputStatus:
          comp.inputStatus = value;
          break;
        case PropertyTypeEnum.required:
          comp.required = value;
          break;
        case PropertyTypeEnum.valueType:
          comp.valueType = value;
          break;
        case TablePropertyTypeEnum.display:
          comp.display = value;
          break;
      }
    }

    // 卸载组件
    function removeComponent(id: string) {
      delete components.value[id];
    }

    return {
      components,
      initComponent,
      updateProperty,
      removeComponent,
    };
  }
);

export function useComponentPropertyStoreHook() {
  return useComponentPropertyStore(store);
}
