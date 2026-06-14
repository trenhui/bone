/**
 * Qiankun JS Entry — 导出微应用的 bootstrap/mount/unmount 生命周期，
 * 供 Shell 通过 import() 动态加载后调用。
 *
 * - dev:  Shell registerMicroApps({ entry: 'http://localhost:3003' })，
 *         qiankun-dev-loader.ts 通过 import('/qiankun-entry.js') 加载此文件，
 *         获取 bootstrap/mount/unmount 后执行生命周期。
 * - prod: vite build 将此文件打包为 UMD，window['bone-iam-app'] 作为入口。
 */
import App from './App';
import './index.css';
import { createBoneMicroAppRenderer } from '@bone/ui';

const lifecycle = createBoneMicroAppRenderer(App, 'bone-iam-app');

export const bootstrap = lifecycle.bootstrap;
export const mount = lifecycle.mount;
export const unmount = lifecycle.unmount;
