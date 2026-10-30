import type { ClaimStatusName } from './claimApi';

/**
 * 案件状态字典 —— **唯一真源**。
 *
 * <p>改造前同一份状态文案在 `ReviewWorkbenchPage`（STATUS_TAG，带颜色）与
 * `ClaimProgressPage`（STATUS_LABEL，只有文字）各写一遍，两处条目集还不一样：
 * 前者缺 `DRAFT/PENDING_PRE_EXAM/...` 之外的部分条目时不会报错，只会让某一页把状态显示成英文枚举名
 * （用户看到 `CLOSED_REJECTED` 而不是「已撤销/拒赔」）。这类漂移没有编译期保护，只能靠收敛成一份来消除。
 *
 * <p>取值必须与后端 `com.bone.claim.domain.model.claim.valueobject.ClaimStatus` 的 `name()` 逐字一致
 * （该枚举注释明确：落库与 SQL 过滤一律按 name，`code()` 只作对外桥接码）。
 * 后端新增状态而本表未跟随时，`claimStatusMeta` 会回退显示原始枚举名——**降级可见，而不是显示错文案**。
 */
export interface ClaimStatusMeta {
  /** antd Tag color */
  color: string;
  /** 中文展示名 */
  label: string;
  /** 是否为终态（不再跃迁，仅申诉可进入 APPEALING） */
  terminal?: boolean;
}

export const CLAIM_STATUS_META: Record<ClaimStatusName, ClaimStatusMeta> = {
  DRAFT: { color: 'default', label: '草稿' },
  ACCEPTED: { color: 'blue', label: '已签收' },
  PENDING_PRE_EXAM: { color: 'processing', label: '待初审分配' },
  PRE_EXAMINING: { color: 'processing', label: '初审中' },
  AUTO_PRE_EXAMINING: { color: 'cyan', label: '自动化初审中' },
  PRE_EXAMINED: { color: 'geekblue', label: '已初审' },
  EVIDENCE_SUBMITTING: { color: 'orange', label: '补录中' },
  PENDING_ADJUST: { color: 'gold', label: '待核赔' },
  ADJUSTING: { color: 'processing', label: '核赔中' },
  ADJUSTED: { color: 'green', label: '已审核' },
  REVIEWING: { color: 'purple', label: '复核中' },
  CLOSED_REJECTED: { color: 'red', label: '已撤销/拒赔生效', terminal: true },
  FINISHED: { color: 'green', label: '已结案', terminal: true },
  APPEALING: { color: 'volcano', label: '申诉中' },
};

/**
 * 状态 → 展示元信息（未知状态回退为「原始枚举名 + 灰色」，绝不编造中文名）。
 */
export function claimStatusMeta(status?: string): ClaimStatusMeta {
  if (!status) {
    return { color: 'default', label: '-' };
  }
  return CLAIM_STATUS_META[status as ClaimStatusName] ?? { color: 'default', label: status };
}

/**
 * 初审工作台待办口径：已签收 + 待初审分配 + 初审中 + 自动化初审中。
 *
 * <p>`ACCEPTED`（7）必须包含：材料完整后才会 `allocate` 到 10，若列表不含 7，则刚受理的案件
 * 在初审工作台里查不到——审核员会以为「单子没进来」。
 */
export const PRE_EXAM_TODO_STATUSES: ClaimStatusName[] = [
  'ACCEPTED',
  'PENDING_PRE_EXAM',
  'PRE_EXAMINING',
  'AUTO_PRE_EXAMINING',
];

/**
 * 核赔工作台的**任务维 → 案件状态集**映射不放在这里，也不放在任何前端文件里。
 *
 * <p>真源是后端 `adapter/web/ReviewTaskStatusMapper`：`WAITING→40`、`ASSIGNED/PROCESSING→41`、
 * `DONE→42/44/50/45`，不传则取默认待办 `40+41`。工作台一律通过
 * `GET /api/v1/review/tasks?status=` 取数，前端只传任务维 4 值。
 *
 * <p>为什么必须这样：此前本文件与工作台各自维护一份「待办 = 40」的口径，后果不是文案漂移而是**功能断链**——
 * 案件被领取后进入 `ADJUSTING(41)`，立刻从列表消失，于是「通过/拒赔/补件」这三个只挂在 41 行上的按钮
 * 再也没机会被点到。映射一旦有两份，其中一份必然落后于状态机的演进。
 */

/** 生成 el/antd Select 选项（按传入状态集，顺序即业务顺序）。 */
export function statusOptions(statuses: ClaimStatusName[]): { value: ClaimStatusName; label: string }[] {
  return statuses.map((value) => ({ value, label: CLAIM_STATUS_META[value].label }));
}
