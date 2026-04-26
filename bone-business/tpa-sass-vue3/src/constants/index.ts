/**
 * 普康影像分类
 */
export const PK_IMAGE_TYPE_LIST = [
  {
    label: "发票",
    value: "1",
  },
  {
    label: "病例",
    value: "2",
  },
  {
    label: "申请书",
    value: "3",
  },
  {
    label: "身份证资料",
    value: "4",
  },
  {
    label: "其他",
    value: "5",
  },
  {
    label: "未分类",
    value: "6",
  },
];

export const SHORT_CUT_LIST = [
  { action: "下一张图片", shortcut: "方向键 向下键 " },
  { action: "上一张图片", shortcut: "方向键 向上键 " },
  { action: "放大", shortcut: "方向键 向右键" },
  { action: "缩小", shortcut: "方向键 向左键" },
  { action: "向左旋转90度", shortcut: "SHIFT + 方向键 向左键" },
  { action: "向右旋转90度", shortcut: "SHIFT + 方向键 向右键" },
  { action: "向左旋转30度", shortcut: "SHIFT + ALT + 方向键 向左键" },
  { action: "向右旋转30度", shortcut: "SHIFT + ALT + 方向键 向右键" },
  { action: "全屏/退出全屏", shortcut: "SHIFT + ENTER" },
  { action: "快速定位到 “是否清晰” 输入框", shortcut: "Alt + 数字键 1" },
  { action: "快速定位到 “影像分类” 输入框", shortcut: "Alt + 数字键 2" },
];

export const BUTTON_NEED_LOADING_EVENT_CODE = [
  "staging",
  "save",
  "submit",
  "event_policy_bind",
  "event_policy_adjust",
  "event_policy_clear_adjust",
];
