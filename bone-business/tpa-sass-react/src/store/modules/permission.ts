import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { TOKEN_KEY } from "@/enums/CacheEnum";

// 先创建一个简化的permission store，用于管理路由和菜单
interface PermissionState {
  routes: any[];
  mixLeftMenus: any[];
  isRoutesLoaded: boolean;
  setRoutes: (routes: any[]) => void;
  setMixLeftMenus: (mixLeftMenus: any[]) => void;
  setIsRoutesLoaded: (isRoutesLoaded: boolean) => void;
  generateRoutes: () => Promise<any>;
}

export const usePermissionStore = create<PermissionState>()(
  persist(
    (set, get) => ({
      routes: [],
      mixLeftMenus: [],
      isRoutesLoaded: false,

      setRoutes: (routes: any[]) => {
        set({ routes });
      },

      setMixLeftMenus: (mixLeftMenus: any[]) => {
        set({ mixLeftMenus });
      },

      setIsRoutesLoaded: (isRoutesLoaded: boolean) => {
        set({ isRoutesLoaded });
      },

      generateRoutes: () => {
        return new Promise<any>(async (resolve, reject) => {
          // 这个方法在React项目中可能需要重新实现，因为React的路由生成方式与Vue不同
          // 目前我们返回一个空的实现
          resolve(null);
        });
      },
    }),
    {
      name: "permission-storage",
      storage: createJSONStorage(() => sessionStorage),
    }
  )
);

export default usePermissionStore;
