#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""读取 model_output.json，生成 ECharts 可视化仪表盘 HTML。"""
import json

D = json.load(open('/Users/renhui.trh/code/bone/tpa-financial-model/model_output.json'))
cf = D['_cashflow']

def arr_traj(key):
    return [round(x) for x in D[key]['metrics']['arr_traj']]

def rev_year(key):
    return [round(x/1e4) for x in D[key]['rev_year']]

def ebitda_year(key):
    return [round(x/1e4) for x in D[key]['ebitda_year']]

def cost_stack(key):
    pay=[round(x/1e4) for x in D[key]['pay_year']]
    cogs=[round(x/1e4) for x in D[key]['cogs_year']]
    sm=[round(x/1e4) for x in D[key]['sm_year']]
    ga=[round(x/1e4) for x in D[key]['ga_year']]
    return pay,cogs,sm,ga

base_arr = arr_traj('base')
opt_arr = arr_traj('optimistic')
pes_arr = arr_traj('pessimistic')
cash = [round(x/1e4) for x in cf['balances']]
b_pay,b_cogs,b_sm,b_ga = cost_stack('base')
rev = rev_year('base')
eb = ebitda_year('base')

a_month = cf['a_month']  # 0-indexed
b_month = cf['b_month']

HTML = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>TPA 智能理赔 SaaS · 三年财务模型仪表盘</title>
<script src="https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js"></script>
<style>
  :root{--bg:#f7f8fa;--card:#ffffff;--ink:#1f2329;--sub:#6b7280;--line:#e5e7eb;
        --blue:#2563eb;--green:#16a34a;--red:#dc2626;--amber:#d97706;--purple:#7c3aed;}
  *{box-sizing:border-box}
  body{margin:0;font-family:-apple-system,"PingFang SC","Microsoft YaHei",Segoe UI,sans-serif;
       background:var(--bg);color:var(--ink);line-height:1.6}
  .wrap{max-width:1120px;margin:0 auto;padding:28px 20px 60px}
  header h1{font-size:24px;margin:0 0 4px}
  header p{color:var(--sub);margin:0 0 20px;font-size:14px}
  .kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:22px}
  .kpi{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px}
  .kpi .v{font-size:22px;font-weight:700}
  .kpi .l{font-size:12px;color:var(--sub);margin-top:4px}
  .grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}
  .card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px 16px 8px}
  .card h3{margin:0 0 10px;font-size:15px}
  .full{grid-column:1 / -1}
  .chart{width:100%;height:320px}
  .note{font-size:12px;color:var(--sub);margin-top:6px}
  @media(max-width:760px){.kpis{grid-template-columns:repeat(2,1fr)}.grid{grid-template-columns:1fr}}
  .tag{display:inline-block;font-size:11px;padding:1px 7px;border-radius:6px;background:#eef2ff;color:#3730a3;margin-left:6px}
</style>
</head>
<body>
<div class="wrap">
  <header>
    <h1>TPA 医疗健康智能理赔 SaaS · 三年财务模型<span class="tag">per-claim ¥5/单</span></h1>
    <p>单元经济 + 简化三表 + 三情景 + VC 融资节奏 ｜ 币种：人民币（万元）｜ 数据为假设性推演，非投资建议</p>
  </header>

  <div class="kpis">
    <div class="kpi"><div class="v">¥13,824万</div><div class="l">基准 M36 ARR（$1,920万）</div></div>
    <div class="kpi"><div class="v">7.7x</div><div class="l">LTV / CAC（3年封顶）</div></div>
    <div class="kpi"><div class="v">M20</div><div class="l">现金流转正首月</div></div>
    <div class="kpi"><div class="v">~88%</div><div class="l">毛利率</div></div>
  </div>

  <div class="grid">
    <div class="card full"><h3>ARR 增长轨迹（三情景，月度，万元）</h3>
      <div id="c_arr" class="chart"></div>
      <div class="note">基准 ARR 由 M12 的 ¥950万 增至 M36 的 ¥13,824万；乐观/悲观跨度约 4.2 倍。</div>
    </div>

    <div class="card"><h3>成本结构（基准，年度，万元）</h3>
      <div id="c_cost" class="chart"></div>
      <div class="note">人员+S&M 占主导；COGS（单均¥0.60）随理赔量线性增长但占比低。</div>
    </div>

    <div class="card"><h3>年度收入 vs EBITDA（基准，万元）</h3>
      <div id="c_ebitda" class="chart"></div>
      <div class="note">Y2 EBITDA 转正（+40万），Y3 达 +3,570万。</div>
    </div>

    <div class="card full"><h3>现金余额走势（基准，含融资注入，万元）</h3>
      <div id="c_cash" class="chart"></div>
      <div class="note">Seed ¥1,000万@M1；A轮 ¥6,000万@M_AM__；B轮 ¥2亿@M_BM__。资本效率高，Seed 即可撑至现金流转正。</div>
    </div>

    <div class="card"><h3>M36 ARR 情景对比（万元）</h3>
      <div id="c_scen" class="chart"></div>
    </div>

    <div class="card"><h3>单位经济与效率指标</h3>
      <div id="c_metric" class="chart"></div>
      <div class="note">LTV/CAC、NRR、CAC回收期、Rule of 40（Y3）。</div>
    </div>
  </div>
</div>

<script>
var months = Array.from({length:36},function(_,i){return 'M'+(i+1);});
function line(id,series,extra){
  var ch=echarts.init(document.getElementById(id));
  ch.setOption(Object.assign({
    tooltip:{trigger:'axis'},
    legend:{top:0,textStyle:{fontSize:11}},
    grid:{left:48,right:18,top:34,bottom:28},
    xAxis:{type:'category',data:months,axisLabel:{fontSize:10}},
    yAxis:{type:'value',axisLabel:{fontSize:10}},
    series:series
  },extra||{}));
  window.addEventListener('resize',function(){ch.resize();});
}
// ARR 三情景
line('c_arr',[
  {name:'乐观',type:'line',smooth:true,data:__OPT__,lineStyle:{width:2},itemStyle:{color:'#16a34a'}},
  {name:'基准',type:'line',smooth:true,data:__BASE__,lineStyle:{width:2},itemStyle:{color:'#2563eb'}},
  {name:'悲观',type:'line',smooth:true,data:__PES__,lineStyle:{width:2},itemStyle:{color:'#dc2626'}}
]);
// 成本结构堆叠
(function(){
  var ch=echarts.init(document.getElementById('c_cost'));
  ch.setOption({
    tooltip:{trigger:'axis',axisPointer:{type:'shadow'}},
    legend:{top:0,textStyle:{fontSize:11}},
    grid:{left:42,right:14,top:34,bottom:24},
    xAxis:{type:'category',data:['Y1','Y2','Y3']},
    yAxis:{type:'value',axisLabel:{fontSize:10}},
    series:[
      {name:'人员',type:'bar',stack:'t',data:__CPAY__,itemStyle:{color:'#2563eb'}},
      {name:'COGS',type:'bar',stack:'t',data:__CCOGS__,itemStyle:{color:'#7c3aed'}},
      {name:'S&M/CAC',type:'bar',stack:'t',data:__CSM__,itemStyle:{color:'#d97706'}},
      {name:'G&A',type:'bar',stack:'t',data:__CGA__,itemStyle:{color:'#9ca3af'}}
    ]
  });
  window.addEventListener('resize',function(){ch.resize();});
})();
// 收入 vs EBITDA
line('c_ebitda',[
  {name:'收入',type:'bar',data:__REV__,itemStyle:{color:'#2563eb'}},
  {name:'EBITDA',type:'line',data:__EB__,smooth:true,lineStyle:{width:3},itemStyle:{color:'#16a34a'}}
],{xAxis:{type:'category',data:['Y1','Y2','Y3']},yAxis:{type:'value'}});
// 现金余额
(function(){
  var ch=echarts.init(document.getElementById('c_cash'));
  var mark=[];
  mark.push({xAxis:__AMONTH__,label:{formatter:'A轮',fontSize:10},lineStyle:{color:'#d97706',type:'dashed'}});
  mark.push({xAxis:__BMONTH__,label:{formatter:'B轮',fontSize:10},lineStyle:{color:'#7c3aed',type:'dashed'}});
  ch.setOption({
    tooltip:{trigger:'axis'},
    grid:{left:52,right:18,top:20,bottom:28},
    xAxis:{type:'category',data:months,axisLabel:{fontSize:10}},
    yAxis:{type:'value',axisLabel:{fontSize:10}},
    series:[{name:'现金余额',type:'line',smooth:true,data:__CASH__,
      areaStyle:{color:'rgba(37,99,235,0.12)'},lineStyle:{width:2},itemStyle:{color:'#2563eb'},
      markLine:{symbol:'none',data:mark}}]
  });
  window.addEventListener('resize',function(){ch.resize();});
})();
// 情景 ARR 对比
(function(){
  var ch=echarts.init(document.getElementById('c_scen'));
  ch.setOption({
    tooltip:{trigger:'axis',axisPointer:{type:'shadow'}},
    grid:{left:52,right:18,top:20,bottom:28},
    xAxis:{type:'category',data:['悲观','基准','乐观']},
    yAxis:{type:'value',axisLabel:{fontSize:10}},
    series:[{type:'bar',data:[
      {value:__PES36__,itemStyle:{color:'#dc2626'}},
      {value:__BASE36__,itemStyle:{color:'#2563eb'}},
      {value:__OPT36__,itemStyle:{color:'#16a34a'}}],barWidth:'45%'}]
  });
  window.addEventListener('resize',function(){ch.resize();});
})();
// 指标卡
(function(){
  var ch=echarts.init(document.getElementById('c_metric'));
  var data=[
    {name:'LTV/CAC',value:'7.7x',c:'#16a34a'},
    {name:'NRR(Y3)',value:'97%',c:'#2563eb'},
    {name:'CAC回收期',value:'3.6月',c:'#d97706'},
    {name:'Rule of 40',value:'265%*',c:'#7c3aed'}
  ];
  ch.setOption({
    tooltip:{show:false},
    grid:{left:10,right:10,top:10,bottom:10},
    xAxis:{type:'category',show:false,data:data.map(function(d){return d.name;})},
    yAxis:{show:false},
    series:[{type:'bar',barWidth:'60%',data:data.map(function(d){return {value:1,itemStyle:{color:d.c},label:{show:true,position:'inside',formatter:d.value+''+d.name,fontSize:12,color:'#fff'}};})}]
  });
  window.addEventListener('resize',function(){ch.resize();});
})();
</script>
</body>
</html>
"""

def wrap(arr): return json.dumps(arr, ensure_ascii=False)
out = HTML
out = out.replace('__OPT__', wrap(opt_arr)).replace('__BASE__', wrap(base_arr)).replace('__PES__', wrap(pes_arr))
out = out.replace('__CPAY__', wrap(b_pay)).replace('__CCOGS__', wrap(b_cogs)).replace('__CSM__', wrap(b_sm)).replace('__CGA__', wrap(b_ga))
out = out.replace('__REV__', wrap(rev)).replace('__EB__', wrap(eb))
out = out.replace('__CASH__', wrap(cash))
out = out.replace('__AMONTH__', str(a_month)).replace('__BMONTH__', str(b_month))
out = out.replace('__PES36__', str(pes_arr[-1])).replace('__BASE36__', str(base_arr[-1])).replace('__OPT36__', str(opt_arr[-1]))

# 修正 note 文案中的月份（人类可读，+1）
out = out.replace('@M_AM__', 'M'+str(a_month+1)).replace('@M_BM__', 'M'+str(b_month+1))

with open('/Users/renhui.trh/code/bone/tpa-financial-model/TPA-SaaS财务模型-仪表盘.html','w',encoding='utf-8') as f:
    f.write(out)
print("HTML written. bytes=", len(out))
