export function createProps() {
  return {
    id: {
      type: String,
      default: "",
    },
    name: {
      type: String,
      default: "",
    },
    body: {
      type: Array,
      default: () => [],
    },
  };
}
