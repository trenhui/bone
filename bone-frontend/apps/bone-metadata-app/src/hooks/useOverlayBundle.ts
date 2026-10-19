import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { MetaField } from '../types';
import type { ListView } from '../utils/uiSchemaEngine';
import {
  activeOverlay,
  activeViewOf,
  createView,
  defaultBundle,
  deleteView,
  isOverlayDirty,
  loadOverlay,
  overlayHiddenContractColumns,
  overlayKey,
  renameColumnOverride,
  renameView,
  resetActiveViewToDefault,
  switchView as switchViewIn,
  updateActiveView,
  type PersonalView,
  type UiOverlay,
  type UiOverlayBundle,
} from '../utils/columnProjection';
import { fetchOverlay, persistOverlay, resetOverlay, OVERLAY_CHANGED_EVENT } from '../utils/overlaySync';
import type { ViewFilter } from '../utils/columnProjection';

/**
 * 个人覆盖（overlay）唯一状态源（双轨收敛 · P2）。
 *
 * <p><b>为什么是一个 hook 而不是各页面各写一份</b>：个人覆盖有两个 surface——
 * 建模台「界面设计器 → 列表」的契约口径核对与运行时数据页「交付口径对照 / 列设置」
 * （唯一编辑器）。历史上交付预览 Tab 与 RuntimeDataManagement 各自持有一份
 * hydrate/persist/view CRUD 实现，是双轨收敛前「两个编辑器」的残留；投影层
 * （projectRuntimeColumns）早已单一真源，状态层也必须如此，否则任一侧漂移
 * 都会让「预览 ≡ 运行时」的结构性保证失效。
 *
 * <p><b>入口分工（业界双轨，编辑入口跟着生命周期走）</b>：
 * 团队契约（列集合/列序/列宽/固定列/列标题）在建模台「界面设计器」配置并发布；
 * 个人覆盖（显隐/密度/个人视图）只在运行时数据页编辑——对标 Salesforce
 * Search Layouts（Setup 配组织级）+ List View personal lists（record home 自助），
 * 个人视图编辑器从不放进 maker 工具。
 *
 * <p><b>hydrate 策略</b>：每实体一次（entityCode 变化才重拉）——fields 晚到
 * （结构 Tab 加字段后返回）不重 hydrate：空覆盖表 = 中性投影，晚到不漂移；
 * 重 hydrate 反而会打断用户正在做的视图操作。三级降级沿用 overlaySync：
 * 服务端覆盖 → 本地缓存 → defaultBundle 占位。
 */
export interface UseOverlayBundle {
  /** 当前 bundle（hydrate 前瞬间为 defaultBundle 占位，不为 null） */
  bundle: UiOverlayBundle | null;
  /** 生效视图的扁平覆盖（投影层唯一入口口径） */
  activeOv: UiOverlay;
  /** 生效视图对象（UI 层管理当前视图用） */
  activeView: PersonalView | null;
  /** 生效覆盖是否偏离默认投影（「恢复默认显示」启用条件） */
  dirty: boolean;
  /** 被个人偏好隐藏、但契约默认显示的列（自伤遮蔽实时判据，见 overlayHiddenContractColumns） */
  shadowedColumns: MetaField[];
  switchView: (id: string) => void;
  createView: (name: string, mode: 'blank' | 'copy') => void;
  renameView: (id: string, name: string) => void;
  deleteView: (id: string) => void;
  toggleVisible: (code: string, visible: boolean) => void;
  setDensity: (density: 'comfortable' | 'compact') => void;
  /**
   * 个人列改名（仅改生效视图；null/空串 = 恢复默认列名）。
   * 与 toggleVisible 同口径：偏好层写口，投影层 projectRuntimeColumns 的
   * personalRename 优先于契约标题消费它（纯函数 renameColumnOverride 保证语义）。
   */
  renameColumn: (code: string, name: string | null) => void;
  /**
   * 视图筛选条件（随个人视图保存/恢复 —— 对标 Airtable/Notion 的 saved view）。
   *
   * <p>此前筛选只在页面 useState 里，切视图即丢；补齐后「视图 = 列 + 筛选 + 排序」
   * 才是一份可复用的工作台面。
   */
  setViewFilters: (filters: ViewFilter[]) => void;
  /** 视图排序（sortField 缺省= 不排序；sortDesc 仅在有 sortField 时有意义） */
  setViewSort: (sortField?: string, sortDesc?: boolean) => void;
  /** 把被遮蔽的契约列全部恢复显示（自伤遮蔽的一键自救） */
  unhideShadowed: () => void;
  /** 恢复生效视图为默认投影（保留视图本身与其他视图） */
  resetActive: () => void;
  /** 全量重置：删 blob（服务端软删 + 本地缓存），回到单「默认视图」 */
  resetAll: () => Promise<void>;
}

