#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TPA 医疗健康智能理赔 SaaS —— 3 年财务模型引擎
定价模式：按单定价（per-claim），基准 ¥5.00/单
方法：Unit Economics + 三表(简化) + 情景分析 + VC 融资节奏
输出：model_output.json（供 Markdown / HTML 渲染）
"""
import json

# ---------------- 全局假设 ----------------
PRICE = 5.00          # 每单价格（元）
INFRA = 0.60          # 每单变动成本（AI 推理 + 云算力 + 存储），即 COGS/单
SUPPORT_VAR = 50.0    # 每客户每月客户成功/支持成本
TAX_RATE = 0.15       # 实际所得税率（假设前 3 年亏损结转，税≈0，仅作后段提示）
HORIZON = 36
USD = 7.2             # 汇率（仅作等值参考）

# 基准：每月新增客户（36 个月爬坡）
NEW_BASE = [1,1,2,2,3,3,4,4,5,6,7,8,
            9,10,11,12,13,14,15,16,17,18,19,20,
            21,22,23,24,25,26,27,28,29,30,31,32]

# 每客户月均理赔单数（使用深度爬坡）
def claims_schedule(start, end):
    return [start + (end-start)*m/(HORIZON-1) for m in range(HORIZON)]

# 人员（月度，12+12+12）
HEADCOUNT = [6,7,8,9,10,11,12,13,14,15,16,18,
             20,22,24,26,28,30,32,34,36,38,40,42,
             45,47,49,51,53,55,57,59,61,63,65,68]
SALARY = [25000]*12 + [27000]*12 + [30000]*12   # 人均全包月薪（研发+销售+职能混合）
GA = [25000]*12 + [50000]*12 + [90000]*12        # 办公/行政/职能（非薪资）
FIXED_SM = [20000]*12 + [40000]*12 + [60000]*12  # 品牌/市场固定投入（不含 CAC 变动）

# 情景参数
SCEN = {
    'base':       dict(label='基准', mult=1.0,  churn=0.015, cac=80000,  claims=(3000,5000)),
    'optimistic': dict(label='乐观', mult=1.40, churn=0.010, cac=70000,  claims=(3500,6000)),
    'pessimistic':dict(label='悲观', mult=0.60, churn=0.030, cac=95000,  claims=(2500,4000)),
}

def run(scn):
    s = SCEN[scn]
    new = [max(0, round(n*s['mult'])) for n in NEW_BASE]
    claims = claims_schedule(*s['claims'])
    churn = s['churn']; cac = s['cac']
    active=[]; a=0
    rev=[]; cogs=[]; gp=[]; sm=[]; pay=[]; ga=[]; opex=[]; ebitda=[]
    for m in range(HORIZON):
        a = a*(1-churn) + new[m]
        active.append(a)
        cl = claims[m]
        tclaims = a*cl
        revenue = tclaims*PRICE
        c = tclaims*INFRA
        g = revenue - c
        sm_m = new[m]*cac + FIXED_SM[m]
        p = HEADCOUNT[m]*SALARY[m]
        gg = GA[m]
        op = p + sm_m + gg
        e = g - op
        rev.append(revenue); cogs.append(c); gp.append(g)
        sm.append(sm_m); pay.append(p); ga.append(gg); opex.append(op); ebitda.append(e)
    return dict(new=new, active=active, claims=claims, rev=rev, cogs=cogs, gp=gp,
                sm=sm, pay=pay, ga=ga, opex=opex, ebitda=ebitda, churn=churn, cac=cac,
                label=s['label'])

def yearly(series):
    out=[]
    for y in range(3):
        seg = series[y*12:(y+1)*12]
        out.append(sum(seg))
    return out

def month_markers(series_active, series_rev):
    # 关键月份快照：M6/M12/M18/M24/M30/M36
    idx=[5,11,17,23,29,35]
    return [dict(m=i+1, active=series_active[i], rev=series_rev[i]) for i in idx]

def cash_flow(base_model, seed=10_000_000, a_round=60_000_000, b_round=200_000_000):
    """基准情景现金流：Seed 注入后找现金转负月份触发 A 轮，再注 B 轮。"""
    ebitda = base_model['ebitda']
    cash=0; balances=[]; injections=[0]*HORIZON
    a_month=None; b_month=None
    # 先找 Seed-only 下首次转负的月份 -> A 轮触发点
    tmp=seed; first_neg=None
    for m in range(HORIZON):
        tmp += ebitda[m]
        if tmp<0 and first_neg is None:
            first_neg=m
    # A 轮放在首次转负当月（或最早 M13）
    a_month = max(first_neg, 12) if first_neg is not None else 15
    b_month = 30  # 规划中的 B 轮时点
    for m in range(HORIZON):
        inj=0
        if m==0: inj+=seed
        if m==a_month: inj+=a_round
        if m==b_month: inj+=b_round
        injections[m]=inj
        cash += ebitda[m] + inj
        balances.append(cash)
    return dict(balances=balances, injections=injections, a_month=a_month, b_month=b_month,
                seed=seed, a_round=a_round, b_round=b_round)

def metrics(model):
    active=model['active']; claims=model['claims']; rev=model['rev']
    churn=model['churn']; cac=model['cac']
    # ARPU 月度（期末 Y3 近似）
    arpu_y3 = claims[35]*PRICE
    var_per_cust = claims[35]*INFRA + SUPPORT_VAR
    contrib = arpu_y3 - var_per_cust
    gm = contrib/arpu_y3
    # LTV（无限期，含使用深度扩张）—— 上界参考
    ltv_inf = (arpu_y3*gm)/churn
    # LTV（3 年封顶，务实口径）—— 36 个月内期望存活月数
    surv_months = sum((1-churn)**k for k in range(36))  # ≈ 1/churn 但封顶 36
    ltv_cap = arpu_y3*gm*surv_months
    ltv_cac = ltv_cap/cac
    payback = cac/(arpu_y3*gm)
    # NRR（复购基，排除新客）：(1-churn)^12 * (claims 扩张)
    nrr_m12 = (1-churn)**12 * (claims[11]/claims[0])
    nrr_m24 = (1-churn)**12 * (claims[23]/claims[11])
    nrr_m36 = (1-churn)**12 * (claims[35]/claims[23])
    # 盈亏平衡月份（实际口径）
    first_oper = None; first_cash = None
    for m in range(HORIZON):
        op = model['gp'][m] - model['pay'][m] - model['ga'][m] - FIXED_SM[m]
        if first_oper is None and op>0: first_oper=m
        if first_cash is None and model['ebitda'][m]>0: first_cash=m
    # ARR 轨迹（月度）
    arr_traj = [rev[m]*12 for m in range(HORIZON)]
    # Rule of 40 / Burn Multiple / Magic Number（年度）
    rev_y = yearly(rev); eb_y=yearly(model['ebitda']); sm_y=yearly(model['sm'])
    rule40=[]; burn=[]; magic=[]
    for y in range(3):
        g = (rev_y[y]/rev_y[y-1]-1) if y>0 else None
        margin = eb_y[y]/rev_y[y]
        rule40.append((g+margin) if g is not None else None)
        if y==0:
            burn.append(abs(eb_y[y])/max(arr_traj[11],1))
            magic.append(None)
        else:
            burn.append(abs(eb_y[y])/max(arr_traj[y*12]-arr_traj[(y-1)*12],1))
            # Magic: (本年营收-上年营收)/上年 S&M
            magic.append((rev_y[y]-rev_y[y-1])/sm_y[y-1] if sm_y[y-1]>0 else None)
    return dict(arpu_y3=arpu_y3, contrib=contrib, gm=gm, ltv_inf=ltv_inf, ltv_cap=ltv_cap,
                ltv_cac=ltv_cac, payback=payback, nrr=[nrr_m12,nrr_m24,nrr_m36],
                first_oper=first_oper, first_cash=first_cash, arr_traj=arr_traj,
                rule40=rule40, burn=burn, magic=magic)

def break_even(model):
    """运营盈亏平衡（不含获客 CAC）与现金盈亏平衡（含 CAC 增长投入）"""
    claims=model['claims']; churn=model['churn']; cac=model['cac']
    # 取 Y3 稳态参数
    cl=claims[35]; arpu=cl*PRICE
    var=cl*INFRA+SUPPORT_VAR
    contrib=arpu-var
    fixed_opex = HEADCOUNT[35]*SALARY[35] + GA[35] + FIXED_SM[35]
    be_customers_opex = fixed_opex/contrib
    be_mrr_opex = be_customers_opex*arpu
    # 含 CAC：假设稳态月新增 = 期末新增（约 NEW_BASE[-1]*mult）
    new_steady = model['new'][35]
    cac_month = new_steady*cac + FIXED_SM[35]
    fixed_total = fixed_opex + cac_month
    be_customers_cash = fixed_total/contrib
    be_mrr_cash = be_customers_cash*arpu
    return dict(be_customers_opex=be_customers_opex, be_mrr_opex=be_mrr_opex,
                be_customers_cash=be_customers_cash, be_mrr_cash=be_mrr_cash,
                fixed_opex=fixed_opex, contrib=contrib, arpu=arpu)

# ---------------- 执行 ----------------
models={k:run(k) for k in SCEN}
cf = cash_flow(models['base'])
out={}
for k,m in models.items():
    out[k]=dict(
        label=m['label'],
        markers=month_markers(m['active'], m['rev']),
        rev_year=yearly(m['rev']),
        ebitda_year=yearly(m['ebitda']),
        gp_year=yearly(m['gp']),
        opex_year=yearly(m['opex']),
        pay_year=yearly(m['pay']),
        sm_year=yearly(m['sm']),
        ga_year=yearly(m['ga']),
        cogs_year=yearly(m['cogs']),
        arr_m36=m['rev'][35]*12,
        active_m36=m['active'][35],
        metrics=metrics(m),
        be=break_even(m),
    )

out['_cashflow']=cf
out['_globals']=dict(price=PRICE, infra=INFRA, support_var=SUPPORT_VAR, tax=TAX_RATE,
                     usd=USD, horizon=HORIZON, new_base=NEW_BASE, headcount=HEADCOUNT,
                     salary=SALARY, ga=GA, fixed_sm=FIXED_SM)

with open('/Users/renhui.trh/code/bone/tpa-financial-model/model_output.json','w',encoding='utf-8') as f:
    json.dump(out,f,ensure_ascii=False,indent=2)

# ---------- 控制台摘要（自检） ----------
print("=== 基准情景关键指标 ===")
b=out['base']
print(f"M36 活跃客户: {b['active_m36']:.0f}   ARR: ¥{b['arr_m36']/1e6:.1f}M (${b['arr_m36']/USD/1e6:.1f}M)")
print(f"年度收入(Y1/Y2/Y3): " + " / ".join(f"¥{x/1e6:.1f}M" for x in b['rev_year']))
print(f"年度 EBITDA(Y1/Y2/Y3): " + " / ".join(f"¥{x/1e6:.1f}M" for x in b['ebitda_year']))
print(f"Y3 ARPU/月: ¥{b['metrics']['arpu_y3']:.0f}  毛利: {b['metrics']['gm']*100:.1f}%")
print(f"LTV(3年封顶): ¥{b['metrics']['ltv_cap']:.0f}  LTV/CAC: {b['metrics']['ltv_cac']:.1f}x  CAC回收: {b['metrics']['payback']:.1f}月")
print(f"NRR(M12/M24/M36): " + " / ".join(f"{x*100:.0f}%" for x in b['metrics']['nrr']))
print(f"运营盈利首月: M{b['metrics']['first_oper']+1}  现金流转正首月: M{b['metrics']['first_cash']+1}")
print(f"运营盈亏平衡客户数: {b['be']['be_customers_opex']:.0f}  MRR: ¥{b['be']['be_mrr_opex']/1e3:.0f}K")
print(f"现金盈亏平衡客户数: {b['be']['be_customers_cash']:.0f}  MRR: ¥{b['be']['be_mrr_cash']/1e3:.0f}K")
print(f"Rule of 40(Y1/Y2/Y3): " + " / ".join((f"{x*100:.0f}%" if x else "-") for x in b['metrics']['rule40']))
print(f"Burn Multiple(Y1/Y2/Y3): " + " / ".join((f"{x:.2f}x" if x else "-") for x in b['metrics']['burn']))
print(f"Magic Number(Y2/Y3): " + " / ".join((f"{x:.2f}" if x else "-") for x in b['metrics']['magic']))
print(f"现金流: Seed¥{cf['seed']/1e6:.0f}M @M1, A轮¥{cf['a_round']/1e6:.0f}M @M{cf['a_month']+1}, B轮¥{cf['b_round']/1e6:.0f}M @M{cf['b_month']+1}")
print(f"期末现金(Y3末): ¥{cf['balances'][-1]/1e6:.1f}M")
print()
print("=== 情景 M36 ARR ===")
for k in ['optimistic','base','pessimistic']:
    v=out[k]
    print(f"{v['label']}: ARR ¥{v['arr_m36']/1e6:.1f}M  活跃客户 {v['active_m36']:.0f}")
print()
print("JSON written -> model_output.json")
