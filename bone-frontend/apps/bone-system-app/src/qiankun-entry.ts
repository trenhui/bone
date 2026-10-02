/**
 * Qiankun JS Entry — 导出微应用的 bootstrap/mount/unmount 生命周期，
 * 供 Shell 通过 import() 动态加载后调用。
 */
import App from './App';
import './App.css';
import './index.css';
import './dayjs-setup';
// i18n 单例每应用须各自初始化（见 shared-utils/i18n/index.ts 头注）；
// 本应用页面全部走 useTranslation，缺此 import 语言包不加载、t() 裸返 key。
import { i18n } from '@bone/shared-utils';
import { createBoneMicroAppRenderer } from '@bone/ui';

void i18n;

const lifecycle = createBoneMicroAppRenderer(App, 'bone-system-app');

export const bootstrap = lifecycle.bootstrap;
export const mount = lifecycle.mount;
export const unmount = lifecycle.unmount;
