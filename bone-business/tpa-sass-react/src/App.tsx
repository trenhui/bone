
import { ConfigProvider } from 'antd';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import './App.css';

// Import views
import Login from './views/login';
import Dashboard from './views/dashboard';

// My Job components
import MyPrecheck from './views/myJob/precheck';
import MyEntry from './views/myJob/entry';
import MyQualityCheck from './views/myJob/qualityCheck';
import MyAudit from './views/myJob/audit';
import MyReview from './views/myJob/review';

// Job Manage components
import GroupSignList from './views/jobManage/groupSign/list';
import GroupSignDetail from './views/jobManage/groupSign/detail';
import GroupSignCreate from './views/jobManage/groupSign/create';
import ClaimHandOver from './views/jobManage/claimHandOver';
import ClaimDistribute from './views/jobManage/claimDistribute';
import UploadRecord from './views/jobManage/uploadRecord';

// Claim Manage components
import PushFail from './views/claimManage/pushFail';
import PushFailHandleRecord from './views/claimManage/pushFail/HandleRecord';
import CopyClaim from './views/claimManage/copyClaim';

// Job Config components
import JobBaseConfig from './views/jobConfig/jobBaseConfig';
import BizIdentityConfigList from './views/jobConfig/bizIdentityConfig/list';
import BizIdentityConfig from './views/jobConfig/bizIdentityConfig/config';
import CreateBizIdentity from './views/jobConfig/bizIdentityConfig/create';

// Policy Config components
import GroupPolicyList from './views/policyConfig/groupPolicyList';
import PolicyRuleConfig from './views/policyConfig/policyRuleConfig';

// System Manage components
import ModelManage from './views/systemManage/modelManage/modelList';
import FieldManage from './views/systemManage/modelManage/fieldList';
import OptionConfig from './views/systemManage/optionConfig';
import EventManage from './views/systemManage/eventManage';
import CreateEvent from './views/systemManage/eventManage/create';
import GroupIndividualConfig from './views/systemManage/groupIndividualConfig';

// Code Tools components
import CodeGeneration from './views/codeTools/codeGeneration';
import DataSourceConfiguration from './views/codeTools/dataSourceConfiguration';

// Other components
import ClaimDetail from './views/claimDetail';
import ClaimImageDetail from './views/claimImageDetail';
import ClaimImageDetailEdit from './views/claimImageDetail/Edit';

// Error pages
const Page401 = () => {
  return <div>401 - Unauthorized</div>;
};

const Page404 = () => {
  return <div>404 - Not Found</div>;
};

function App() {
  return (
    <ConfigProvider>
      <Router>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Layout><Dashboard /></Layout>} />
          <Route path="/401" element={<Page401 />} />
          <Route path="/404" element={<Page404 />} />
          
          {/* My Job routes */}
          <Route path="/myJob/myPrecheck" element={<Layout><MyPrecheck /></Layout>} />
          <Route path="/myJob/myEntry" element={<Layout><MyEntry /></Layout>} />
          <Route path="/myJob/myQualityCheck" element={<Layout><MyQualityCheck /></Layout>} />
          <Route path="/myJob/myAudit" element={<Layout><MyAudit /></Layout>} />
          <Route path="/myJob/myReview" element={<Layout><MyReview /></Layout>} />
          
          {/* Job Manage routes */}
          <Route path="/jobManage/groupSignList" element={<Layout><GroupSignList /></Layout>} />
          <Route path="/jobManage/groupSignDetail" element={<Layout><GroupSignDetail /></Layout>} />
          <Route path="/jobManage/groupSignCreate" element={<Layout><GroupSignCreate /></Layout>} />
          <Route path="/jobManage/claimHandOver" element={<Layout><ClaimHandOver /></Layout>} />
          <Route path="/jobManage/claimDistribute" element={<Layout><ClaimDistribute /></Layout>} />
          <Route path="/jobManage/uploadRecord" element={<Layout><UploadRecord /></Layout>} />
          
          {/* Claim Manage routes */}
          <Route path="/claimManage/pushFail" element={<Layout><PushFail /></Layout>} />
          <Route path="/claimManage/pushFailHandleRecord" element={<Layout><PushFailHandleRecord /></Layout>} />
          <Route path="/claimManage/copyClaim" element={<Layout><CopyClaim /></Layout>} />
          
          {/* Job Config routes */}
          <Route path="/jobConfig/jobBaseConfig" element={<Layout><JobBaseConfig /></Layout>} />
          <Route path="/jobConfig/bizIdentityList" element={<Layout><BizIdentityConfigList /></Layout>} />
          <Route path="/jobConfig/bizIdentityConfig" element={<Layout><BizIdentityConfig /></Layout>} />
          <Route path="/jobConfig/createBizIdentity" element={<Layout><CreateBizIdentity /></Layout>} />
          
          {/* Policy Config routes */}
          <Route path="/policyConfig/groupPolicyList" element={<Layout><GroupPolicyList /></Layout>} />
          <Route path="/policyConfig/policyRuleConfig" element={<Layout><PolicyRuleConfig /></Layout>} />
          
          {/* System Manage routes */}
          <Route path="/systemManage/modelManage" element={<Layout><ModelManage /></Layout>} />
          <Route path="/systemManage/fieldManage" element={<Layout><FieldManage /></Layout>} />
          <Route path="/systemManage/optionConfig" element={<Layout><OptionConfig /></Layout>} />
          <Route path="/systemManage/eventManage" element={<Layout><EventManage /></Layout>} />
          <Route path="/systemManage/createEvent" element={<Layout><CreateEvent /></Layout>} />
          <Route path="/systemManage/groupIndividualConfig" element={<Layout><GroupIndividualConfig /></Layout>} />
          
          {/* Code Tools routes */}
          <Route path="/codeTools/codeGeneration" element={<Layout><CodeGeneration /></Layout>} />
          <Route path="/codeTools/dataSourceConfiguration" element={<Layout><DataSourceConfiguration /></Layout>} />
          
          {/* Other routes */}
          <Route path="/claimImageDetailEdit" element={<ClaimImageDetailEdit />} />
          <Route path="/claimImageDetail" element={<ClaimImageDetail />} />
          <Route path="/claimDetail" element={<ClaimDetail />} />
        </Routes>
      </Router>
    </ConfigProvider>
  );
}

export default App;