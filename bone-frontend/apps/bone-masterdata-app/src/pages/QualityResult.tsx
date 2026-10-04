import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, InputNumber, Space, Table, Tag, Tooltip } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { DataQualityResult } from '@/types';
import { qualityResultApi } from '@/services/api';
import { useEntityScope } from '../context/EntityScopeContext';
import EntityScopeSelect from '../components/EntityScopeSelect';

/**
 * 质量检查结果：以「主数据模型」为查询维度（全局作用域共享当前模型），可再按记录 ID 下钻。
 *
 * 后端 `QualityApplicationService#listQualityResults` 按入参返回两种粒度（DTO.level 区分）：
 * - 不传 recordId → `SUMMARY`：该模型下各次质检任务的整体结论；
 * - 传 recordId  → `DETAIL`：该记录在各次质检中逐规则的判定，来自 `mdm_qcheck_detail`。
 *
 * 历史背景：该表此前只有 DDL、无实体与写入链路，recordId 只被原样回填到 DTO 而不参与筛选，
 * "按记录查质量结果"语义不成立（返回结果与该记录无关），所以上一版把记录输入框整个删掉了。
 * 链路补齐后，此处恢复为可选的下钻条件，而不是唯一入口。
 */
const QualityResult: React.FC = () => {
  const { entityId, currentEntity } = useEntityScope();
  const [recordId, setRecordId] = useState<number | undefined>(undefined);
  const [appliedRecordId, setAppliedRecordId] = useState<number | undefined>(undefined);
  const [results, setResults] = useState<DataQualityResult[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchResults = useCallback(async (eid?: string, rid?: string) => {
    if (!eid) {
      setResults([]);
      return;
    }
    setLoading(true);
    try {
      const res = await qualityResultApi.list({
        masterDataEntityId: eid,
        ...(rid ? { recordId: rid } : {}),
      });
      setResults(res.data ?? []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchResults(entityId, appliedRecordId);
  }, [entityId, appliedRecordId, fetchResults]);

  const isDetail = appliedRecordId !== undefined;

  const columns = useMemo(() => {
    const shared = [
      { title: '检查任务', dataIndex: 'qualityCheckId', width: 130, render: (v: number) => v ?? '-' },
      {
        title: '是否通过',
        dataIndex: 'passed',
        width: 100,
        render: (v: boolean) => (v ? <Tag color="green">通过</Tag> : <Tag color="red">未通过</Tag>),
      },
      { title: '消息', dataIndex: 'message' },
      { title: '时间', dataIndex: 'timestamp', width: 180 },
    ];
    if (!isDetail) {
      return shared;
    }
    return [
      { title: '规则 ID', dataIndex: 'dataQualityRuleId', width: 130, render: (v: number) => v ?? '-' },
      { title: '记录 ID', dataIndex: 'masterDataRecordId', width: 130 },
      ...shared,
    ];
  }, [isDetail]);

  return (
    <Card
      title="质量检查结果"
      extra={
        <Space wrap>
          <EntityScopeSelect width={240} />
          <InputNumber
            style={{ width: 190 }}
            placeholder="记录 ID（下钻，可空）"
            min={1}
            value={recordId}
            onChange={(v) => setRecordId(v ?? undefined)}
            onPressEnter={() => setAppliedRecordId(recordId)}
          />
          <Tooltip title="按记录查看逐规则判定明细">
            <Button
              icon={<SearchOutlined />}
              disabled={!entityId}
              onClick={() => setAppliedRecordId(recordId)}
            >
              下钻
            </Button>
          </Tooltip>
          <Button
            icon={<ReloadOutlined />}
            disabled={!entityId}
            onClick={() => void fetchResults(entityId, appliedRecordId)}
          >
            刷新
          </Button>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={
          isDetail
            ? `当前展示记录 ${appliedRecordId} 的逐规则判定明细（每行 = 某规则 × 该记录）。`
            : '当前展示该模型下各次质检任务的整体结论。'
        }
        description="质量明细在每次执行质量检查时写入（规则 × 记录）。若某次任务查不到明细，说明它是在明细链路补齐之前执行的，重新执行一次检查即可。"
      />
      {isDetail && (
        <Button size="small" style={{ marginBottom: 12 }} onClick={() => setAppliedRecordId(undefined)}>
          返回任务汇总
        </Button>
      )}
      <Table
        rowKey={(row) => `${row.level}-${row.id}`}
        columns={columns}
        dataSource={results}
        loading={loading}
        locale={{
          emptyText: !entityId
            ? '请先选择主数据模型'
            : isDetail
              ? '该记录暂无质量明细（可能尚未执行过覆盖它的检查）'
              : '该模型下暂无质量结果，可先在质量规则页执行检查',
        }}
        scroll={{ x: 900 }}
        pagination={{ pageSize: 10 }}
      />
      {entityId && currentEntity && (
        <div style={{ color: 'rgba(0,0,0,0.45)' }}>
          当前模型：{currentEntity.name}
          {currentEntity.status === 'PUBLISHED' ? '（已发布）' : '（草稿）'}
        </div>
      )}
    </Card>
  );
};

export default QualityResult;
