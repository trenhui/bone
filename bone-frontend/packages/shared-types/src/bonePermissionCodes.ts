/**
 * BONE 平台权限码契约（与 IAM 详设 §3.7、JWT authorities / iam_permission.code 一致）。
 * @see doc/design/modules/6. IAM账号权限管理模块详细设计方案.md
 */

/** `{domain}:{resource}:{action}` 或 `{domain}:{action}` */
export type BonePermissionCode = string;

export const BonePermissionDomain = {
  IAM: 'iam',
  METADATA: 'metadata',
  EXTENSION: 'extension',
  MASTERDATA: 'masterdata',
  INTEGRATION: 'integration',
  SYSTEM: 'system',
} as const;

/** As-Is：后端已引用或 JWT 种子已包含 */
export const BonePermissionCodes = {
  // IAM（管理面 · 与后端 DefaultPermissionCodes 和数据库 iam_permission.code 一致）
  IAM_ACCOUNTS_READ: 'iam:accounts:read',
  IAM_ACCOUNTS_WRITE: 'iam:accounts:write',
  IAM_ROLES_READ: 'iam:roles:read',
  IAM_ROLES_WRITE: 'iam:roles:write',
  IAM_PERMISSIONS_READ: 'iam:permissions:read',
  IAM_PERMISSIONS_WRITE: 'iam:permissions:write',
  IAM_AUDIT_READ: 'iam:audit:read',
  IAM_AUDIT_WRITE: 'iam:audit:write',
  IAM_TENANTS_READ: 'iam:tenants:read',
  IAM_TENANTS_WRITE: 'iam:tenants:write',
  IAM_SESSIONS_READ: 'iam:sessions:read',
  IAM_SESSIONS_WRITE: 'iam:sessions:write',
  IAM_DEPTS_READ: 'iam:depts:read',
  IAM_DEPTS_WRITE: 'iam:depts:write',
  IAM_MENUS_READ: 'iam:menus:read',
  IAM_MENUS_WRITE: 'iam:menus:write',

  // System / Console（bone-system 控制台聚合 API）
  SYS_CONSOLE_READ: 'sys:console:read',

  // 元数据（bone-metadata-server @PreAuthorize）
  METADATA_READ: 'metadata:read',
  METADATA_WRITE: 'metadata:write',
  // G5 权限码拆分（2a §4.3）：建模 / 运行时 / 平台模板；旧码保留为 deprecated 别名
  METADATA_MODEL_READ: 'metadata:model:read',
  METADATA_MODEL_WRITE: 'metadata:model:write',
  METADATA_RUNTIME_READ: 'metadata:runtime:read',
  METADATA_RUNTIME_WRITE: 'metadata:runtime:write',
  METADATA_TEMPLATE_READ: 'metadata:template:read',
  METADATA_TEMPLATE_WRITE: 'metadata:template:write',

  // 扩展（bone-extension-studio · admin JWT 种子）
  EXTENSION_POINTS_READ: 'extension:points:read',
  EXTENSION_POINTS_WRITE: 'extension:points:write',
  EXTENSION_PLUGINS_DEPLOY: 'extension:plugins:deploy',
  // 5a G3 权限分层：插件读/写/绑定、生效切换、观测、市场
  EXTENSION_PLUGINS_READ: 'extension:plugins:read',
  EXTENSION_PLUGINS_WRITE: 'extension:plugins:write',
  EXTENSION_PLUGINS_BIND: 'extension:plugins:bind',
  EXTENSION_RUNTIME_PUBLISH: 'extension:runtime:publish',
  EXTENSION_OBSERVE_READ: 'extension:observe:read',
  EXTENSION_MARKETPLACE_INSTALL: 'extension:marketplace:install',
  EXTENSION_MARKETPLACE_MANAGE: 'extension:marketplace:manage',

  // 主数据（G6 落地：种子 iam_permission + DefaultPermissionCodes + @PreAuthorize 三处同步）
  MASTERDATA_ENTITIES_READ: 'masterdata:entities:read',
  MASTERDATA_ENTITIES_WRITE: 'masterdata:entities:write',
  MASTERDATA_RECORDS_READ: 'masterdata:records:read',
  MASTERDATA_RECORDS_WRITE: 'masterdata:records:write',
  MASTERDATA_RECORDS_APPROVE: 'masterdata:records:approve',
  MASTERDATA_CATEGORIES_READ: 'masterdata:categories:read',
  MASTERDATA_CATEGORIES_WRITE: 'masterdata:categories:write',
  MASTERDATA_TEMPLATES_READ: 'masterdata:templates:read',
  MASTERDATA_TEMPLATES_WRITE: 'masterdata:templates:write',
  MASTERDATA_TEMPLATES_INSTANTIATE: 'masterdata:templates:instantiate',
  MASTERDATA_SUBSCRIPTIONS_WRITE: 'masterdata:subscriptions:write',
  MASTERDATA_QUALITY_WRITE: 'masterdata:quality:write',
  MASTERDATA_REFERENCE_READ: 'masterdata:reference:read',
  MASTERDATA_REFERENCE_WRITE: 'masterdata:reference:write',
  MASTERDATA_GOVERNANCE_WRITE: 'masterdata:governance:write',

  // [Target] 集成
  INTEGRATION_FLOWS_READ: 'integration:flows:read',
  INTEGRATION_FLOWS_WRITE: 'integration:flows:write',
  INTEGRATION_CONNECTORS_WRITE: 'integration:connectors:write',
} as const satisfies Record<string, BonePermissionCode>;

