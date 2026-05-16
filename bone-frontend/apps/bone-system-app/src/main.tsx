import App from './App';
import './index.css';
import { createBoneMicroAppRenderer } from '@bone/ui';

const lifecycle = createBoneMicroAppRenderer(App, 'bone-system-app');

if (!(window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__) {
  lifecycle.render({});
}

export const bootstrap = lifecycle.bootstrap;
export const mount = lifecycle.mount;
export const unmount = lifecycle.unmount;
