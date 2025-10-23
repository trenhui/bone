import React from 'react';
import {
  createStyled as emotionCreateStyled,
  CreateStyledOptions,
  ThemingContext,
  Theme as EmotionTheme
} from '@emotion/react';
import { Interpolation, ObjectInterpolation, Property } from '@emotion/serialize';

// 主题类型定义
export interface Theme extends EmotionTheme {
  colors?: Record<string, any>;
  typography?: Record<string, any>;
  spacing?: Record<string, any>;
  breakpoints?: Record<string, string>;
  zIndex?: Record<string, number>;
  borderRadius?: Record<string, string>;
  boxShadow?: Record<string, string>;
}

// 变体配置类型
export interface VariantsConfig<T extends string = string> {
  [key: string]: {
    [K in T]?: Record<string, any>;
  };
}

// 组件样式配置类型
export interface ComponentStyleConfig<P = any> {
  base?: ObjectInterpolation<Theme & P>;
  variants?: VariantsConfig;
  compoundVariants?: Array<{
    [key: string]: any;
    css: ObjectInterpolation<Theme & P>;
  }>;
  defaultVariants?: Record<string, string>;
}

// 处理token引用
function processToken(value: any): any {
  if (typeof value === 'string' && value.startsWith('token(') && value.endsWith(')')) {
    // 提取token路径，例如 token(colors.primary.500) -> colors.primary.500
    const tokenPath = value.slice(6, -1).trim();
    // 返回一个函数，该函数会在主题上下文中被调用
    return (theme: Theme) => {
      return tokenPath.split('.').reduce((obj: any, key: string) => {
        return obj && obj[key] !== undefined ? obj[key] : value;
      }, theme);
    };
  }
  return value;
}

// 递归处理样式对象中的token
export function processStyleTokens(style: any): any {
  if (typeof style === 'object' && style !== null && !Array.isArray(style)) {
    const processed: any = {};
    for (const [key, value] of Object.entries(style)) {
      // 跳过css属性的特殊处理
      if (key === 'css') {
        processed[key] = value;
      } else {
        processed[key] = processStyleTokens(value);
      }
    }
    return processed;
  }
  return processToken(style);
}

// 创建styled组件
interface CreateStyled extends ReturnType<typeof emotionCreateStyled> {
  <P extends object = {}>(
    component: React.ElementType,
    options?: CreateStyledOptions
  ): StyledComponentFactory<P>;
}

type StyledComponentFactory<P extends object = {}> = {
  <Props extends object = {}>(
    style: ComponentStyleConfig<P & Props> | ObjectInterpolation<Theme & P & Props>
  ): React.ForwardRefExoticComponent<
    React.PropsWithoutRef<P & Props & { as?: React.ElementType }>
    & React.RefAttributes<any>
  >;
};

// 创建自定义的styled函数
export const createStyled = (options?: CreateStyledOptions): CreateStyled => {
  const emotionStyled = emotionCreateStyled(options);

  const styled: CreateStyled = (component, styledOptions) => {
    return (styleConfig) => {
      // 处理样式配置
      const processedStyle = typeof styleConfig === 'function' 
        ? styleConfig 
        : processComponentStyle(styleConfig);

      return emotionStyled(component, styledOptions)(processedStyle);
    };
  };

  return styled as CreateStyled;
};

// 处理组件样式配置
function processComponentStyle(styleConfig: ComponentStyleConfig): ObjectInterpolation<any> {
  const processed: ObjectInterpolation<any> = {};

  // 处理基础样式
  if (styleConfig.base) {
    processed.base = processStyleTokens(styleConfig.base);
  }

  // 处理变体
  if (styleConfig.variants) {
    processed.variants = processVariants(styleConfig.variants);
  }

  // 处理复合变体
  if (styleConfig.compoundVariants) {
    processed.compoundVariants = styleConfig.compoundVariants.map(variant => ({
      ...variant,
      css: processStyleTokens(variant.css)
    }));
  }

  // 处理默认变体
  if (styleConfig.defaultVariants) {
    processed.defaultVariants = styleConfig.defaultVariants;
  }

  return processed;
}

// 处理变体样式
function processVariants(variants: VariantsConfig): VariantsConfig {
  const processed: VariantsConfig = {};

  for (const [variantKey, variantValues] of Object.entries(variants)) {
    processed[variantKey] = {};
    for (const [valueKey, style] of Object.entries(variantValues)) {
      processed[variantKey][valueKey] = processStyleTokens(style);
    }
  }

  return processed;
}

// 默认的styled函数
export const styled = createStyled();

// 生成响应式样式的工具函数
export function responsiveStyle<T extends string | number | Property.CSSProperties>(
  prop: keyof Property.CSSProperties,
  value: T | T[] | Record<string, T>,
  breakpoints?: string[]
): Property.CSSProperties {
  if (!Array.isArray(value) && typeof value !== 'object') {
    return { [prop]: value };
  }

  const result: Property.CSSProperties = {};
  const breakpointList = breakpoints || ['sm', 'md', 'lg', 'xl', '2xl'];

  if (Array.isArray(value)) {
    // 数组格式: [默认值, sm值, md值, ...]
    result[prop] = value[0];
    
    value.slice(1).forEach((val, index) => {
      if (breakpointList[index]) {
        result[`@media (min-width: ${breakpointList[index]})`] = {
          [prop]: val
        };
      }
    });
  } else {
    // 对象格式: { default: 'value', sm: 'value', ... }
    if ('default' in value) {
      result[prop] = value.default;
    }
    
    for (const [breakpoint, val] of Object.entries(value)) {
      if (breakpoint !== 'default' && breakpointList.includes(breakpoint)) {
        result[`@media (min-width: ${breakpointList[breakpointList.indexOf(breakpoint)]})`] = {
          [prop]: val
        };
      }
    }
  }

  return result;
}

// 获取主题的hook
export function useTheme<T extends Theme = Theme>(): T {
  const theme = React.useContext(ThemingContext);
  return (theme as unknown) as T;
}

// 创建主题提供者
export function createThemeProvider(theme: Theme) {
  const ThemeProvider = ({ children }: { children: React.ReactNode }) => {
    return (
      <ThemingContext.Provider value={theme}>
        {children}
      </ThemingContext.Provider>
    );
  };

  return ThemeProvider;
}

// 导出工具函数
export * from './utils';