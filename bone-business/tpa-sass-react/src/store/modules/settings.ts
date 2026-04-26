import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import defaultSettings from "@/settings";
import { ThemeEnum } from "@/enums/ThemeEnum";

type SettingsValue = boolean | string;

interface SettingsState {
  settingsVisible: boolean;
  tagsView: boolean;
  sidebarLogo: boolean;
  fixedHeader: boolean;
  layout: string;
  watermarkEnabled: boolean;
  themeColor: string;
  theme: string;
  changeSetting: ({ key, value }: { key: string; value: SettingsValue }) => void;
  changeTheme: (val: string) => void;
  changeThemeColor: (color: string) => void;
  changeLayout: (val: string) => void;
  setSettingsVisible: (visible: boolean) => void;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set, get) => ({
      settingsVisible: false,
      tagsView: defaultSettings.tagsView,
      sidebarLogo: defaultSettings.sidebarLogo,
      fixedHeader: defaultSettings.fixedHeader,
      layout: defaultSettings.layout,
      watermarkEnabled: defaultSettings.watermarkEnabled,
      themeColor: defaultSettings.themeColor,
      theme: defaultSettings.theme,

      changeSetting: ({ key, value }: { key: string; value: SettingsValue }) => {
        set({ [key]: value } as Partial<SettingsState>);
      },

      changeTheme: (val: string) => {
        set({ theme: val });
        // 简单的主题切换逻辑
        if (typeof document !== "undefined") {
          if (val === ThemeEnum.DARK) {
            document.documentElement.classList.add("dark");
          } else {
            document.documentElement.classList.remove("dark");
          }
        }
      },

      changeThemeColor: (color: string) => {
        set({ themeColor: color });
      },

      changeLayout: (val: string) => {
        set({ layout: val });
      },

      setSettingsVisible: (visible: boolean) => {
        set({ settingsVisible: visible });
      },
    }),
    {
      name: "settings-storage",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        tagsView: state.tagsView,
        sidebarLogo: state.sidebarLogo,
        fixedHeader: state.fixedHeader,
        layout: state.layout,
        watermarkEnabled: state.watermarkEnabled,
        themeColor: state.themeColor,
        theme: state.theme,
      }),
    }
  )
);

export default useSettingsStore;