export function useOverlayBundle(
  entityCode: string | null,
  fields: MetaField[],
  listView: ListView | null,
): UseOverlayBundle {
  const [bundle, setBundle] = useState<UiOverlayBundle | null>(null);

  // 渲染期同步的 ref（UiSchemaDesigner schemaRef 同模式）：事件回调里取最新值，
  // setState 更新器保持纯粹——React 18.3 对 render-phase update 重放路径下，
  // 更新器内嵌套副作用会被双倍应用。
  const bundleRef = useRef<UiOverlayBundle | null>(null);
  bundleRef.current = bundle;
  const fieldsRef = useRef<MetaField[]>(fields);
  fieldsRef.current = fields;

  useEffect(() => {
    if (!entityCode) {
      setBundle(null);
      return;
    }
    let cancelled = false;
    // 占位用**当前** fields（ref）而非 effect 闭包快照：实体详情页 fields 常晚到，
    // 闭包快照会是空数组；空覆盖表虽中性，但 dirty 判据会因此误报。
    setBundle(defaultBundle(fieldsRef.current));
    fetchOverlay(entityCode).then((b) => {
      if (!cancelled) setBundle(b ?? defaultBundle(fieldsRef.current));
    });
    return () => {
      cancelled = true;
    };
    // listView 不参与 hydrate 依赖：契约变化不重拉覆盖（覆盖与契约是两层，
    // 契约发布后覆盖原样叠加，由 shadowedColumns 实时判据提示，不打断用户）。
  }, [entityCode]);

  /**
   * 外部变更感知：让同一文档内的另一个 surface 改动立刻反映到本surface。
   *
   * <p><b>修的是真实漂移而非探针假象</b>：antd Tabs 默认不销毁 Tab 内容，交付预览类 surface
   * 与「运行时数据页」会同时挂在同一棵 React 树上；上面的 hydrate effect 只依赖
   * entityCode，实体不变就不重拉 ⇒ 用户在数据页改完个人列显隐后切回另一个 surface，
   * 看到的仍是页面加载时的过期快照：「我的所见」列集与运行时不一致，且
   * 「你的个人偏好正在隐藏 N 个契约列」告警永远不触发（shadowedColumns 恒为空），
   * 而这恰是双轨设计要杜绝的「预览 ≡ 运行时」失效。
   *
   * <p>取值走**本地缓存**而非重新 GET：persistOverlay 先写本地再异步推服务端，
   * 此刻本地即最新真源（若重新 GET，PUT 未落库时会读回旧值，反而引入新的漂移）。
   * storage 事件覆盖跨浏览器标签（同文档广播跨不过去）。
   */
  useEffect(() => {
    if (!entityCode) return;
    const reload = () => setBundle(loadOverlay(entityCode) ?? defaultBundle(fieldsRef.current));
    const onOverlayChanged = (e: Event) => {
      const detail = (e as CustomEvent<{ entityCode?: string }>).detail;
      if (detail?.entityCode === entityCode) reload();
    };
    const key = overlayKey(entityCode);
    const onStorage = (e: StorageEvent) => {
      if (e.key === key) reload();
    };
    window.addEventListener(OVERLAY_CHANGED_EVENT, onOverlayChanged);
    window.addEventListener('storage', onStorage);
    return () => {
      window.removeEventListener(OVERLAY_CHANGED_EVENT, onOverlayChanged);
      window.removeEventListener('storage', onStorage);
    };
  }, [entityCode]);

  const persist = useCallback(
    (next: UiOverlayBundle) => {
      setBundle(next);
      if (entityCode) persistOverlay(entityCode, next);
    },
    [entityCode],
  );

  const patchActive = useCallback(
    (patch: (v: PersonalView) => PersonalView) => {
      const prev = bundleRef.current;
      if (!prev) return;
      persist(updateActiveView(prev, patch));
    },
    [persist],
  );

  const activeOv = useMemo(() => activeOverlay(bundle), [bundle]);
  const activeView = useMemo(() => activeViewOf(bundle), [bundle]);
  const dirty = useMemo(() => isOverlayDirty(activeOv, fields, listView), [activeOv, fields, listView]);
  const shadowedColumns = useMemo(
    () => overlayHiddenContractColumns(fields, activeOv, listView),
    [fields, activeOv, listView],
  );

  const unhideShadowed = useCallback(() => {
    const shadowed = new Set(shadowedColumns.map((f) => f.code));
    if (shadowed.size === 0) return;
    patchActive((v) => ({
      ...v,
      overrides: Object.fromEntries(
        Object.entries(v.overrides).map(([code, ov]) =>
          shadowed.has(code) ? [code, { ...ov, visible: true }] : [code, ov],
        ),
      ),
    }));
  }, [shadowedColumns, patchActive]);

  const resetAll = useCallback(async () => {
    if (entityCode) await resetOverlay(entityCode);
    setBundle(defaultBundle(fieldsRef.current));
  }, [entityCode]);

  return {
    bundle,
    activeOv,
    activeView,
    dirty,
    shadowedColumns,
    switchView: useCallback((id: string) => {
      const prev = bundleRef.current;
      if (prev) persist(switchViewIn(prev, id));
    }, [persist]),
    createView: useCallback((name: string, mode: 'blank' | 'copy') => {
      const prev = bundleRef.current;
      if (prev) persist(createView(prev, name, fieldsRef.current, mode));
    }, [persist]),
    renameView: useCallback((id: string, name: string) => {
      const prev = bundleRef.current;
      if (prev) persist(renameView(prev, id, name));
    }, [persist]),
    deleteView: useCallback((id: string) => {
      const prev = bundleRef.current;
      if (prev) persist(deleteView(prev, id));
    }, [persist]),
    toggleVisible: useCallback((code: string, visible: boolean) => {
      patchActive((v) => ({
        ...v,
        overrides: { ...v.overrides, [code]: { ...v.overrides[code], visible } },
      }));
    }, [patchActive]),
    setDensity: useCallback((density: 'comfortable' | 'compact') => {
      patchActive((v) => ({ ...v, density }));
    }, [patchActive]),
    setViewFilters: useCallback((filters: ViewFilter[]) => {
      // 空数组归一为 undefined：避免"视图存了空筛选"与"从未设过筛选"两种状态混淆
      // （前者会让 UI 显示"该视图已配置筛选但无条目"的诡异态）。
      patchActive((v) => ({ ...v, filters: filters.length ? filters : undefined }));
    }, [patchActive]),
    setViewSort: useCallback((sortField?: string, sortDesc?: boolean) => {
      patchActive((v) => ({ ...v, sortField, sortDesc: sortField ? !!sortDesc : undefined }));
    }, [patchActive]),
    renameColumn: useCallback((code: string, name: string | null) => {
      patchActive((v) => ({
        ...v,
        overrides: renameColumnOverride(v.overrides, code, name, fieldsRef.current),
      }));
    }, [patchActive]),
    unhideShadowed,
    resetActive: useCallback(() => {
      const prev = bundleRef.current;
      if (prev) persist(resetActiveViewToDefault(prev, fieldsRef.current));
    }, [persist]),
    resetAll,
  };
}

export default useOverlayBundle;
