import { RouteRecordRaw } from "vue-router";
import router, { constantRoutes } from "@/router";
import { store } from "@/store";
import { isQiankun } from "@/utils/qiankun";
import pkg from "../../../package.json";
import AuthAPI, { LoginInfo, IMenu, RouteVO } from "@/api/auth";

const modules = import.meta.glob("../../views/**/**.vue");
const Layout = () => import("@/layout/index.vue");

export const usePermissionStore = defineStore("permission", () => {
  /** 所有路由，包括静态和动态路由 */
  const routes = ref<RouteRecordRaw[]>([]);
  /** 混合模式左侧菜单 */
  const mixLeftMenus = ref<RouteRecordRaw[]>([]);
  /** 菜单是否加载完成 */
  const isRoutesLoaded = ref(false);

  /**
   * 生成动态路由
   */
  function generateRoutes() {
    return new Promise<LoginInfo | null>(async (resolve, reject) => {
      if (isRoutesLoaded.value) {
        // 已经生成菜单
        return resolve(null);
      } else {
        // 尚未生成菜单
        if (isQiankun()) {
          // 乾坤环境下生成菜单
          try {
            const permission = sessionStorage.getItem("permission");
            if (permission) {
              const { routes = [] } = JSON.parse(permission);
              const appMenu = routes.find(
                (item: any) => item.appName === pkg.name
              );
              const dynamicRoutes = transformRoutes(
                transformMenusToRoutes(appMenu.children)
              );
              routes.value = constantRoutes.concat(dynamicRoutes);
              dynamicRoutes.forEach((route: RouteRecordRaw) =>
                router.addRoute(route)
              );
              isRoutesLoaded.value = true;
              resolve(routes.value);
            } else {
              reject(new Error("permission is null"));
            }
          } catch (error) {
            reject(error);
          }
        } else {
          // 非乾坤环境生成菜单
          try {
            const res = await AuthAPI.getLoginInfo();
            // 获取对应APP的菜单
            const menu =
              res?.menus?.find?.((item: any) => item.appName === pkg.name)
                ?.children || [];
            const dynamicRoutes = transformRoutes(transformMenusToRoutes(menu));
            routes.value = constantRoutes.concat(dynamicRoutes);
            dynamicRoutes.forEach((route: RouteRecordRaw) =>
              router.addRoute(route)
            );

            isRoutesLoaded.value = true;
            resolve(res);
          } catch (error) {
            reject(error);
          }
        }
      }
    });
  }

  /**
   * 混合模式菜单下根据顶部菜单路径设置左侧菜单
   *
   * @param topMenuPath - 顶部菜单路径
   */
  const setMixLeftMenus = (topMenuPath: string) => {
    const matchedItem = routes.value.find((item) => item.path === topMenuPath);
    if (matchedItem && matchedItem.children) {
      mixLeftMenus.value = matchedItem.children;
    }
  };

  return {
    routes,
    generateRoutes,
    mixLeftMenus,
    setMixLeftMenus,
    isRoutesLoaded,
  };
});

/**
 * 将 IMenu 转换为 RouteVO 结构
 * @param menu IMenu 菜单对象
 * @returns RouteVO 路由对象
 */
export function transformMenuToRoute(menu: IMenu): RouteVO {
  const route: RouteVO = {
    path: menu.path,
    name: menu.componentName,
    component: menu.component,
    children: menu.children?.map((child) => transformMenuToRoute(child)) || [],
    meta: {
      title: menu.name,
      icon: menu.icon,
      hidden: !menu.visible,
      keepAlive: menu.keepAlive,
      alwaysShow:
        menu.children && menu.children.length > 0 ? menu.alwaysShow : false,
    },
  };

  return route;
}

export function transformMenusToRoutes(menus: IMenu[]): RouteVO[] {
  const routes: RouteVO[] = [];
  menus.forEach((menu) => {
    routes.push(transformMenuToRoute(menu));
  });
  return routes;
}

/**
 * 转换路由数据为组件
 */
const transformRoutes = (routes: RouteVO[]) => {
  const asyncRoutes: RouteRecordRaw[] = [];
  routes.forEach((route) => {
    const tmpRoute = { ...route } as RouteRecordRaw;
    // 顶级目录，替换为 Layout 组件
    if (!tmpRoute.component) {
      tmpRoute.component = Layout;
    } else if (tmpRoute.component?.toString() == "Layout") {
      tmpRoute.component = Layout;
    } else {
      // 其他菜单，根据组件路径动态加载组件
      const component = modules[`../../views/${tmpRoute.component}.vue`];
      if (component) {
        tmpRoute.component = component;
      } else {
        tmpRoute.component = modules[`../../views/error-page/404.vue`];
      }
    }

    if (tmpRoute.children && tmpRoute.children.length > 0) {
      tmpRoute.children = transformRoutes(route.children);
    } else {
      tmpRoute.children = undefined;
    }

    asyncRoutes.push(tmpRoute);
  });

  return asyncRoutes;
};

/**
 * 在组件外使用 Pinia store 实例
 * @see https://pinia.vuejs.org/core-concepts/outside-component-usage.html
 */
export function usePermissionStoreHook() {
  return usePermissionStore(store);
}
