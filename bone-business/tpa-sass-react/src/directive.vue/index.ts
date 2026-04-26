import type { App } from "vue";

import { hasPerm } from "./permission";
import loadMore from "./loadMore";
import adaptive from "./adaptive";

// 全局注册 directive
export function setupDirective(app: App<Element>) {
  app.directive("hasPerm", hasPerm);
  app.directive("loadMore", loadMore);
  app.directive("adaptive", adaptive);
}
