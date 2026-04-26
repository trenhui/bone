interface DialogState {
  visible: boolean;
  params?: Record<string, any>;
  onClose?: Function;
}

export const useDialogManager = () => {
  const dialogs = reactive<Record<string, DialogState>>({
    createPlan: { visible: false, params: {}, onClose: undefined },
    createCoverage: { visible: false, params: {}, onClose: undefined },
    createLiability: { visible: false, params: {}, onClose: undefined },
    editPlan: { visible: false, params: {}, onClose: undefined },
    editCoverage: { visible: false, params: {}, onClose: undefined },
    editLiability: { visible: false, params: {}, onClose: undefined },
    setLiabilityRelation: { visible: false, params: {}, onClose: undefined },
    liabilityPushConfig: { visible: false, params: {}, onClose: undefined },
  });

  const show = (
    name: keyof typeof dialogs,
    params = {},
    onClose?: Function
  ) => {
    dialogs[name].visible = true;
    dialogs[name].params = params;
    dialogs[name].onClose = onClose;
  };

  const hide = (name: keyof typeof dialogs, result?: any) => {
    const dialog = dialogs[name];
    dialog.params = {};
    if (dialog.onClose) {
      dialog.onClose(result);
      dialog.onClose = undefined;
      dialog.visible = false;
    }
  };

  return {
    dialogs,
    show,
    hide,
  };
};