/** 控制台创建权限时的推荐编码（含 Target 规划项，便于预置目录） */
export const BONE_PERMISSION_CODE_CATALOG: ReadonlyArray<{
  code: BonePermissionCode;
  name: string;
  domain: string;
  maturity: 'As-Is' | 'Target';
}> = [
  { code: BonePermissionCodes.IAM_ACCOUNTS_READ, name: 'IAM-账号查看', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_ACCOUNTS_WRITE, name: 'IAM-账号维护', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_ROLES_READ, name: 'IAM-角色查看', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_ROLES_WRITE, name: 'IAM-角色维护', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_PERMISSIONS_READ, name: 'IAM-权限查看', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_PERMISSIONS_WRITE, name: 'IAM-权限维护', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_AUDIT_READ, name: 'IAM-审计查看', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_AUDIT_WRITE, name: 'IAM-审计设置', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_TENANTS_READ, name: 'IAM-租户查看', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_TENANTS_WRITE, name: 'IAM-租户维护', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_SESSIONS_READ, name: 'IAM-会话查看', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_SESSIONS_WRITE, name: 'IAM-会话吊销', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_DEPTS_READ, name: 'IAM-组织查看', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_DEPTS_WRITE, name: 'IAM-组织维护', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_MENUS_READ, name: 'IAM-菜单查看', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.IAM_MENUS_WRITE, name: 'IAM-菜单维护', domain: 'iam', maturity: 'As-Is' },
  { code: BonePermissionCodes.SYS_CONSOLE_READ, name: 'SYS-控制台查看', domain: 'system', maturity: 'As-Is' },
  { code: BonePermissionCodes.METADATA_READ, name: '元数据-读（deprecated 别名）', domain: 'metadata', maturity: 'As-Is' },
  { code: BonePermissionCodes.METADATA_WRITE, name: '元数据-写（deprecated 别名）', domain: 'metadata', maturity: 'As-Is' },
  { code: BonePermissionCodes.METADATA_MODEL_READ, name: '元数据-建模查看', domain: 'metadata', maturity: 'As-Is' },
  { code: BonePermissionCodes.METADATA_MODEL_WRITE, name: '元数据-建模维护', domain: 'metadata', maturity: 'As-Is' },
  { code: BonePermissionCodes.METADATA_RUNTIME_READ, name: '元数据-运行时查看', domain: 'metadata', maturity: 'As-Is' },
  { code: BonePermissionCodes.METADATA_RUNTIME_WRITE, name: '元数据-运行时维护', domain: 'metadata', maturity: 'As-Is' },
  { code: BonePermissionCodes.METADATA_TEMPLATE_READ, name: '元数据-模板查看', domain: 'metadata', maturity: 'As-Is' },
  { code: BonePermissionCodes.METADATA_TEMPLATE_WRITE, name: '元数据-模板维护', domain: 'metadata', maturity: 'As-Is' },
  { code: BonePermissionCodes.EXTENSION_POINTS_READ, name: '扩展点-读', domain: 'extension', maturity: 'As-Is' },
  { code: BonePermissionCodes.EXTENSION_POINTS_WRITE, name: '扩展点-写', domain: 'extension', maturity: 'As-Is' },
  { code: BonePermissionCodes.EXTENSION_PLUGINS_DEPLOY, name: '插件-部署', domain: 'extension', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_ENTITIES_READ, name: '主数据-实体查看', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_ENTITIES_WRITE, name: '主数据-实体维护', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_RECORDS_READ, name: '主数据-记录查看', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_RECORDS_WRITE, name: '主数据-记录维护', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_RECORDS_APPROVE, name: '主数据-记录审批', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_CATEGORIES_READ, name: '主数据-分类查看', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_CATEGORIES_WRITE, name: '主数据-分类维护', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_TEMPLATES_READ, name: '主数据-模板查看', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_TEMPLATES_WRITE, name: '主数据-模板维护', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_TEMPLATES_INSTANTIATE, name: '主数据-模板实例化', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_SUBSCRIPTIONS_WRITE, name: '主数据-订阅维护', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_QUALITY_WRITE, name: '主数据-质量治理', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_REFERENCE_READ, name: '主数据-参考数据查看', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_REFERENCE_WRITE, name: '主数据-参考数据维护', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.MASTERDATA_GOVERNANCE_WRITE, name: '主数据-治理操作', domain: 'masterdata', maturity: 'As-Is' },
  { code: BonePermissionCodes.INTEGRATION_FLOWS_READ, name: '集成-流程读', domain: 'integration', maturity: 'Target' },
  { code: BonePermissionCodes.INTEGRATION_FLOWS_WRITE, name: '集成-流程写', domain: 'integration', maturity: 'Target' },
  { code: BonePermissionCodes.INTEGRATION_CONNECTORS_WRITE, name: '集成-连接器写', domain: 'integration', maturity: 'Target' },
];

export const ALL_BONE_PERMISSION_CODE_VALUES: BonePermissionCode[] = Object.values(BonePermissionCodes);
