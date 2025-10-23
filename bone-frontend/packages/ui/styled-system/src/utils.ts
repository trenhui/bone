import { Theme } from './index';

// 获取token值
export function getToken(
  path: string,
  theme: Theme,
  defaultValue?: any
): any {
  const segments = path.split('.');
  let value: any = theme;

  for (const segment of segments) {
    if (value == null || typeof value !== 'object') {
      return defaultValue;
    }
    value = value[segment];
  }

  return value !== undefined ? value : defaultValue;
}

// 格式化CSS变量名
export function formatCssVariableName(path: string): string {
  return `--${path.replace(/\./g, '-').replace(/([A-Z])/g, '-$1').toLowerCase()}`;
}

// 创建CSS变量引用
export function cssVar(path: string): string {
  return `var(${formatCssVariableName(path)})`;
}

// 合并样式对象
export function mergeStyles(...styles: any[]): any {
  return styles.reduce((acc, style) => {
    if (!style) return acc;

    // 处理函数样式
    if (typeof style === 'function') {
      return (theme: Theme) => {
        const resolvedAcc = typeof acc === 'function' ? acc(theme) : acc;
        const resolvedStyle = style(theme);
        return mergeStyles(resolvedAcc, resolvedStyle);
      };
    }

    // 合并对象样式
    const merged: any = { ...acc };
    
    for (const [key, value] of Object.entries(style)) {
      if (key === 'css' || !acc[key]) {
        merged[key] = value;
      } else if (typeof value === 'object' && typeof acc[key] === 'object' && !Array.isArray(value) && !Array.isArray(acc[key])) {
        merged[key] = mergeStyles(acc[key], value);
      } else {
        merged[key] = value;
      }
    }

    return merged;
  }, {});
}

// 创建条件样式
export function conditionalStyle(condition: boolean | ((props: any, theme: Theme) => boolean), trueStyle: any, falseStyle?: any) {
  return (props: any, theme: Theme) => {
    const isTrue = typeof condition === 'function' ? condition(props, theme) : condition;
    return isTrue ? trueStyle : falseStyle || {};
  };
}

// 创建属性样式映射
export function mapPropToStyle(prop: string, styleMap: Record<any, any>, defaultValue?: any) {
  return (props: any) => {
    const value = props[prop];
    return value in styleMap ? styleMap[value] : defaultValue || {};
  };
}

// 生成媒体查询
export function mediaQuery(
  breakpoint: string | number,
  style: any
): any {
  const breakpointValue = typeof breakpoint === 'number' ? `${breakpoint}px` : breakpoint;
  
  return {
    [`@media (min-width: ${breakpointValue})`]: style
  };
}

// 生成响应式样式
export function responsive(
  breakpoints: Record<string, string>,
  prop: string,
  styles: Record<string, any>
): any {
  const result: any = {};
  
  for (const [bp, value] of Object.entries(styles)) {
    if (bp === 'default') {
      result[prop] = value;
    } else if (breakpoints[bp]) {
      result[`@media (min-width: ${breakpoints[bp]})`] = {
        [prop]: value
      };
    }
  }
  
  return result;
}

// 处理伪类样式
export function pseudoStyle(
  pseudoClass: string,
  style: any
): any {
  return {
    [`&:${pseudoClass}`]: style
  };
}

// 处理伪元素样式
export function pseudoElement(
  pseudoElement: string,
  style: any
): any {
  return {
    [`&::${pseudoElement}`]: style
  };
}

// 生成过渡样式
export function transition(
  properties: string | string[],
  duration: string = '250ms',
  easing: string = 'ease-in-out'
): string {
  const props = Array.isArray(properties) ? properties.join(', ') : properties;
  return `${props} ${duration} ${easing}`;
}

