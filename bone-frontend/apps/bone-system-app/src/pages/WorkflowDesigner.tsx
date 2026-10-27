import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Layout,
  Card,
  Button,
  Space,
  Input,
  Select,
  InputNumber,
  Tag,
  Drawer,
  Empty,
  Alert,
  Typography,
  Popconfirm,
  Tooltip,
  message,
  Switch,
  Divider,
  Badge,
  Modal,
} from 'antd';
import {
  PlusOutlined,
  SaveOutlined,
  SendOutlined,
  EyeOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  BranchesOutlined,
  AuditOutlined,
} from '@ant-design/icons';
import {
  compile,
  type FlowDoc,
  type Step,
  type ApproveStep,
  type CcStep,
  type ConditionStep,
  type Branch,
  type AssignmentRule,
  type AssignmentStrategy,
  type ApprovalMode,
  type LayoutPoint,
  type Violation,
  PARAM_KEY_BY_STRATEGY,
  groupViolationsByNode,
  hintOf,
  isPublishable,
} from '@bone/workflow-dsl';
import {
  workflowDesignerApi,
  docFromDefinitionJson,
  toWorkflowFailure,
  type WorkflowDefinitionSummary,
  type WorkflowPublished,
} from '@/services/workflowApi';
import { BonePermissionCodes, hasPermission } from '../auth/permission';

const { Sider, Content } = Layout;
const { Text, Title } = Typography;

const NODE_W = 200;
const NODE_H = 64;
const ROW_GAP = 110;
const COL_GAP = 240;

let codeSeq = 0;
const genCode = (prefix: string) => `${prefix}_${(++codeSeq).toString(36)}_${Date.now().toString(36).slice(-4)}`;

/** 从文档树计算默认画布坐标（未被拖拽覆盖时使用）。 */
function computeDefaultLayout(doc: FlowDoc): Record<string, LayoutPoint> {
  const layout: Record<string, LayoutPoint> = { START: { x: 0, y: 0 } };
  let y = ROW_GAP;

  const walk = (steps: Step[], depth: number) => {
    for (const step of steps) {
      layout[step.code] = { x: depth * COL_GAP, y };
      y += ROW_GAP;
      if (step.kind === 'condition') {
        for (const branch of step.branches) walk(branch.body, depth + 1);
        walk(step.defaultBranch.body, depth + 1);
      }
    }
  };
  walk(doc.steps, 0);
  layout.END = { x: 0, y };
  return layout;
}

const blankDoc = (key: string, name: string): FlowDoc => ({ key, name, steps: [] });

const emptyApprove = (code: string): ApproveStep => ({
  kind: 'approve',
  code,
  label: '审批节点',
  mode: 'SINGLE',
  rules: [],
});
const emptyCc = (code: string): CcStep => ({ kind: 'cc', code, label: '抄送节点', rules: [] });
const emptyCondition = (code: string): ConditionStep => ({
  kind: 'condition',
  code,
  label: '条件分支',
  branches: [{ id: genCode('b'), label: '分支 1', when: null, body: [] }],
  defaultBranch: { id: genCode('b'), label: '默认', when: null, body: [] },
});

/** 不可变地更新文档树里某个 code 对应的 step。 */
function updateStep(doc: FlowDoc, code: string, updater: (s: Step) => Step): FlowDoc {
  const map = (steps: Step[]): Step[] =>
    steps.map((s) => {
      if (s.code === code) return updater(s);
      if (s.kind === 'condition') {
        return {
          ...s,
          branches: s.branches.map((b) => ({ ...b, body: map(b.body) })),
          defaultBranch: { ...s.defaultBranch, body: map(s.defaultBranch.body) },
        };
      }
      return s;
    });
  return { ...doc, steps: map(doc.steps) };
}

function removeStep(doc: FlowDoc, code: string): FlowDoc {
  const filter = (steps: Step[]): Step[] =>
    steps
      .filter((s) => s.code !== code)
      .map((s) =>
        s.kind === 'condition'
          ? {
              ...s,
              branches: s.branches.map((b) => ({ ...b, body: filter(b.body) })),
              defaultBranch: { ...s.defaultBranch, body: filter(s.defaultBranch.body) },
            }
          : s,
      );
  return { ...doc, steps: filter(doc.steps) };
}

/** 把 step 追加到主链或某条件节点的某分支 body。 */
function appendStep(doc: FlowDoc, step: Step, targetBranchId: string | null): FlowDoc {
  if (targetBranchId == null) return { ...doc, steps: [...doc.steps, step] };
  const map = (steps: Step[]): Step[] =>
    steps.map((s) => {
      if (s.kind !== 'condition') return s;
      const branches = s.branches.map((b) =>
        b.id === targetBranchId ? { ...b, body: [...b.body, step] } : b,
      );
      const defaultBranch =
        s.defaultBranch.id === targetBranchId
          ? { ...s.defaultBranch, body: [...s.defaultBranch.body, step] }
          : s.defaultBranch;
      return { ...s, branches, defaultBranch };
    });
  return { ...doc, steps: map(doc.steps) };
}

/** 试运行报告的前端投影（与后端 {@code SimulationReport} 镜像的界面所需字段）。 */
interface SimReport {
  path?: string[];
  conditionTraces?: unknown[];
  warnings?: string[];
  error?: string | null;
}

