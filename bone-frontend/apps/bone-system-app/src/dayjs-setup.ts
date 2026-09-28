import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import utc from 'dayjs/plugin/utc';

// S-5：统一 dayjs 国际化与 UTC 支持（原代码 0 处配置，
// 导致日志/时间展示依赖运行环境默认 locale）。仅 locale 与 utc 扩展，不引入 timezone 插件。
dayjs.extend(utc);
dayjs.locale('zh-CN');

export {};
