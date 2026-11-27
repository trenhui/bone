import { useCallback, useEffect, useState, type ReactElement } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Space, Table, Tag, Typography, message } from 'antd';
import {
  claimApi,
  normalizeTotal,
  type ClaimHeadProjection,
} from '../services/claimApi';
import { claimStatusMeta } from '../services/claimStatus';
import { PageHeader } from '../components/PageHeader';
import { ClaimQueryBar } from '../components/ClaimQueryBar';
import {
  ClaimQueryFields,
  formValuesToQueryString,
  queryParamsFromState,
  type ClaimQueryFormValues,
} from '../components/ClaimQueryFields';
import { ClaimIdLink } from '../components/ClaimIdLink';
import { PolicyNoLink } from '../components/PolicyNoLink';
import { MoneyText } from '../components/Money';
import { useClaimQuery } from '../hooks/useClaimQuery';
import { useSavedViews } from '../hooks/useSavedViews';
import { resolveErrorMessage } from '@bone/shared-utils';

/** 全局案件查询：覆盖所有菜单的「先找到案件，再进入作业」入口。 */
export function CaseQueryPage(): ReactElement {
  const navigate = useNavigate();
  const {
    query,
    setKeyword,
    setPage,
    setSize,
    reset,
    applyQueryString,
    currentQueryString,
  } = useClaimQuery();
  const savedViews = useSavedViews();
  const [rows, setRows] = useState<ClaimHeadProjection[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await claimApi.query(queryParamsFromState(query));
      setRows(resp?.data?.records ?? []);
      setTotal(normalizeTotal(resp?.data?.total));
    } catch (err) {
      message.error(resolveErrorMessage(err, '案件查询失败'));
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = (values: ClaimQueryFormValues) => {
    applyQueryString(formValuesToQueryString(values));
  };

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="案件查询"
        description="跨案件状态、按常用业务条件组合检索；查询结果可直接进入初审、核赔或案件 360。"
      />

      <Card size="small" title="查询条件" style={{ marginBottom: 16 }}>
        <ClaimQueryFields query={query} onSubmit={submit} onReset={reset} />
      </Card>

      <Card
        size="small"
        extra={
          <ClaimQueryBar
            keyword={query.keyword}
            onKeyword={setKeyword}
            placeholder="关键字：案件ID / 赔案号 / 保单号 / 发票号"
            onRefresh={() => void load()}
            views={savedViews.views}
            onApplyView={(view) => applyQueryString(view.queryString)}
            onSaveView={(name) => savedViews.save(name, currentQueryString())}
            onRemoveView={savedViews.remove}
            onTogglePin={savedViews.togglePin}
          />
        }
      >
        <Table<ClaimHeadProjection>
          aria-label="案件查询结果"
          rowKey="claimId"
          loading={loading}
          dataSource={rows}
          scroll={{ x: 980 }}
          pagination={{
            current: query.page,
            pageSize: query.size,
            total,
            showSizeChanger: true,
            pageSizeOptions: [20, 50, 100],
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              if (s !== query.size) setSize(s);
              else setPage(p);
            },
          }}
          columns={[
            {
              title: '赔案号 / 案件ID',
              dataIndex: 'claimNo',
              width: 200,
              fixed: 'left',
              render: (v: string, row: ClaimHeadProjection) => (
                <ClaimIdLink claimId={row.claimId} text={v} testId={`case-query-link-${row.claimId}`} />
              ),
            },
            {
              title: '保单号',
              dataIndex: 'policyNo',
              width: 170,
              render: (v: string) => <PolicyNoLink policyNo={v} />,
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 120,
              render: (v: string) => {
                const meta = claimStatusMeta(v);
                return <Tag color={meta.color}>{meta.label}</Tag>;
              },
            },
            { title: '阶段', dataIndex: 'stage', width: 120, render: (v?: string) => v ?? '-' },
            {
              title: '申请金额',
              dataIndex: 'claimAmount',
              width: 110,
              align: 'right',
              render: (v: number) => <MoneyText value={v} />,
            },
            { title: '受理时间', dataIndex: 'createdAt', width: 165 },
            {
              title: '操作',
              width: 200,
              fixed: 'right',
              render: (_, row) => (
                <Space size={4} wrap>
                  <Button type="link" size="small" onClick={() => navigate(`/case?claimId=${row.claimId}`)}>
                    案件360
                  </Button>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => navigate(`/pre-exam?f_claimId=${row.claimId}`)}
                  >
                    去初审
                  </Button>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => navigate(`/review?f_claimId=${row.claimId}`)}
                  >
                    去核赔
                  </Button>
                </Space>
              ),
            },
          ]}
        />
        {rows.length === 0 && !loading && (
          <Typography.Text type="secondary">没有符合条件的案件，请调整查询条件后重试。</Typography.Text>
        )}
      </Card>
    </div>
  );
}
