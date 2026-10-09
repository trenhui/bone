export * from './storage';
export * from './format';
// 分页 total 归一（后端全局 Long→String 序列化，total 运行期是字符串，见 Bone-API-规范 §5.3）
export * from './paging';
// i18n：语言包 + 格式化（locale 由 Shell 单源下发，见 doc/design/国际化设计方案.md §5）
export * from './i18n/index';
export * from './i18n/format';
export * from './i18n/errorMessage';
export * from './i18n/subscribe';
// 统一权限判定（纯函数层；UI 层入口见 @bone/ui 的 <Auth> / <AuthButton>）
export * from './authority/index';