import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import AuthAPI, { type LoginFormData, type UserInfo as AuthUserInfo } from "@/api/auth";
import UserAPI, { type UserInfo } from "@/api/user";
import { TOKEN_KEY } from "@/enums/CacheEnum";

interface UserState {
  user: {
    roles: string[];
    perms: string[];
    [key: string]: any;
  };
  login: (loginData: LoginFormData) => Promise<void>;
  getUserInfo: () => Promise<UserInfo>;
  logout: () => Promise<void>;
  resetToken: () => Promise<void>;
  setUser: (user: any) => void;
}

export const useUserStore = create<UserState>()(
  persist(
    (set, get) => {
      // 从 sessionStorage 初始化权限
      const getInitialPermission = () => {
        if (typeof sessionStorage !== "undefined") {
          try {
            const permission = sessionStorage.getItem("permission");
            if (permission) {
              return JSON.parse(permission);
            }
          } catch {
            // 忽略
          }
        }
        return { roles: [], perms: [] };
      };

      const initialPermission = getInitialPermission();

      return {
        user: {
          roles: initialPermission.roles || [],
          perms: initialPermission.perms || [],
        },

        login: (loginData: LoginFormData) => {
          return new Promise<void>((resolve, reject) => {
            AuthAPI.login(loginData)
              .then(async (token) => {
                localStorage.setItem(TOKEN_KEY, "Bearer " + token);
                // 这里我们需要调用 getLoginInfo 来获取用户权限
                try {
                  const loginInfo = await AuthAPI.getLoginInfo();
                  set({
                    user: {
                      ...loginInfo.user,
                      roles: loginInfo.roles,
                      perms: loginInfo.permissions,
                    },
                  });
                  // 保存到 sessionStorage
                  sessionStorage.setItem("permission", JSON.stringify({
                    roles: loginInfo.roles,
                    perms: loginInfo.permissions,
                  }));
                } catch {
                  // 忽略错误
                }
                resolve();
              })
              .catch((error) => {
                reject(error);
              });
          });
        },

        getUserInfo: () => {
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
                set({ user: { ...data } });
                resolve(data);
              })
              .catch((error) => {
                reject(error);
              });
          });
        },

        logout: () => {
          return new Promise<void>((resolve, reject) => {
            AuthAPI.logout()
              .then(() => {
                localStorage.setItem(TOKEN_KEY, "");
                sessionStorage.removeItem("permission");
                if (typeof location !== "undefined") {
                  location.reload();
                }
                resolve();
              })
              .catch((error) => {
                reject(error);
              });
          });
        },

        resetToken: () => {
          return new Promise<void>((resolve) => {
            localStorage.setItem(TOKEN_KEY, "");
            sessionStorage.removeItem("permission");
            resolve();
          });
        },

        setUser: (user: any) => {
          set({ user: { ...user } });
        },
      };
    },
    {
      name: "user-storage",
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        user: state.user,
      }),
    }
  )
);

export default useUserStore;
