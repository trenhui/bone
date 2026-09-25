import { create } from 'zustand';
import { DatabaseTable } from '../services/types';

interface GeneratorStore {
  // 表结构相关状态（跨页面共享的 UI 选择态；列表数据走 React Query）
  tables: DatabaseTable[];
  selectedTables: string[];
  activeDataSourceId: string;
  metadataSource: 'PHYSICAL_DB' | 'CATALOG_SNAPSHOT';

  // 操作方法
  setTables: (tables: DatabaseTable[]) => void;
  setSelectedTables: (tableNames: string[]) => void;
  setActiveDataSourceId: (id: string) => void;
  setMetadataSource: (source: 'PHYSICAL_DB' | 'CATALOG_SNAPSHOT') => void;

  // 重置状态
  reset: () => void;
}

export const useGeneratorStore = create<GeneratorStore>((set) => ({
  tables: [],
  selectedTables: [],
  activeDataSourceId: '',
  metadataSource: 'PHYSICAL_DB',

  setTables: (tables) => set({ tables }),
  setSelectedTables: (selectedTables) => set({ selectedTables }),
  setActiveDataSourceId: (activeDataSourceId) => set({ activeDataSourceId }),
  setMetadataSource: (metadataSource) => set({ metadataSource }),

  reset: () =>
    set({
      tables: [],
      selectedTables: [],
      activeDataSourceId: '',
      metadataSource: 'PHYSICAL_DB',
    }),
}));