/**
 * 画布状态快照：用于判断「有没有未保存的修改」。
 *
 * <p>比对的是**编译产物**而不是 state 对象引用：拖动一个节点只是改 layout，流程语义没变，但版式要保存，
 * 所以快照必须包含 layout；反过来，若拿原始 doc 比，编译选项变化（有损还原）也会被误判成「已修改」。
 * 编译失败返回 null（有损/半成品不参与脏判定，它们本来就禁止保存）。
 */
function snapshotOf(doc: FlowDoc, layout: Record<string, LayoutPoint>): string | null {
  try {
    return JSON.stringify(compile(doc, { layout: Object.keys(layout).length ? layout : undefined }));
  } catch {
    return null;
  }
}

/**
 * 二次确认（Promise 化）。
 *
 * <p>为什么不用 {@code Popconfirm}：需要在 async 流程中间（切换流程、载入历史版本、409 冲突）停下来问一句，
 * 而 Popconfirm 是绑定在某个按钮上的声明式组件，塞不进「已经在执行」的异步函数。
 */
function confirmDiscard(title: string, content: string, okText: string): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      title,
      content,
      okText,
      cancelText: '继续编辑',
      okButtonProps: { danger: true },
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

const WorkflowDesigner: React.FC = () => {
  const [defKey, setDefKey] = useState('');
  const [defName, setDefName] = useState('');
  const [doc, setDoc] = useState<FlowDoc | null>(null);
  const [layout, setLayout] = useState<Record<string, LayoutPoint>>({});
  const [selected, setSelected] = useState<string | null>(null);
  const [revision, setRevision] = useState<number | null>(null);
  const [draftExists, setDraftExists] = useState(false);
  /** 上一次「读入 / 保存」时的画布快照，与当前快照比对即得 dirty。 */
  const [savedSnapshot, setSavedSnapshot] = useState<string | null>(null);
  const [lossy, setLossy] = useState(false);
  const [violations, setViolations] = useState<Violation[]>([]);
  const [simReport, setSimReport] = useState<SimReport | null>(null);
  const [defList, setDefList] = useState<WorkflowDefinitionSummary[]>([]);
  const [versions, setVersions] = useState<WorkflowPublished[]>([]);
  const [versionDrawer, setVersionDrawer] = useState(false);
  const [, setLoading] = useState(false);
  const [drag, setDrag] = useState<{ code: string; dx: number; dy: number } | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  /**
   * 写权限（UX 门禁）：没有 workflow:definitions:write 时，保存/发布/丢弃/上下线全部禁用，
   * 画布仍可查看、校验与试运行——「能看不能改」比「整页 403」更接近真实分权场景（评审者、业务方）。
   * 真正的授权在后端 WorkflowDesignerAuthorizer，这里只避免让用户点出一个必然失败的请求。
   */
  const canWrite = useMemo(
    () => hasPermission(BonePermissionCodes.WORKFLOW_DEFINITIONS_WRITE),
    [],
  );

  const compiled = useMemo(() => {
    if (!doc) return null;
    try {
      return compile(doc, { layout: Object.keys(layout).length ? layout : undefined });
    } catch {
      return null;
    }
  }, [doc, layout]);

  const violationByNode = useMemo(() => groupViolationsByNode(violations), [violations]);

  /**
   * 默认坐标（只依赖文档结构，与拖拽覆盖无关）。
   *
   * <p>必须 memo：它此前在<b>每个节点、每条边</b>的渲染里都被调用一次，100 个节点就是 100 次全树遍历 + 100 份对象；
   * 拖动时每次 mousemove 都重算，画布越复杂越涩，而症状表现为「节点一多就卡」，很难归因到渲染函数里的一行取值。
   */
  const defaultLayout = useMemo<Record<string, LayoutPoint>>(
    () => (doc ? computeDefaultLayout(doc) : {}),
    [doc],
  );

  /** 画布当前快照 / 未保存标记（比的是编译产物，见 {@link snapshotOf}）。 */
  const currentSnapshot = useMemo(() => (doc ? snapshotOf(doc, layout) : null), [doc, layout]);
  const dirty = currentSnapshot !== null && currentSnapshot !== savedSnapshot;

  /**
   * 失败统一呈现（联调实测：拦截器在非 2xx 时 reject，不 catch 就完全没有反馈）。
   *
   * <p>三个分支各有不同处置：403 说明授权没接或没权限，只能提示；409 是并发写冲突，必须重新拉草稿
   * 否则后续每次保存都会继续 409；422 是发布 fail-closed，要把后端回传的违规清单直接灌进画布高亮。
   */
  const reportFailure = useCallback(
    (error: unknown, scene: string, onConflict?: () => void): boolean => {
      const failure = toWorkflowFailure(error);
      switch (failure.kind) {
        case 'unauthenticated':
        case 'forbidden':
          message.error(`${scene}失败：无权限（${failure.message}）`);
          break;
        case 'conflict':
          message.warning(`${scene}失败：${failure.message}`);
          onConflict?.();
          break;
        case 'invalid':
          if (failure.violations.length) setViolations(failure.violations);
          message.error(
            `${scene}失败：存在 ${failure.violations.length} 处违规（已在画布高亮），请修正后重试`,
          );
          break;
        case 'network':
          message.error(`${scene}失败：网络异常，请检查后端是否可达`);
          break;
        default:
          message.error(`${scene}失败：${failure.message}`);
      }
      return false;
    },
    [],
  );

  const refreshList = useCallback(async () => {
    try {
      const res = await workflowDesignerApi.listLatest();
      if (res.code === 200) setDefList(res.data ?? []);
    } catch (error) {
      reportFailure(error, '加载流程定义列表');
    }
  }, [reportFailure]);

  useEffect(() => {
    void refreshList();
  }, [refreshList]);

  /**
   * 把服务端的定义原文装进画布，并把结果记为「已保存」基线。
   *
   * <p>三条载入路径（草稿 / 最新已发布版 / 空白新建）共用它：此前是三段近乎复制粘贴的代码，各自 setDoc +
   * setLayout，基线维护一旦漏掉一处，那条路径就会永远显示「未保存」或者反过来把修改当已保存。
   */
  const applyLoaded = useCallback(
    (key: string, definitionJson: string, fallbackName: string) => {
      const parsed = docFromDefinitionJson(definitionJson);
      let uiLayout: Record<string, LayoutPoint> | undefined;
      try {
        uiLayout = (JSON.parse(definitionJson) as { ui?: { layout?: Record<string, LayoutPoint> } }).ui
          ?.layout;
      } catch {
        // 原文不可解析：由 parsed == null 的有损分支兜住
      }
      const nextDoc = parsed ?? blankDoc(key, fallbackName);
      const nextLayout = uiLayout ?? computeDefaultLayout(nextDoc);
      setDoc(nextDoc);
      setLayout(nextLayout);
      setSavedSnapshot(snapshotOf(nextDoc, nextLayout));
      setLossy(!parsed);
      setSelected(null);
    },
    [],
  );

  const openKey = useCallback(
    async (key: string, name?: string, force = false) => {
      // force 只给「用户已在上层确认过」的调用方用（409 处置），否则会二次弹窗。
      if (!force && dirty) {
        const proceed = await confirmDiscard(
          '当前画布有未保存的修改',
          `打开「${key}」会丢弃画布上未保存的内容，确定继续？`,
          '放弃修改并打开',
        );
        if (!proceed) return;
      }
      setDefKey(key);
      if (name) setDefName(name);
      setSelected(null);
      setViolations([]);
      setSimReport(null);
      setLoading(true);
      try {
        const draft = await workflowDesignerApi.findDraft(key);
        if (draft.code === 200 && draft.data) {
          setDraftExists(true);
          setRevision(draft.data.revision);
          applyLoaded(key, draft.data.definitionJson, draft.data.defName);
          setDefName(draft.data.defName || name || key);
          return;
        }
        // 无草稿：尝试取已发布最新版还原（flowDoc 在 ui 段）。后端按版本号降序返回，data[0] 即最新版。
        const vers = await workflowDesignerApi.listVersions(key);
        if (vers.code === 200 && vers.data && vers.data.length > 0) {
          const latest = vers.data[0];
          applyLoaded(key, latest.definitionJson, latest.defName);
          setDraftExists(false);
          setRevision(null);
          return;
        }
        const fresh = blankDoc(key, name ?? key);
        const freshLayout = computeDefaultLayout(fresh);
        setDoc(fresh);
        setLayout(freshLayout);
        setSavedSnapshot(snapshotOf(fresh, freshLayout));
        setLossy(false);
        setDraftExists(false);
        setRevision(null);
      } catch (error) {
        // 读不到草稿/版本时不能把界面留在空白态：给出空文档让人可继续编辑，同时说明原因。
        reportFailure(error, '打开流程定义');
        const fresh = blankDoc(key, name ?? key);
        const freshLayout = computeDefaultLayout(fresh);
        setDoc(fresh);
        setLayout(freshLayout);
        setSavedSnapshot(snapshotOf(fresh, freshLayout));
        setLossy(false);
        setDraftExists(false);
        setRevision(null);
      } finally {
        setLoading(false);
      }
    },
    [applyLoaded, dirty, reportFailure],
  );

  /** 只把 revision 基线对齐到服务端，**不动画布**——用户选择「保留本地修改」时用。 */
  const syncRevision = useCallback(
    async (key: string) => {
      try {
        const draft = await workflowDesignerApi.findDraft(key);
        if (draft.code === 200 && draft.data) {
          setDraftExists(true);
          setRevision(draft.data.revision);
        } else {
          // 对方把草稿发布或丢弃了：本地内容此时没有基线，下次保存会以新建草稿落库。
          setDraftExists(false);
          setRevision(null);
        }
      } catch (error) {
        reportFailure(error, '同步草稿版本');
      }
    },
    [reportFailure],
  );

  /**
   * 409 的统一处置：必须让用户显式选，不能替他决定。
   *
   * <p>上一版实现是「冲突就重新拉草稿」——它解决了「revision 永远落后、每次保存都 409」，
   * 代价是把「不再报错」换成了「悄悄丢掉用户画了半天的内容」，而这比 409 本身严重得多。现在分两种：
   * 无本地修改时直接拉（无损）；有本地修改时二选一——载入服务器版本，或保留本地但把 revision 对齐
   * （下次保存会覆盖对方，属于知情下的覆盖）。
   */
  const handleConflict = useCallback(
    async (key: string) => {
      if (!dirty) {
        void openKey(key, undefined, true);
        return;
      }
      const takeServer = await new Promise<boolean>((resolve) => {
        Modal.confirm({
          title: '草稿已被他人修改',
          content:
            '载入服务器上的最新草稿会丢弃你当前画布上未保存的修改；选择「保留本地修改」则只对齐版本号，下次保存会覆盖对方的内容。',
          okText: '载入服务器版本',
          cancelText: '保留本地修改',
          okButtonProps: { danger: true },
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (takeServer) {
        void openKey(key, undefined, true);
        return;
      }
      await syncRevision(key);
      message.info('已对齐到最新 revision；再次保存将覆盖对方内容');
    },
    [dirty, openKey, syncRevision],
  );

  const onValidate = useCallback(async () => {
    if (!compiled) return;
    try {
      const res = await workflowDesignerApi.validate(JSON.stringify(compiled));
      if (res.code === 200) {
        const list = res.data?.violations ?? [];
        setViolations(list);
        if (isPublishable(list)) message.success('校验通过，可发布');
        else message.warning(`存在 ${list.length} 处问题`);
      }
    } catch (error) {
      reportFailure(error, '校验');
    }
  }, [compiled, reportFailure]);

  const onSaveDraft = useCallback(async () => {
    if (!doc || !defKey) {
      message.error('请先指定流程 key');
      return;
    }
    if (lossy) {
      message.error('当前为旧数据有损还原，请补全节点后再保存');
      return;
    }
    const def = compile(doc, { layout: Object.keys(layout).length ? layout : undefined });
    try {
      const res = await workflowDesignerApi.saveDraft(defKey, JSON.stringify(def), revision);
      if (res.code === 200 && res.data) {
        setRevision(res.data.draft.revision);
        setDraftExists(true);
        setViolations(res.data.violations);
        // 保存成功了才推进基线，否则「已保存」标记会先于落库出现，用户以为存住了其实没有。
        setSavedSnapshot(JSON.stringify(def));
        message.success(`草稿已保存（revision=${res.data.draft.revision}）`);
      }
    } catch (error) {
      // 409 = 别人的 revision 更新了：交给 handleConflict 让用户选「载入服务器版本」还是「保留本地」。
      reportFailure(error, '保存草稿', () => void handleConflict(defKey));
    }
  }, [doc, defKey, lossy, layout, revision, reportFailure, handleConflict]);

  const onPublish = useCallback(async () => {
    if (!doc || !defKey) return;
    // 发布的对象是「库里的草稿」，不是画布。没有草稿时先保存——否则后端会回 409「没有草稿，无法发布」，
    // 而用户刚点完「发布」看到「没有草稿」只会以为是权限或系统问题。
    if (!draftExists) {
      message.warning('画布内容还没有存成草稿，请先点「保存草稿」再发布');
      return;
    }
    const def = compile(doc, { layout: Object.keys(layout).length ? layout : undefined });
    try {
      // 前端预检只为省一次往返，最终以服务端 fail-closed 结论为准（下面 422 分支会接收后端回传的违规）。
      const vRes = await workflowDesignerApi.validate(JSON.stringify(def));
      const localViolations = vRes.data?.violations ?? [];
      if (vRes.code !== 200 || !isPublishable(localViolations)) {
        setViolations(localViolations);
        message.error('存在违规，拒绝发布（fail-closed）');
        return;
      }
      setViolations([]);
      const res = await workflowDesignerApi.publish(defKey, revision);
      if (res.code === 200) {
        message.success(`已发布 v${res.data?.version}`);
        setDraftExists(false);
        setRevision(null);
        // 草稿已被转成不可变版本，画布内容与库里一致，故推进基线（否则会一直显示「未保存」）。
        setSavedSnapshot(JSON.stringify(def));
        void refreshList();
      }
    } catch (error) {
      reportFailure(error, '发布', () => void handleConflict(defKey));
    }
  }, [doc, defKey, draftExists, layout, revision, refreshList, reportFailure, handleConflict]);

  const onSimulate = useCallback(async () => {
    if (!compiled) return;
    try {
      const res = await workflowDesignerApi.simulate({
        definitionJson: JSON.stringify(compiled),
        sampleData: {},
        initiatorId: 'sim-user',
      });
      if (res.code === 200) setSimReport(res.data ?? null);
    } catch (error) {
      reportFailure(error, '试运行');
    }
  }, [compiled, reportFailure]);

  const onDiscardDraft = useCallback(async () => {
    if (!defKey) return;
    try {
      await workflowDesignerApi.discardDraft(defKey);
      setDraftExists(false);
      setRevision(null);
      setViolations([]);
      // 草稿已从库里删除，画布内容此刻「哪里都没存」，基线必须清空让它显示为未保存。
      setSavedSnapshot(null);
      message.success('草稿已丢弃');
    } catch (error) {
      reportFailure(error, '丢弃草稿');
    }
  }, [defKey, reportFailure]);

  const onOpenVersions = useCallback(async () => {
    setVersionDrawer(true);
    if (!defKey) return;
    try {
      const res = await workflowDesignerApi.listVersions(defKey);
      if (res.code === 200) setVersions(res.data ?? []);
    } catch (error) {
      reportFailure(error, '加载版本列表');
    }
  }, [defKey, reportFailure]);

  const onToggleEnabled = useCallback(
    async (version: number, enabled: boolean) => {
      if (!defKey) return;
      try {
        await workflowDesignerApi.setEnabled(defKey, version, enabled);
        const res = await workflowDesignerApi.listVersions(defKey);
        if (res.code === 200) setVersions(res.data ?? []);
        void refreshList();
        message.success(enabled ? `v${version} 已上线` : `v${version} 已下线`);
      } catch (error) {
        reportFailure(error, enabled ? '上线版本' : '下线版本');
      }
    },
    [defKey, refreshList, reportFailure],
  );

  /**
   * 把历史版本载入画布。
   *
   * <p>必须确认：载入是**覆盖画布**的动作，而覆盖之后只有「保存草稿」才会落库——用户很容易以为「载入了就是切过去了」，
   * 于是接着改了别的地方再保存，把想保留的草稿冲掉。故这里明确提示「保存后将覆盖当前草稿」。
   */
  const loadVersion = useCallback(
    async (v: WorkflowPublished) => {
      if (dirty) {
        const proceed = await confirmDiscard(
          `载入 v${v.version} 会覆盖当前画布`,
          '当前画布有未保存的修改，载入历史版本会丢弃它们。注意：载入后保存将覆盖现有草稿。',
          '载入并丢弃修改',
        );
        if (!proceed) return;
      }
      applyLoaded(v.defKey, v.definitionJson, v.defName);
      setVersionDrawer(false);
      message.info(`已载入 v${v.version} 到画布；保存后将成为草稿内容`);
    },
    [applyLoaded, dirty],
  );

  const onAdd = (kind: 'approve' | 'cc' | 'condition') => {
    if (!doc) return;
    const code = genCode(kind);
    const step: Step =
      kind === 'approve' ? emptyApprove(code) : kind === 'cc' ? emptyCc(code) : emptyCondition(code);
    const targetBranch = selected ? findBranchId(doc, selected) : null;
    setDoc(appendStep(doc, step, targetBranch));
    setSelected(code);
  };

  const onDelete = (code: string) => {
    if (!doc) return;
    setDoc(removeStep(doc, code));
    setSelected(null);
  };

  const onMouseDown = (e: React.MouseEvent, code: string) => {
    e.preventDefault();
    const start = layout[code] ?? { x: 0, y: 0 };
    setDrag({ code, dx: e.clientX - start.x, dy: e.clientY - start.y });
  };
  useEffect(() => {
    if (!drag) return;
    const move = (e: MouseEvent) => {
      setLayout((prev) => ({ ...prev, [drag.code]: { x: e.clientX - drag.dx, y: e.clientY - drag.dy } }));
    };
    const up = () => setDrag(null);
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
    return () => {
      window.removeEventListener('mousemove', move);
      window.removeEventListener('mouseup', up);
    };
  }, [drag]);

  /**
   * 关闭 / 刷新页面时拦一下。
   *
   * <p>这是最常见的一条丢数据路径，比 409 常见得多：设计一个流程要十几分钟，误关标签、误刷新全白干。浏览器只允许
   * 「提示」，文案由浏览器决定（不能自定义），但没有这一步就连提示都没有。
   */
  useEffect(() => {
    if (!dirty) return;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      // 部分浏览器（Chrome < 119）只认 returnValue 的赋值，两处都写才覆盖全。
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);

  const selectedStep = doc && selected ? findStep(doc, selected) : null;

  return (
    <Layout style={{ height: '100%' }}>
      <Sider width={300} theme="light" style={{ borderRight: '1px solid #f0f0f0', overflow: 'auto' }}>
        <div style={{ padding: 16 }}>
          <Title level={5}>工作流设计器</Title>
          <Input
            placeholder="流程 key（如 expense_designer）"
            value={defKey}
            onChange={(e) => setDefKey(e.target.value)}
            addonBefore="key"
            style={{ marginBottom: 8 }}
          />
          <Input
            placeholder="流程名称"
            value={defName}
            onChange={(e) => setDefName(e.target.value)}
            style={{ marginBottom: 8 }}
          />
          <Button block onClick={() => void openKey(defKey, defName)}>打开 / 新建</Button>
          <Divider>已有流程</Divider>
          <Space direction="vertical" style={{ width: '100%' }}>
            {defList.map((d) => (
              <Button key={d.defKey} block type={d.defKey === defKey ? 'primary' : 'default'} onClick={() => void openKey(d.defKey, d.defName)}>
                {d.defName} <Tag color={d.enabled ? 'green' : 'default'}>v{d.version}</Tag>
              </Button>
            ))}
            {defList.length === 0 && <Empty description="暂无流程" />}
          </Space>
        </div>
      </Sider>
      <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <div style={{ padding: 12, borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space wrap>
            <Button
              icon={<SaveOutlined />}
              onClick={onSaveDraft}
              disabled={!doc || !canWrite || lossy}
              title={lossy ? '当前为旧数据有损还原，保存已禁用，请补全节点后重新载入' : undefined}
            >
              保存草稿
            </Button>
            <Button
              icon={<SendOutlined />}
              type="primary"
              onClick={onPublish}
              disabled={!doc || !canWrite || lossy}
              title={lossy ? '有损还原的定义无法发布，请先补全节点并重新载入' : undefined}
            >
              发布
            </Button>
            <Button icon={<EyeOutlined />} onClick={onValidate} disabled={!doc}>
              校验
            </Button>
            <Button icon={<ExperimentOutlined />} onClick={onSimulate} disabled={!doc}>
              试运行
            </Button>
            <Popconfirm title="确认丢弃草稿？" onConfirm={() => void onDiscardDraft()}>
              <Button icon={<DeleteOutlined />} danger disabled={!draftExists || !canWrite}>
                丢弃草稿
              </Button>
            </Popconfirm>
            <Button icon={<AuditOutlined />} onClick={() => void onOpenVersions()}>
              版本
            </Button>
            {draftExists && <Tag color="orange">草稿 rev {revision}</Tag>}
            {dirty && (
              <Tooltip title="画布内容与上次保存/载入不一致，关闭页面前请先保存草稿">
                <Tag color="red">未保存</Tag>
              </Tooltip>
            )}
            {lossy && <Tag color="red">有损还原</Tag>}
            {!canWrite && (
              <Tooltip title="缺少 workflow:definitions:write，仅可查看 / 校验 / 试运行">
                <Tag color="default">只读</Tag>
              </Tooltip>
            )}
          </Space>
          <Space>
            <Tooltip title="向主链或选中条件分支添加节点">
              <Button icon={<PlusOutlined />} onClick={() => onAdd('approve')} disabled={!canWrite}>
                审批
              </Button>
            </Tooltip>
            <Button icon={<PlusOutlined />} onClick={() => onAdd('cc')} disabled={!canWrite}>
              抄送
            </Button>
            <Button
              icon={<BranchesOutlined />}
              onClick={() => onAdd('condition')}
              disabled={!canWrite}
            >
              条件
            </Button>
          </Space>
        </div>

        <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
          {/* 画布 */}
          <div
            ref={containerRef}
            style={{ flex: 1, position: 'relative', overflow: 'auto', background: '#fafafa' }}
          >
            {!doc && <Empty style={{ marginTop: 80 }} description="指定流程 key 后开始设计" />}
            {doc && compiled && (
              <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
                {compiled.edges.map((e, i) => {
                  const a = layout[e.from] ?? defaultLayout[e.from];
                  const b = layout[e.to] ?? defaultLayout[e.to];
                  if (!a || !b) return null;
                  return (
                    <line
                      key={i}
                      x1={a.x + NODE_W / 2}
                      y1={a.y + NODE_H}
                      x2={b.x + NODE_W / 2}
                      y2={b.y}
                      stroke="#bbb"
                      strokeWidth={1.5}
                    />
                  );
                })}
              </svg>
            )}
            {doc && compiled && compiled.nodes.map((n) => {
              const pos = layout[n.code] ?? defaultLayout[n.code] ?? { x: 0, y: 0 };
              const vs = violationByNode.get(n.code) ?? [];
              const color =
                n.type === 'START' ? '#52c41a' : n.type === 'END' ? '#999' : n.type === 'CONDITION' ? '#722ed1' : n.type === 'CC' ? '#13c2c2' : '#1677ff';
              return (
                <div
                  key={n.code}
                  onMouseDown={(e) => onMouseDown(e, n.code)}
                  onClick={() => setSelected(n.code)}
                  style={{
                    position: 'absolute',
                    left: pos.x,
                    top: pos.y,
                    width: NODE_W,
                    height: NODE_H,
                    cursor: 'grab',
                    border: `2px solid ${selected === n.code ? '#fa8c16' : color}`,
                    borderRadius: 8,
                    background: '#fff',
                    boxShadow: selected === n.code ? '0 0 0 3px rgba(250,140,22,.2)' : '0 1px 4px rgba(0,0,0,.1)',
                    padding: '6px 10px',
                    userSelect: 'none',
                  }}
                >
                  <div style={{ fontSize: 12, color }}>{n.type}</div>
                  <div style={{ fontWeight: 600, fontSize: 13, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{n.label}</div>
                  {vs.length > 0 && <Badge count={vs.length} size="small" style={{ position: 'absolute', top: -8, right: -8 }} />}
                </div>
              );
            })}
          </div>

          {/* 属性面板 */}
          <div style={{ width: 320, borderLeft: '1px solid #f0f0f0', padding: 16, overflow: 'auto' }}>
            {!selectedStep && <Empty description="选中节点以编辑属性" />}
            {selectedStep && (
              <StepPropertyPanel
                step={selectedStep}
                onChange={(next) => setDoc((d) => (d ? updateStep(d, selectedStep.code, () => next) : d))}
                onDelete={() => onDelete(selectedStep.code)}
                violations={violationByNode.get(selectedStep.code) ?? []}
                readOnly={!canWrite}
              />
            )}
            {violations.length > 0 && (
              <>
                <Divider>校验问题</Divider>
                <Space direction="vertical" style={{ width: '100%' }}>
                  {violations.map((v, i) => (
                    <Alert
                      key={i}
                      type="error"
                      showIcon
                      message={`${v.code}${v.locator ? ` @${v.locator}` : ''}`}
                      description={hintOf(v).suggestion ?? v.message}
                      onClick={() => v.locator && setSelected(v.locator)}
                      style={{ cursor: v.locator ? 'pointer' : 'default' }}
                    />
                  ))}
                </Space>
              </>
            )}
            {simReport && (
              <>
                <Divider>试运行</Divider>
                <SimReportView report={simReport} />
              </>
            )}
          </div>
        </div>
      </Content>

      <Drawer title={`版本管理 · ${defKey}`} open={versionDrawer} onClose={() => setVersionDrawer(false)} width={420}>
        <Space direction="vertical" style={{ width: '100%' }}>
          {versions.map((v) => (
            <Card key={v.version} size="small">
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <span>
                  v{v.version} <Tag color={v.enabled ? 'green' : 'default'}>{v.enabled ? '已上线' : '已下线'}</Tag>
                </span>
                <Space>
                  <Button size="small" onClick={() => void loadVersion(v)}>载入</Button>
                  <Switch
                    checked={v.enabled}
                    size="small"
                    disabled={!canWrite}
                    onChange={(on) => void onToggleEnabled(v.version, on)}
                  />
                </Space>
              </Space>
            </Card>
          ))}
          {versions.length === 0 && <Empty description="暂无版本" />}
        </Space>
      </Drawer>
    </Layout>
  );
};

/** 在文档树里定位 code 所在的 step。 */
function findStep(doc: FlowDoc, code: string): Step | null {
  const walk = (steps: Step[]): Step | null => {
    for (const s of steps) {
      if (s.code === code) return s;
      if (s.kind === 'condition') {
        for (const b of s.branches) {
          const r = walk(b.body);
          if (r) return r;
        }
        const d = walk(s.defaultBranch.body);
        if (d) return d;
      }
    }
    return null;
  };
  return walk(doc.steps);
}

/** 若选中节点在某个条件分支体内，返回该分支 id（用于向其追加子节点）。 */
function findBranchId(doc: FlowDoc, code: string): string | null {
  const walk = (steps: Step[]): string | null => {
    for (const s of steps) {
      if (s.kind !== 'condition') continue;
      for (const b of s.branches) {
        if (b.body.some((x) => x.code === code)) return b.id;
        const r = walk(b.body);
        if (r) return r;
      }
      if (s.defaultBranch.body.some((x) => x.code === code)) return s.defaultBranch.id;
      const d = walk(s.defaultBranch.body);
      if (d) return d;
    }
    return null;
  };
  return walk(doc.steps);
}

const STEP_COLOR: Record<string, string> = {
  approve: '#1677ff',
  cc: '#13c2c2',
  condition: '#722ed1',
};

const StepPropertyPanel: React.FC<{
  step: Step;
  onChange: (next: Step) => void;
  onDelete: () => void;
  violations: Violation[];
  readOnly?: boolean;
}> = ({ step, onChange, onDelete, violations, readOnly = false }) => {
  return (
    // 只读模式用原生 <fieldset disabled> 一次性停用面板内全部表单控件（input / select / button），
    // 不逐个传 disabled：属性面板里控件有二十多个，逐个传既易漏也让后续新增控件默认「忘了门禁」。
    <fieldset
      disabled={readOnly}
      style={{ border: 0, padding: 0, margin: 0, minWidth: 0, opacity: readOnly ? 0.75 : 1 }}
    >
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Tag color={STEP_COLOR[step.kind]}>{step.kind}</Tag>
        <Button size="small" danger icon={<DeleteOutlined />} onClick={onDelete}>
          删除
        </Button>
      </Space>
      <Divider style={{ margin: '12px 0' }} />
      <div style={{ marginBottom: 8 }}>
        <Text type="secondary">节点编码</Text>
        <div>{step.code}</div>
      </div>
      <div style={{ marginBottom: 8 }}>
        <Text type="secondary">名称</Text>
        <Input value={step.label} onChange={(e) => onChange({ ...step, label: e.target.value })} />
      </div>

      {step.kind === 'approve' && (
        <>
          <div style={{ marginBottom: 8 }}>
            <Text type="secondary">完成方式</Text>
            <Select
              style={{ width: '100%' }}
              value={step.mode}
              onChange={(v: ApprovalMode) => onChange({ ...step, mode: v })}
              options={[
                { value: 'SINGLE', label: '单签' },
                { value: 'ALL', label: '会签（全部）' },
                { value: 'ANY', label: '或签（任一）' },
                { value: 'RATIO', label: '比例会签' },
              ]}
            />
          </div>
          {step.mode === 'RATIO' && (
            <div style={{ marginBottom: 8 }}>
              <Text type="secondary">至少通过人数</Text>
              <InputNumber min={1} style={{ width: '100%' }} value={step.quorum ?? 1} onChange={(v) => onChange({ ...step, quorum: v ?? 1 })} />
            </div>
          )}
          <RuleEditor rules={step.rules} onChange={(rules) => onChange({ ...step, rules })} />
        </>
      )}

      {step.kind === 'cc' && <RuleEditor rules={step.rules} onChange={(rules) => onChange({ ...step, rules })} />}

      {step.kind === 'condition' && (
        <BranchEditor branches={step.branches} defaultBranch={step.defaultBranch} onChange={(branches, defaultBranch) => onChange({ ...step, branches, defaultBranch })} />
      )}

      {violations.length > 0 && (
        <Alert
          style={{ marginTop: 8 }}
          type="error"
          showIcon
          message={violations.map((v) => `${v.code}`).join('、')}
          description={violations.map((v) => hintOf(v).suggestion ?? v.message).join('；')}
        />
      )}
    </fieldset>
  );
};

const RuleEditor: React.FC<{ rules: AssignmentRule[]; onChange: (rules: AssignmentRule[]) => void }> = ({ rules, onChange }) => {
  const update = (i: number, rule: AssignmentRule) => onChange(rules.map((r, idx) => (idx === i ? rule : r)));
  return (
    <div>
      <Text type="secondary">审批人 / 抄送人指派</Text>
      {rules.map((rule, i) => (
        <Card key={i} size="small" style={{ margin: '8px 0' }}>
          <Select
            style={{ width: '100%', marginBottom: 6 }}
            value={rule.strategy}
            onChange={(v: AssignmentStrategy) => update(i, { strategy: v, params: {} })}
            options={[
              { value: 'LINE_MANAGER', label: '直属主管' },
              { value: 'DEPT_HEAD', label: '部门负责人' },
              { value: 'LEVEL_UP', label: '向上 N 级' },
              { value: 'ROLE', label: '角色' },
              { value: 'USER', label: '指定人' },
              { value: 'FIELD', label: '表单字段' },
              { value: 'INITIATOR_SELECT', label: '发起人自选' },
            ]}
          />
          {PARAM_KEY_BY_STRATEGY[rule.strategy] && (
            <Input
              placeholder={PARAM_KEY_BY_STRATEGY[rule.strategy] as string}
              value={(rule.params[PARAM_KEY_BY_STRATEGY[rule.strategy] as string] as string) ?? ''}
              onChange={(e) => update(i, { ...rule, params: { ...rule.params, [PARAM_KEY_BY_STRATEGY[rule.strategy] as string]: e.target.value } })}
            />
          )}
          <Button size="small" danger style={{ marginTop: 6 }} onClick={() => onChange(rules.filter((_, idx) => idx !== i))}>
            移除
          </Button>
        </Card>
      ))}
      <Button size="small" icon={<PlusOutlined />} onClick={() => onChange([...rules, { strategy: 'LINE_MANAGER', params: {} }])}>
        添加指派
      </Button>
    </div>
  );
};

const OPERATORS = [
  { value: 'eq', label: '等于' },
  { value: 'neq', label: '不等于' },
  { value: 'gt', label: '大于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lt', label: '小于' },
  { value: 'lte', label: '小于等于' },
  { value: 'in', label: '属于' },
  { value: 'between', label: '介于' },
];

const BranchEditor: React.FC<{
  branches: Branch[];
  defaultBranch: Branch;
  onChange: (branches: Branch[], defaultBranch: Branch) => void;
}> = ({ branches, defaultBranch, onChange }) => {
  const renderBranch = (b: Branch, isDefault: boolean) => (
    <Card key={b.id} size="small" title={isDefault ? `默认分支：${b.label}` : `分支：${b.label}`} style={{ margin: '8px 0' }}>
      <Input
        placeholder="分支名称"
        value={b.label}
        onChange={(e) => patch(b.id, { ...b, label: e.target.value }, isDefault)}
        style={{ marginBottom: 6 }}
      />
      {!isDefault && (
        <div style={{ display: 'flex', gap: 4, marginBottom: 6 }}>
          <Input placeholder="字段 fieldCode" value={(b.when as { fieldCode?: string } | null)?.fieldCode ?? ''} onChange={(e) => patch(b.id, { ...b, when: { fieldCode: e.target.value, operator: ((b.when as { operator?: string } | null)?.operator as never) ?? 'eq', value: ((b.when as { value?: unknown } | null)?.value as never) ?? '' } }, isDefault)} />
          <Select
            style={{ width: 90 }}
            value={((b.when as { operator?: string } | null)?.operator as string) ?? 'eq'}
            onChange={(op) => patch(b.id, { ...b, when: { fieldCode: ((b.when as { fieldCode?: string } | null)?.fieldCode as string) ?? '', operator: op as never, value: ((b.when as { value?: unknown } | null)?.value as never) ?? '' } }, isDefault)}
            options={OPERATORS}
          />
          <Input placeholder="值" value={((b.when as { value?: unknown } | null)?.value as string) ?? ''} onChange={(e) => patch(b.id, { ...b, when: { fieldCode: ((b.when as { fieldCode?: string } | null)?.fieldCode as string) ?? '', operator: ((b.when as { operator?: string } | null)?.operator as never) ?? 'eq', value: e.target.value as never } }, isDefault)} />
        </div>
      )}
      <Text type="secondary">分支内步骤：{b.body.length} 个</Text>
    </Card>
  );
  const patch = (id: string, next: Branch, isDefault: boolean) => {
    if (isDefault) onChange(branches, next);
    else onChange(branches.map((x) => (x.id === id ? next : x)), defaultBranch);
  };
  return (
    <div>
      {branches.map((b) => renderBranch(b, false))}
      {renderBranch(defaultBranch, true)}
      <Button size="small" icon={<PlusOutlined />} onClick={() => onChange([...branches, { id: genCode('b'), label: `分支 ${branches.length + 1}`, when: null, body: [] }], defaultBranch)}>
        添加分支
      </Button>
    </div>
  );
};

const SimReportView: React.FC<{ report: { path?: string[]; conditionTraces?: unknown[]; warnings?: string[]; error?: string | null } }> = ({ report }) => {
  if (report.error) return <Alert type="error" message="试运行失败" description={report.error} />;
  return (
    <div>
      <div><Text type="secondary">路径：</Text> {report.path?.join(' → ')}</div>
      {report.warnings?.map((w, i) => (
        <Alert key={i} type="warning" showIcon message={w} style={{ margin: '4px 0' }} />
      ))}
      {report.conditionTraces && report.conditionTraces.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <Text type="secondary">分支判定：</Text>
          {report.conditionTraces.map((t, i) => (
            <div key={i}>{(t as { label?: string }).label}：{(t as { branches?: Array<{ label: string; matched: boolean | null }> }).branches?.map((b) => `${b.label}=${b.matched}`).join('，')}</div>
          ))}
        </div>
      )}
    </div>
  );
};

export default WorkflowDesigner;
