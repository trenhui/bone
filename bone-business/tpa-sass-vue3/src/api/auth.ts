import request from "@/utils/request";

class AuthAPI {
  /** 登录 接口*/
  static login(data: LoginFormData): Promise<string> {
    return request({
      url: "/admin-api/system/auth/login",
      method: "post",
      data,
    });
  }

  /** 注销 接口*/
  static logout() {
    return request({
      url: "/admin-api/system/auth/logout",
      method: "post",
    });
  }

  /* 获取用户信息接口（菜单、权限、角色） */
  static getLoginInfo(): Promise<LoginInfo> {
    return request<null, LoginInfo>({
      url: `/admin-api/system/auth/get-permission-info`,
      method: "get",
    });
  }

  /** 通过登录凭证，调用TPA-SAAS的登录新接口，实现效果等同于通过账号密码登录 */
  static loginTokenByRSA(data: { cipherText: string }) {
    return request<any, string>({
      url: "/admin-api/system/auth/loginTokenByRSA",
      method: "post",
      data,
    });
  }

  /** 获取验证码 接口*/
  static getCaptcha() {
    return request<any, CaptchaResult>({
      url: "/api/v1/auth/captcha",
      method: "get",
    });
  }
}

export default AuthAPI;

/* 用户信息 */
export interface UserInfo {
  avatar?: string;
  /* 用户名 */
  username: string;
  /* 昵称 */
  nickname: string;
  /* id */
  id: number;
  /* 部门id */
  deptId?: number;
}

/* 权限相关接口 */
export interface LoginInfo {
  /* 菜单 */
  menus: any[];
  /* 权限 */
  permissions: string[];
  /* 角色 */
  roles: string[];
  /* 用户信息 */
  user: UserInfo;
}

/** 登录表单数据 */
export interface LoginFormData {
  /** 用户名 */
  username: string;
  /** 密码 */
  password: string;
}

/** 登录响应 */
export interface LoginResult {
  /** 访问token */
  accessToken?: string;
  /** 过期时间(单位：毫秒) */
  expires?: number;
  /** 刷新token */
  refreshToken?: string;
  /** token 类型 */
  tokenType?: string;
}

/** 验证码响应 */
export interface CaptchaResult {
  /** 验证码缓存key */
  captchaKey: string;
  /** 验证码图片Base64字符串 */
  captchaBase64: string;
}

export interface IMenu {
  id: number;
  parentId: number;
  name: string;
  path: string;
  component: string;
  componentName: string;
  icon: string;
  visible: boolean;
  keepAlive: boolean;
  alwaysShow: boolean;
  children: IMenu[];
}

/** RouteVO，路由对象 */
export interface RouteVO {
  /** 子路由列表 */
  children: RouteVO[];
  /** 组件路径 */
  component?: string;
  /** 路由属性 */
  meta?: Meta;
  /** 路由名称 */
  name?: string;
  /** 路由路径 */
  path?: string;
  /** 跳转链接 */
  redirect?: string;
}

/** Meta，路由属性 */
export interface Meta {
  /** 【目录】只有一个子路由是否始终显示 */
  alwaysShow?: boolean;
  /** 是否隐藏(true-是 false-否) */
  hidden?: boolean;
  /** ICON */
  icon?: string;
  /** 【菜单】是否开启页面缓存 */
  keepAlive?: boolean;
  /** 路由title */
  title?: string;
}
