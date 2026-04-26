import { DisplayModeEnum } from "@/enums/DisplayModeEnum";

export const useBaseComponentConfig = (
  displayMode,
  modalManager,
  updateSchema,
  componentId
) => {
  const componentRef = ref(null);

  const isConfig = computed(() => {
    return displayMode.value === DisplayModeEnum.CONFIG;
  });

  const handleClick = () => {
    modalManager.showModal(
      "UpdateFieldDrawer",
      { id: componentId },
      (isChange = false) => {
        if (isChange) {
          updateSchema(DisplayModeEnum.CONFIG);
        }
      }
    );
  };

  const handleConfig = () => {
    if (!componentRef.value) return;

    if (isConfig.value) {
      componentRef.value.addEventListener("click", handleClick);
    } else {
      componentRef.value.removeEventListener("click", handleClick);
    }
  };

  watch(isConfig, () => {
    handleConfig();
  });

  onMounted(() => {
    handleConfig();
  });

  return {
    isConfig,
    componentRef,
  };
};
