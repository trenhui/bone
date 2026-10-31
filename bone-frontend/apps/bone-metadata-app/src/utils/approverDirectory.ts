import { createApiClient } from '@bone/shared-services';

/**
 * 审批人姓名目录（IAM 账户 id → 姓名），带进程内缓存。
 *
 * <p><b>为什么不放在后端做</b>：{@code bone-iam} 是独立部署服务（全仓无模块把它当库消费），
 * 元数据服务没有 IAM 客户端；为「把 9104 显示成张伟」给元数据服务拉一条跨服务依赖，代价
 * 远大于收益。浏览器侧本来就已经过网关、本来就带 token，直接读一次账号目录最省。
 *
 * <p><b>失败必须静默</b>：普通报销用户通常没有 {@code iam:account:read}，拿 403 是常态而非
 * 异常——此时返回空表，调用方降级为「N 人待办」。绝不可把账户 id 顶上来当名字。
 *
 * <p><b>缓存</b>：TTL 5 分钟、并发去重（同一次渲染里多列/多行同时要名字，不能打 N 次目录）。
 */

const iamApi = createApiClient('/api/v1/iam', { timeout: 10000 });

/** 后端账号分页上限 200，225 个账号按页拉完即可；上限 5 页纯粹是防御（目录不该无限膨胀）。 */
const PAGE_SIZE = 200;
const MAX_PAGES = 5;
const TTL_MS = 5 * 60 * 1000;

export type ApproverDirectory = Record<string, string>;

interface IamAccountRow {
  id?: string | number;
  username?: string;
  realName?: string | null;
}

interface IamAccountPage {
  data?: { records?: IamAccountRow[]; total?: number | string };
}

let cached: { at: number; map: ApproverDirectory } | null = null;
let inflight: Promise<ApproverDirectory> | null = null;

/** 取审批人姓名目录（失败返回空表，不抛错）。 */
export function loadApproverNames(): Promise<ApproverDirectory> {
  if (cached && Date.now() - cached.at < TTL_MS) {
    return Promise.resolve(cached.map);
  }
  if (inflight) {
    return inflight;
  }
  inflight = (async () => {
    const map: ApproverDirectory = {};
    try {
      for (let page = 1; page <= MAX_PAGES; page += 1) {
        const res = await iamApi.get<never, IamAccountPage>('/accounts', {
          params: { page, size: PAGE_SIZE },
        });
        const rows = res?.data?.records ?? [];
        for (const row of rows) {
          const id = row.id == null ? '' : String(row.id);
          const name = row.realName || row.username || '';
          if (id !== '' && name !== '') {
            map[id] = name;
          }
        }
        if (rows.length < PAGE_SIZE) {
          break;
        }
      }
      cached = { at: Date.now(), map };
    } catch {
      // 无读权限 / 网关不可用：降级为空表（调用方显示「N 人待办」）
    } finally {
      inflight = null;
    }
    return map;
  })();
  return inflight;
}

/** 测试/登出用：清空缓存，避免切换账号后仍显示上一租户的姓名。 */
export function resetApproverDirectoryCache(): void {
  cached = null;
  inflight = null;
}

/** 人员选择器选项（姓名搜索）。 */
export interface ApproverOption {
  value: string;
  label: string;
}

/** id→姓名 表转下拉选项（按姓名排序，便于搜索时视觉稳定）。 */
export function toApproverOptions(names: ApproverDirectory): ApproverOption[] {
  return Object.entries(names)
    .map(([value, label]) => ({ value, label }))
    .sort((a, b) => a.label.localeCompare(b.label, 'zh-Hans-CN'));
}

/**
 * 是否为「审批人」字段（账号 id 形态）。
 *
 * <p>判据刻意收窄到整型 + `approver_id` 命名：把任意 `*_id` 都换成人员选择器会把
 * 类别 id / 成本中心 id 这类**非人员**外键也变成人员下拉（那是更严重的错配）。
 * 真正的根治是把字段建模为 REFERENCE 指向账户实体，那属于建模层动作；
 * 这里只覆盖平台已知的审批人字段命名约定。
 */
export function isApproverPickerField(field: { code?: string; type?: string }): boolean {
  const code = (field.code || '').toLowerCase();
  const type = (field.type || '').toUpperCase();
  if (!['LONG', 'INTEGER', 'BIGINT'].includes(type)) {
    return false;
  }
  return code === 'approver_id' || code.endsWith('_approver_id');
}
