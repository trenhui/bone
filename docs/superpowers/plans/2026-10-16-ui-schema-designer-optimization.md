# 界面设计器优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 已发布实体的数据页读发布快照，设计器继续编辑草稿并标出待发布；预览与正在编辑的视图一致，点选和属性不写出协议外字段。

**Architecture:** `GET /entities/{id}/ui-schema` 仍返回草稿 `json`，并附带可空的 `publishedJson` / `publishedVersion`。数据页用纯函数选择渲染源。设计器的撤销、插入位置、发布态比较、属性白名单都抽成纯函数再接到现有组件。不改八个 Tab，不改 validate 入参，不改 `schemaForExecution`。

**Tech Stack:** Java 21 + Spring Boot 3.5（`UiSchemaApplicationService`）、React 18 + TypeScript + Vitest（`bone-metadata-app`）、现有 `bone-ui-schema.schema.json` 白名单。

**规格：** `.superpowers/specs/2026-10-16-ui-schema-designer-optimization-design.md`

## Global Constraints

- 不新增表，不改 `POST /entities/{id}/ui-schema/validate` 的无 body 契约。
- 不合并八个 Tab，不做自由画布，不做服务端版本历史，不把 `views.detail` 接到数据页。
- 不修改 `schemaForExecution`：有快照读快照，无快照读草稿。
- 保存草稿只写 `schema_json`，不得改写 `published_schema_json`。
- `props.span` 不是协议字段。新操作不得写出它。已有未知 props 键保留，检查器不展示、不删除。
- 设计器预览始终渲染内存中的草稿，不读 `publishedJson`。
- 发布态只比较最近一次 GET 或保存成功响应里的已保存草稿与快照，不看编辑器缓冲区。
- 改过的 Java 执行 `mvn -pl bone-engine/bone-metadata-server spotless:apply`。
- 每个任务结束时的 commit 只在用户明确要求提交时执行。本计划处于待审核，确认前不改产品代码。

---

### Task 1: 响应带上发布快照

**Files:**
- Modify: `bone-engine/bone-metadata-server/src/main/java/com/bone/metadata/catalog/application/query/dto/UiSchemaResp.java`
- Modify: `bone-engine/bone-metadata-server/src/main/java/com/bone/metadata/catalog/application/UiSchemaApplicationService.java`（`toResp`，约 517–524 行）
- Test: `bone-engine/bone-metadata-server/src/test/java/com/bone/metadata/catalog/application/UiSchemaApplicationServiceTest.java`

**Interfaces:**
- Consumes: `UiSchemaDef.getSchemaJson()`、`getPublishedSchemaJson()`、`getPublishedVersion()`、`snapshotOnPublish(int)`
- Produces: `UiSchemaResp.publishedJson: String`、`UiSchemaResp.publishedVersion: Integer`。无快照时两者为 null。`json` 仍是草稿。

- [ ] **Step 1: 写失败测试**

在 `UiSchemaApplicationServiceTest` 末尾增加：

```java
@Test
@DisplayName("读草稿附带发布快照；无快照时 published 字段为 null")
void getDraftSchema_shouldExposePublishedSnapshot() {
  UiSchemaDef def = row(schema(""), 1L);
  def.snapshotOnPublish(4);
  def.replaceDraft(schema("\"linkageRules\":[]"), 1);
  when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(def));

  UiSchemaResp resp = service.getDraftSchema(9001L).orElseThrow();

  assertThat(resp.getJson()).contains("linkageRules");
  assertThat(resp.getPublishedJson()).doesNotContain("linkageRules");
  assertThat(resp.getPublishedVersion()).isEqualTo(4);
}

@Test
@DisplayName("保存草稿不改写已有发布快照")
void saveDraftSchema_shouldKeepPublishedSnapshot() {
  UiSchemaDef def = row(schema(""), 0L);
  def.snapshotOnPublish(2);
  String frozen = def.getPublishedSchemaJson();
  when(uiSchemaDefRepository.findByTenantAndEntity(7L, 9001L)).thenReturn(Optional.of(def));

  UiSchemaResp resp = service.saveDraftSchema(9001L, json(schema("\"linkageRules\":[]")), 0L);

  assertThat(def.getPublishedSchemaJson()).isEqualTo(frozen);
  assertThat(resp.getPublishedJson()).isEqualTo(frozen);
  assertThat(resp.getPublishedVersion()).isEqualTo(2);
  assertThat(resp.getJson()).contains("linkageRules");
}
```

