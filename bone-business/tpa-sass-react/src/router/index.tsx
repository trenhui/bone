import React, { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import Layout from '@/components/Layout';

// 懒加载组件
const Login = lazy(() => import('@/views/login'));
const Dashboard = lazy(() => import('@/views/dashboard'));
const Error401 = lazy(() => import('@/views/error-page/401'));
const Error404 = lazy(() => import('@/views/error-page/404'));
const Redirect = lazy(() => import('@/views/redirect'));

// 我的作业
const MyPrecheck = lazy(() => import('@/views/myJob/precheck'));
const MyEntry = lazy(() => import('@/views/myJob/entry'));
const MyQualityCheck = lazy(() => import('@/views/myJob/qualityCheck'));
const MyAudit = lazy(() => import('@/views/myJob/audit'));
const MyReview = lazy(() => import('@/views/myJob/review'));

// 作业管理
const GroupInsuranceSignList = lazy(() => import('@/views/jobManage/groupSign/list'));
const GroupInsuranceSignDetail = lazy(() => import('@/views/jobManage/groupSign/detail'));
const NewSign = lazy(() => import('@/views/jobManage/groupSign/create'));
const ClaimHandOver = lazy(() => import('@/views/jobManage/claimHandOver'));
const ClaimDistribute = lazy(() => import('@/views/jobManage/claimDistribute'));
const UploadRecord = lazy(() => import('@/views/jobManage/uploadRecord'));

// 赔案管理
const PushFail = lazy(() => import('@/views/claimManage/pushFail'));
const PushFailHandleRecord = lazy(() => import('@/views/claimManage/pushFail/HandleRecord'));
const CopyClaim = lazy(() => import('@/views/claimManage/copyClaim'));

// 作业配置
const JobBaseConfig = lazy(() => import('@/views/jobConfig/jobBaseConfig'));
const BizIdentityConfigList = lazy(() => import('@/views/jobConfig/bizIdentityConfig/list'));
const BizIdentityConfig = lazy(() => import('@/views/jobConfig/bizIdentityConfig/config'));
const CreateBizIdentity = lazy(() => import('@/views/jobConfig/bizIdentityConfig/create'));

// 团单个险管理
const GroupPolicyList = lazy(() => import('@/views/policyConfig/groupPolicyList'));
const PolicyRuleConfig = lazy(() => import('@/views/policyConfig/policyRuleConfig'));

// 系统管理
const ModelManage = lazy(() => import('@/views/systemManage/modelManage/modelList'));
const FieldManage = lazy(() => import('@/views/systemManage/modelManage/fieldList'));
const OptionConfig = lazy(() => import('@/views/systemManage/optionConfig'));
const EventManage = lazy(() => import('@/views/systemManage/eventManage'));
const CreateEvent = lazy(() => import('@/views/systemManage/eventManage/create'));
const GroupIndividualConfig = lazy(() => import('@/views/systemManage/groupIndividualConfig'));

// 代码工具
const CodeGeneration = lazy(() => import('@/views/codeTools/codeGeneration'));
const DataSourceConfiguration = lazy(() => import('@/views/codeTools/dataSourceConfiguration'));

// 赔案详情
const ClaimImageDetailEdit = lazy(() => import('@/views/claimImageDetail/Edit'));
const ClaimImageDetail = lazy(() => import('@/views/claimImageDetail'));
const ClaimDetail = lazy(() => import('@/views/claimDetail'));

// 加载中的占位组件
const Loader = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '200px' }}>
    <div>加载中...</div>
  </div>
);

