/**
 * Qiankun JS Entry — 导出微应用的 bootstrap/mount/unmount 生命周期，
 * 供 Shell 通过 import() 动态加载后调用。
 */
import App from './App';
import './index.css';
import { createBoneMicroAppRenderer } from '@bone/ui';

const lifecycle = createBoneMicroAppRenderer(App, 'bone-metadata-app');

export const bootstrap = lifecycle.bootstrap;
export const mount = lifecycle.mount;
export const unmount = lifecycle.unmount;
