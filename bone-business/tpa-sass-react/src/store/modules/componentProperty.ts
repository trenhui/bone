
import { create } from "zustand";
import {
  PropertyTypeEnum,
  TablePropertyTypeEnum,
} from "@/enums/rule/PropertyTypeEnum";

interface ComponentPropertyState {
  components: Record<string, any>;
  initComponent: (id: string, props: any) => void;
  updateProperty: (
    id: string,
    property: PropertyTypeEnum | TablePropertyTypeEnum,
    value: any
  ) => void;
  removeComponent: (id: string) => void;
}

export const useComponentPropertyStore = create<ComponentPropertyState>()(
  (set, get) => ({
    components: {},

    initComponent: (id: string, props: any) => {
      set((state) => {
        const currentComponent = state.components[id];
        if (!currentComponent) {
          return {
            components: {
              ...state.components,
              [id]: { ...props },
            },
          };
        } else {
          return {
            components: {
              ...state.components,
              [id]: { ...props, ...currentComponent },
            },
          };
        }
      });
    },

    updateProperty: (
      id: string,
      property: PropertyTypeEnum | TablePropertyTypeEnum,
      value: any
    ) => {
      set((state) => {
        const components = { ...state.components };
        if (!components[id]) {
          components[id] = {};
        }

        const comp = components[id];
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

        return { components };
      });
    },

    removeComponent: (id: string) => {
      set((state) => {
        const { [id]: _, ...remainingComponents } = state.components;
        return { components: remainingComponents };
      });
    },
  })
);

export default useComponentPropertyStore;