`row()` 已存在：`UiSchemaDef.create` 后 `setId(55L)`。`schema(String)` 把 inner 拼进 JSON 对象。`snapshotOnPublish` 把当时草稿复制为快照。之后 `replaceDraft` 只改草稿。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -pl bone-engine/bone-metadata-server -Dtest=UiSchemaApplicationServiceTest#getDraftSchema_shouldExposePublishedSnapshot test`

Expected: 编译失败，`getPublishedJson()` 不存在。

- [ ] **Step 3: 最小实现**

`UiSchemaResp` 增加：

```java
/** 发布快照 JSON。从未发布为 null。 */
private String publishedJson;

/** 快照对应的实体 version。从未发布为 null。 */
private Integer publishedVersion;
```

`toResp` 在现有赋值后增加：

```java
resp.setPublishedJson(def.getPublishedSchemaJson());
resp.setPublishedVersion(def.getPublishedVersion());
```

不改 `replaceDraft`、`snapshotOnPublish`、`schemaForExecution`。

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -pl bone-engine/bone-metadata-server -Dtest=UiSchemaApplicationServiceTest test`

Expected: 全部通过。

Run: `mvn -pl bone-engine/bone-metadata-server spotless:apply`

- [ ] **Step 5: Commit**

仅当用户要求提交时：

```bash
git add bone-engine/bone-metadata-server/src/main/java/com/bone/metadata/catalog/application/query/dto/UiSchemaResp.java \
  bone-engine/bone-metadata-server/src/main/java/com/bone/metadata/catalog/application/UiSchemaApplicationService.java \
  bone-engine/bone-metadata-server/src/test/java/com/bone/metadata/catalog/application/UiSchemaApplicationServiceTest.java
git commit -m "$(cat <<'EOF'
feat(metadata): 界面 schema 响应带回发布快照

数据页需要与服务端规则读同一份已发布 schema，同时设计器继续编辑草稿。
EOF
)"
```

---

### Task 2: 数据页选择发布快照

**Files:**
- Create: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/runtimeSource.ts`
- Create: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/runtimeSource.test.ts`
- Modify: `bone-frontend/apps/bone-metadata-app/src/services/metadataApi.ts`（`UiSchemaResp`，约 426–432 行）
- Modify: `bone-frontend/apps/bone-metadata-app/src/hooks/useBoneUiSchema.ts`

**Interfaces:**
- Consumes: Task 1 的 `publishedJson` / `publishedVersion`
- Produces:
  - `runtimeSchemaText(resp: { json?: string | null; publishedJson?: string | null }): { text: string | null; source: 'published' | 'draft' | 'absent' }`
  - `canonicalJson(text: string | null): string | null`
  - `publishPhase(savedDraft: string | null, publishedJson: string | null): 'unpublished' | 'published' | 'pending'`

- [ ] **Step 1: 写失败测试**

`runtimeSource.test.ts`：

```ts
import { describe, expect, it } from 'vitest';
import { canonicalJson, publishPhase, runtimeSchemaText } from './runtimeSource';

describe('runtimeSchemaText', () => {
  it('有发布快照时忽略草稿', () => {
    expect(runtimeSchemaText({ json: '{"a":1}', publishedJson: '{"a":2}' })).toEqual({
      text: '{"a":2}', source: 'published',
    });
  });

  it('无快照时用草稿', () => {
    expect(runtimeSchemaText({ json: '{"a":1}', publishedJson: null })).toEqual({
      text: '{"a":1}', source: 'draft',
    });
  });

  it('空白快照视为未发布', () => {
    expect(runtimeSchemaText({ json: '{"a":1}', publishedJson: '  ' }).source).toBe('draft');
  });

  it('两者皆空', () => {
    expect(runtimeSchemaText({ json: null, publishedJson: null })).toEqual({
      text: null, source: 'absent',
    });
  });
});

