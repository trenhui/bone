/**
 * 定位滚动：focus 变化时把对应锚点滚入视口。
 *
 * 闭环动机：自检/变更预告的「点击定位」此前只切换面板 + 画高亮环——若目标在
 * 可视区外（长表单滚到底部的分区、第 80 条规则），用户只看到面板变了、不知道
 * 看哪里，还得自己滚动查找（对标 IDE 的 "reveal in editor"：跳转必滚动）。
 *
 * 用法：面板把自己「当前 focus 对应的锚点键」传给 hook，并在对应元素上渲染
 * `data-locate="<同一个键>"`。键由面板按自己的索引语义自拟（如 `s2:c1`），
 * 本 hook 不关心语义，只做「键 → 元素 → scrollIntoView」。
 */
import { useEffect } from 'react';

export function useScrollToFocus(anchorKey: string | undefined): void {
  useEffect(() => {
    if (!anchorKey) return;
    // 面板内容在同一个 tick 渲染（setTab/setFocus 同一事件内批处理），
    // 但 Tabs 切换后容器高度变化，布局稳定前的滚动可能落空——双保险：
    // 先同步执行一次，再下一帧兜底（Tabs 切换/懒挂载场景）。
    const reveal = () => {
      const el = document.querySelector(`[data-locate="${CSS.escape(anchorKey)}"]`);
      el?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    };
    reveal();
    const raf = requestAnimationFrame(reveal);
    return () => cancelAnimationFrame(raf);
  }, [anchorKey]);
}
