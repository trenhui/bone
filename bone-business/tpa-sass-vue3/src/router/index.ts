import type { App } from "vue";
import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import { isQiankun } from "@/utils/qiankun";
import pkg from "@/../package.json";

export const Layout = () => import("@/layout/index.vue");

// 静态路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: "/redirect",
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: "/redirect/:path(.*)",
        component: () => import("@/views/redirect/index.vue"),
      },
    ],
  },

  {
    path: "/login",
    component: () => import("@/views/login/index.vue"),
    meta: { hidden: true },
  },

  {
    path: "/",
    name: "/",
    component: Layout,
    redirect: "/dashboard",
    children: [
      {
        path: "dashboard",
        component: () => import("@/views/dashboard/index.vue"),
        // 用于 keep-alive 功能，需要与 SFC 中自动推导或显式声明的组件名称一致
        // 参考文档: https://cn.vuejs.org/guide/built-ins/keep-alive.html#include-exclude
        name: "Dashboard",
        meta: {
          title: "dashboard",
          icon: "homepage",
          affix: true,
          keepAlive: true,
        },
      },
      {
        path: "401",
        component: () => import("@/views/error-page/401.vue"),
        meta: { hidden: true },
      },
      {
        path: "404",
        component: () => import("@/views/error-page/404.vue"),
        meta: { hidden: true },
      },
    ],
  },

  {
    path: "/myJob",
    component: Layout,
    name: "myJob",
    meta: {
      title: "我的作业",
      icon: "menu",
      hidden: false,
      alwaysShow: true,
      params: null,
    },
    children: [
      {
        path: "myPrecheck",
        component: () => import("@/views/myJob/precheck/index.vue"),
        name: "MyPrecheck",
        meta: {
          title: "我的初审",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "myEntry",
        component: () => import("@/views/myJob/entry/index.vue"),
        name: "MyEntry",
        meta: {
          title: "我的录入",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "myQualityCheck",
        component: () => import("@/views/myJob/qualityCheck/index.vue"),
        name: "MyQualityCheck",
        meta: {
          title: "我的质检",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "myAudit",
        component: () => import("@/views/myJob/audit/index.vue"),
        name: "MyAudit",
        meta: {
          title: "我的审核",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "myReview",
        component: () => import("@/views/myJob/review/index.vue"),
        name: "MyReview",
        meta: {
          title: "我的复核",
          icon: "menu",
          hidden: false,
        },
      },
    ],
  },

  {
    path: "/jobManage",
    component: Layout,
    name: "jobManage",
    meta: {
      title: "作业管理",
      icon: "menu",
      hidden: false,
      alwaysShow: true,
      params: null,
    },
    children: [
      {
        path: "groupInsuranceSignList",
        component: () => import("@/views/jobManage/groupSign/list/index.vue"),
        name: "GroupInsuranceSignList",
        meta: {
          title: "团险签收",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "groupInsuranceSignDetail",
        component: () => import("@/views/jobManage/groupSign/detail/index.vue"),
        name: "GroupInsuranceSignDetail",
        meta: {
          title: "团险签收批次详情",
          icon: "menu",
          hidden: true,
        },
      },
      {
        path: "newSign",
        component: () => import("@/views/jobManage/groupSign/create/index.vue"),
        name: "NewSign",
        meta: {
          title: "新批次签收",
          icon: "menu",
          hidden: true,
        },
      },
      {
        path: "claimHandOver",
        component: () => import("@/views/jobManage/claimHandOver/index.vue"),
        name: "ClaimHandOver",
        meta: {
          title: "转交赔案",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "claimDistribute",
        component: () => import("@/views/jobManage/claimDistribute/index.vue"),
        name: "ClaimDistribute",
        meta: {
          title: "分配赔案",
          icon: "menu",
          hidden: false,
        },
      },

      {
        path: "uploadRecord",
        component: () => import("@/views/jobManage/uploadRecord/index.vue"),
        name: "UploadRecord",
        meta: {
          title: "导入记录",
          icon: "menu",
          hidden: false,
        },
      },
    ],
  },

  {
    path: "/claimManage",
    component: Layout,
    name: "claimManage",
    meta: {
      title: "赔案管理",
      icon: "menu",
      hidden: false,
    },
    children: [
      {
        path: "pushFail",
        component: () => import("@/views/claimManage/pushFail/index.vue"),
        name: "PushFail",
        meta: {
          title: "推送失败",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "pushFailHandleRecord",
        component: () =>
          import("@/views/claimManage/pushFail/HandleRecord.vue"),
        name: "PushFailHandleRecord",
        meta: {
          title: "推送失败处理记录",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "copyClaim",
        component: () => import("@/views/claimManage/copyClaim/index.vue"),
        name: "CopyClaim",
        meta: {
          title: "复制赔案",
          icon: "menu",
          hidden: false,
        },
      },
    ],
  },

  {
    path: "/jobConfig",
    component: Layout,
    name: "JobConfig",
    meta: {
      title: "作业配置",
      icon: "menu",
      hidden: false,
      alwaysShow: true,
      params: null,
    },
    children: [
      {
        path: "jobBaseConfig",
        component: () => import("@/views/jobConfig/jobBaseConfig/index.vue"),
        name: "JobBaseConfig",
        meta: {
          title: "标准作业配置",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "bizIdentityList",
        component: () =>
          import("@/views/jobConfig/bizIdentityConfig/list/index.vue"),
        name: "BizIdentityConfigList",
        meta: {
          title: "主体专属列表",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "bizIdentityConfig",
        component: () =>
          import("@/views/jobConfig/bizIdentityConfig/config/index.vue"),
        name: "BizIdentityConfig",
        meta: {
          title: "主体专属配置",
          icon: "menu",
          hidden: true,
        },
      },
      {
        path: "createBizIdentity",
        component: () =>
          import("@/views/jobConfig/bizIdentityConfig/create/index.vue"),
        name: "CreateBizIdentity",
        meta: {
          title: "创建主体专属",
          icon: "menu",
          hidden: true,
        },
      },
    ],
  },

  {
    path: "/policyConfig",
    component: Layout,
    name: "policyConfig",
    meta: {
      title: "团单个险管理",
      icon: "menu",
      hidden: false,
      alwaysShow: true,
      params: null,
    },
    children: [
      {
        path: "groupPolicyList",
        component: () =>
          import("@/views/policyConfig/groupPolicyList/index.vue"),
        name: "GroupPolicyList",
        meta: {
          title: "团险保单管理",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "policyRuleConfig",
        component: () =>
          import("@/views/policyConfig/policyRuleConfig/index.vue"),
        name: "PolicyRuleConfig",
        meta: {
          title: "保单规则配置",
          icon: "menu",
          hidden: true,
        },
      },
    ],
  },

  {
    path: "/systemManage",
    component: Layout,
    name: "systemManage",
    meta: {
      title: "系统管理",
      icon: "menu",
      hidden: false,
      alwaysShow: true,
      params: null,
    },
    children: [
      {
        path: "modelManage",
        component: () =>
          import("@/views/systemManage/modelManage/modelList/index.vue"),
        name: "ModelManage",
        meta: {
          title: "数据模型管理",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "fieldManage",
        component: () =>
          import("@/views/systemManage/modelManage/fieldList/index.vue"),
        name: "FieldManage",
        meta: {
          title: "字段管理",
          icon: "menu",
          hidden: true,
        },
      },
      {
        path: "optionConfig",
        component: () => import("@/views/systemManage/optionConfig/index.vue"),
        name: "OptionConfig",
        meta: {
          title: "系统选项配置",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "eventManage",
        component: () => import("@/views/systemManage/eventManage/index.vue"),
        name: "EventManage",
        meta: {
          title: "事件管理",
          icon: "menu",
          hidden: false,
        },
      },
      {
        path: "createEvent",
        component: () => import("@/views/systemManage/eventManage/create.vue"),
        name: "CreateEvent",
        meta: {
          title: "创建事件",
          icon: "menu",
          hidden: true,
        },
      },
      {
        path: "groupIndividualConfig",
        component: () =>
          import("@/views/systemManage/groupIndividualConfig/index.vue"),
        name: "GroupIndividualConfig",
        meta: {
          title: "团单个险配置",
          icon: "menu",
          hidden: false,
        },
      },
    ],
  },

  {
    path: "/codeTools",
    component: Layout,
    name: "codeTools",
    meta: {
      title: "代码基础设施",
      icon: "menu",
      hidden: false,
      alwaysShow: true,
      params: null,
    },
    children: [
      {
        path: "codeGeneration",
        component: () => import("@/views/codeTools/codeGeneration/index.vue"),
        name: "codeGeneration",
        meta: {
          title: "代码生成",
          icon: "menu",
          hidden: false,
          keepAlive: true,
          alwaysShow: false,
          params: null,
        },
      },
      {
        path: "dataSourceConfiguration",
        component: () =>
          import("@/views/codeTools/dataSourceConfiguration/index.vue"),
        name: "dataSourceConfiguration",
        meta: {
          title: "数据源配置",
          icon: "menu",
          hidden: false,
          keepAlive: true,
          alwaysShow: false,
          params: null,
        },
      },
    ],
  },

  {
    path: "/claimImageDetailEdit",
    component: () => import("@/views/claimImageDetail/Edit.vue"),
    name: "ClaimImageDetailEdit",
    meta: {
      title: "赔案影像管理",
      icon: "menu",
      hidden: true,
    },
  },

  {
    path: "/claimImageDetail",
    component: () => import("@/views/claimImageDetail/index.vue"),
    name: "ClaimImageDetail",
    meta: {
      title: "查看赔案影像",
      icon: "menu",
      hidden: true,
    },
  },

  {
    path: "/claimDetail",
    name: "ClaimDetail",
    component: () => import("@/views/claimDetail/index.vue"),
    meta: { hidden: true },
  },
];

/**
 * 创建路由
 */
const router = createRouter({
  history: createWebHistory(
    // isQiankun() ? `/layout/${pkg.name}` : `/${pkg.name}`
    isQiankun() ? `/layout/${pkg.name}` : `/`
  ),
  strict: false,
  routes: constantRoutes,
  // 刷新时，滚动条位置还原
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

// 全局注册 router
export function setupRouter(app: App<Element>) {
  app.use(router);
}

/**
 * 重置路由
 */
export function resetRouter() {
  router.replace({ path: "/login" });
}

export default router;
