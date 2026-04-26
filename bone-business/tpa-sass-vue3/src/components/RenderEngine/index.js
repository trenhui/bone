import { defineAsyncComponent } from "vue";

export default {
  install(app) {
    const baseModules = import.meta.glob("./base/*/index.vue");

    const viewModules = import.meta.glob("./views/*/index.vue");

    for (const [key, value] of Object.entries(baseModules)) {
      app.component(
        "PK" + key.replace("./", "").split("/")[1],
        defineAsyncComponent(value)
      );
    }

    for (const [key, value] of Object.entries(viewModules)) {
      app.component(
        key.replace("./", "").split("/")[1],
        defineAsyncComponent(value)
      );
    }
  },
};
