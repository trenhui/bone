import { createApp } from "vue";
import App from "./App.vue";
import setupPlugins from "@/plugins";
import { renderWithQiankun } from "vite-plugin-qiankun/dist/helper";
import { isQiankun } from "./utils/qiankun";
import pkg from "../package.json";
import RenderEngine from "@/components/RenderEngine/index";

// 本地SVG图标
import "virtual:svg-icons-register";

// 样式
import "element-plus/theme-chalk/dark/css-vars.css";
import "@/styles/index.scss";
import "uno.css";
import "animate.css";

let app: any = null;
let retryCount = 0;
const MAX_RETRY_COUNT = 5;

const render = (props: any = {}) => {
  console.log("开始渲染应用");
  const { container } = props;
  console.log("容器信息:", {
    container,
    hasContainer: !!container,
    appElement: container
      ? container.querySelector("#app")
      : document.querySelector("#app"),
  });

  app = createApp(App);
  console.log("Vue应用实例创建完成");

  app.use(setupPlugins);
  console.log("插件安装完成");

  app.use(RenderEngine);
  console.log("RenderEngine安装完成");

  const mountElement = container
    ? container.querySelector("#app")
    : document.querySelector("#app");
  console.log("准备挂载到元素:", mountElement);

  if (!mountElement) {
    if (retryCount < MAX_RETRY_COUNT) {
      retryCount++;
      console.warn(
        `挂载元素不存在，第${retryCount}次重试，最多重试${MAX_RETRY_COUNT}次, 重试间隔20ms`
      );
      setTimeout(() => {
        render(props);
      }, 20);
      return;
    } else {
      console.error(`挂载失败：在${MAX_RETRY_COUNT}次重试后仍未找到挂载元素`);
      throw new Error(`挂载失败：在${MAX_RETRY_COUNT}次重试后仍未找到挂载元素`);
    }
  }

  try {
    app.mount(mountElement);
    console.log("应用挂载完成");
    retryCount = 0; // 重置重试次数
  } catch (error) {
    console.error("应用挂载失败:", error);
    throw error;
  }
};

renderWithQiankun({
  mount(props: any) {
    console.log("qiankun渲染");
    return new Promise((resolve) => {
      props.onGlobalStateChange((state: any) => {
        console.log(`子应用${pkg.name}接收的参数`, state);
        state.publicPath &&
          window.localStorage.setItem("mainJumpPublicPath", state.publicPath);
      }, true);
      render(props);
      resolve();
    });
  },
  bootstrap() {
    console.log("%c", "color:green;", `子应用${pkg.name}bootstrap`);
  },
  update() {
    console.log("%c", "color:green;", `子应用${pkg.name}update`);
  },
  unmount(props: any) {
    console.log(`子应用${pkg.name}unmount`, props);
    app.unmount();
    app._container.innerHTML = "";
    app = null;
  },
});

if (!isQiankun()) {
  console.log("开始本地渲染");
  render();
  console.log("本地渲染完成");
}
