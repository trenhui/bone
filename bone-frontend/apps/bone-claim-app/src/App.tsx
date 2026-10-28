import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom';
import { App as AntdApp, Layout, Menu, type MenuProps } from 'antd';
import {
  AuditOutlined,
  CalculatorOutlined,
  FileAddOutlined,
  BankOutlined,
  FormOutlined,
  HistoryOutlined,
  FileProtectOutlined,
  PauseCircleOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { ClaimSubmitPage } from './pages/ClaimSubmitPage';
import { ReviewWorkbenchPage } from './pages/ReviewWorkbenchPage';
import { ClaimProgressPage } from './pages/ClaimProgressPage';
import { AdjustmentAttributionPage } from './pages/AdjustmentAttributionPage';
import { EvidenceUploadPage } from './pages/EvidenceUploadPage';
import { FundWorkbenchPage } from './pages/FundWorkbenchPage';
import { AppealPage } from './pages/AppealPage';
import { SuspendPage } from './pages/SuspendPage';
import { InspectionWorkbenchPage } from './pages/InspectionWorkbenchPage';
import './App.css';

/**
 * synapClaim 智能理赔微应用·工作台外壳。
 *
 * 导航（渐进式试点的六个页，均由领域 controller 提供读写，未提前抽低代码通用渲染器）：
 * - `/review`   核赔工作台（RQ-MVP-004~005，含 A3 决策图轨迹回放抽屉）
 * - `/adjust`   理算归因（A6 只读展示；「schema as data」试点）
 * - `/evidence` 材料上传（A2 登记 + 展示；直传 bone-file 拿 fileRef）
 * - `/fund`     资金工作台（A7 手写不低代码化；结算单状态机 + 额度三值）
 * - `/submit`   员工端报案（RQ-MVP-001~003）
 * - `/progress` 员工端进度追踪（PRD §7.2「进度追踪」）
 */

const NAV_ITEMS: MenuProps['items'] = [
  { key: '/review', icon: <AuditOutlined />, label: '核赔工作台' },
  { key: '/adjust', icon: <CalculatorOutlined />, label: '理算归因' },
  { key: '/evidence', icon: <FileAddOutlined />, label: '材料上传' },
  { key: '/fund', icon: <BankOutlined />, label: '资金工作台' },
  { key: '/appeal', icon: <FileProtectOutlined />, label: '申诉复议' },
  { key: '/suspend', icon: <PauseCircleOutlined />, label: '挂起/恢复' },
  { key: '/inspection', icon: <SafetyCertificateOutlined />, label: '后督抽检' },
  { key: '/submit', icon: <FormOutlined />, label: '员工报案' },
  { key: '/progress', icon: <HistoryOutlined />, label: '进度追踪' },
];

const AppShell: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const selectedKey =
    NAV_ITEMS?.map((i) => i!.key as string).find((k) => location.pathname.startsWith(k)) ?? '/review';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider theme="light" breakpoint="lg" collapsedWidth={0}>
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 600,
            color: '#1677ff',
            fontSize: 16,
          }}
        >
          智能理赔
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={NAV_ITEMS}
          onClick={({ key }) => navigate(key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Content style={{ padding: 0 }}>
          <Routes>
            <Route path="/submit" element={<ClaimSubmitPage />} />
            <Route path="/progress" element={<ClaimProgressPage />} />
            <Route path="/review" element={<ReviewWorkbenchPage />} />
            <Route path="/adjust" element={<AdjustmentAttributionPage />} />
            <Route path="/evidence" element={<EvidenceUploadPage />} />
            <Route path="/fund" element={<FundWorkbenchPage />} />
            <Route path="/appeal" element={<AppealPage />} />
            <Route path="/suspend" element={<SuspendPage />} />
            <Route path="/inspection" element={<InspectionWorkbenchPage />} />
            <Route path="/" element={<Navigate to="/review" replace />} />
          </Routes>
        </Layout.Content>
      </Layout>
    </Layout>
  );
};

export const App: React.FC = () => {
  return (
    <AntdApp>
      <BrowserRouter future={{ v7_relativeSplatPath: true }}>
        <AppShell />
      </BrowserRouter>
    </AntdApp>
  );
};