describe('publishPhase', () => {
  it('无快照为未发布', () => {
    expect(publishPhase('{"b":1,"a":2}', null)).toBe('unpublished');
  });

  it('键序不同视为已发布', () => {
    expect(publishPhase('{"b":1,"a":2}', '{"a":2,"b":1}')).toBe('published');
  });

  it('内容不同为待重新发布', () => {
    expect(publishPhase('{"a":2}', '{"a":1}')).toBe('pending');
  });
});

describe('canonicalJson', () => {
  it('对象键递归排序，数组保持原序', () => {
    expect(canonicalJson('{"b":{"d":1,"c":[2,1]},"a":1}')).toBe('{"a":1,"b":{"c":[2,1],"d":1}}');
  });

  it('非法 JSON 返回 null', () => {
    expect(canonicalJson('{')).toBeNull();
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd bone-frontend && npx vitest run apps/bone-metadata-app/src/components/uiSchema/runtimeSource.test.ts`

Expected: FAIL，找不到 `./runtimeSource`。

- [ ] **Step 3: 实现纯函数并接到 hook**

`runtimeSource.ts`：

```ts
export function canonicalJson(text: string | null | undefined): string | null {
  if (text == null || text.trim() === '') return null;
  try {
    return JSON.stringify(sortValue(JSON.parse(text)));
  } catch {
    return null;
  }
}

function sortValue(v: unknown): unknown {
  if (Array.isArray(v)) return v.map(sortValue);
  if (v && typeof v === 'object') {
    const src = v as Record<string, unknown>;
    const out: Record<string, unknown> = {};
    for (const k of Object.keys(src).sort()) out[k] = sortValue(src[k]);
    return out;
  }
  return v;
}

export function runtimeSchemaText(resp: {
  json?: string | null;
  publishedJson?: string | null;
}): { text: string | null; source: 'published' | 'draft' | 'absent' } {
  const published = resp.publishedJson?.trim() ? resp.publishedJson! : null;
  if (published) return { text: published, source: 'published' };
  if (resp.json != null && resp.json !== '') return { text: resp.json, source: 'draft' };
  return { text: null, source: 'absent' };
}

export function publishPhase(
  savedDraft: string | null,
  publishedJson: string | null,
): 'unpublished' | 'published' | 'pending' {
  if (!publishedJson?.trim()) return 'unpublished';
  const a = canonicalJson(savedDraft);
  const b = canonicalJson(publishedJson);
  if (a == null || b == null) return savedDraft === publishedJson ? 'published' : 'pending';
  return a === b ? 'published' : 'pending';
}
```

`metadataApi.ts` 的 `UiSchemaResp` 增加：

```ts
publishedJson?: string | null;
publishedVersion?: number | null;
```

`useBoneUiSchema.ts` 的解析改为：

```ts
const picked = runtimeSchemaText(res.data);
if (picked.source === 'absent' || picked.text == null) {
  setStatus('absent');
  setRaw(null);
  return;
}
try {
  const parsed = JSON.parse(picked.text) as BoneUiSchema;
  const vc = checkSchemaVersion(parsed);
  setRaw(parsed);
  setStatus(vc === 'ok' ? 'ok' : vc === 'warn-unknown-minor' ? 'warn-unknown-minor' : 'error');
} catch {
  if (picked.source === 'published') {
    console.warn('[uiSchema] publishedJson 解析失败，回落旧表单');
  }
  setRaw(null);
  setStatus('error');
}
```

发布快照解析失败时不要改用草稿。`status === 'error'` 时 `RuntimeDataManagement` 已回落旧表单。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd bone-frontend && npx vitest run apps/bone-metadata-app/src/components/uiSchema/runtimeSource.test.ts apps/bone-metadata-app/src/utils/uiSchemaEngine.test.ts`

Expected: PASS。

- [ ] **Step 5: Commit**

仅当用户要求提交时。message：`fix(metadata-ui): 数据页渲染已发布的界面快照`。

---

### Task 3: 设计器标出待重新发布

**Files:**
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/UiSchemaDesigner.tsx`

**Interfaces:**
- Consumes: `publishPhase`、`UiSchemaResp.publishedJson`
- Produces: 顶栏第三态文案：`未发布` / `已发布` / `待重新发布`。与现有「未保存改动 / 已保存」并列，不互相覆盖。

- [ ] **Step 1: 保存服务端副本**

在 designer 增加状态：

```ts
const [serverDraft, setServerDraft] = useState<string | null>(null);
const [publishedJson, setPublishedJson] = useState<string | null>(null);
```

`load` 与 `save` 成功时：

```ts
setServerDraft(res.data?.json ?? null);
setPublishedJson(res.data?.publishedJson ?? null);
```

`load` 在无行、本地 `buildDefaultSchema` 时：`setServerDraft(null)`、`setPublishedJson(null)`。不要把编辑器里的 `schema` 写进 `serverDraft`。

- [ ] **Step 2: 顶栏标签**

```tsx
const phase = publishPhase(serverDraft, publishedJson);
const phaseTag = phase === 'pending'
  ? <Tag color="gold">待重新发布</Tag>
  : phase === 'published'
    ? <Tag color="green">已发布</Tag>
    : <Tag>未发布</Tag>;
```

放在现有脏标记 Tag 旁边。`dirty === true` 时不隐藏 `phaseTag`。按钮「后端校验」改为「校验已保存草稿」，tooltip 写明「校验的是已保存草稿，不是当前未保存内容」。

- [ ] **Step 3: 手工核对**

已发布实体：改一个分区标题，顶栏仍是「已发布」+「未保存改动」。保存后变为「待重新发布」+「已保存」。不点实体「发布」时，数据页录入弹窗仍是旧布局。

- [ ] **Step 4: Commit**

仅当用户要求提交时。message：`feat(metadata-ui): 设计器标出界面草稿待发布`。

---

### Task 4: 撤销栈不再嵌套 setState，恢复快照保持未保存

**Files:**
- Create: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/history.ts`
- Create: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/history.test.ts`
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/UiSchemaDesigner.tsx`（`commit` / `undo` / `redo` / `jumpToPast` / `restoreSnap`）

**Interfaces:**
- Produces:

```ts
export interface History<T> { past: T[]; present: T; future: T[] }
export function commitHistory<T>(h: History<T>, next: T): History<T>
export function undoHistory<T>(h: History<T>): History<T>
export function redoHistory<T>(h: History<T>): History<T>
export function jumpHistory<T>(h: History<T>, fromMostRecentIndex: number): History<T>
```

栈深 50。`fromMostRecentIndex === 0` 等价于一次 undo。

- [ ] **Step 1: 写失败测试**

```ts
import { describe, expect, it } from 'vitest';
import { commitHistory, jumpHistory, redoHistory, undoHistory, type History } from './history';

const h = (present: string, past: string[] = [], future: string[] = []): History<string> =>
  ({ past, present, future });

describe('history', () => {
  it('commit 把当前压入 past 并清空 future', () => {
    const next = commitHistory(h('a', [], ['z']), 'b');
    expect(next).toEqual({ past: ['a'], present: 'b', future: [] });
  });

  it('undo 后 redo 回到 commit 结果', () => {
    const committed = commitHistory(h('a'), 'b');
    const undone = undoHistory(committed);
    expect(undone.present).toBe('a');
    expect(redoHistory(undone).present).toBe('b');
  });

  it('空 past 时 undo 不变', () => {
    expect(undoHistory(h('a'))).toEqual(h('a'));
  });

  it('jump 0 等价于 undo，被跳过的中间态可 redo', () => {
    let s = h('a');
    s = commitHistory(s, 'b');
    s = commitHistory(s, 'c');
    const jumped = jumpHistory(s, 1);
    expect(jumped.present).toBe('a');
    expect(redoHistory(redoHistory(jumped)).present).toBe('c');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd bone-frontend && npx vitest run apps/bone-metadata-app/src/components/uiSchema/history.test.ts`

Expected: FAIL，模块不存在。

- [ ] **Step 3: 实现并替换 designer 里的嵌套更新**

`history.ts` 用不可变数组。`commitHistory`：`past: [...h.past, h.present].slice(-50)`，`future: []`。`undoHistory` / `redoHistory` 在空栈时返回原对象。`jumpHistory` 把 `past[pastIndex]` 设为 present，`past.slice(pastIndex + 1)` 与原 present 按原顺序放进 future 前面，future 截断到 50。

Designer 删除 `useCallback` 空依赖的 `commit`。事件里同步读当前 `schema` / `past` / `future`：

```ts
const commit = (next: BoneUiSchema) => {
  if (!schema) return;
  const h = commitHistory({ past, present: schema, future }, next);
  setPast(h.past);
  setSchema(h.present);
  setFuture(h.future);
};
```

`undo` / `redo` / `jumpToPast` 同样先算出下一份 `History`，再三次 `setState`。禁止在 `setSchema` 的更新函数里调用 `setPast` 或 `setFuture`。

`restoreSnap` 在 lint 通过后只调用 `commit(s.schema)`，然后 `message.success('已恢复到本地快照，尚未保存')`。删除其中的 `setBaseline`、`setPast([])`、`setFuture([])`。协议错误的 `Modal.warning` 保持不动。

- [ ] **Step 4: 跑测试确认通过**

Run: `cd bone-frontend && npx vitest run apps/bone-metadata-app/src/components/uiSchema/history.test.ts`

Expected: PASS。

手工：恢复快照后顶栏为「未保存改动」；Ctrl+Z 回到恢复前；不点保存就「重新加载」，内容回到服务端。

- [ ] **Step 5: Commit**

仅当用户要求提交时。message：`fix(metadata-ui): 恢复界面快照不再伪装成已保存`。

---

### Task 5: 预览跟着结构视图，手机档单列，占位控件

**Files:**
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/UiSchemaDesigner.tsx`
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/StructurePanel.tsx`
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/SchemaPreview.tsx`
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/uiSchemaTheme.css`（`.uis-stage` 一段，约 63–66 行）

**Interfaces:**
- Consumes: `viewOf(schema, 'form' | 'detail')`
- Produces: 设计器状态 `designerView: 'form' | 'detail' | 'list'`，同时传给结构面板和预览。`schemaOps.ViewKey` 仍只有 `form | detail`。

- [ ] **Step 1: 共用视图**

把 `useState<ViewKey>('form')` 改成：

```ts
type DesignerView = 'form' | 'detail' | 'list';
const [designerView, setDesignerView] = useState<DesignerView>('form');
```

结构面板的 Segmented 增加 `{ value: 'list', label: '列表' }`。`designerView === 'list'` 时面板正文换成一行说明：「列、搜索和页签在「列表」页签编辑，右侧是预览。」不渲染分区。`form` / `detail` 仍把 `designerView` 作为 `ViewKey` 传给 `viewOf`。

`SchemaPreview` 删除内部 `useState<'form' | 'list'>`。新增必填 prop `view: DesignerView`。`view === 'list'` 时渲染现有 `ListViewPreview`。否则：

```ts
const sections = viewOf(resolved, view).sections ?? [];
```

替换现在写死的 `formOf(resolved)`（含 `baseStates` 与画布两处）。身份模拟栏里的表单/列表 Segmented 删除，避免第二套视图状态。

- [ ] **Step 2: 删掉 span，占位三种控件**

`StructurePanel` 的 `ControlInspector` 删除「栅格宽度」那一块 `Select`。

`SchemaPreview` 字段容器删除 `props.span` 读取。占满行的条件只保留：

```ts
const full = c.controlKind === 'CHILD_TABLE' || c.controlKind === 'ATTACHMENT' || c.controlKind === 'TIMELINE';
```

`FieldControl` 在 `INPUT` 默认分支之前：

```tsx
if (kind === 'CHART' || kind === 'SLIDER' || kind === 'QUOTE') {
  const label = kind === 'CHART' ? '图表' : kind === 'SLIDER' ? '滑块' : '引用块';
  return <div className="uis-empty">{label}预览为占位，不写入表单值</div>;
}
```

这三个分支不调用 `onChange`。

- [ ] **Step 3: 手机档单列**

在 `.uis-stage.mobile { max-width: 390px; }` 后增加：

```css
.uis-stage.mobile .uis-grid2 { grid-template-columns: 1fr; }
```

不改 `.uis-stage.tablet` 的 760px，不删除视口 `@media (max-width: 560px)`。

- [ ] **Step 4: 核对**

Run: `cd bone-frontend && npx vitest run apps/bone-metadata-app/src/components/uiSchema/schemaOps.test.ts apps/bone-metadata-app/src/components/uiSchema/lint.ts`

若 lint 没有对应测试文件，改为：

Run: `cd bone-frontend && npx vitest run apps/bone-metadata-app/src/components/uiSchema`

Expected: 现有测试 PASS。

手工：结构面板切到「详情视图」，右侧出现 `views.detail` 的分区，录入表单预览不变。桌面宽度切到手机，表单变单列。图表控件显示占位文案，旁边文本框的值不变。

- [ ] **Step 5: Commit**

仅当用户要求提交时。message：`fix(metadata-ui): 界面预览对齐正在编辑的视图`。

---

### Task 6: 点选插入与白名单属性

**Files:**
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/schemaOps.ts`（在 `addControl` 后）
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/schemaOps.test.ts`
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/constants.ts`
- Create: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/inspectorProps.ts`
- Create: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/inspectorProps.test.ts`
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/StructurePanel.tsx`
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/SchemaPreview.tsx`
- Modify: `bone-frontend/apps/bone-metadata-app/src/components/uiSchema/UiSchemaDesigner.tsx`

**Interfaces:**
- Produces:

```ts
export type StructureSelection =
  | { kind: 'section'; si: number }
  | { kind: 'control'; si: number; ci: number }
  | null;

export function insertControlAfter(
  s: BoneUiSchema, view: ViewKey, si: number, afterCi: number, field: MetaField | undefined,
): BoneUiSchema

export function placeField(
  s: BoneUiSchema, view: ViewKey, selection: StructureSelection, field: MetaField | undefined,
): { schema: BoneUiSchema; selection: StructureSelection }

export function propFieldsFor(kind: string | undefined): string[]

export function patchWhitelistedProp(
  props: Record<string, unknown> | undefined, key: string, value: unknown,
): Record<string, unknown>

export function roleOptions(schema: BoneUiSchema): string[]
```

`propFieldsFor` 返回值与规格 §3.3 表一致。未列出的类型返回 `[]`。`roleOptions` = `variants[].match.roles` 并上 `employee`、`reviewer`、`admin`，去重后按编码排序。

- [ ] **Step 1: 写失败测试**

`schemaOps.test.ts` 增加：用 `buildDefaultSchema` 加两个分区，第二分区放一个控件。`placeField(..., { kind: 'control', si: 1, ci: 0 }, field)` 后，新字段在该控件后面，第一分区不变。`selection === null` 时进最后一个分区末尾。零分区时先出现一个分区再放入字段。

`inspectorProps.test.ts`：

```ts
import { describe, expect, it } from 'vitest';
import { patchWhitelistedProp, propFieldsFor, roleOptions } from './inspectorProps';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';

describe('propFieldsFor', () => {
  it('金额只暴露协议内的精度和币种字段', () => {
    expect(propFieldsFor('MONEY')).toEqual(['currencyField', 'precision']);
  });

  it('未知类型不暴露 props', () => {
    expect(propFieldsFor('SWITCH')).toEqual([]);
  });
});

describe('patchWhitelistedProp', () => {
  it('合并时保留未知键', () => {
    expect(patchWhitelistedProp({ span: 12, precision: 0 }, 'precision', 2)).toEqual({
      span: 12, precision: 2,
    });
  });

  it('空值删除该白名单键，不动其他键', () => {
    expect(patchWhitelistedProp({ precision: 2, span: 8 }, 'precision', undefined)).toEqual({ span: 8 });
  });
});

describe('roleOptions', () => {
  it('并上变体角色并排序', () => {
    const schema = { variants: [{ match: { roles: ['approver', 'employee'] } }] } as BoneUiSchema;
    expect(roleOptions(schema)).toEqual(['admin', 'approver', 'employee', 'reviewer']);
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd bone-frontend && npx vitest run apps/bone-metadata-app/src/components/uiSchema/schemaOps.test.ts apps/bone-metadata-app/src/components/uiSchema/inspectorProps.test.ts`

Expected: FAIL，`placeField` / `inspectorProps` 不存在。

- [ ] **Step 3: 实现纯函数**

`insertControlAfter` 复制 `addControl` 的控件对象，用 `splice(afterCi + 1, 0, control)` 插入，不 mutate 原 schema。

`placeField`：

```ts
export function placeField(s, view, selection, field) {
  let next = viewOf(s, view).sections.length === 0 ? addSection(s, view) : s;
  if (selection?.kind === 'control') {
    next = insertControlAfter(next, view, selection.si, selection.ci, field);
    return { schema: next, selection: { kind: 'control', si: selection.si, ci: selection.ci + 1 } };
  }
  const si = selection?.kind === 'section' ? selection.si : viewOf(next, view).sections.length - 1;
  next = addControl(next, view, si, field);
  const ci = viewOf(next, view).sections[si].controls.length - 1;
  return { schema: next, selection: { kind: 'control', si, ci } };
}
```

越界 `si` 时退回最后一个分区。`patchWhitelistedProp` 浅拷贝；`value` 为 `undefined` 或 `''` 时删除 `key`。

`propFieldsFor` 用一张常量映射，键必须都出现在 `PROP_WHITELIST`。写一个测试断言映射的每个键都在 `PROP_WHITELIST` 里，防止再引入 `span`。

- [ ] **Step 4: 接到界面**

`StructureSelection` 状态放在 `UiSchemaDesigner`，传给 `StructurePanel` 和 `SchemaPreview`。

- 点分区头：`{ kind: 'section', si }`。点控件行：`{ kind: 'control', si, ci }`。
- 预览字段点击调用同一 setter，并给该字段容器加描边 class。`view === 'list'` 时不注册点击。
- `FieldPalette` 的点击改为 `placeField`。拖入某个分区仍调用 `addControl(schema, view, si, field)`，不看选中项。
- `ControlInspector` 在显示名、占位、隐藏、只读之下，按 `propFieldsFor(control.controlKind)` 渲染输入。数字键用 `InputNumber`，其余用 `Input`。`onPatch` 走 `patchWhitelistedProp`，这样 `span` 留在对象里但界面不出现。
- `SchemaPreview` 的角色 `Select` options 改为 `roleOptions(schema).map((r) => ({ value: r, label: r }))`。

- [ ] **Step 5: 跑测试并手工核对**

Run: `cd bone-frontend && npx vitest run apps/bone-metadata-app/src/components/uiSchema`

Expected: PASS。

手工：选中第二分区的控件，字段库点一个未用字段，它出现在该控件后面。金额 `precision` 设为 2，自检无白名单错误。变体角色 `approver` 出现在身份模拟里，选中后「变体：」文案变化。

- [ ] **Step 6: Commit**

仅当用户要求提交时。message：`feat(metadata-ui): 界面设计器按协议白名单编辑控件`。

---

## 自检

规格覆盖：

- A1 读路径与顶栏三态：Task 1–3
- A2 恢复快照、撤销嵌套更新、span、共用视图、手机档、三种占位：Task 4–5
- A3 点选、插入位置、白名单、角色选项：Task 6
- A4 不做项：Global Constraints。按钮文案在 Task 3，入参不改。

不在本计划里：qiankun 离开确认、服务端版本历史、详情接到数据页、统计卡聚合、子表列模型、validate 请求体、八 Tab 合并。
