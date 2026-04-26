import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

interface TagsViewState {
  visitedViews: TagView[];
  cachedViews: string[];
  addVisitedView: (view: TagView) => void;
  addCachedView: (view: TagView) => void;
  delVisitedView: (view: TagView) => Promise<TagView[]>;
  delCachedView: (view: TagView) => Promise<string[]>;
  delOtherVisitedViews: (view: TagView) => Promise<TagView[]>;
  delOtherCachedViews: (view: TagView) => Promise<string[]>;
  updateVisitedView: (view: TagView) => void;
  addView: (view: TagView) => void;
  delView: (view: TagView) => Promise<{ visitedViews: TagView[]; cachedViews: string[] }>;
  delOtherViews: (view: TagView) => Promise<{ visitedViews: TagView[]; cachedViews: string[] }>;
  delLeftViews: (view: TagView) => Promise<{ visitedViews: TagView[] }>;
  delRightViews: (view: TagView) => Promise<{ visitedViews: TagView[] }>;
  delAllViews: () => Promise<{ visitedViews: TagView[]; cachedViews: string[] }>;
  delAllVisitedViews: () => Promise<TagView[]>;
  delAllCachedViews: () => Promise<string[]>;
}

export const useTagsViewStore = create<TagsViewState>()(
  persist(
    (set, get) => ({
      visitedViews: [],
      cachedViews: [],

      addVisitedView: (view: TagView) => {
        set((state) => {
          if (state.visitedViews.some((v) => v.path === view.path)) {
            return state;
          }
          const newVisitedViews = view.affix 
            ? [view, ...state.visitedViews] 
            : [...state.visitedViews, view];
          return { visitedViews: newVisitedViews };
        });
      },

      addCachedView: (view: TagView) => {
        set((state) => {
          const viewName = view.name;
          if (state.cachedViews.includes(viewName)) {
            return state;
          }
          if (view.keepAlive) {
            return { cachedViews: [...state.cachedViews, viewName] };
          }
          return state;
        });
      },

      delVisitedView: (view: TagView) => {
        return new Promise((resolve) => {
          set((state) => {
            const newVisitedViews = state.visitedViews.filter((v) => v.path !== view.path);
            resolve([...newVisitedViews]);
            return { visitedViews: newVisitedViews };
          });
        });
      },

      delCachedView: (view: TagView) => {
        return new Promise((resolve) => {
          set((state) => {
            const viewName = view.name;
            const newCachedViews = state.cachedViews.filter((v) => v !== viewName);
            resolve([...newCachedViews]);
            return { cachedViews: newCachedViews };
          });
        });
      },

      delOtherVisitedViews: (view: TagView) => {
        return new Promise((resolve) => {
          set((state) => {
            const newVisitedViews = state.visitedViews.filter((v) => v?.affix || v.path === view.path);
            resolve([...newVisitedViews]);
            return { visitedViews: newVisitedViews };
          });
        });
      },

      delOtherCachedViews: (view: TagView) => {
        return new Promise((resolve) => {
          set((state) => {
            const viewName = view.name as string;
            const index = state.cachedViews.indexOf(viewName);
            const newCachedViews = index > -1 ? [state.cachedViews[index]] : [];
            resolve([...newCachedViews]);
            return { cachedViews: newCachedViews };
          });
        });
      },

      updateVisitedView: (view: TagView) => {
        set((state) => {
          const newVisitedViews = state.visitedViews.map((v) => 
            v.path === view.path ? { ...v, ...view } : v
          );
          return { visitedViews: newVisitedViews };
        });
      },

      addView: (view: TagView) => {
        get().addVisitedView(view);
        get().addCachedView(view);
      },

      delView: (view: TagView) => {
        return new Promise(async (resolve) => {
          const visitedViews = await get().delVisitedView(view);
          const cachedViews = await get().delCachedView(view);
          resolve({ visitedViews, cachedViews });
        });
      },

      delOtherViews: (view: TagView) => {
        return new Promise(async (resolve) => {
          const visitedViews = await get().delOtherVisitedViews(view);
          const cachedViews = await get().delOtherCachedViews(view);
          resolve({ visitedViews, cachedViews });
        });
      },

      delLeftViews: (view: TagView) => {
        return new Promise((resolve) => {
          set((state) => {
            const currIndex = state.visitedViews.findIndex((v) => v.path === view.path);
            if (currIndex === -1) {
              resolve({ visitedViews: [...state.visitedViews] });
              return state;
            }
            
            const newVisitedViews = state.visitedViews.filter((item, index) => {
              if (index >= currIndex || item?.affix) {
                return true;
              }
              return false;
            });
            
            const newCachedViews = state.cachedViews.filter((name) => {
              const item = state.visitedViews.find((v) => v.name === name);
              if (!item) return false;
              const index = state.visitedViews.indexOf(item);
              return index >= currIndex || item?.affix;
            });

            resolve({ visitedViews: [...newVisitedViews] });
            return { visitedViews: newVisitedViews, cachedViews: newCachedViews };
          });
        });
      },

      delRightViews: (view: TagView) => {
        return new Promise((resolve) => {
          set((state) => {
            const currIndex = state.visitedViews.findIndex((v) => v.path === view.path);
            if (currIndex === -1) {
              resolve({ visitedViews: [...state.visitedViews] });
              return state;
            }
            
            const newVisitedViews = state.visitedViews.filter((item, index) => {
              return index <= currIndex || item?.affix;
            });

            resolve({ visitedViews: [...newVisitedViews] });
            return { visitedViews: newVisitedViews };
          });
        });
      },

      delAllViews: () => {
        return new Promise((resolve) => {
          set((state) => {
            const affixTags = state.visitedViews.filter((tag) => tag?.affix);
            resolve({ visitedViews: [...affixTags], cachedViews: [] });
            return { visitedViews: affixTags, cachedViews: [] };
          });
        });
      },

      delAllVisitedViews: () => {
        return new Promise((resolve) => {
          set((state) => {
            const affixTags = state.visitedViews.filter((tag) => tag?.affix);
            resolve([...affixTags]);
            return { visitedViews: affixTags };
          });
        });
      },

      delAllCachedViews: () => {
        return new Promise((resolve) => {
          set({ cachedViews: [] });
          resolve([]);
        });
      },
    }),
    {
      name: "tags-view-storage",
      storage: createJSONStorage(() => sessionStorage),
    }
  )
);

export default useTagsViewStore;
