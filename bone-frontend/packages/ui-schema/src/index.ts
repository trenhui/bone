/**
 * @bone/ui-schema —— BoneUiSchema 协议引擎 + 运行时渲染器（design / runtime 共享单源）。
 *
 * 导出：
 * - 协议引擎（纯函数、零 React 依赖）：evalCond / linkageState / fieldVisible /
 *   controlBaseStates / runSubmit / validateControlValues / resolveVisibleActions /
 *   checkSchemaVersion / resolveVariant 等，以及全套协议类型。
 * - 运行时渲染器：SchemaRenderer（只读展示形态，供 claim-app 等运行时页消费）。
 *
 * 设计器（bone-metadata-app）的 SchemaPreview 仍保留在其应用内（带场景模拟/角色栏/主题），
 * 但同样消费本包导出的引擎，确保「预览 == 运行时」同源。
 */
export * from './engine';
export { SchemaRenderer } from './SchemaRenderer';
export type { SchemaRendererProps } from './SchemaRenderer';
