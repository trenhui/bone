import { useDictStore } from "@/store/modules/dict";

export function setupDict() {
  const dictStore = useDictStore();
  dictStore.initBaseDicts();
}
