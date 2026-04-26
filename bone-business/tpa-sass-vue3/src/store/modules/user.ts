import AuthAPI, { type LoginFormData } from "@/api/auth";
import UserAPI, { type UserInfo } from "@/api/user";
import { resetRouter } from "@/router";
import { store, usePermissionStore } from "@/store";
import { TOKEN_KEY } from "@/enums/CacheEnum";

export const useUserStore = defineStore("user", () => {
  const permissionStore = usePermissionStore();

  const permission = JSON.parse(sessionStorage.getItem("permission") || "{}");
  const user = ref<UserInfo>({
    roles: permission.roles || [],
    perms: permission.permissions || [],
  });

  /**
   * 登录
   *
   * @param {LoginData}
   * @returns
   */
  function login(loginData: LoginFormData) {
    return new Promise<void>((resolve, reject) => {
      AuthAPI.login(loginData)
        .then(async (data) => {
          localStorage.setItem(TOKEN_KEY, "Bearer" + " " + data); // Bearer eyJhbGciOiJIUzI1NiJ9.xxx.xxx
          const res = await permissionStore.generateRoutes();
          res && res.roles && (user.value.roles = res.roles);
          res && res.roles && (user.value.perms = res.permissions);
          resolve();
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  // 获取信息(用户昵称、头像、角色集合、权限集合)
  function getUserInfo() {
    return new Promise<UserInfo>((resolve, reject) => {
      UserAPI.getInfo()
        .then((data) => {
          if (!data) {
            reject("Verification failed, please Login again.");
            return;
          }
          if (!data.roles || data.roles.length <= 0) {
            reject("getUserInfo: roles must be a non-null array!");
            return;
          }
          Object.assign(user.value, { ...data });
          resolve(data);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  // user logout
  function logout() {
    return new Promise<void>((resolve, reject) => {
      AuthAPI.logout()
        .then(() => {
          localStorage.setItem(TOKEN_KEY, "");
          location.reload(); // 清空路由
          resolve();
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  // remove token
  function resetToken() {
    return new Promise<void>((resolve) => {
      localStorage.setItem(TOKEN_KEY, "");
      resetRouter();
      resolve();
    });
  }

  return {
    user,
    login,
    getUserInfo,
    logout,
    resetToken,
  };
});

/**
 * 用于在组件外部（如在Pinia Store 中）使用 Pinia 提供的 store 实例。
 * 官方文档解释了如何在组件外部使用 Pinia Store：
 * https://pinia.vuejs.org/core-concepts/outside-component-usage.html#using-a-store-outside-of-a-component
 */
export function useUserStoreHook() {
  return useUserStore(store);
}
