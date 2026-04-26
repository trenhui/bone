const fs = require('fs');
const path = require('path');

const pages = [
  { path: 'error-page/401', title: '401 - 无权访问', subtitle: '抱歉，您没有权限访问此页面' },
  { path: 'error-page/404', title: '404 - 页面未找到', subtitle: '抱歉，您访问的页面不存在' },
  { path: 'redirect', title: '重定向中', subtitle: '正在重定向到目标页面...' },
  
  // 我的作业
  { path: 'myJob/precheck', title: '我的初审', subtitle: '初审作业页面' },
  { path: 'myJob/entry', title: '我的录入', subtitle: '录入作业页面' },
  { path: 'myJob/qualityCheck', title: '我的质检', subtitle: '质检作业页面' },
  { path: 'myJob/audit', title: '我的审核', subtitle: '审核作业页面' },
  { path: 'myJob/review', title: '我的复核', subtitle: '复核作业页面' },
  
  // 作业管理
  { path: 'jobManage/groupSign/list', title: '团险签收', subtitle: '团险签收列表' },
  { path: 'jobManage/groupSign/detail', title: '团险签收批次详情', subtitle: '团险签收批次详情' },
  { path: 'jobManage/groupSign/create', title: '新批次签收', subtitle: '创建新批次签收' },
  { path: 'jobManage/claimHandOver', title: '转交赔案', subtitle: '转交赔案管理' },
  { path: 'jobManage/claimDistribute', title: '分配赔案', subtitle: '分配赔案管理' },
  { path: 'jobManage/uploadRecord', title: '导入记录', subtitle: '导入记录列表' },
  
  // 赔案管理
  { path: 'claimManage/pushFail', title: '推送失败', subtitle: '推送失败赔案列表' },
  { path: 'claimManage/pushFail/HandleRecord', title: '推送失败处理记录', subtitle: '推送失败处理记录' },
  { path: 'claimManage/copyClaim', title: '复制赔案', subtitle: '复制赔案功能' },
  
  // 作业配置
  { path: 'jobConfig/jobBaseConfig', title: '标准作业配置', subtitle: '标准作业配置' },
  { path: 'jobConfig/bizIdentityConfig/list', title: '主体专属列表', subtitle: '主体专属配置列表' },
  { path: 'jobConfig/bizIdentityConfig/config', title: '主体专属配置', subtitle: '主体专属配置' },
  { path: 'jobConfig/bizIdentityConfig/create', title: '创建主体专属', subtitle: '创建主体专属配置' },
  
  // 团单个险管理
  { path: 'policyConfig/groupPolicyList', title: '团险保单管理', subtitle: '团险保单管理' },
  { path: 'policyConfig/policyRuleConfig', title: '保单规则配置', subtitle: '保单规则配置' },
  
  // 系统管理
  { path: 'systemManage/modelManage/modelList', title: '数据模型管理', subtitle: '数据模型管理' },
  { path: 'systemManage/modelManage/fieldList', title: '字段管理', subtitle: '字段管理' },
  { path: 'systemManage/optionConfig', title: '系统选项配置', subtitle: '系统选项配置' },
  { path: 'systemManage/eventManage', title: '事件管理', subtitle: '事件管理' },
  { path: 'systemManage/eventManage/create', title: '创建事件', subtitle: '创建新事件' },
  { path: 'systemManage/groupIndividualConfig', title: '团单个险配置', subtitle: '团单个险配置' },
  
  // 代码工具
  { path: 'codeTools/codeGeneration', title: '代码生成', subtitle: '代码生成工具' },
  { path: 'codeTools/dataSourceConfiguration', title: '数据源配置', subtitle: '数据源配置' },
  
  // 赔案详情
  { path: 'claimImageDetail/Edit', title: '赔案影像管理', subtitle: '赔案影像管理' },
  { path: 'claimImageDetail', title: '查看赔案影像', subtitle: '查看赔案影像' },
  { path: 'claimDetail', title: '赔案详情', subtitle: '赔案详情' },
];

const template = (title, subtitle) => `import React from 'react';
import Placeholder from '@/components/Placeholder';

const Page: React.FC = () => {
  return <Placeholder title="${title}" subTitle="${subtitle}" />;
};

export default Page;
`;

pages.forEach((page) => {
  const filePath = path.join(__dirname, 'src/views', page.path, 'index.tsx');
  const dirPath = path.dirname(filePath);
  
  // 确保目录存在
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
  }
  
  // 写入文件
  fs.writeFileSync(filePath, template(page.title, page.subtitle));
  console.log(`Generated: ${filePath}`);
});

console.log('\nAll placeholder pages generated successfully!');
