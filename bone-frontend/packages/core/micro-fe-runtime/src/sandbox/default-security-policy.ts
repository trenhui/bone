import { SandboxSecurityPolicy } from '../types';

export class DefaultSecurityPolicy implements SandboxSecurityPolicy {
  private dangerousProperties: Set<string> = new Set([
    'eval',
    'Function',
    '__proto__',
    'constructor',
    'prototype',
    'Symbol.unscopables',
    'document.cookie',
    'document.write',
    'document.writeln',
    'window.top',
    'window.parent',
    'window.frameElement',
    'window.opener'
  ]);

  private wrappedProperties: Set<string> = new Set([
    'setTimeout',
    'setInterval',
    'requestAnimationFrame',
    'cancelAnimationFrame',
    'fetch',
    'XMLHttpRequest',
    'WebSocket',
    'localStorage',
    'sessionStorage'
  ]);

  checkAccess(type: string, prop: string | symbol): 'allow' | 'block' | 'wrap' {
    const propString = String(prop);
    const fullPath = `${type}.${propString}`;

    // 检查是否是危险属性
    if (this.dangerousProperties.has(propString) || this.dangerousProperties.has(fullPath)) {
      return 'block';
    }

    // 检查是否需要包装
    if (this.wrappedProperties.has(propString)) {
      return 'wrap';
    }

    // 默认允许访问
    return 'allow';
  }

  checkWrite(type: string, prop: string | symbol): boolean {
    const propString = String(prop);
    const fullPath = `${type}.${propString}`;

    // 禁止修改危险属性
    if (this.dangerousProperties.has(propString) || this.dangerousProperties.has(fullPath)) {
      return false;
    }

    // 禁止修改只读属性
    const readOnlyProperties = [
      'location',
      'history',
      'document',
      'navigator',
      'screen',
      'console'
    ];
    
    if (readOnlyProperties.includes(propString)) {
      return false;
    }

    // 默认允许写入
    return true;
  }

  checkDelete(type: string, prop: string | symbol): boolean {
    const propString = String(prop);

    // 禁止删除核心属性
    const coreProperties = [
      'Object',
      'Array',
      'Function',
      'String',
      'Number',
      'Boolean',
      'Date',
      'RegExp',
      'Math',
      'JSON'
    ];
    
    if (coreProperties.includes(propString)) {
      return false;
    }

    // 默认允许删除
    return true;
  }
}