import { Card, Steps } from 'antd';
import { useCodeGeneration } from './code-generation/useCodeGeneration';
import MainPanel from './code-generation/MainPanel';
import SyncTablesModal from './code-generation/SyncTablesModal';
import GenerateConfigModal from './code-generation/GenerateConfigModal';
import ResultModal from './code-generation/ResultModal';

const CodeGeneration: React.FC = () => {
  const generator = useCodeGeneration();

  // 向导阶段：0 选表 → 1 配置 → 2 结果（跟随弹窗状态）
  const current = generator.resultModalVisible
    ? 2
    : generator.configModalVisible
      ? 1
      : 0;

  return (
    <>
      <Card style={{ marginBottom: 16 }}>
        <Steps
          current={current}
          items={[
            { title: '选择数据' },
            { title: '配置生成' },
            { title: '获取结果' },
          ]}
        />
      </Card>
      <MainPanel {...generator} />
      <SyncTablesModal {...generator} />
      <GenerateConfigModal {...generator} />
      <ResultModal {...generator} />
    </>
  );
};

export default CodeGeneration;
