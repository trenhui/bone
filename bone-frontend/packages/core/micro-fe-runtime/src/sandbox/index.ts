import { SandboxFactory } from './sandbox-factory';
import { EnhancedProxySandbox } from './enhanced-proxy-sandbox';
import { IframeSandbox } from './iframe-sandbox';
import { SnapshotSandbox } from './snapshot-sandbox';

// 导出沙箱工厂和各种沙箱实现
export {
  SandboxFactory,
  EnhancedProxySandbox,
  IframeSandbox,
  SnapshotSandbox
};

// 导出默认的沙箱工厂实例
export default SandboxFactory;

/**
 * 创建沙箱的便捷函数
 * @param config 沙箱配置
 */
export async function createSandbox(config: any) {
  return await SandboxFactory.create(config);
}