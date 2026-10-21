/**
 * BONE 品牌标识 · 人字梁
 *
 * 图形：单笔画人字（∧）。它是 BONE「骨架」最直接的视觉——人字梁是人类最古老的承重结构；
 * 同时它是拉丁 A 的骨架，与全拉丁字标天然同源。名称取 BONE 本身（README 主名），
 * 「Admin」是历史残留（README 明写「仓库中无 bone-admin 模块」），故不再附加任何后缀。
 *
 * 形态判据（deviceScaleFactor:1 真实渲染实测，非放大截图）：
 * · 单笔画、无负形 ⇒ 16 / 20 / 24 / 34px 四档全部清晰可辨。
 * · 已否决并勿回退：①「斜骨 + 四球」——20px 下两球中心距 2.65px < 球直径 4.63px，
 *   球间间隙 −1.98px，物理上不可能分辨，糊成斜胶囊；②「B 双弧」——16px 下两个负形孔闭合；
 *   ③「拱 + 独立拱心石」——20~34px 下拱心石全部消失，只剩门洞。
 *
 * 用法：置于渐变品牌芯片（.brand-chip / .logo-icon）之上，颜色继承 currentColor。
 * 同一路径复用于 favicon（public/bone-mark.svg），两端必须同步修改。
 */

/** 人字标记几何（32×32 视框）——与 favicon 保持一致，勿单独改一边
    跨度 20 单位 + 描边 6 单位 ⇒ 含描边视觉占 svg 的约 81%、占 34px 芯片约 50%（品牌块惯例 45~55%） */
const BONE_MARK = {
  path: 'M6 24.5 L16 7.5 L26 24.5',
  strokeWidth: 6,
} as const;

export interface BrandMarkProps {
  /** 渲染边长（px），默认 21（34px 芯片内的光学尺寸，约占芯片 62%） */
  size?: number;
}

export default function BrandMark({ size = 21 }: BrandMarkProps): JSX.Element {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      aria-hidden="true"
      focusable="false"
      style={{ display: 'block' }}
    >
      <path
        d={BONE_MARK.path}
        stroke="currentColor"
        strokeWidth={BONE_MARK.strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}