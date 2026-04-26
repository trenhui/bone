export function createProps() {
  return {
    id: {
      type: String,
      default: "",
    },
    code: {
      type: String,
      default: "",
    },
    name: {
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
  };
}