// 生成阴影样式
export function shadow(level: number = 1): string {
  const shadows = [
    '0 1px 2px rgba(0,0,0,0.05)',
    '0 4px 6px -1px rgba(0,0,0,0.1), 0 2px 4px -1px rgba(0,0,0,0.06)',
    '0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -2px rgba(0,0,0,0.05)',
    '0 20px 25px -5px rgba(0,0,0,0.1), 0 10px 10px -5px rgba(0,0,0,0.04)'
  ];
  
  return shadows[Math.min(level, shadows.length - 1)] || shadows[0];
}

// 生成圆角样式
export function borderRadius(size: 'sm' | 'md' | 'lg' | 'xl' | 'full' | string = 'md'): string {
  const radii: Record<string, string> = {
    sm: '0.125rem',
    md: '0.375rem',
    lg: '0.5rem',
    xl: '0.75rem',
    full: '9999px'
  };
  
  return radii[size] || size;
}

// 生成flexbox实用样式
export function flexbox({
  direction = 'row',
  justify = 'flex-start',
  align = 'stretch',
  gap = 0
}: {
  direction?: string;
  justify?: string;
  align?: string;
  gap?: number | string;
} = {}): any {
  return {
    display: 'flex',
    flexDirection: direction,
    justifyContent: justify,
    alignItems: align,
    gap: typeof gap === 'number' ? `${gap}px` : gap
  };
}

// 生成网格实用样式
export function grid({
  columns = '1',
  rows = '1',
  gap = 0,
  templateColumns,
  templateRows
}: {
  columns?: number | string;
  rows?: number | string;
  gap?: number | string;
  templateColumns?: string;
  templateRows?: string;
} = {}): any {
  return {
    display: 'grid',
    gridTemplateColumns: templateColumns || (typeof columns === 'number' ? `repeat(${columns}, minmax(0, 1fr))` : columns),
    gridTemplateRows: templateRows || (typeof rows === 'number' ? `repeat(${rows}, minmax(0, 1fr))` : rows),
    gap: typeof gap === 'number' ? `${gap}px` : gap
  };
}

// 生成位置样式
export function position(
  type: 'static' | 'relative' | 'absolute' | 'fixed' | 'sticky' = 'relative',
  offsets?: {
    top?: number | string;
    right?: number | string;
    bottom?: number | string;
    left?: number | string;
  } = {}
): any {
  const positionStyle: any = {
    position: type
  };
  
  if (offsets) {
    Object.entries(offsets).forEach(([key, value]) => {
      positionStyle[key] = typeof value === 'number' ? `${value}px` : value;
    });
  }
  
  return positionStyle;
}

// 生成尺寸样式
export function size(
  width?: number | string,
  height?: number | string
): any {
  const sizeStyle: any = {};
  
  if (width !== undefined) {
    sizeStyle.width = typeof width === 'number' ? `${width}px` : width;
  }
  
  if (height !== undefined) {
    sizeStyle.height = typeof height === 'number' ? `${height}px` : height;
  }
  
  return sizeStyle;
}

// 生成边距样式
export function margin(
  top?: number | string,
  right?: number | string,
  bottom?: number | string,
  left?: number | string
): any {
  return generateSpacingStyle('margin', top, right, bottom, left);
}

// 生成内边距样式
export function padding(
  top?: number | string,
  right?: number | string,
  bottom?: number | string,
  left?: number | string
): any {
  return generateSpacingStyle('padding', top, right, bottom, left);
}

// 生成间距样式的辅助函数
function generateSpacingStyle(
  property: string,
  top?: number | string,
  right?: number | string,
  bottom?: number | string,
  left?: number | string
): any {
  const spacingStyle: any = {};
  
  const values = [top, right, bottom, left];
  const directions = ['Top', 'Right', 'Bottom', 'Left'];
  
  values.forEach((value, index) => {
    if (value !== undefined) {
      spacingStyle[`${property}${directions[index]}`] = typeof value === 'number' ? `${value}px` : value;
    }
  });
  
  return spacingStyle;
}