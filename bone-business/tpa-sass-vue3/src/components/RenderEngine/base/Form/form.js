export function createProps() {
  return {
    id: {
      type: String,
      default: "",
    },
    eventTriggerList: {
      type: Array,
      default: () => [],
    },
    body: {
      type: Array,
      default: () => [],
    },
    isAffix: {
      type: Boolean,
      default: false,
    },
  };
}