const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <Suspense fallback={<Loader />}>
        <Login />
      </Suspense>
    ),
  },
  {
    path: '/',
    element: <Layout />,
    children: [
      {
        index: true,
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: 'redirect/:path',
        element: (
          <Suspense fallback={<Loader />}>
            <Redirect />
          </Suspense>
        ),
      },
      {
        path: 'dashboard',
        element: (
          <Suspense fallback={<Loader />}>
            <Dashboard />
          </Suspense>
        ),
      },
      {
        path: '401',
        element: (
          <Suspense fallback={<Loader />}>
            <Error401 />
          </Suspense>
        ),
      },
      {
        path: '404',
        element: (
          <Suspense fallback={<Loader />}>
            <Error404 />
          </Suspense>
        ),
      },
      // 我的作业
      {
        path: 'myJob/myPrecheck',
        element: (
          <Suspense fallback={<Loader />}>
            <MyPrecheck />
          </Suspense>
        ),
      },
      {
        path: 'myJob/myEntry',
        element: (
          <Suspense fallback={<Loader />}>
            <MyEntry />
          </Suspense>
        ),
      },
      {
        path: 'myJob/myQualityCheck',
        element: (
          <Suspense fallback={<Loader />}>
            <MyQualityCheck />
          </Suspense>
        ),
      },
      {
        path: 'myJob/myAudit',
        element: (
          <Suspense fallback={<Loader />}>
            <MyAudit />
          </Suspense>
        ),
      },
      {
        path: 'myJob/myReview',
        element: (
          <Suspense fallback={<Loader />}>
            <MyReview />
          </Suspense>
        ),
      },
      // 作业管理
      {
        path: 'jobManage/groupInsuranceSignList',
        element: (
          <Suspense fallback={<Loader />}>
            <GroupInsuranceSignList />
          </Suspense>
        ),
      },
      {
        path: 'jobManage/groupInsuranceSignDetail',
        element: (
          <Suspense fallback={<Loader />}>
            <GroupInsuranceSignDetail />
          </Suspense>
        ),
      },
      {
        path: 'jobManage/newSign',
        element: (
          <Suspense fallback={<Loader />}>
            <NewSign />
          </Suspense>
        ),
      },
      {
        path: 'jobManage/claimHandOver',
        element: (
          <Suspense fallback={<Loader />}>
            <ClaimHandOver />
          </Suspense>
        ),
      },
      {
        path: 'jobManage/claimDistribute',
        element: (
          <Suspense fallback={<Loader />}>
            <ClaimDistribute />
          </Suspense>
        ),
      },
      {
        path: 'jobManage/uploadRecord',
        element: (
          <Suspense fallback={<Loader />}>
            <UploadRecord />
          </Suspense>
        ),
      },
      // 赔案管理
      {
        path: 'claimManage/pushFail',
        element: (
          <Suspense fallback={<Loader />}>
            <PushFail />
          </Suspense>
        ),
      },
      {
        path: 'claimManage/pushFailHandleRecord',
        element: (
          <Suspense fallback={<Loader />}>
            <PushFailHandleRecord />
          </Suspense>
        ),
      },
      {
        path: 'claimManage/copyClaim',
        element: (
          <Suspense fallback={<Loader />}>
            <CopyClaim />
          </Suspense>
        ),
      },
      // 作业配置
      {
        path: 'jobConfig/jobBaseConfig',
        element: (
          <Suspense fallback={<Loader />}>
            <JobBaseConfig />
          </Suspense>
        ),
      },
      {
        path: 'jobConfig/bizIdentityList',
        element: (
          <Suspense fallback={<Loader />}>
            <BizIdentityConfigList />
          </Suspense>
        ),
      },
      {
        path: 'jobConfig/bizIdentityConfig',
        element: (
          <Suspense fallback={<Loader />}>
            <BizIdentityConfig />
          </Suspense>
        ),
      },
      {
        path: 'jobConfig/createBizIdentity',
        element: (
          <Suspense fallback={<Loader />}>
            <CreateBizIdentity />
          </Suspense>
        ),
      },
      // 团单个险管理
      {
        path: 'policyConfig/groupPolicyList',
        element: (
          <Suspense fallback={<Loader />}>
            <GroupPolicyList />
          </Suspense>
        ),
      },
      {
        path: 'policyConfig/policyRuleConfig',
        element: (
          <Suspense fallback={<Loader />}>
            <PolicyRuleConfig />
          </Suspense>
        ),
      },
      // 系统管理
      {
        path: 'systemManage/modelManage',
        element: (
          <Suspense fallback={<Loader />}>
            <ModelManage />
          </Suspense>
        ),
      },
      {
        path: 'systemManage/fieldManage',
        element: (
          <Suspense fallback={<Loader />}>
            <FieldManage />
          </Suspense>
        ),
      },
      {
        path: 'systemManage/optionConfig',
        element: (
          <Suspense fallback={<Loader />}>
            <OptionConfig />
          </Suspense>
        ),
      },
      {
        path: 'systemManage/eventManage',
        element: (
          <Suspense fallback={<Loader />}>
            <EventManage />
          </Suspense>
        ),
      },
      {
        path: 'systemManage/createEvent',
        element: (
          <Suspense fallback={<Loader />}>
            <CreateEvent />
          </Suspense>
        ),
      },
      {
        path: 'systemManage/groupIndividualConfig',
        element: (
          <Suspense fallback={<Loader />}>
            <GroupIndividualConfig />
          </Suspense>
        ),
      },
      // 代码工具
      {
        path: 'codeTools/codeGeneration',
        element: (
          <Suspense fallback={<Loader />}>
            <CodeGeneration />
          </Suspense>
        ),
      },
      {
        path: 'codeTools/dataSourceConfiguration',
        element: (
          <Suspense fallback={<Loader />}>
            <DataSourceConfiguration />
          </Suspense>
        ),
      },
      // 赔案详情
      {
        path: 'claimImageDetailEdit',
        element: (
          <Suspense fallback={<Loader />}>
            <ClaimImageDetailEdit />
          </Suspense>
        ),
      },
      {
        path: 'claimImageDetail',
        element: (
          <Suspense fallback={<Loader />}>
            <ClaimImageDetail />
          </Suspense>
        ),
      },
      {
        path: 'claimDetail',
        element: (
          <Suspense fallback={<Loader />}>
            <ClaimDetail />
          </Suspense>
        ),
      },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/404" replace />,
  },
]);

export default router;
