/**
 * JSON 规范化（递归排序键）字符串：跨前后端序列化差异做内容等价比较。
 *
 * 为什么需要：schema 的「草稿 vs 已发布快照」是否一致、规则是否被修改，
 * 都不能靠 `JSON.stringify` 直接比——键序、空白、数组内元素顺序中的无关差异
 * （前端 setState 重建对象）会造成假阳性。排序键后比较得到「内容等价」判定。
 *
 * 单一真源：设计器发布态三态、变更预告的规则 modify 判定都走这里，
 * 禁止各处再写一份 normalize。
 */

/** 递归排序对象键后 stringify；入参为合法 JSON 值（调用方负责 parse） */
export function normJson(value: unknown): string {
  const sort = (v: unknown): unknown =>
    Array.isArray(v)
      ? v.map(sort)
      : v && typeof v === 'object'
        ? Object.fromEntries(
          Object.keys(v as Record<string, unknown>)
            .sort()
            .map((k): [string, unknown] => [k, sort((v as Record<string, unknown>)[k])]),
        )
        : v;
  return JSON.stringify(sort(value));
}
