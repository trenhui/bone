/**
 * 交付口径对照（个人覆盖层 · 运行时数据页）。
 *
 * <p><b>为什么在这里</b>：原「交付预览」Tab 把两件事塞在一起——① 团队契约的最终投影
 * （列集合/列序/列宽/固定列）② 个人覆盖叠加后的「我的所见」。前者归「界面设计器」
 * （其SchemaPreview 跑运行时同一套引擎、已含 tabs/search/dataScope/stats，还带
 * 「草稿/线上」对照，信息严格更强）；后者是**个人语义**，读者是终端用户、编辑器就在本页
 * 工具栏，放进建模台反而让个人偏好污染团队契约配置现场。
 *
 * <p><b>它解决的真问题（自伤遮蔽）</b>：契约默认显示的列被个人偏好盖住时，用户在
 * 数据页看到的列比契约少，却不知道原因，于是回到建模台改契约——「配了不生效」。
 * 这里把「契约应显示 N 列 / 你实际看到 M 列」与被遮蔽的列名直接摆出来，并给一键自救。
 *
 * <p>口径来源与运行时列表完全同源（同一个 {@code projectRuntimeColumns}），
 * 不另写投影，避免"对照表与实际列表对不上"的第二份真源。
 */
import { useMemo } from 'react';
import { Alert, Button, Card, Space, Tag, Tooltip, Typography } from 'antd';
import { EyeOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import type { MetaField } from '../types';
import type { ListView } from '../utils/uiSchemaEngine';
import {
  defaultOverlay,
  fieldsOutsideContract,
  projectRuntimeColumns,
} from '../utils/columnProjection';
import type { UseOverlayBundle } from '../hooks/useOverlayBundle';

const { Text } = Typography;

interface Props {
  overlay: UseOverlayBundle;
  fields: MetaField[];
  listView: ListView | null;
  /** 去建模台「界面设计器」配置团队契约（由页面注入，避免反向依赖路由） */
  onOpenDesigner?: () => void;
}

/** 一行口径摘要：层名 + 最终列数 + 列名（超量折叠，避免工具栏被长列表撑开） */
function LayerSummary({
  icon,
  color,
  label,
  fields: cols,
  emptyText,
}: {
  icon: React.ReactNode;
  color: string;
  label: string;
  fields: MetaField[];
  emptyText: string;
}) {
  const preview = cols.slice(0, 6).map((f) => f.displayName || f.code);
  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'baseline', flexWrap: 'wrap' }}>
      <Tag color={color} icon={icon} style={{ flex: 'none' }}>
        {label}
      </Tag>
      <Text strong style={{ fontSize: 13 }}>
        {cols.length} 列
      </Text>
      {cols.length === 0 ? (
        <Text type="secondary" style={{ fontSize: 12 }}>{emptyText}</Text>
      ) : (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {preview.join('、')}
          {cols.length > preview.length && ` 等 ${cols.length} 列`}
        </Text>
      )}
    </div>
  );
}

const ContractScopeCard: React.FC<Props> = ({ overlay, fields, listView, onOpenDesigner }) => {
  // 契约口径：默认覆盖（无个人叠加）——与建模台看到的完全一致
  const contractCols = useMemo(
    () => projectRuntimeColumns(fields, defaultOverlay(fields), listView),
    [fields, listView],
  );
  // 个人口径：契约 ⊕ 当前生效个人视图（与运行时列表同一投影函数、同一 activeOv）
  const mineCols = useMemo(
    () => projectRuntimeColumns(fields, overlay.activeOv, listView),
    [fields, overlay.activeOv, listView],
  );
  const shadowed = overlay.shadowedColumns;
  const outsideContract = useMemo(
    () => (listView ? fieldsOutsideContract(fields, listView) : []),
    [fields, listView],
  );

  // 只在真的有差异时展开明细——无差异时折叠为一行摘要，避免给日常操作加视觉噪音
  const diverged = shadowed.length > 0;

  return (
    <Card
      size="small"
      style={{ marginBottom: 12 }}
      title={<span style={{ fontSize: 14 }}><EyeOutlined /> 交付口径对照</span>}
      extra={
        <Space>
          {listView ? (
            <Tooltip title="团队契约层已发布：列集合/列序/列宽/固定列以「界面设计器」契约为准（发布后对全员生效）">
              <Tag color="green" icon={<TeamOutlined />}>团队契约 · 已发布</Tag>
            </Tooltip>
          ) : (
            <Tooltip title="尚未在「界面设计器」定义列表契约：当前列集合由字段结构决定">
              <Tag icon={<TeamOutlined />}>团队契约 · 未配置</Tag>
            </Tooltip>
          )}
          {onOpenDesigner && (
            <Button size="small" onClick={onOpenDesigner}>
              去界面设计器配团队列
            </Button>
          )}
        </Space>
      }
    >
      <Space direction="vertical" size={8} style={{ width: '100%' }}>
        <LayerSummary
          icon={<TeamOutlined />}
          color="green"
          label="全员所见（契约）"
          fields={contractCols.map((c) => c.field)}
          emptyText="契约未配置列，运行时按字段结构渲染"
        />
        <LayerSummary
          icon={<UserOutlined />}
          color="blue"
          label="我的所见（含个人覆盖）"
          fields={mineCols.map((c) => c.field)}
          emptyText="个人偏好把列全部隐藏了"
        />

        {/* 自伤遮蔽告警：只在真的被盖住时出现，避免长期占位。 */}
        {diverged && (
          <Alert
            type="warning"
            showIcon
            message={`你的个人偏好正在隐藏 ${shadowed.length} 个契约列`}
            description={
              <div style={{ fontSize: 13 }}>
                <div>
                  被遮蔽：{shadowed.map((f) => f.displayName || f.code).join('、')}
                </div>
                <div style={{ marginTop: 4 }}>
                  这会让「配了不生效」：设计器契约默认显示的列，被你这里的个人显隐偏好盖住。
                  恢复显示只动你本人，不影响团队契约与其他成员。
                </div>
              </div>
            }
            action={
              <Button size="small" onClick={overlay.unhideShadowed}>
                恢复显示
              </Button>
            }
          />
        )}

        {outsideContract.length > 0 && (
          <Alert
            type="info"
            showIcon
            message={`有 ${outsideContract.length} 个字段不在团队契约里，运行时列表不会显示`}
            description={
              <div style={{ fontSize: 13 }}>
                <div>
                  {outsideContract.slice(0, 6).map((f) => f.displayName || f.code).join('、')}
                  {outsideContract.length > 6 && ` 等 ${outsideContract.length} 个`}
                </div>
                {onOpenDesigner && (
                  <Button type="link" size="small" style={{ padding: '0 4px' }} onClick={onOpenDesigner}>
                    去设计器加入
                  </Button>
                )}
              </div>
            }
          />
        )}
      </Space>
    </Card>
  );
};

export default ContractScopeCard;