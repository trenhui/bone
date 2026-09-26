/**
 * Monaco 自托管：替换 `@monaco-editor/react` 默认的 CDN loader。
 *
 * <p>默认 loader 从公网 CDN（jsdelivr）拉取 monaco；生产若走内网网关或 CDN 受限，
 * 加载会静默失败、模板预览区只剩空白且无报错。这里把 monaco 打进 bundle 并接管 worker 创建。
 *
 * <p>本应用只用 Monaco 做<b>只读</b>模板预览（handlebars 高亮），不编辑代码，
 * 因此不需要 json / css / html / ts 等语言 worker，统一回落到 editor.worker 即可。
 *
 * <p>必须在渲染任何 Editor 之前执行（见 main.tsx 的 import 顺序）。
 */
import * as monaco from 'monaco-editor';
import { loader } from '@monaco-editor/react';
// 注意路径：monaco-editor 0.57 的 package.json exports 为 {"./*": "./esm/vs/*.js"}，
// 即子路径已被重映射到 esm/vs 下，因此这里要写 monaco-editor/editor/... 而不是
// monaco-editor/esm/vs/editor/... （后者会被解析成 esm/vs/esm/vs/... 而失败）。
import editorWorker from 'monaco-editor/editor/editor.worker?worker';

// MonacoEnvironment 由 monaco-editor 在 globalThis 上声明，Window 上无此属性，故断言
(self as unknown as { MonacoEnvironment: unknown }).MonacoEnvironment = {
  getWorker: () => new editorWorker(),
};

loader.config({ monaco });
