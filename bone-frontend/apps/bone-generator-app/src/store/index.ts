import { create } from 'zustand';
import { DataSource, DatabaseTable, GenerateCodeRequest, CodeGenerationResponse } from '../services/types';

interface GeneratorStore {
  // 数据源相关状态
  dataSources: DataSource[];
  currentDataSource: DataSource | null;
  loadingDataSources: boolean;
  
  // 表结构相关状态
  tables: DatabaseTable[];
  selectedTables: string[];
  loadingTables: boolean;
  activeDataSourceId: string;
  metadataSource: 'PHYSICAL_DB' | 'CATALOG_SNAPSHOT';
  
  // 代码生成相关状态
  generateRequest: GenerateCodeRequest;
  generateResponse: CodeGenerationResponse | null;
  loadingGenerate: boolean;
  
  // 操作方法
  setDataSources: (dataSources: DataSource[]) => void;
  setCurrentDataSource: (dataSource: DataSource | null) => void;
  setLoadingDataSources: (loading: boolean) => void;
  
  setTables: (tables: DatabaseTable[]) => void;
  setSelectedTables: (tableNames: string[]) => void;
  setLoadingTables: (loading: boolean) => void;
  setActiveDataSourceId: (id: string) => void;
  setMetadataSource: (source: 'PHYSICAL_DB' | 'CATALOG_SNAPSHOT') => void;
  
  setGenerateRequest: (request: Partial<GenerateCodeRequest>) => void;
  setGenerateResponse: (response: CodeGenerationResponse | null) => void;
  setLoadingGenerate: (loading: boolean) => void;
  
  // 重置状态
  reset: () => void;
}

export const useGeneratorStore = create<GeneratorStore>((set) => ({
  // 初始状态
  dataSources: [],
  currentDataSource: null,
  loadingDataSources: false,
  
  tables: [],
  selectedTables: [],
  loadingTables: false,
  activeDataSourceId: '',
  metadataSource: 'PHYSICAL_DB',
  
  generateRequest: {
    templateId: '1',
    name: '',
    language: 'java',
    framework: 'spring-boot',
    dataSourceId: '',
    tableNames: [],
    basePackage: 'com.example',
    moduleName: 'demo',
  },
  generateResponse: null,
  loadingGenerate: false,
  
  // 操作方法
  setDataSources: (dataSources) => set({ dataSources }),
  setCurrentDataSource: (currentDataSource) => set({ currentDataSource }),
  setLoadingDataSources: (loadingDataSources) => set({ loadingDataSources }),
  
  setTables: (tables) => set({ tables }),
  setSelectedTables: (selectedTables) => set({ selectedTables }),
  setLoadingTables: (loadingTables) => set({ loadingTables }),
  setActiveDataSourceId: (activeDataSourceId) => set({ activeDataSourceId }),
  setMetadataSource: (metadataSource) => set({ metadataSource }),
  
  setGenerateRequest: (request) => set((state) => ({
    generateRequest: { ...state.generateRequest, ...request }
  })),
  setGenerateResponse: (generateResponse) => set({ generateResponse }),
  setLoadingGenerate: (loadingGenerate) => set({ loadingGenerate }),
  
  // 重置状态
  reset: () => set({
    dataSources: [],
    currentDataSource: null,
    loadingDataSources: false,
    tables: [],
    selectedTables: [],
    loadingTables: false,
    activeDataSourceId: '',
    metadataSource: 'PHYSICAL_DB',
    generateRequest: {
      templateId: '1',
      name: '',
      language: 'java',
      framework: 'spring-boot',
      dataSourceId: '',
      tableNames: [],
      basePackage: 'com.example',
      moduleName: 'demo',
    },
    generateResponse: null,
    loadingGenerate: false,
  }),
}));