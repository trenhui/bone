import { useEffect, useState, type ReactElement } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { resolveErrorCode } from '@bone/shared-utils';
import { PageHeader } from '../components/PageHeader';
import {
  claimApi,
  type AppendCaseReq,
  type ClaimItemReq,
  type ClaimReportDetail,
  type ItemCategory,
  type SubmitClaimReq,
} from '../services/claimApi';
import { claimConfigApi, type FeeCategoryView } from '../services/claimConfigApi';

/**
 * 员工端 · 报案页（synapClaim 一期：企业补充医疗 · 门诊小额快赔）。
 *
 * 提交即受理（后端 DRAFT→7）；缺件走工作台「要求补件」回流，不阻塞受理。
 * 材料上传（ATTACHMENT 真上传）为后续迭代，当前以发票代码/号码登记（R-FRAUD-07 唯一约束）。
 */
/**
 * 报案表单值类型：出险日期为 dayjs（DatePicker），提交时转 ISO 字符串；
 * 明细走 Form.List， items 为行数组。
 */
type SubmitFormValues = Omit<SubmitClaimReq, 'outInsureTime'> & {
  outInsureTime: dayjs.Dayjs;
  items: ClaimItemReq[];
};

export function ClaimSubmitPage(): ReactElement {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  /**
   * 追加模式：`/submit?reportNo=RP...` 表示「为既有报案追加一笔赔付」。
   *
   * <p>为什么复用同一个页面而不是新开一页：报案与追加赔案的**输入项几乎相同**（票据、明细、同意记录号），
   * 唯一差别是事故上下文（保单/计划/被保人/出险时间）在追加时由报案继承、不接受录入。
   * 新开一页等于把表单、目录下拉、明细表格、错误分支复制一份——两份实现必然漂移。
   */
  const reportNo = searchParams.get('reportNo');
  const isAppend = Boolean(reportNo);
  const [form] = Form.useForm<SubmitFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<{
    claimId?: string;
    claimNo: string;
    reportNo?: string;
    status: string;
  } | null>(null);
  /** 追加模式下的事故上下文（只读展示，值由后端从报案给出）。 */
  const [report, setReport] = useState<ClaimReportDetail | null>(null);
  const [reportError, setReportError] = useState<string | null>(null);
  // 费用类别目录（t_ref_fee_category）：明细按类别落 fee_code，理算侧「责任 × 费用类别」规则据此命中。
  // 取不到时下拉为空但不阻塞报案——未分类明细按责任主比例算，并在理算诊断里提示补录。
  const [feeCategories, setFeeCategories] = useState<FeeCategoryView[]>([]);
  const [feeCatalogFailed, setFeeCatalogFailed] = useState(false);
  useEffect(() => {
    claimConfigApi
      .listAllFeeCategories()
      .then((res) => {
        setFeeCategories(res?.data ?? []);
        setFeeCatalogFailed(false);
      })
      .catch(() => {
        setFeeCategories([]);
        setFeeCatalogFailed(true);
      });
  }, []);

  // 追加模式：先把报案的事故上下文取回来。取不到就直接停手——
  // 让用户在不知道「往哪个事故里追加」的情况下盲填一笔赔付，比报错更危险。
  useEffect(() => {
    if (!reportNo) return;
    let alive = true;
    claimApi
      .reportDetail(reportNo)
      .then((resp) => {
        if (!alive) return;
        const detail = resp?.data ?? null;
        setReport(detail);
        setReportError(detail ? null : '未取到报案信息');
        if (detail?.claimType) form.setFieldValue('claimType', detail.claimType);
      })
      .catch((err) => {
        if (!alive) return;
        const code = resolveErrorCode(err);
        setReport(null);
        setReportError(
          code === 'SC_CLAIM_REPORT_NOT_FOUND'
            ? '报案不存在或无权查看：请确认链接里的报案号（RP 开头）正确'
            : '报案信息加载失败，请稍后重试',
        );
      });
    return () => {
      alive = false;
    };
  }, [reportNo, form]);

  const onFinish = async (values: SubmitFormValues) => {
    if (isAppend) {
      // 追加模式下保单维度字段不渲染，values 里也没有它们——
      // 请求体刻意只带「赔付维度」字段，事故上下文一律由后端从报案继承。
      const req: AppendCaseReq = {
        claimType: values.claimType,
        invoiceCode: values.invoiceCode,
        invoiceNo: values.invoiceNo,
        consentRecordId: values.consentRecordId,
        items: values.items ?? [],
      };
      setSubmitting(true);
      try {
        const resp = await claimApi.appendCase(reportNo ?? '', req);
        const envelope = resp as (typeof resp & { success?: boolean });
        if (envelope.success === false || envelope.code !== 200) {
          const err = new Error(envelope.message || '追加失败') as Error & { errorCode?: string };
          const inner = envelope.data as { errorCode?: string } | undefined;
          if (inner?.errorCode) err.errorCode = inner.errorCode;
          throw err;
        }
        const data = resp?.data;
        if (!data?.claimNo) {
          throw new Error('追加成功但未返回赔案号，请联系核赔员确认，切勿重复提交');
        }
        setResult({
          claimId: data.claimId,
          claimNo: data.claimNo,
          reportNo: data.reportNo,
          status: data?.status ?? '',
        });
        message.success(`已追加赔案：${data.claimNo}`);
        return;
      } catch (err) {
        const errorCode = resolveErrorCode(err);
        message.error(
          errorCode === 'SC_CLAIM_REPORT_NOT_FOUND'
            ? '报案不存在或无权查看'
            : errorCode === 'SC_CLAIM_REPORT_CLOSED'
              ? '该报案已关闭，不能再追加赔案；如需继续赔付请重新报案'
              : errorCode === 'SC_CLAIM_REPORT_CONTEXT_MISMATCH'
                ? '请求里带了不该传的保单维度字段（保单/计划/被保人/出险时间），这些值一律继承报案'
                : errorCode === 'SC_CLAIM_CONCURRENT_CONFLICT'
                  ? '同一笔赔付已被处理（幂等键冲突），请刷新后确认是否已追加成功'
                  : '追加失败，请稍后重试或联系核赔员',
        );
        return;
      } finally {
        setSubmitting(false);
      }
    }

    // 「至少一个保单标识」是后端同一条约束的前置提示：两个都空提交上去只会换回一个 400 错误码，
    // 在表单内就挡住能少一次无意义的往返（规则本身不放松——后端仍是唯一判定方）。
    if (!values.policyNo?.trim() && !values.insurerPolicyNo?.trim()) {
      message.error('请填写平台保单号或保司保单号（至少一个）');
      return;
    }
    setSubmitting(true);
    try {
      const req: SubmitClaimReq = {
        ...values,
        outInsureTime: values.outInsureTime.toISOString(),
        items: values.items ?? [],
      };
      const resp = await claimApi.submit(req);
      // 共享拦截器以 success:false 为业务失败真源；这里再守一道，避免非标准信封
      // （无 success 字段）被误当成功渲染。缺赔案号时禁止弹"已受理"，并提示勿重复提交。
      const envelope = resp as (typeof resp & { success?: boolean });
      if (envelope.success === false || envelope.code !== 200) {
        const err = new Error(envelope.message || '提交失败') as Error & { errorCode?: string };
        const inner = envelope.data as { errorCode?: string } | undefined;
        if (inner?.errorCode) err.errorCode = inner.errorCode;
        throw err;
      }
      const data = resp?.data;
      if (!data?.claimNo) {
        throw new Error('报案受理成功但未返回赔案号，请联系核赔员确认，切勿重复提交');
      }
      setResult({ claimId: data.claimId, claimNo: data.claimNo, status: data?.status ?? '' });
      message.success(`报案已受理：${data.claimNo}`);
      form.resetFields();
    } catch (err) {
      // 统一错误通道（resolveErrorCode）：类型收窄与多形态信封解析收口在 shared-utils，
      // 页面只按码分支，避免每页各写一套 `as { errorCode?: string }` 强转而语义漂移
      const errorCode = resolveErrorCode(err);
      const text =
        errorCode === 'SC_CLAIM_POLICY_NOT_ACTIVE'
          ? '保单不存在或未生效，请核对保单号/计划'
          : errorCode === 'SC_CLAIM_OUT_OF_POLICY_PERIOD'
            ? '出险时间不在保单有效期内'
            : errorCode === 'SC_CLAIM_INSURED_MISMATCH'
              ? '当前登录账号与该保单被保人不匹配'
              : errorCode === 'SC_CLAIM_POLICY_AMBIGUOUS'
                ? '该保司保单号对应多张保单，无法确定归属：请改填平台保单号，或联系核赔员确认'
                : errorCode === 'SC_CLAIM_POLICY_NUMBER_CONFLICT'
                  ? '平台保单号与保司保单号指向不同保单，请核对后重新提交'
                  : errorCode === 'SC_CLAIM_POLICY_KEY_REQUIRED'
                    ? '请填写平台保单号或保司保单号（至少一个）'
                    : '提交失败，请稍后重试或联系核赔员';
      message.error(text);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 24 }}>
      <PageHeader title={isAppend ? '追加赔案' : '理赔申请'} />
      {isAppend && reportError && (
        <Alert type="error" showIcon style={{ marginBottom: 16 }} message="无法追加" description={reportError} />
      )}
      {isAppend && report && (
        <Card size="small" title={`正在为报案 ${report.reportNo} 追加赔案`} style={{ marginBottom: 16 }}>
          <Descriptions column={2} size="small">
            <Descriptions.Item label="保单号">{report.policyNo}</Descriptions.Item>
            <Descriptions.Item label="险种">{report.claimType}</Descriptions.Item>
            <Descriptions.Item label="出险时间">{report.outInsureTime}</Descriptions.Item>
            <Descriptions.Item label="已建赔案">
              <Tag>{report.caseCount} 笔</Tag>
            </Descriptions.Item>
          </Descriptions>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            保单、计划、被保人与出险时间由报案继承，此处不接受修改：这些值在事故维度恒定，
            允许逐笔覆盖等于允许把 A 保单的赔付挂到 B 事故上。
          </Typography.Paragraph>
        </Card>
      )}
      {!isAppend && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="门诊小额快赔：≤500 元且材料完整、无风险标记、责任明确可自动通过；其余转人工核赔。"
        />
      )}
      {feeCatalogFailed && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="费用类别目录加载失败"
          description="当前无法选择费用类别，未分类明细将按责任主比例计算并在理算诊断中提示补录；请稍后刷新重试。"
        />
      )}
      {result && (
        <Alert
          type="success"
          showIcon
          style={{ marginBottom: 16 }}
          message={
            isAppend ? `已追加赔案（${result.claimNo}）` : `报案已受理（${result.claimNo}）`
          }
          description={
            <>
              当前状态：<Tag color="blue">{result.status}</Tag>{' '}
              {isAppend
                ? '同一报案下的赔案共用事故上下文，年度免赔与责任限额按同案累计。'
                : '决策建议将在 30 秒内返回，可在「我的报案」查看。'}
              {result.claimId && (
                <Button
                  type="link"
                  onClick={() => navigate(`/progress?claimId=${result.claimId}`)}
                  style={{ paddingLeft: 0 }}
                >
                  查看进度
                </Button>
              )}
            </>
          }
        />
      )}
      <Card title={isAppend ? '赔付信息' : '报案信息'}>
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ claimType: 'OUTPATIENT', items: [{}] as ClaimItemReq[] }}
        >
          <Space size="large" wrap>
            {/*
              事故维度字段：仅「报案」模式渲染。
              追加模式下这些值由报案继承，所以必须**真的不渲染**而不是 hidden ——
              hidden 的表单项仍参与 required 校验，会让用户在没看到这些字段的情况下被"请选择出险日期"拦住。
            */}
            {!isAppend && (
              <>
                <Form.Item
                  name="policyNo"
                  label="保单号"
                  tooltip="平台保单号。与「保司保单号」至少填一个；两者都填时以本字段为准，号码不一致会被拒收。"
                >
                  <Input placeholder="EG20260001" style={{ width: 220 }} />
                </Form.Item>
                {/*
                  保司侧保单号（§16-2）：员工手上常常只有保司给的号（保司来单/保险卡）。
                  选填而非必填——平台号仍是主键，两个都必填只会让日常报案多一次无谓录入；
                  「至少填一个」的约束在提交前统一校验（后端也有同一道闸，返回 400 可见）。
                */}
                <Form.Item
                  name="insurerPolicyNo"
                  label="保司保单号"
                  tooltip="保险卡/保司通知上的保单号（选填）。保司号可能对应多张平台保单，若无法唯一确定，系统会提示改填平台保单号。"
                >
                  <Input placeholder="如 INS-2026-0001" style={{ width: 220 }} />
                </Form.Item>
                <Form.Item
                  name="planId"
                  label="计划ID"
                  rules={[{ required: true, message: '请输入计划ID' }]}
                >
                  <InputNumber placeholder="7" stringMode style={{ width: 160 }} />
                </Form.Item>
                <Form.Item
                  name="insuredIdHash"
                  label="被保人标识"
                  tooltip="服务端按登录主体计算，演示环境填写假名化ID"
                  rules={[{ required: true, message: '请输入被保人标识' }]}
                >
                  <Input placeholder="hash-abc" style={{ width: 220 }} />
                </Form.Item>
                <Form.Item
                  name="outInsureTime"
                  label="出险日期"
                  rules={[{ required: true, message: '请选择出险日期' }]}
                >
                  <DatePicker style={{ width: 180 }} />
                </Form.Item>
              </>
            )}
            <Form.Item name="invoiceCode" label="发票代码">
              <Input placeholder="04400001" style={{ width: 200 }} />
            </Form.Item>
            <Form.Item name="invoiceNo" label="发票号码">
              <Input placeholder="12345678" style={{ width: 200 }} />
            </Form.Item>
            {/*
              PIPL 单独同意留痕（选填）：医疗数据属敏感个人信息，第 29 条要求单独同意。
              选填而非必填——存量渠道/旁路同步没有这个号，做成必填会把存量报案全挡在门口；
              登记后后端不可改写（覆盖 = 证据失效），故不给「修改」入口。
            */}
            <Form.Item
              name="consentRecordId"
              label="同意记录号"
              tooltip="PIPL 单独同意记录号（选填）。仅在已接同意流水时填写；登记后不可修改，填错请走数据更正工单。"
            >
              <Input placeholder="选填，如 CONSENT-2026-0001" style={{ width: 240 }} />
            </Form.Item>
          </Space>

          <Form.List name="items">
            {(fields, { add, remove }) => (
              <>
                <Typography.Text strong>费用明细（至少一项）</Typography.Text>
                {/* antd 已弃用 rowKey 回调的第二参 index；改用 Form.List 自带稳定 key，
                    删行后行标识不漂移（否则输入框焦点/校验态会错位到相邻行）。 */}
                <Table<ClaimItemReq & { __rowKey: number }>
                  rowKey="__rowKey"
                  dataSource={fields.map((f) => ({
                    ...(form.getFieldValue(['items', f.name]) as ClaimItemReq),
                    __rowKey: f.key,
                  }))}
                  pagination={false}
                  size="small"
                  style={{ marginBottom: 12 }}
                  columns={[
                    {
                      title: '项目名称',
                      render: (_, __, i) => (
                        <Form.Item
                          name={[i, 'itemName']}
                          rules={[{ required: true, message: '必填' }]}
                          style={{ marginBottom: 0 }}
                        >
                          <Input placeholder="诊查费" />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '费用类别',
                      width: 170,
                      render: (_, __, i) => (
                        <Form.Item name={[i, 'feeCode']} style={{ marginBottom: 0 }}>
                          <Select
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            placeholder="选择类别"
                            options={feeCategories.map((c) => ({
                              value: c.feeCode,
                              label: c.feeName,
                            }))}
                          />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '类别',
                      width: 140,
                      render: (_, __, i) => (
                        <Form.Item name={[i, 'category']} initialValue="SERVICE" style={{ marginBottom: 0 }}>
                          <Select<ItemCategory>
                            options={[
                              { value: 'DRUG', label: '药品' },
                              { value: 'SERVICE', label: '诊疗服务' },
                              { value: 'EXAM', label: '检查' },
                              { value: 'MATERIAL', label: '材料' },
                            ]}
                          />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '金额（元）',
                      width: 160,
                      render: (_, __, i) => (
                        <Form.Item
                          name={[i, 'amount']}
                          rules={[{ required: true, message: '必填' }]}
                          style={{ marginBottom: 0 }}
                        >
                          <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                        </Form.Item>
                      ),
                    },
                    {
                      title: '操作',
                      width: 70,
                      render: (_, __, i) =>
                        fields.length > 1 ? (
                          <Button type="link" danger onClick={() => remove(i)}>
                            删除
                          </Button>
                        ) : null,
                    },
                  ]}
                />
                <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({ category: 'SERVICE' })}>
                  添加明细
                </Button>
              </>
            )}
          </Form.List>

          <Form.Item style={{ marginTop: 24 }}>
            <Space>
              {/*
                追加模式下按钮必须换文案：用户点的是「往已有报案里加一笔赔付」，
                写着「提交理赔」会让人以为又要新建一次报案（于是回到本页重新报案，把一起事故拆成两个）。
                加载报案失败时一并禁用——事故上下文没拿到就提交，等于盲填赔付归属。
              */}
              <Button
                type="primary"
                htmlType="submit"
                loading={submitting}
                disabled={isAppend && (!report || report.status !== 'REPORTED')}
              >
                {isAppend ? '追加赔案' : '提交理赔'}
              </Button>
              <Button onClick={() => form.resetFields()}>重置</Button>
              {isAppend && (
                <Button type="link" onClick={() => navigate(-1)}>
                  返回
                </Button>
              )}
            </Space>
          </Form.Item>
        </Form>
      </Card>
      <Typography.Paragraph type="secondary" style={{ marginTop: 16 }}>
        {isAppend ? (
          <>
            发票代码/号码是重复报销校验依据（R-FRAUD-07）：同一张发票在同一租户内只能赔付一次，
            跨赔案重复提交会被拒收。出险时间沿用报案登记的事故时间，不按本笔就诊日期修改。
          </>
        ) : (
          <>
            出险日期请按就诊日期填写（医保结算单日期）；发票代码/号码为重复报销校验依据（R-FRAUD-07），请勿填错。
            演示环境当前使用{dayjs().format('YYYY-MM-DD')}的客户端日期。
          </>
        )}
      </Typography.Paragraph>
    </div>
  );
}
