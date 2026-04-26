import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import defaultSettings from "@/settings";
import { DeviceEnum } from "@/enums/DeviceEnum";
import { SidebarStatusEnum } from "@/enums/SidebarStatusEnum";

interface AppState {
  device: string;
  size: string;
  language: string;
  sidebarStatus: string;
  sidebar: {
    opened: boolean;
    withoutAnimation: boolean;
  };
  activeTopMenuPath: string;
  toggleSidebar: () => void;
  closeSideBar: () => void;
  openSideBar: () => void;
  toggleDevice: (val: string) => void;
  changeSize: (val: string) => void;
  changeLanguage: (val: string) => void;
  activeTopMenu: (val: string) => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      device: DeviceEnum.DESKTOP,
      size: defaultSettings.size,
      language: defaultSettings.language,
      sidebarStatus: SidebarStatusEnum.CLOSED,
      sidebar: {
        opened: false,
        withoutAnimation: false,
      },
      activeTopMenuPath: "",

      toggleSidebar: () => {
        set((state) => {
          const newOpened = !state.sidebar.opened;
          return {
            sidebar: {
              ...state.sidebar,
              opened: newOpened,
            },
            sidebarStatus: newOpened ? SidebarStatusEnum.OPENED : SidebarStatusEnum.CLOSED,
          };
        });
      },

      closeSideBar: () => {
        set((state) => ({
          sidebar: {
            ...state.sidebar,
            opened: false,
          },
          sidebarStatus: SidebarStatusEnum.CLOSED,
        }));
      },

      openSideBar: () => {
        set((state) => ({
          sidebar: {
            ...state.sidebar,
            opened: true,
          },
          sidebarStatus: SidebarStatusEnum.OPENED,
        }));
      },

      toggleDevice: (val: string) => {
        set({ device: val });
      },

      changeSize: (val: string) => {
        set({ size: val });
      },

      changeLanguage: (val: string) => {
        set({ language: val });
      },

      activeTopMenu: (val: string) => {
        set({ activeTopMenuPath: val });
      },
    }),
    {
      name: "app-storage",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        device: state.device,
        size: state.size,
        language: state.language,
        sidebarStatus: state.sidebarStatus,
        activeTopMenuPath: state.activeTopMenuPath,
      }),
    }
  )
);

export default useAppStore;
