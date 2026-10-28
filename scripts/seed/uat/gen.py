#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""BONE 对外 UAT / 客户演示种子数据生成器（确定性 · 拟真 · 全模块串联）

设计红线（对齐 doc/uat-demo-data-plan.md §0）：
  A 拟真可读：全部显示名/编码来自受控中文词典，无 UUID/随机串当显示名
  B 幂等确定：固定随机种子 + 固定 ID 段，同一份输入永远产出同一份数据
  C 黄金动线：一条手工策展数据贯穿 IAM→建模→MDM→流程→渠道→订单→物流→支付→集成→AI→看板→理赔
  D 拓扑序加载 + 校验门禁（配合 95_validate.py）
  E 职责分离 SoD：审批人 ≠ 提交人；结算大额双人复核换人
  F 数据老化：所有时间戳由基准日倒推，创建早于审批早于完成
  G 金额守恒：订单总额 = Σ明细小计 + 运费 − 优惠；额度 initial = claimed + frozen + available
  H 状态取值全部来自代码枚举，零幽灵状态
  I PII 最小化：理赔域 insured_id_hash 假名化，手机号/银行卡脱敏

用法:
  python3 gen.py              # 生成 SQL 到 build/（可评审）
  python3 gen.py --apply      # 生成后直接灌库
  python3 gen.py --scale large  # 放大体量（默认 medium）
"""
import os, sys, json, random, hashlib, datetime, argparse

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, "build")
SEED = 20261028
TENANT = 1001
BASE = datetime.datetime(2026, 10, 28, 9, 0, 0)   # 基准日
PW_HASH = "$2a$10$nZLjv4A8i.Q64tYZxrXVTuPQJ.g337OkdOx8rAKnKJL3a2dqdKR8q"  # 123456
COMPANY = "鼎晖智造集团股份有限公司"

# ============================================================ 0. 工具
R = random.Random(SEED)


def dt(days_ago, hour=None, minute=None, jitter_min=0):
    """基准日倒推 days_ago 天；jitter_min 给出分钟级抖动，保证确定性。"""
    d = BASE - datetime.timedelta(days=days_ago)
    if hour is None:
        hour = R.randint(8, 19)
    if minute is None:
        minute = R.randint(0, 59)
    d = d.replace(hour=hour, minute=minute, second=R.randint(0, 59))
    if jitter_min:
        d += datetime.timedelta(minutes=R.randint(0, jitter_min))
    return d.strftime("%Y-%m-%d %H:%M:%S")


def money(x):
    return round(float(x), 2)


def seq(start):
    """确定性自增 ID 发生器。"""
    n = start
    while True:
        yield n
        n += 1


def h(seed_str):
    return hashlib.sha256(str(seed_str).encode()).hexdigest()


def j(obj):
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


TABLES = {}      # table -> (columns, rows)


def add(table, columns, rows):
    TABLES.setdefault(table, (columns, []))
    TABLES[table][1].extend(rows)


# ============================================================ 1. 受控中文词典
SURNAME = [("王", "wang"), ("李", "li"), ("张", "zhang"), ("刘", "liu"), ("陈", "chen"), ("杨", "yang"),
           ("黄", "huang"), ("赵", "zhao"), ("周", "zhou"), ("吴", "wu"), ("徐", "xu"), ("孙", "sun"),
           ("马", "ma"), ("朱", "zhu"), ("胡", "hu"), ("林", "lin"), ("郭", "guo"), ("何", "he"),
           ("高", "gao"), ("罗", "luo"), ("郑", "zheng"), ("梁", "liang"), ("谢", "xie"), ("宋", "song"),
           ("唐", "tang"), ("许", "xu2"), ("韩", "han"), ("冯", "feng"), ("邓", "deng"), ("曹", "cao"),
           ("彭", "peng"), ("曾", "zeng"), ("肖", "xiao"), ("田", "tian"), ("董", "dong"), ("袁", "yuan"),
           ("潘", "pan"), ("蒋", "jiang"), ("蔡", "cai"), ("余", "yu"), ("杜", "du"), ("叶", "ye"),
           ("程", "cheng"), ("苏", "su"), ("魏", "wei"), ("吕", "lv"), ("丁", "ding"), ("任", "ren"),
           ("沈", "shen"), ("姚", "yao"), ("卢", "lu"), ("姜", "jiang2"), ("崔", "cui"), ("钟", "zhong")]
GIVEN = [("伟", "wei"), ("芳", "fang"), ("娜", "na"), ("敏", "min"), ("静", "jing"), ("丽", "li"),
         ("强", "qiang"), ("磊", "lei"), ("军", "jun"), ("洋", "yang"), ("勇", "yong"), ("艳", "yan"),
         ("杰", "jie"), ("娟", "juan"), ("涛", "tao"), ("明", "ming"), ("超", "chao"), ("霞", "xia"),
         ("平", "ping"), ("刚", "gang"), ("建华", "jianhua"), ("文博", "wenbo"), ("思远", "siyuan"),
         ("嘉怡", "jiayi"), ("子轩", "zixuan"), ("浩然", "haoran"), ("梓涵", "zihan"), ("雨欣", "yuxin"),
         ("俊杰", "junje"), ("心怡", "xinyi"), ("宇航", "yuhang"), ("佳怡", "jiayi2"), ("泽宇", "zeyu"),
         ("诗涵", "shihan"), ("一诺", "yinuo"), ("沐辰", "muchen"), ("若曦", "ruoxi"), ("晨曦", "chenxi"),
         ("博文", "bowen"), ("雅静", "yajing"), ("天佑", "tianyou"), ("瑞泽", "ruize"), ("皓轩", "haoxuan"),
         ("欣妍", "xinyan"), ("建国", "jianguo"), ("秀英", "xiuying"), ("桂兰", "guilan"), ("志强", "zhiqiang"),
         ("晓东", "xiaodong"), ("海燕", "haiyan"), ("雪梅", "xuemei"), ("春林", "chunlin")]

PROVINCES = [("上海市", "310000", ["浦东新区", "310115", "徐汇区", "310104", "闵行区", "310112", "嘉定区", "310114"]),
             ("广东省", "440000", ["深圳市", "440300", "广州市", "440100", "东莞市", "441900", "佛山市", "440600"]),
             ("江苏省", "320000", ["苏州市", "320500", "南京市", "320100", "无锡市", "320200", "常州市", "320400"]),
             ("浙江省", "330000", ["杭州市", "330100", "宁波市", "330200", "温州市", "330300", "嘉兴市", "330400"]),
             ("北京市", "110000", ["海淀区", "110108", "朝阳区", "110105", "西城区", "110102", "大兴区", "110115"]),
             ("山东省", "370000", ["青岛市", "370200", "济南市", "370100", "烟台市", "370600"]),
             ("四川省", "510000", ["成都市", "510100", "绵阳市", "510700", "德阳市", "510600"]),
             ("湖北省", "420000", ["武汉市", "420100", "宜昌市", "420500", "襄阳市", "420600"]),
             ("福建省", "350000", ["厦门市", "350200", "福州市", "350100", "泉州市", "350500"]),
             ("湖南省", "430000", ["长沙市", "430100", "株洲市", "430200", "湘潭市", "430300"]),
             ("河南省", "410000", ["郑州市", "410100", "洛阳市", "410300", "新乡市", "410700"]),
             ("安徽省", "340000", ["合肥市", "340100", "芜湖市", "340200"]),
             ("陕西省", "610000", ["西安市", "610100", "宝鸡市", "610300"]),
             ("辽宁省", "210000", ["沈阳市", "210100", "大连市", "210200"]),
             ("天津市", "120000", ["滨海新区", "120116", "南开区", "120104"])]

CITY_WORD = ["瀚海", "云枢", "恒昇", "启元", "中科", "华创", "锐驰", "博远", "鼎新", "同辉", "安捷", "天工",
             "汇通", "本源", "灵犀", "长风", "弘远", "智联", "普瑞", "嘉合", "越洋", "沐光", "川流", "青云",
             "蓝图", "金诚", "立行", "广联", "纳川", "协同", "元启", "德茂", "aspect瑞", "联创", "万象",
             "新宇", "恒信", "智源", "致远", "开源"]
COMPANY_SUFFIX = ["科技有限公司", "智能装备有限公司", "智能制造有限公司", "电子科技有限公司", "自动化技术有限公司",
                  "精密机械有限公司", "信息技术有限公司", "数字科技有限公司", "供应链管理有限公司", "工业设备有限公司"]
SUPPLIER_SUFFIX = ["精密机械有限公司", "电子元件有限公司", "新材料有限公司", "模具制造有限公司", "塑胶制品有限公司",
                   "五金制品有限公司", "包装材料有限公司", "电机有限公司", "传感器有限公司", "线束有限公司"]
INDUSTRIES = ["智能制造", "汽车零部件", "消费电子", "新能源", "医疗器械", "半导体", "物流仓储", "建筑工程",
              "家用电器", "工业自动化", "通信设备", "轨道交通"]

# 商品：品类 → (系列名, 规格模板, 价格区间)
PRODUCT_CAT = [
    ("智能家电", "智能扫地机器人", ["X1", "X2", "Pro", "Max", "S2", "Lite"], (1299, 4299)),
    ("智能家电", "无线吸尘器", ["V10", "V12", "V15", "G5"], (699, 2499)),
    ("智能家电", "空气净化器", ["A3", "A5", "P2000", "P3000"], (999, 3299)),
    ("智能家电", "智能门锁", ["D1", "D3 Pro", "K8", "K9 Max"], (899, 2799)),
    ("智能家电", "智能摄像头", ["C2", "C4", "C6 Pro"], (199, 799)),
    ("智能家电", "反渗透净水器", ["R400", "R600", "R800 Pro"], (1099, 3499)),
    ("智能家电", "超声波加湿器", ["H200", "H400", "H600"], (299, 899)),
    ("智能家电", "声波电动牙刷", ["T3", "T5", "T7 Pro"], (199, 699)),
    ("工业自动化", "边缘计算网关", ["EG-2000", "EG-3000", "EG-5000X"], (1899, 6800)),
    ("工业自动化", "伺服驱动器", ["SD-400", "SD-750", "SD-1500"], (1280, 5400)),
    ("工业自动化", "工业相机", ["IC-500W", "IC-1200W", "IC-2000W"], (2200, 9800)),
    ("工业自动化", "温振传感器", ["VS-100", "VS-200", "VS-300H"], (560, 1800)),
    ("工业自动化", "PLC 控制器", ["PLC-200", "PLC-400", "PLC-800"], (1500, 7200)),
    ("工业自动化", "工业交换机", ["SW-8G", "SW-16G", "SW-24G"], (680, 3200)),
    ("工业自动化", "变频调速器", ["VF-3K", "VF-7K", "VF-15K"], (1100, 4600)),
    ("工业自动化", "视觉检测模组", ["VM-Base", "VM-Pro", "VM-Ultra"], (3600, 12800)),
    ("配件耗材", "HEPA 滤网", ["标准版", "升级版", "除菌版"], (59, 199)),
    ("配件耗材", "主刷滚刷", ["标准版", "防缠绕版"], (39, 129)),
    ("配件耗材", "锂电池组", ["3200mAh", "5200mAh", "6400mAh"], (129, 399)),
    ("配件耗材", "充电底座", ["标准版", "集尘版"], (199, 899)),
]
MATERIAL_CAT = [
    ("原材料", "铝合金型材", ["6061-T6", "6063-T5", "7075-T6"], "KG", (18, 46)),
    ("原材料", "冷轧钢板", ["SPCC 1.0mm", "SPCC 1.5mm", "SPCC 2.0mm"], "KG", (5, 12)),
    ("原材料", "工程塑料粒子", ["ABS", "PC/ABS", "PA66+GF30"], "KG", (12, 38)),
    ("原材料", "硅胶密封件", ["邵氏 50", "邵氏 60", "邵氏 70"], "PCS", (0.4, 3.2)),
    ("原材料", "稀土永磁体", ["N38", "N42", "N52"], "PCS", (8, 66)),
    ("原材料", "铜箔", ["18μm", "35μm", "70μm"], "KG", (62, 98)),
    ("电子元器件", "贴片电阻", ["0402 10K", "0603 4.7K", "0805 100R"], "PCS", (0.01, 0.08)),
    ("电子元器件", "电解电容", ["470μF 25V", "1000μF 35V", "2200μF 50V"], "PCS", (0.3, 2.6)),
    ("电子元器件", "MCU 主控芯片", ["STM32F407", "GD32F450", "ESP32-S3"], "PCS", (12, 68)),
    ("电子元器件", "锂电芯", ["18650 2600mAh", "21700 5000mAh"], "PCS", (8, 26)),
    ("电子元器件", "无刷电机", ["BLDC-2204", "BLDC-3508", "BLDC-5010"], "PCS", (36, 280)),
    ("电子元器件", "激光雷达模组", ["LDS-360", "LDS-360Pro"], "PCS", (128, 420)),
    ("电子元器件", "摄像头模组", ["2MP", "5MP", "8MP 广角"], "PCS", (32, 186)),
    ("电子元器件", "PCB 空板", ["4层 FR-4", "6层 FR-4", "8层 HDI"], "PCS", (6, 58)),
    ("包装辅料", "瓦楞纸箱", ["5层 400×300×200", "5层 500×400×300", "7层 600×500×400"], "PCS", (2, 12)),
    ("包装辅料", "EPE 珍珠棉", ["10mm", "20mm", "30mm"], "M2", (1, 6)),
    ("包装辅料", "说明书", ["A5 铜版", "A5 胶版", "A4 折页"], "PCS", (0.4, 2.4)),
    ("包装辅料", "不干胶标签", ["哑银 40×30", "热敏 60×40"], "PCS", (0.05, 0.4)),
]
ASSET_CAT = [("生产设备", ["数控加工中心", "立式注塑机", "高速贴片机", "激光切割机", "自动化测试台"], (180000, 2600000)),
             ("IT 设备", ["机架服务器", "研发工作站", "笔记本电脑", "核心交换机", "存储阵列"], (6000, 320000)),
             ("办公家具", ["会议桌", "职员办公桌椅", "文件柜", "接待沙发"], (1200, 46000)),
             ("运输工具", ["商务车", "厢式货车", "电动叉车"], (68000, 460000))]
POSITIONS = ["总经理", "事业部总监", "部门经理", "科室主管", "高级工程师", "工程师", "助理工程师", "专员",
             "高级专员", "主管会计师", "数据分析师", "产品经理", "项目经理", "采购主管", "质量工程师",
             "工艺工程师", "仓储主管", "客服专员", "内审专员", "运维工程师"]
CHANNELS = [("TAOBAO", "淘宝旗舰店", "tb", "TAOBAO"), ("JD", "京东自营店", "jd", "JD"),
            ("DOUYIN", "抖音小店", "dy", "DOUYIN"), ("PDD", "拼多多官方店", "pdd", "PDD"),
            ("WECHAT_MINI", "微信小程序商城", "wx", "WECHAT_MINI"), ("OFFICIAL", "官网商城", "web", "OFFICIAL")]
WAREHOUSES = [("WH-SH-PD", "上海浦东中心仓"), ("WH-GD-SZ", "深圳华南仓"), ("WH-JS-SZ", "苏州华东仓")]
LOGISTICS = ["顺丰速运", "京东物流", "中通快递", "圆通速递", "德邦快递", "韵达快递"]

# ============================================================ 2. 规模
SCALE = {"medium": dict(customer=500, supplier=200, material=800, product=500, project=40,
                        employee=200, asset=100, buyer=60, account=200, order=2000,
                        wf_instance=150, claim=150, exec_log=800),
         "large": dict(customer=1200, supplier=500, material=2000, product=1200, project=80,
                       employee=400, asset=300, buyer=150, account=400, order=6000,
                       wf_instance=400, claim=400, exec_log=2400)}


# ============================================================ 3. 各模块构建
def build_tenant():
    cols = ["id", "name", "code", "level", "status", "admin_email", "max_accounts", "max_roles",
            "created_by", "updated_by", "created_at", "updated_at", "deleted", "version",
            "allocated_accounts", "allocated_roles"]
    add("iam_tenant", cols, [(TENANT, COMPANY, "DEMO", 1, 1, "admin@dinghui-mfg.example.com",
                              500, 100, 1, 1, dt(720), dt(1), 0, 1, 0, 0)])
    # 平台基线租户 0 保持不动；仅为演示补充租户级配置
    cfg = ["id", "tenant_id", "config_key", "config_value", "description", "config_type", "encrypted",
           "created_by", "updated_by", "created_at", "updated_at", "deleted", "version"]
    rows = []
    sid = seq(280001)
    for k, v, d, t in [
        ("demo.company_name", COMPANY, "演示企业全称", "SYSTEM"),
        ("demo.fiscal_year", "2026", "当前财年", "SYSTEM"),
        ("demo.base_currency", "CNY", "本位币（ISO 4217）", "SYSTEM"),
        ("masterdata.approval.required", "true", "主数据发布必须走审批流", "FEATURE"),
        ("order.auto_ship_after_pay_hours", "24", "支付后自动发货时限（小时）", "SERVICE"),
        ("inventory.safety_stock_alert", "true", "低于安全库存触发补货预警", "FEATURE"),
        ("claim.pii_masking", "strict", "理赔域 PII 强制假名化", "SYSTEM"),
    ]:
        i = next(sid)
        rows.append((i, TENANT, k, v, d, t, 0, 1, 1, dt(180), dt(3), 0, 1))
    add("sys_config", cfg, rows)
    # 告警规则
    alc = ["id", "tenant_id", "name", "description", "metric_name", "threshold_value", "alert_level",
           "notification_channels", "enabled", "created_by", "updated_by", "created_at", "updated_at",
           "deleted", "version"]
    rows = []
    sid = seq(281001)
    for name, metric, th, lv, desc in [
        ("库存低于安全库存", "inventory.available_qty", 20, "WARNING", "可用库存低于安全库存阈值时触发补货提醒"),
        ("订单支付超时", "order.pay_wait_minutes", 120, "ERROR", "订单创建超过 2 小时仍未支付"),
        ("主数据审批积压", "mdm.pending_approval_count", 30, "WARNING", "待审批主数据记录积压超过阈值"),
    ]:
        i = next(sid)
        rows.append((i, TENANT, name, desc, metric, th, lv, j(["IN_APP", "WORK_WECHAT"]), 1,
                     1, 1, dt(150), dt(5), 0, 1))
    add("sys_alert_rule", alc, rows)
    # 定时任务
    stc = ["id", "tenant_id", "name", "cron", "handler", "status", "last_run_at", "next_run_at",
           "created_at", "updated_at", "deleted", "version"]
    rows = []
    sid = seq(282001)
    for name, cron, handler in [
        ("渠道订单同步", "0 */1 * * * ?", "intFlowHandler#syncChannelOrder"),
        ("库存快照归档", "0 0 2 * * ?", "inventoryHandler#archiveSnapshot"),
    ]:
        i = next(sid)
        rows.append((i, TENANT, name, cron, handler, "ENABLED", dt(0, 2, 0), dt(0, 3, 0),
                     dt(200), dt(2), 0, 1))
    add("sys_schedule_task", stc, rows)


def build_org():
    """组织树 + 账号 + 角色 + 授权。返回 (dept_ids, accounts[{id,name,dept,role}])"""
    # ---- 部门 ----
    dcols = ["id", "tenant_id", "name", "parent_id", "order_no", "status", "created_by", "updated_by",
             "created_at", "updated_at", "deleted", "version"]
    root = 11001
    l1 = [
        ("总裁办公室", ["战略投资部", "法务合规部", "行政后勤部"]),
        ("智能制造事业部", ["生产计划科", "装配车间", "机加工车间", "质量管理科", "设备维护科", "工艺工程科"]),
        ("新零售事业部", ["淘宝运营组", "京东运营组", "抖音直播组", "拼多多运营组", "小程序商城组", "会员运营组"]),
        ("供应链中心", ["采购管理科", "供应商管理科", "仓储物流科", "配送调度科", "库存控制科"]),
        ("财务共享中心", ["总账会计科", "应收应付科", "成本核算科", "资金管理科", "税务管理科"]),
        ("研发中心", ["智能家电研究所", "工业自动化研究所", "嵌入式软件组", "结构设计组", "测试验证组"]),
        ("人力资源中心", ["招聘管理科", "薪酬绩效科", "培训发展科", "员工关系科"]),
        ("信息技术中心", ["平台架构组", "应用开发组", "数据治理组", "运维安全组"]),
        ("市场营销中心", ["品牌传播科", "市场洞察科", "渠道拓展科", "数字营销科"]),
        ("客户服务与成功部", ["售前咨询组", "售后服务组", "客户成功组", "投诉处理组"]),
        ("内部审计与合规部", ["内控审计科", "数据安全科"]),
    ]
    rows = [(root, TENANT, COMPANY, None, 0, 1, 1, 1, dt(720), dt(60), 0, 1)]
    did = seq(11002)
    dept_names = {}
    for oi, (pname, children) in enumerate(l1, start=1):
        pid = next(did)
        rows.append((pid, TENANT, pname, root, oi, 1, 1, 1, dt(700), dt(50), 0, 1))
        dept_names[pid] = pname
        for ci, cname in enumerate(children, start=1):
            cid = next(did)
            rows.append((cid, TENANT, cname, pid, ci, 1, 1, 1, dt(690), dt(40), 0, 1))
            dept_names[cid] = "%s / %s" % (pname, cname)
    add("iam_dept", dcols, rows)
    dept_ids = sorted(dept_names)

    # ---- 角色 ----
    rcols = ["id", "tenant_id", "name", "code", "type", "description", "parent_role_id", "created_by",
             "updated_by", "created_at", "updated_at", "deleted", "version"]
    roles = [
        ("租户总管", "DEMO_TENANT_OWNER", "租户内最高权限，负责租户级配置与授权"),
        ("组织管理员", "DEMO_ORG_ADMIN", "维护组织架构、账号与角色分配"),
        ("主数据管家", "DEMO_MDM_STEWARD", "负责主数据清洗、去重与质量治理"),
        ("主数据审批人", "DEMO_MDM_APPROVER", "主数据发布前的评审与批准（SoD：不得为提交人）"),
        ("流程发起人", "DEMO_WF_INITIATOR", "发起报销/采购/请假/主数据变更流程"),
        ("流程审批人", "DEMO_WF_APPROVER", "审批在途流程任务（SoD：不得为发起人）"),
        ("财务专员", "DEMO_FINANCE", "应收应付、成本核算与资金结算"),
        ("业务专员", "DEMO_SALES", "客户、订单与渠道日常运营"),
        ("审计员", "DEMO_AUDITOR", "只读审计，可查看全部操作留痕"),
        ("低代码配置员", "DEMO_LOWCODE", "业务建模与页面配置、扩展点编排"),
        ("运维工程师", "DEMO_OPS", "集成流、定时任务与运行监控"),
        ("只读访客", "DEMO_READONLY", "对外演示只读账号，不可写入"),
    ]
    rrows, rid_gen = [], seq(121001)
    role_id = {}
    for name, code, desc in roles:
        i = next(rid_gen)
        role_id[code] = i
        rrows.append((i, TENANT, name, code, 1, desc, None, 1, 1, dt(700), dt(30), 0, 1))
    add("iam_role", rcols, rrows)

    # ---- 权限绑定（复用平台已有 108 个权限码）----
    import _conf
    c = _conf.connect()
    cur = c.cursor()
    cur.execute("SELECT id, code FROM iam_permission WHERE deleted=0 ORDER BY id")
    perms = cur.fetchall()
    c.close()
    by_prefix = {}
    for pid, pcode in perms:
        by_prefix.setdefault(pcode.split(":")[0], []).append(pid)
    # 角色 → 权限域映射（有域就用域，没域退化为全部只读类）
    role_scope = {
        "DEMO_TENANT_OWNER": None,  # 全部
        "DEMO_ORG_ADMIN": ["iam"],
        "DEMO_MDM_STEWARD": ["masterdata", "mdm"],
        "DEMO_MDM_APPROVER": ["masterdata", "mdm", "workflow"],
        "DEMO_WF_INITIATOR": ["workflow", "wf"],
        "DEMO_WF_APPROVER": ["workflow", "wf", "masterdata"],
        "DEMO_FINANCE": ["finance", "settle", "commerce"],
        "DEMO_SALES": ["commerce", "order", "customer", "masterdata"],
        "DEMO_AUDITOR": ["audit", "system"],
        "DEMO_LOWCODE": ["metadata", "generator", "extension"],
        "DEMO_OPS": ["integration", "system", "generator"],
        "DEMO_READONLY": ["iam"],
    }
    rprows, rpid = [], seq(122001)
    for code, rid in role_id.items():
        scope = role_scope.get(code)
        if scope is None:
            sel = [p[0] for p in perms]
        else:
            sel = []
            for s in scope:
                sel.extend(by_prefix.get(s, []))
            if not sel:
                sel = [p[0] for p in perms if p[1].endswith(":read")] or [perms[0][0]]
        for pid in sel:
            rprows.append((next(rpid), rid, pid, dt(700)))
    add("iam_role_permission", ["id", "role_id", "permission_id", "created_at"], rprows)

    # ---- 账号 ----
    acols = ["id", "tenant_id", "username", "password_hash", "email", "phone", "real_name", "avatar_url",
             "dept_id", "status", "is_admin", "last_login_at", "last_login_ip", "login_fail_count",
             "locked_at", "password_updated_at", "created_by", "updated_by", "created_at", "updated_at",
             "deleted", "version"]
    arows, arows_rel = [], []
    aid = seq(120001)
    arid = seq(123001)
    accounts = []
    used_uname = set()
    n_account = SCALE[SCALE_NAME]["account"]

    def mk_user(idx, surname, given, dept_id, force_roles=None, is_admin=0, uname=None):
        uid = next(aid)
        s_cn, s_py = surname
        g_cn, g_py = given
        base = "%s.%s" % (s_py.rstrip("2"), g_py.rstrip("2"))
        uname = uname or base
        k = 2
        while uname in used_uname:
            uname = "%s%d" % (base, k)
            k += 1
        used_uname.add(uname)
        email = "%s@dinghui-mfg.example.com" % uname.replace(".", "_")
        phone = "1%d%09d" % (R.choice([3, 5, 7, 8, 9]), R.randint(0, 999999999))
        display_phone = phone[:3] + "****" + phone[7:]
        created = dt(R.randint(200, 720))
        arows.append((uid, TENANT, uname, PW_HASH, email, display_phone, s_cn + g_cn, None, dept_id, 1,
                      is_admin, dt(R.randint(0, 14), 9, 30), "10.16.%d.%d" % (R.randint(0, 60), R.randint(2, 250)),
                      0, None, dt(R.randint(30, 200)), 1, 1, created, dt(R.randint(0, 30)), 0, 1))
        if force_roles:
            rs = force_roles
        else:
            rs = [R.choice(["DEMO_SALES", "DEMO_WF_INITIATOR", "DEMO_MDM_STEWARD", "DEMO_FINANCE",
                            "DEMO_LOWCODE", "DEMO_OPS", "DEMO_READONLY", "DEMO_WF_APPROVER"])]
        for rc in rs:
            arows_rel.append((next(arid), TENANT, uid, role_id[rc], created))
        accounts.append(dict(id=uid, name=s_cn + g_cn, dept=dept_id, dept_name=dept_names.get(dept_id, ""),
                             roles=rs, uname=uname))
        return accounts[-1]

    # 黄金动线关键人物（固定，保证演示可复述）
    # CEO 兼租户总管：固定用户名 tenant_admin，便于对外演示登录复述
    zhang = mk_user(0, ("张", "zhang"), ("伟", "wei"), 11002, ["DEMO_TENANT_OWNER"], 1,
                    uname="tenant_admin")
    li = mk_user(0, ("李", "li"), ("静", "jing"), 11012, ["DEMO_MDM_STEWARD"])               # 主数据管家
    wang = mk_user(0, ("王", "wang"), ("芳", "fang"), 11031, ["DEMO_MDM_APPROVER", "DEMO_WF_APPROVER"])
    chen = mk_user(0, ("陈", "chen"), ("晨", "chen2"), 11018, ["DEMO_SALES"])                # 业务专员
    zhao = mk_user(0, ("赵", "zhao"), ("敏", "min"), 11028, ["DEMO_FINANCE"])                # 财务
    huang = mk_user(0, ("黄", "huang"), ("磊", "lei"), 11043, ["DEMO_LOWCODE"])             # 低代码
    sun = mk_user(0, ("孙", "sun"), ("涛", "tao"), 11047, ["DEMO_OPS"])                      # 运维
    zhou = mk_user(0, ("周", "zhou"), ("雅静", "yajing"), 11056, ["DEMO_AUDITOR"])           # 审计
    GOLDEN.update(ceo=zhang, steward=li, approver=wang, sales=chen, finance=zhao,
                  lowcode=huang, ops=sun, auditor=zhou)
    # 关键人物所在部门偏好
    for _ in range(n_account - len(accounts)):
        mk_user(0, R.choice(SURNAME), R.choice(GIVEN), R.choice(dept_ids))
    add("iam_account", acols, arows)
    add("iam_account_role", ["id", "tenant_id", "account_id", "role_id", "created_at"], arows_rel)
    return dept_ids, accounts


def build_dict():
    """系统字典：受控值域 + GB/T 2260 行政区划级联 + ISO 4217 币种"""
    tcols = ["id", "tenant_id", "code", "name", "category", "module_code", "enum_class", "max_depth",
             "value_type", "value_regex", "code_segments", "description", "builtin", "editable",
             "sort", "status", "created_at", "updated_at", "deleted", "version"]
    icols = ["id", "tenant_id", "type_code", "code", "label", "value", "enum_name", "tag_type", "i18n_key",
             "external_code", "effective_from", "effective_to", "is_default", "sort", "status",
             "description", "created_at", "updated_at", "deleted", "version"]
    hcols = ["id", "tenant_id", "type_code", "hierarchy_code", "code", "parent_code", "path", "level",
             "sort", "created_at", "updated_at", "deleted", "version"]
    trows, irows, hrows = [], [], []
    tid, iid, hid = seq(200001), seq(200101), seq(200501)
    spec = [
        ("gender", "性别", "ENUM", "system", "STRING", 0, None, [("M", "男", "M"), ("F", "女", "F"), ("U", "未知", "U")]),
        ("region", "行政区划", "CASCADE", "system", "STRING", 3, "2,2,2", None),
        ("currency", "币种", "LIST", "system", "STRING", 0, None,
         [("CNY", "人民币", "CNY"), ("USD", "美元", "USD"), ("EUR", "欧元", "EUR"), ("HKD", "港币", "HKD"),
          ("JPY", "日元", "JPY"), ("GBP", "英镑", "GBP")]),
        ("customer_level", "客户等级", "LIST", "masterdata", "STRING", 0, None,
         [("A", "战略客户", "A"), ("B", "重点客户", "B"), ("C", "普通客户", "C"), ("D", "观察客户", "D")]),
        ("supplier_level", "供应商等级", "LIST", "masterdata", "STRING", 0, None,
         [("STRATEGIC", "战略供应商", "STRATEGIC"), ("CORE", "核心供应商", "CORE"),
          ("NORMAL", "合格供应商", "NORMAL"), ("PROBATION", "试用供应商", "PROBATION")]),
        ("material_category", "物料分类", "CASCADE", "masterdata", "STRING", 2, "2,2", None),
        ("uom", "计量单位", "LIST", "masterdata", "STRING", 0, None,
         [("PCS", "个", "PCS"), ("KG", "千克", "KG"), ("M", "米", "M"), ("M2", "平方米", "M2"),
          ("SET", "套", "SET"), ("BOX", "箱", "BOX"), ("TON", "吨", "TNE")]),
        ("channel_type", "渠道类型", "ENUM", "commerce", "STRING", 0, None,
         [("TAOBAO", "淘宝", "TAOBAO"), ("JD", "京东", "JD"), ("DOUYIN", "抖音", "DOUYIN"),
          ("PDD", "拼多多", "PDD"), ("WECHAT_MINI", "微信小程序", "WECHAT_MINI"), ("OFFICIAL", "官网商城", "OFFICIAL")]),
        ("order_status", "订单状态", "ENUM", "commerce", "STRING", 0, None,
         [("CREATED", "已创建", "CREATED"), ("PAID", "已支付", "PAID"), ("SHIPPED", "已发货", "SHIPPED"),
          ("DELIVERED", "已送达", "DELIVERED"), ("CANCELLED", "已取消", "CANCELLED")]),
        ("shipment_status", "物流状态", "ENUM", "commerce", "STRING", 0, None,
         [("CREATED", "已创建", "CREATED"), ("SHIPPED", "已发货", "SHIPPED"), ("IN_TRANSIT", "运输中", "IN_TRANSIT"),
          ("SIGNED", "已签收", "SIGNED"), ("FAILED", "异常", "FAILED")]),
        ("payment_status", "支付状态", "ENUM", "commerce", "STRING", 0, None,
         [("PENDING", "待支付", "PENDING"), ("PAYING", "支付中", "PAYING"), ("SUCCESS", "支付成功", "SUCCESS"),
          ("FAILED", "支付失败", "FAILED")]),
        ("approval_result", "审批结论", "ENUM", "workflow", "STRING", 0, None,
         [("APPROVED", "通过", "APPROVED"), ("REJECTED", "驳回", "REJECTED"), ("WITHDRAWN", "撤回", "WITHDRAWN")]),
        ("mdm_record_status", "主数据记录状态", "ENUM", "masterdata", "STRING", 0, None,
         [("DRAFT", "草稿", "DRAFT"), ("PENDING_APPROVAL", "待审批", "PENDING_APPROVAL"),
          ("APPROVED", "审批通过", "APPROVED"), ("PUBLISHED", "已发布", "PUBLISHED"),
          ("SUPERSEDED", "已被新版本取代", "SUPERSEDED"), ("ARCHIVED", "已归档", "ARCHIVED")]),
        ("employment_type", "用工形式", "LIST", "masterdata", "STRING", 0, None,
         [("REGULAR", "正式员工", "REGULAR"), ("PROBATION", "试用期", "PROBATION"),
          ("INTERN", "实习生", "INTERN"), ("OUTSOURCE", "外包驻场", "OUTSOURCE")]),
    ]
    for sort, (code, name, cat, mod, vt, depth, seg, items) in enumerate(spec, start=1):
        trows.append((next(tid), TENANT, code, name, cat, mod, None, depth, vt, None, seg,
                      "%s（%s）" % (name, "级联" if cat == "CASCADE" else "平铺"), 0, 1, sort, 1,
                      dt(700), dt(20), 0, 1))
        if items:
            for s2, (ic, lab, val) in enumerate(items, start=1):
                tag = ("success" if ic in ("A", "STRATEGIC", "PUBLISHED", "SUCCESS", "APPROVED", "DELIVERED", "SIGNED")
                       else "error" if ic in ("D", "PROBATION", "CANCELLED", "FAILED", "REJECTED") else "default")
                irows.append((next(iid), TENANT, code, ic, lab, val, ic, tag, None,
                              val if code in ("currency", "uom") else None,
                              None, None, 1 if s2 == 1 else 0, s2, 1, None, dt(700), dt(20), 0, 1))
    # 行政区划级联（GB/T 2260 片段）
    for pi, (pname, pcode, cities) in enumerate(PROVINCES, start=1):
        irows.append((next(iid), TENANT, "region", pcode, pname, pcode, None, "default", None, "CN-" + pcode[:2],
                      None, None, 0, pi, 1, None, dt(700), dt(20), 0, 1))
        hrows.append((next(hid), TENANT, "region", "DEFAULT", pcode, None, "/%s/" % pcode, 1, pi,
                      dt(700), dt(20), 0, 1))
        for ci in range(0, len(cities), 2):
            cname, ccode = cities[ci], cities[ci + 1]
            irows.append((next(iid), TENANT, "region", ccode, cname, ccode, None, "default", None, None,
                          None, None, 0, ci // 2 + 1, 1, None, dt(700), dt(20), 0, 1))
            hrows.append((next(hid), TENANT, "region", "DEFAULT", ccode, pcode, "/%s/%s/" % (pcode, ccode), 2,
                          ci // 2 + 1, dt(700), dt(20), 0, 1))
    # 物料分类级联
    for pi, top in enumerate(["原材料", "电子元器件", "包装辅料"], start=1):
        tc = "M%d" % pi
        irows.append((next(iid), TENANT, "material_category", tc, top, top, None, "default", None, None,
                      None, None, 0, pi, 1, None, dt(700), dt(20), 0, 1))
        hrows.append((next(hid), TENANT, "material_category", "DEFAULT", tc, None, "/%s/" % tc, 1, pi,
                      dt(700), dt(20), 0, 1))
    add("sys_dict_type", tcols, trows)
    add("sys_dict_item", icols, irows)
    add("sys_dict_hierarchy", hcols, hrows)


def build_metadata():
    """meta_entity / meta_field：8 个业务对象（建模 → MDM → 出码 的源头）"""
    ecols = ["id", "tenant_id", "name", "code", "display_name", "description", "table_name", "type",
             "delivery_mode", "status", "module_id", "scope", "template_id", "is_builtin", "icon",
             "sort_order", "row_policies", "created_by", "updated_by", "created_at", "updated_at",
             "deleted", "version", "auto_number"]
    fcols = ["id", "tenant_id", "entity_id", "module_id", "name", "code", "display_name", "type", "length",
             "numeric_precision", "is_required", "is_unique", "is_pk", "is_indexed", "default_value",
             "enum_values", "enum_source", "validation_rules", "data_classification", "is_pii",
             "sensitivity_level", "data_steward", "business_term", "source_system", "comment",
             "sort_order", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version",
             "physical_column"]
    ents = [
        ("客户", "customer", "客户", "客户主数据：统一客户视图与授信", "mdm_customer", 1, 1,
         [("客户名称", "name", "STRING", 200, 1, 0, "PUBLIC", 0, "L1", "客户全称，工商注册名"),
          ("统一社会信用代码", "unifiedSocialCreditCode", "STRING", 18, 1, 1, "INTERNAL", 0, "L2", "GB 32100-2015"),
          ("联系人", "contact", "STRING", 50, 1, 0, "INTERNAL", 1, "L2", "主要业务联系人"),
          ("联系电话", "phone", "STRING", 20, 1, 0, "CONFIDENTIAL", 1, "L3", "脱敏存储"),
          ("所属区域", "regionCode", "STRING", 12, 1, 0, "PUBLIC", 0, "L1", "GB/T 2260"),
          ("客户等级", "customerLevel", "ENUM", 2, 1, 0, "PUBLIC", 0, "L1", "A/B/C/D"),
          ("授信额度", "creditLimit", "DECIMAL", 18, 0, 0, "CONFIDENTIAL", 0, "L3", "单位：元"),
          ("所属行业", "industry", "STRING", 50, 0, 0, "PUBLIC", 0, "L1", "")]),
        ("供应商", "supplier", "供应商", "供应商主数据与资质", "mdm_supplier", 1, 1,
         [("供应商名称", "name", "STRING", 200, 1, 0, "PUBLIC", 0, "L1", ""),
          ("统一社会信用代码", "unifiedSocialCreditCode", "STRING", 18, 1, 1, "INTERNAL", 0, "L2", ""),
          ("联系人", "contact", "STRING", 50, 1, 0, "INTERNAL", 1, "L2", ""),
          ("联系电话", "phone", "STRING", 20, 1, 0, "CONFIDENTIAL", 1, "L3", ""),
          ("供应商等级", "supplierLevel", "ENUM", 20, 1, 0, "PUBLIC", 0, "L1", "战略/核心/合格/试用"),
          ("资质状态", "qualificationStatus", "ENUM", 20, 1, 0, "INTERNAL", 0, "L2", "有效/待审/失效"),
          ("供货周期", "leadTimeDays", "INT", 11, 0, 0, "PUBLIC", 0, "L1", "单位：天")]),
        ("物料", "material", "物料", "生产物料与采购件", "mdm_material", 1, 1,
         [("物料名称", "name", "STRING", 200, 1, 0, "PUBLIC", 0, "L1", ""),
          ("物料分类", "materialCategory", "STRING", 50, 1, 0, "PUBLIC", 0, "L1", ""),
          ("规格型号", "spec", "STRING", 100, 1, 0, "PUBLIC", 0, "L1", ""),
          ("计量单位", "unit", "STRING", 10, 1, 0, "PUBLIC", 0, "L1", ""),
          ("标准单价", "unitPrice", "DECIMAL", 18, 0, 0, "INTERNAL", 0, "L2", ""),
          ("安全库存", "safetyStock", "INT", 11, 0, 0, "PUBLIC", 0, "L1", "")]),
        ("商品", "product", "商品", "对外销售商品（全渠道上架主体）", "mdm_product", 1, 1,
         [("商品名称", "name", "STRING", 200, 1, 0, "PUBLIC", 0, "L1", ""),
          ("商品品类", "productCategory", "STRING", 50, 1, 0, "PUBLIC", 0, "L1", ""),
          ("品牌", "brand", "STRING", 50, 1, 0, "PUBLIC", 0, "L1", ""),
          ("型号", "model", "STRING", 50, 1, 0, "PUBLIC", 0, "L1", ""),
          ("计量单位", "unit", "STRING", 10, 1, 0, "PUBLIC", 0, "L1", ""),
          ("零售价", "listPrice", "DECIMAL", 18, 0, 0, "PUBLIC", 0, "L1", ""),
          ("成本价", "costPrice", "DECIMAL", 18, 0, 0, "CONFIDENTIAL", 0, "L3", ""),
          ("质保月数", "warrantyMonths", "INT", 11, 0, 0, "PUBLIC", 0, "L1", "")]),
        ("项目", "project", "项目", "研发与技改项目", "mdm_project", 1, 1,
         [("项目名称", "name", "STRING", 200, 1, 0, "PUBLIC", 0, "L1", ""),
          ("项目经理", "projectManager", "STRING", 50, 1, 0, "INTERNAL", 1, "L2", ""),
          ("开始日期", "startDate", "DATE", 10, 1, 0, "PUBLIC", 0, "L1", ""),
          ("计划完工", "endDate", "DATE", 10, 1, 0, "PUBLIC", 0, "L1", ""),
          ("预算金额", "budget", "DECIMAL", 18, 0, 0, "CONFIDENTIAL", 0, "L3", ""),
          ("进度", "progress", "INT", 11, 0, 0, "PUBLIC", 0, "L1", "百分比")]),
        ("员工", "employee", "员工", "员工主数据（与 IAM 账号同源）", "mdm_employee", 1, 1,
         [("姓名", "name", "STRING", 100, 1, 0, "INTERNAL", 1, "L2", ""),
          ("工号", "employeeNo", "STRING", 20, 1, 1, "INTERNAL", 0, "L2", ""),
          ("所属部门", "deptName", "STRING", 100, 1, 0, "INTERNAL", 0, "L2", ""),
          ("岗位", "position", "STRING", 50, 1, 0, "INTERNAL", 0, "L2", ""),
          ("入职日期", "hireDate", "DATE", 10, 1, 0, "INTERNAL", 0, "L2", ""),
          ("用工形式", "employmentType", "ENUM", 20, 1, 0, "INTERNAL", 0, "L2", "")]),
        ("资产", "asset", "资产", "固定资产台账", "mdm_asset", 1, 1,
         [("资产名称", "name", "STRING", 200, 1, 0, "PUBLIC", 0, "L1", ""),
          ("资产编号", "assetNo", "STRING", 30, 1, 1, "INTERNAL", 0, "L2", ""),
          ("资产类别", "assetCategory", "STRING", 50, 1, 0, "PUBLIC", 0, "L1", ""),
          ("原值", "originalValue", "DECIMAL", 18, 0, 0, "CONFIDENTIAL", 0, "L3", ""),
          ("净值", "netValue", "DECIMAL", 18, 0, 0, "CONFIDENTIAL", 0, "L3", ""),
          ("存放地点", "location", "STRING", 100, 0, 0, "INTERNAL", 0, "L2", ""),
          ("保管人", "custodian", "STRING", 50, 0, 0, "INTERNAL", 1, "L2", "")]),
        ("渠道买家", "channel_buyer", "渠道买家", "各渠道下单买家（昵称假名化）", "mdm_channel_buyer", 1, 1,
         [("买家昵称", "name", "STRING", 100, 1, 0, "INTERNAL", 1, "L2", ""),
          ("渠道编码", "channelCode", "STRING", 30, 1, 0, "PUBLIC", 0, "L1", ""),
          ("会员等级", "memberLevel", "ENUM", 20, 1, 0, "PUBLIC", 0, "L1", ""),
          ("注册省份", "province", "STRING", 50, 0, 0, "PUBLIC", 0, "L1", ""),
          ("累计订单数", "totalOrders", "INT", 11, 0, 0, "PUBLIC", 0, "L1", "")]),
    ]
    erows, frows = [], []
    eid, fid = seq(210001), seq(211001)
    ent_ids = {}
    steward = GOLDEN["steward"]["id"]
    for so, (name, code, disp, desc, tbl, tp, st, fields) in enumerate(ents, start=1):
        i = next(eid)
        ent_ids[code] = i
        erows.append((i, TENANT, name, code, disp, desc, tbl, tp, 0, st, None, "TENANT", None, 0,
                      "database", so, None, steward, steward, dt(360), dt(15), 0, 1, None))
        for fo, (fname, fcode, ftype, flen, freq, funiq, dclass, pii, sens, cmt) in enumerate(fields, start=1):
            ev = None
            if fcode in ("customerLevel",):
                ev = j(["A", "B", "C", "D"])
            if fcode in ("supplierLevel",):
                ev = j(["STRATEGIC", "CORE", "NORMAL", "PROBATION"])
            if fcode in ("qualificationStatus",):
                ev = j(["VALID", "PENDING", "EXPIRED"])
            if fcode in ("employmentType",):
                ev = j(["REGULAR", "PROBATION", "INTERN", "OUTSOURCE"])
            if fcode in ("memberLevel",):
                ev = j(["NORMAL", "SILVER", "GOLD", "PLATINUM"])
            frows.append((next(fid), TENANT, i, None, fname, fcode, fname, ftype, flen,
                          2 if ftype == "DECIMAL" else None, freq, funiq, 0, 1 if funiq else 0, None,
                          ev, None, None, dclass, pii, sens, "李静（主数据管家）",
                          fname, "UAT-SEED", cmt, fo, steward, steward, dt(360), dt(15), 0, 1, None))
    add("meta_entity", ecols, erows)
    add("meta_field", fcols, frows)
    return ent_ids


def build_mdm(ent_ids, accounts):
    """mdm_entity / mdm_field / mdm_record / steward / reference / quality"""
    ecols = ["id", "tenant_id", "meta_entity_id", "entity_code", "entity_name", "description", "category",
             "domain_code", "template_id", "template_version", "owning_app_id", "governance_tier",
             "is_versioning", "workflow_enabled", "status", "created_by", "updated_by", "created_at",
             "updated_at", "deleted", "version", "submitted_by", "submitted_at", "approved_by",
             "approved_at", "rejected_by", "reject_reason", "reject_at"]
    fcols = ["id", "tenant_id", "master_data_entity_id", "name", "code", "type", "length", "required",
             "default_value", "description", "sort_order", "source_type", "enabled", "reference_set_code",
             "min_value", "max_value", "created_at", "created_by", "updated_at", "updated_by", "deleted", "version"]
    rcols = ["id", "tenant_id", "mdm_entity_id", "record_code", "display_name", "current_data", "status",
             "version_number", "effective_from", "effective_to", "is_current", "parent_record_id",
             "submitted_by", "publish_time", "created_by", "updated_by", "created_at", "updated_at",
             "deleted", "version"]
    scols = ["id", "tenant_id", "mdm_entity_id", "account_id", "role_type", "created_by", "created_at",
             "updated_at", "deleted", "version"]
    sscols = ["id", "tenant_id", "steward_id", "scope_type", "scope_value", "created_at", "deleted", "version"]
    rscols = ["id", "tenant_id", "set_code", "set_name", "external_standard", "description", "status",
              "created_by", "created_at", "updated_at", "deleted", "version"]
    rvcols = ["id", "tenant_id", "set_id", "value_code", "value_name", "external_code", "sort_order",
              "enabled", "created_by", "created_at", "updated_at", "deleted", "version"]
    qcols = ["id", "tenant_id", "mdm_entity_id", "record_id", "check_id", "rule_id", "issue_desc",
             "severity", "status", "assignee_id", "due_at", "resolved_by", "resolved_at", "created_by",
             "created_at", "updated_at", "deleted", "version"]
    cccols = ["id", "tenant_id", "mdm_entity_id", "name", "code", "description", "parent_category_id"]

    steward = GOLDEN["steward"]
    approver = GOLDEN["approver"]
    erows, frows, rrows = [], [], []
    eid, fid = seq(220001), seq(221001)
    mids = {}
    order = ["customer", "supplier", "material", "product", "project", "employee", "asset", "channel_buyer"]
    names = {"customer": "客户", "supplier": "供应商", "material": "物料", "product": "商品",
             "project": "项目", "employee": "员工", "asset": "资产", "channel_buyer": "渠道买家"}
    tiers = {"customer": "L3", "supplier": "L3", "material": "L2", "product": "L3",
             "project": "L1", "employee": "L2", "asset": "L2", "channel_buyer": "L1"}
    for code in order:
        i = next(eid)
        mids[code] = i
        tier = tiers[code]
        erows.append((i, TENANT, ent_ids[code], code.upper(), names[code],
                      "%s主数据（治理等级 %s）" % (names[code], tier), names[code], code.upper(), None, None, None,
                      tier, 1 if tier in ("L2", "L3") else 0, 1 if tier == "L3" else 0, "PUBLISHED",
                      steward["id"], approver["id"], dt(350), dt(14), 0, 1,
                      steward["id"], dt(349), approver["id"], dt(348), None, None, None))
    add("mdm_entity", ecols, erows)

    # mdm_field：从 meta_field 投影（简化：每个实体 4~8 个关键字段）
    import _conf
    c = _conf.connect()
    cur = c.cursor()
    cur.execute("""SELECT entity_id, code, display_name, type, length FROM meta_field
                   WHERE tenant_id=%s AND deleted=0 ORDER BY entity_id, sort_order""", (TENANT,))
    meta_fields = {}
    for e, code, dn, ty, ln in cur.fetchall():
        meta_fields.setdefault(e, []).append((code, dn, ty, ln or 50))
    c.close()
    code_by_eid = {v: k for k, v in ent_ids.items()}
    for code, mid in mids.items():
        eid_meta = ent_ids[code]
        for so, (fcode, fname, ftype, flen) in enumerate(meta_fields.get(eid_meta, [])[:8], start=1):
            frows.append((next(fid), TENANT, mid, fname, fcode, ftype, flen, 1 if so <= 3 else 0, None,
                          "%s · %s" % (names[code], fname), so, "TENANT", 1, None, None, None,
                          dt(350), steward["id"], dt(14), steward["id"], 0, 1))
    add("mdm_field", fcols, frows)

    # ---- 记录 ----
    S = SCALE[SCALE_NAME]
    rec_seq = {"customer": seq(300001), "supplier": seq(301001), "material": seq(302001),
               "product": seq(303001), "project": seq(304001), "employee": seq(305001),
               "asset": seq(306001), "channel_buyer": seq(307001)}
    code_pfx = {"customer": "CUST", "supplier": "SUP", "material": "MAT", "product": "PROD",
                "project": "PRJ", "employee": "EMP", "asset": "AST", "channel_buyer": "BUY"}
    recs = {k: [] for k in order}
    STATUS_POOL = (["PUBLISHED"] * 68 + ["DRAFT"] * 10 + ["PENDING_APPROVAL"] * 10 +
                   ["APPROVED"] * 7 + ["SUPERSEDED"] * 3 + ["ARCHIVED"] * 2)

    def emit(domain, rec_id, rcode, dname, data, status, sub=None, app=None, created=None):
        sub = sub or (steward["id"] if status in ("DRAFT",) else steward["id"])
        app = app if (status not in ("DRAFT", "PENDING_APPROVAL")) else None
        created = created or dt(R.randint(20, 340))
        pub = created
        if status == "PUBLISHED":
            pub = (datetime.datetime.strptime(created, "%Y-%m-%d %H:%M:%S") +
                   datetime.timedelta(days=R.randint(1, 5))).strftime("%Y-%m-%d %H:%M:%S")
        recs[domain].append((rec_id, TENANT, mids[domain], rcode, dname, j(data), status, 1,
                             pub, None, 1, None, sub, pub if status == "PUBLISHED" else None,
                             steward["id"], app or steward["id"], created,
                             pub if status == "PUBLISHED" else created, 0, 1))

    # 客户（i==0 为黄金动线锚点：上海瀚海科技有限公司）
    for i in range(S["customer"]):
        if i == 0:
            rid = next(rec_seq["customer"])
            data = {"name": "上海瀚海科技有限公司", "unifiedSocialCreditCode": "91310000MA1FL2AB3X",
                    "contact": "陈晨", "phone": "138****0111", "regionCode": "310115",
                    "regionName": "浦东新区", "customerLevel": "A", "creditLimit": 500000.00,
                    "industry": "智能制造", "settlementTerms": "NET30",
                    "address": "上海市浦东新区张江高科技园区科苑路 88 号 6 栋",
                    "salesOwner": "陈晨"}
            emit("customer", rid, "CUST-2026-000001", "上海瀚海科技有限公司", data, "PUBLISHED")
            continue
        prov = R.choice(PROVINCES)
        n_city = len(prov[2]) // 2
        city = prov[2][R.randrange(n_city) * 2:][:2]
        cname = "%s%s%s" % (R.choice(CITY_WORD), R.choice(["科技", "智能", "实业", "数字", "智造", "云", ""]),
                            R.choice(COMPANY_SUFFIX))
        city_nm, city_cd = city[0], city[1]
        full = "%s%s" % (city_nm, cname)
        rid = next(rec_seq["customer"])
        code = "%s-2026-%06d" % (code_pfx["customer"], i + 1)
        contact = R.choice(SURNAME)[0] + R.choice(GIVEN)[0]
        data = {"name": full, "unifiedSocialCreditCode": "91%s%s%09dX" % (prov[1][:2], city_cd[2:4], R.randint(0, 999999999)),
                "contact": contact, "phone": "1%d****%04d" % (R.choice([3, 5, 8]), R.randint(0, 9999)),
                "regionCode": city_cd, "regionName": city_nm,
                "customerLevel": R.choice(["A"] * 12 + ["B"] * 30 + ["C"] * 40 + ["D"] * 18),
                "creditLimit": money(R.choice([200000, 500000, 800000, 1200000, 2000000])),
                "industry": R.choice(INDUSTRIES),
                "settlementTerms": R.choice(["NET30", "NET60", "PREPAID"]),
                "address": "%s%s路 %d 号 %d 栋" % (prov[0], city_nm, R.randint(1, 999), R.randint(1, 99)),
                "salesOwner": R.choice(accounts)["name"]}
        emit("customer", rid, code, full, data, R.choice(STATUS_POOL))
    # 供应商
    for i in range(S["supplier"]):
        prov = R.choice(PROVINCES)
        sname = "%s%s%s" % (R.choice([prov[0][:2], ""]), R.choice(CITY_WORD), R.choice(SUPPLIER_SUFFIX))
        rid = next(rec_seq["supplier"])
        code = "%s-2026-%06d" % (code_pfx["supplier"], i + 1)
        data = {"name": sname, "unifiedSocialCreditCode": "91%s%012d" % (prov[1][:2], R.randint(0, 999999999999)),
                "contact": R.choice(SURNAME)[0] + R.choice(GIVEN)[0],
                "phone": "1%d****%04d" % (R.choice([3, 5, 8]), R.randint(0, 9999)),
                "regionCode": prov[1], "regionName": prov[0],
                "supplierLevel": R.choice(["STRATEGIC"] * 10 + ["CORE"] * 25 + ["NORMAL"] * 45 + ["PROBATION"] * 20),
                "qualificationStatus": R.choice(["VALID"] * 82 + ["PENDING"] * 12 + ["EXPIRED"] * 6),
                "leadTimeDays": R.choice([3, 5, 7, 10, 15, 21, 30]),
                "settlementTerms": R.choice(["NET30", "NET60", "NET90"]),
                "bankAccount": "**** **** **** %04d" % R.randint(0, 9999),
                "category": R.choice(["原材料", "电子元器件", "包装辅料", "外协加工"])}
        emit("supplier", rid, code, sname, data, R.choice(STATUS_POOL))
    # 物料
    for i in range(S["material"]):
        cat, base, specs, unit, prange = R.choice(MATERIAL_CAT)
        spec = R.choice(specs)
        mname = "%s %s" % (base, spec)
        rid = next(rec_seq["material"])
        code = "%s-2026-%06d" % (code_pfx["material"], i + 1)
        data = {"name": mname, "materialCategory": cat, "spec": spec, "unit": unit,
                "unitPrice": money(R.uniform(*prange)),
                "safetyStock": R.choice([50, 100, 200, 500, 1000]),
                "shelfLifeDays": R.choice([180, 365, 730, 0]),
                "sourceType": R.choice(["自制", "外购", "外协"]),
                "hazardFlag": 1 if R.random() < 0.06 else 0,
                "supplierCode": "SUP-2026-%06d" % R.randint(1, S["supplier"])}
        emit("material", rid, code, mname, data, R.choice(STATUS_POOL))
    # 商品（全渠道上架主体）
    prods = []
    for i in range(S["product"]):
        if i == 0:
            # 黄金动线锚点：鼎晖智能扫地机器人 X1
            rid = next(rec_seq["product"])
            data = {"name": "鼎晖智能扫地机器人 X1", "productCategory": "智能家电", "brand": "鼎晖 DINGHUI",
                    "model": "X1", "spec": "扫拖一体 / 激光导航 / 5200mAh", "unit": "台",
                    "listPrice": 2999.00, "costPrice": 1236.00, "warrantyMonths": 24,
                    "launchDate": "2025-11-18", "barcode": "6901234500017", "status": "ON_SALE"}
            emit("product", rid, "PROD-2026-000001", "鼎晖智能扫地机器人 X1", data, "PUBLISHED")
            prods.append((rid, "PROD-2026-000001", "鼎晖智能扫地机器人 X1", 2999.00, 1236.00, "智能家电"))
            continue
        cat, base, models, prange = R.choice(PRODUCT_CAT)
        model = R.choice(models)
        pname = "鼎晖%s %s" % (base, model)
        rid = next(rec_seq["product"])
        code = "%s-2026-%06d" % (code_pfx["product"], i + 1)
        listp = money(R.uniform(*prange))
        costp = money(listp * R.uniform(0.42, 0.68))
        data = {"name": pname, "productCategory": cat, "brand": "鼎晖 DINGHUI", "model": model,
                "spec": "%s / %s" % (base, model), "unit": "台" if cat != "配件耗材" else "件",
                "listPrice": listp, "costPrice": costp,
                "warrantyMonths": R.choice([12, 24, 36]),
                "launchDate": (BASE - datetime.timedelta(days=R.randint(60, 900))).strftime("%Y-%m-%d"),
                "barcode": "69%012d" % R.randint(0, 999999999999),
                "status": "ON_SALE"}
        status = R.choice(STATUS_POOL)
        emit("product", rid, code, pname, data, status)
        prods.append((rid, code, pname, listp, costp, cat))
    # 项目
    for i in range(S["project"]):
        base = R.choice([p[1] for p in PRODUCT_CAT])
        pname = "%s%s项目" % (base, R.choice(["量产导入", "二代研发", "成本优化", "工艺改进", "国产化替代"]))
        rid = next(rec_seq["project"])
        code = "%s-2026-%06d" % (code_pfx["project"], i + 1)
        start = BASE - datetime.timedelta(days=R.randint(30, 400))
        data = {"name": pname, "projectCode": code, "projectManager": R.choice(accounts)["name"],
                "startDate": start.strftime("%Y-%m-%d"),
                "endDate": (start + datetime.timedelta(days=R.randint(90, 540))).strftime("%Y-%m-%d"),
                "budget": money(R.uniform(500000, 18000000)),
                "progress": R.choice([10, 25, 40, 55, 70, 85, 95, 100]),
                "status": R.choice(["进行中"] * 6 + ["已结项"] * 3 + ["已立项"] * 1),
                "department": R.choice(["研发中心", "智能制造事业部", "供应链中心"])}
        emit("project", rid, code, pname, data, R.choice(STATUS_POOL))
    # 员工（与 IAM 账号同源，工号一一对应）
    for i, a in enumerate(accounts):
        rid = next(rec_seq["employee"])
        code = "%s-2026-%06d" % (code_pfx["employee"], i + 1)
        hire = BASE - datetime.timedelta(days=R.randint(30, 3000))
        data = {"name": a["name"], "employeeNo": "DH%04d" % (i + 1), "gender": R.choice(["M", "F"]),
                "deptName": a["dept_name"], "position": R.choice(POSITIONS),
                "hireDate": hire.strftime("%Y-%m-%d"),
                "employmentType": R.choice(["REGULAR"] * 85 + ["PROBATION"] * 8 + ["INTERN"] * 4 + ["OUTSOURCE"] * 3),
                "workLocation": R.choice(["上海总部", "深圳基地", "苏州工厂"]),
                "email": a["uname"].replace(".", "_") + "@dinghui-mfg.example.com",
                "mobile": "1%d****%04d" % (R.choice([3, 5, 8]), R.randint(0, 9999))}
        emit("employee", rid, code, a["name"], data,
             "PUBLISHED" if i % 20 else "PENDING_APPROVAL")
    # 资产
    for i in range(S["asset"]):
        cat, names_, prange = R.choice(ASSET_CAT)
        aname = R.choice(names_)
        rid = next(rec_seq["asset"])
        code = "%s-2026-%06d" % (code_pfx["asset"], i + 1)
        ov = money(R.uniform(*prange))
        months = R.choice([36, 60, 120])
        used = R.randint(1, months)
        data = {"name": aname, "assetNo": "AST%s%05d" % (cat[:2].upper(), i + 1), "assetCategory": cat,
                "model": "%s-%d" % (aname[:2].upper(), R.randint(100, 999)),
                "purchaseDate": (BASE - datetime.timedelta(days=used * 30)).strftime("%Y-%m-%d"),
                "originalValue": ov, "netValue": money(ov * (1 - used / months * 0.9)),
                "location": R.choice(["上海总部 A 座", "深圳基地 2 号厂房", "苏州工厂 1 号车间", "北京研发中心"]),
                "custodian": R.choice(accounts)["name"],
                "status": R.choice(["在用"] * 8 + ["闲置"] * 1 + ["待报废"] * 1),
                "depreciationMonths": months}
        emit("asset", rid, code, aname, data, R.choice(STATUS_POOL))
    # 渠道买家（昵称脱敏）
    nick_words = ["清风", "小满", "阿泽", "橙子", "南风", "木木", "晨光", "知秋", "星野", "麦芽", "青柠", "初雪"]
    for i in range(S["buyer"]):
        ch = R.choice(CHANNELS)
        nick = "%s%s%d" % (R.choice(nick_words), R.choice(["同学", "先生", "女士", ""]), R.randint(1, 99))
        rid = next(rec_seq["channel_buyer"])
        code = "%s-2026-%06d" % (code_pfx["channel_buyer"], i + 1)
        data = {"name": nick, "channelCode": ch[0], "channelName": ch[1],
                "memberLevel": R.choice(["NORMAL"] * 40 + ["SILVER"] * 30 + ["GOLD"] * 22 + ["PLATINUM"] * 8),
                "province": R.choice(PROVINCES)[0],
                "registeredAt": (BASE - datetime.timedelta(days=R.randint(30, 900))).strftime("%Y-%m-%d"),
                "totalOrders": R.randint(1, 86), "totalSpend": money(R.uniform(199, 68000))}
        emit("channel_buyer", rid, code, "%s（%s）" % (nick, ch[1]), data, R.choice(STATUS_POOL))

    for k in order:
        add("mdm_record", rcols, recs[k])

    # ---- 数据管家 ----
    srows, ssrows = [], []
    sid, ssid = seq(240001), seq(241001)
    stewards = [a for a in accounts if "DEMO_MDM_STEWARD" in a["roles"]][:12]
    if len(stewards) < 12:
        stewards += [a for a in accounts if a not in stewards][:12 - len(stewards)]
    for si, a in enumerate(stewards):
        dom = order[si % len(order)]
        i = next(sid)
        srows.append((i, TENANT, mids[dom], a["id"], "PRIMARY" if si % 3 == 0 else "DEPUTY",
                      1, dt(340), dt(10), 0, 1))
        ssrows.append((next(ssid), TENANT, i, "DOMAIN", dom.upper(), dt(340), 0, 1))
    add("mdm_steward", scols, srows)
    add("mdm_steward_scope", sscols, ssrows)

    # ---- 参考数据 ----
    rsrows, rvrows = [], []
    rsid, rvid = seq(250001), seq(251001)
    refsets = [
        ("CUSTOMER_LEVEL", "客户等级", "内部标准", ["A", "B", "C", "D"]),
        ("SUPPLIER_LEVEL", "供应商等级", "内部标准", ["STRATEGIC", "CORE", "NORMAL", "PROBATION"]),
        ("UOM", "计量单位", "GB/T 17295", ["PCS", "KG", "M", "M2", "SET", "BOX", "TON"]),
        ("CURRENCY", "币种", "ISO 4217", ["CNY", "USD", "EUR", "HKD", "JPY", "GBP"]),
        ("CHANNEL_TYPE", "渠道类型", "内部标准", [c[0] for c in CHANNELS]),
        ("MATERIAL_CATEGORY", "物料分类", "内部标准", ["原材料", "电子元器件", "包装辅料"]),
        ("EMPLOYMENT_TYPE", "用工形式", "内部标准", ["REGULAR", "PROBATION", "INTERN", "OUTSOURCE"]),
        ("SETTLEMENT_TERMS", "结算账期", "内部标准", ["PREPAID", "NET30", "NET60", "NET90"]),
        ("ASSET_CATEGORY", "资产类别", "内部标准", ["生产设备", "IT 设备", "办公家具", "运输工具"]),
        ("MEMBER_LEVEL", "会员等级", "内部标准", ["NORMAL", "SILVER", "GOLD", "PLATINUM"]),
    ]
    for code, name, std, vals in refsets:
        i = next(rsid)
        rsrows.append((i, TENANT, code, name, std, "%s（%s）" % (name, std), "PUBLISHED", 1, dt(340), dt(10), 0, 1))
        for so, v in enumerate(vals, start=1):
            rvrows.append((next(rvid), TENANT, i, v, v, v, so, 1, 1, dt(340), dt(10), 0, 1))
    add("mdm_reference_set", rscols, rsrows)
    add("mdm_reference_value", rvcols, rvrows)

    # ---- 质量工单 ----
    qrows = []
    qid = seq(260001)
    rules = ["字段完备性：必填字段缺失", "格式合规：统一社会信用代码校验失败",
             "唯一性：疑似重复记录（名称相似度 > 0.9）", "值域合规：等级取值不在参考数据集内",
             "时效合规：资质证书已过期", "一致性：区域编码与地址不匹配"]
    for _ in range(40):
        dom = R.choice(order)
        pool = recs[dom]
        if not pool:
            continue
        rec = R.choice(pool)
        sev = R.choice(["HIGH"] * 15 + ["MEDIUM"] * 45 + ["LOW"] * 40)
        st = R.choice(["OPEN"] * 45 + ["IN_PROGRESS"] * 25 + ["RESOLVED"] * 30)
        created = dt(R.randint(5, 120))
        resolved = dt(R.randint(0, 4)) if st == "RESOLVED" else None
        qrows.append((next(qid), TENANT, mids[dom], rec[0], R.randint(1000, 9999),
                      R.randint(1, 40), R.choice(rules), sev, st,
                      steward["id"], (BASE + datetime.timedelta(days=R.randint(3, 30))).strftime("%Y-%m-%d %H:%M:%S"),
                      steward["id"] if st == "RESOLVED" else None, resolved,
                      1, created, resolved or created, 0, 1))
    add("mdm_quality_issue", qcols, qrows)

    # ---- 分类 ----
    crows = []
    cid = seq(270001)
    for code, mid in mids.items():
        for cn in ({"customer": ["战略客户", "渠道客户", "终端客户"],
                    "supplier": ["战略供应商", "核心供应商", "辅材供应商"],
                    "material": ["原材料", "电子元器件", "包装辅料"],
                    "product": ["智能家电", "工业自动化", "配件耗材"],
                    "project": ["研发项目", "技改项目", "信息化项目"],
                    "employee": ["正式员工", "试用期员工", "实习生"],
                    "asset": ["生产设备", "IT 设备", "办公家具", "运输工具"],
                    "channel_buyer": ["高价值买家", "普通买家"]}.get(code, [])):
            crows.append((next(cid), TENANT, mid, cn, cn.upper().replace(" ", "_"),
                          "%s · %s" % (names[code], cn), None))
    add("mdm_category", cccols, crows)
    return mids, recs, prods


def build_commerce(recs, prods, accounts):
    """渠道 / 上架 / 库存 / 订单 / 明细 / 发货 / 轨迹 / 支付"""
    chcols = ["id", "tenant_id", "channel_code", "channel_name", "ext_impl_code", "api_endpoint",
              "app_key", "enabled", "order_sync_enabled", "last_sync_at", "remark", "version",
              "created_by", "updated_by", "created_at", "updated_at", "deleted"]
    cpcols = ["id", "tenant_id", "channel_code", "product_id", "product_name", "channel_product_id",
              "listing_status", "listing_price", "listing_stock", "last_sync_at", "fail_reason",
              "version", "created_by", "updated_by", "created_at", "updated_at", "deleted"]
    invcols = ["id", "tenant_id", "product_id", "product_name", "warehouse_code", "available_qty",
               "reserved_qty", "safety_stock", "version", "created_by", "updated_by", "created_at",
               "updated_at", "deleted"]
    ocols = ["id", "tenant_id", "customer_id", "total_amount", "status", "order_no", "channel_source",
             "channel_code", "channel_order_no", "channel_buyer_id", "freight_amount", "discount_amount",
             "paid_time", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version"]
    icols = ["id", "order_id", "product_id", "product_name", "quantity", "unit_price", "subtotal",
             "created_by", "updated_by", "created_at", "updated_at", "deleted"]
    shcols = ["id", "tenant_id", "order_id", "channel_code", "shipment_no", "logistics_company",
              "tracking_no", "status", "receiver_name", "receiver_phone", "receiver_address",
              "channel_ack", "channel_ack_at", "shipped_at", "signed_at", "fail_reason", "remark",
              "version", "created_by", "updated_by", "created_at", "updated_at", "deleted"]
    stcols = ["id", "tenant_id", "shipment_id", "trace_time", "trace_status", "trace_desc",
              "created_by", "updated_by", "created_at", "updated_at", "deleted"]
    pcols = ["id", "tenant_id", "order_id", "customer_id", "amount", "channel", "status",
             "channel_trade_no", "pay_url", "version", "paid_at", "refunded_at", "refund_amount",
             "created_at", "updated_at", "deleted", "created_by", "updated_by", "refund_no",
             "settle_status", "currency", "pay_expire_at"]

    ops = GOLDEN["ops"]
    sales = GOLDEN["sales"]
    # 渠道
    chrows = []
    cid = seq(400001)
    ch_ids = {}
    for code, name, key, _ in CHANNELS:
        i = next(cid)
        ch_ids[code] = i
        chrows.append((i, TENANT, code, name, "IMPL_" + code.lower(),
                       "https://openapi.%s.example.com/v1" % key, "AK_DH_%s_2026" % code.lower(),
                       1, 1, dt(0, 3, 0), "%s · 全渠道订单自动同步" % name, 1,
                       ops["id"], ops["id"], dt(360), dt(0, 3, 0), 0))
    add("bp_channel", chcols, chrows)

    # 渠道商品上架（~85% 覆盖）
    cprows = []
    cpid = seq(410001)
    listings = []
    for rid, code, pname, listp, costp, cat in prods:
        for ch in CHANNELS:
            golden_listing = (rid == 303001 and ch[0] == "TAOBAO")
            if not golden_listing and R.random() < 0.15:
                continue
            i = next(cpid)
            price = money(listp) if golden_listing else money(listp * R.uniform(0.92, 1.06))
            stock = R.randint(0, 800)
            st = "ON_SALE" if R.random() < 0.9 else "OFF_SHELF"
            cprows.append((i, TENANT, ch[0], rid, pname, "%s-%s-%06d" % (ch[0], code.split("-")[-1], i - 410000),
                           st, price, stock, dt(R.randint(0, 7), 3, 0),
                           None if st == "ON_SALE" else "平台规则变更，待重新报备", 1,
                           sales["id"], sales["id"], dt(R.randint(30, 300)), dt(R.randint(0, 7), 3, 0), 0))
            listings.append((ch[0], rid, price))
    add("bp_channel_product", cpcols, cprows)

    # 库存（商品 × 3 仓）
    invrows = []
    invid = seq(420001)
    for rid, code, pname, listp, costp, cat in prods:
        for wh in WAREHOUSES:
            invrows.append((next(invid), TENANT, rid, pname, wh[0], R.randint(0, 1200),
                            R.randint(0, 120), R.choice([50, 100, 200]), 1,
                            sales["id"], sales["id"], dt(R.randint(30, 300)), dt(R.randint(0, 7)), 0))
    add("bp_inventory", invcols, invrows)

    # 订单：状态分布真实（含在途与终态）
    S = SCALE[SCALE_NAME]
    customers = recs["customer"]
    buyers = recs["channel_buyer"]
    orows, irows = [], []
    oid, iid = seq(500001), seq(510001)
    ship_rows, trace_rows, pay_rows = [], [], []
    sid, tid, pid = seq(430001), seq(432001), seq(440001)
    STATUS_POOL = (["DELIVERED"] * 45 + ["SHIPPED"] * 15 + ["PAID"] * 12 + ["CREATED"] * 8 + ["CANCELLED"] * 10 + ["CANCELLED"] * 10)
    orders = []
    list_by_ch = {}
    for ch_code, rid, price in listings:
        list_by_ch.setdefault(ch_code, []).append((rid, price))
    for n in range(S["order"]):
        o_id = next(oid)
        golden_order = (n == 0)
        if golden_order:
            cust = customers[0]                       # 300001 上海瀚海科技有限公司
            ch = CHANNELS[0]                          # TAOBAO
            status = "DELIVERED"
            created = dt(6, 10, 25)
        else:
            cust = R.choice(customers)
            ch = R.choice(CHANNELS)
            status = R.choice(STATUS_POOL)
            created = dt(R.randint(1, 179), R.randint(8, 22), R.randint(0, 59))
        pool = list_by_ch.get(ch[0]) or [(p[0], p[3]) for p in prods]
        cdt = datetime.datetime.strptime(created, "%Y-%m-%d %H:%M:%S")
        items, total = [], 0.0
        if golden_order:
            items.append((next(iid), o_id, 303001, "鼎晖智能扫地机器人 X1", 1, 2999.00, 2999.00,
                          sales["id"], sales["id"], created, created, 0))
            total = 2999.00
        for _ in range(0 if golden_order else R.randint(1, 4)):
            rid, price = R.choice(pool)
            pname = next((p[2] for p in prods if p[0] == rid), "商品")
            qty = R.randint(1, 5)
            up = money(price)
            sub = money(up * qty)
            total += sub
            items.append((next(iid), o_id, rid, pname, qty, up, sub, sales["id"], sales["id"], created, created, 0))
        freight = 0.0 if total >= 999 else money(R.choice([8, 12, 15, 20]))
        discount = 0.00 if golden_order else money(total * R.choice([0, 0, 0, 0.05, 0.08, 0.1, 0.15]))
        total_amount = money(total + freight - discount)
        buyer = R.choice(buyers) if buyers else None
        paid = None
        if status in ("PAID", "SHIPPED", "DELIVERED"):
            paid = (cdt + datetime.timedelta(minutes=R.randint(3, 300))).strftime("%Y-%m-%d %H:%M:%S")
        orows.append((o_id, TENANT, cust[0], total_amount, status,
                      "SO-2026-%06d" % (n + 1), ch[0], ch[0],
                      "%s%s" % (ch[2].upper(), R.randint(10 ** 12, 10 ** 13 - 1)),
                      (buyer[3] if buyer else None), freight, discount, paid,
                      sales["id"], sales["id"], created,
                      (cdt + datetime.timedelta(days=R.randint(0, 6))).strftime("%Y-%m-%d %H:%M:%S"), 0, 1))
        irows.extend(items)
        orders.append((o_id, "SO-2026-%06d" % (n + 1), status, total_amount, created, cdt,
                       ch[0], cust[0], paid, items))
        # 发货 + 轨迹
        if status in ("SHIPPED", "DELIVERED"):
            s_id = next(sid)
            lg = R.choice(LOGISTICS)
            ship_t = (cdt + datetime.timedelta(hours=R.randint(6, 48))).strftime("%Y-%m-%d %H:%M:%S")
            sdt = datetime.datetime.strptime(ship_t, "%Y-%m-%d %H:%M:%S")
            signed = (sdt + datetime.timedelta(days=R.randint(1, 5))).strftime("%Y-%m-%d %H:%M:%S") if status == "DELIVERED" else None
            st = "SIGNED" if status == "DELIVERED" else R.choice(["SHIPPED", "IN_TRANSIT"])
            rec = accounts[R.randrange(len(accounts))]
            ship_rows.append((s_id, TENANT, o_id, ch[0], "SH-2026-%06d" % (s_id - 430000), lg,
                              "%s%012d" % (lg[:2].upper(), R.randint(0, 999999999999)),
                              st, rec["name"], "1%d****%04d" % (R.choice([3, 5, 8]), R.randint(0, 9999)),
                              "%s%s路 %d 号 %d 室" % (R.choice(PROVINCES)[0], R.choice(["人民", "中山", "建设", "科技"]),
                                                  R.randint(1, 999), R.randint(1, 30)),
                              1, ship_t, ship_t, signed, None, None, 1,
                              sales["id"], sales["id"], ship_t, signed or ship_t, 0))
            trace_t = sdt
            segs = [("【%s】快递员已揽收，正在发往转运中心" % lg, "SHIPPED"),
                    ("【%s】已到达 %s 转运中心" % (lg, R.choice(PROVINCES)[0]), "IN_TRANSIT"),
                    ("【%s】正在派送中，请保持电话畅通" % lg, "IN_TRANSIT"),
                    ("【%s】已签收，感谢使用" % lg, "SIGNED")]
            for k, (desc, tst) in enumerate(segs[:4 if status == "DELIVERED" else R.randint(1, 3)]):
                trace_rows.append((next(tid), TENANT, s_id, trace_t.strftime("%Y-%m-%d %H:%M:%S"), tst, desc,
                                   sales["id"], sales["id"], trace_t.strftime("%Y-%m-%d %H:%M:%S"),
                                   trace_t.strftime("%Y-%m-%d %H:%M:%S"), 0))
                trace_t += datetime.timedelta(hours=R.randint(4, 20))
        # 支付
        if status in ("PAID", "SHIPPED", "DELIVERED"):
            pay_rows.append((next(pid), TENANT, o_id, cust[0], total_amount,
                             R.choice(["ALIPAY", "WECHAT_PAY", "UNIONPAY", "CHANNEL_WALLET"]),
                             "SUCCESS", "%s%016d" % (R.choice(["20", "42"]), R.randint(0, 10 ** 14)),
                             None, 1, paid, None, 0.00, paid, paid, 0, sales["id"], sales["id"], None,
                             "SETTLED", "CNY",
                             (cdt + datetime.timedelta(minutes=30)).strftime("%Y-%m-%d %H:%M:%S")))
        elif status == "CREATED" and R.random() < 0.5:
            pay_rows.append((next(pid), TENANT, o_id, cust[0], total_amount, R.choice(["ALIPAY", "WECHAT_PAY"]),
                             "PENDING", None, "https://pay.dinghui-mfg.example.com/cashier/%d" % o_id, 1,
                             None, None, 0.00, created, created, 0, sales["id"], sales["id"], None,
                             "UNSETTLED", "CNY",
                             (cdt + datetime.timedelta(minutes=30)).strftime("%Y-%m-%d %H:%M:%S")))
    add("t_order", ocols, orows)
    add("t_order_item", icols, irows)
    add("bp_shipment", shcols, ship_rows)
    add("bp_shipment_trace", stcols, trace_rows)
    add("bp_payment", pcols, pay_rows)
    return ch_ids, orders


def build_workflow(mids, accounts, recs):
    """流程定义 / 绑定 / 实例 / 任务 / 通知"""
    dcols = ["id", "tenant_id", "def_key", "def_name", "def_version", "status", "enabled",
             "definition_json", "created_by", "created_at", "updated_by", "updated_at", "deleted", "version"]
    bcols = ["id", "tenant_id", "entity_code", "def_key", "start_action", "enabled", "created_by",
             "created_at", "updated_by", "updated_at", "deleted", "version"]
    icols = ["id", "tenant_id", "entity_code", "record_id", "def_key", "def_version", "current_node_code",
             "phase", "started_by", "started_at", "node_round", "node_entered_at", "reject_rounds",
             "finished_at", "updated_at", "created_by", "created_at", "updated_by", "deleted", "version"]
    acols = ["tenant_id", "entity_code", "record_id", "instance_id", "created_at", "updated_at", "deleted"]
    tcols = ["id", "tenant_id", "instance_id", "node_code", "assignee_id", "status", "decision", "comment",
             "due_at", "node_round", "decided_at", "created_at", "updated_at", "created_by", "updated_by",
             "deleted", "version"]
    ncols = ["id", "tenant_id", "recipient_id", "type", "instance_id", "entity_code", "record_id",
             "node_code", "title", "body", "read_flag", "created_at", "updated_at", "created_by",
             "updated_by", "deleted", "version"]

    lowcode = GOLDEN["lowcode"]
    approvers = [a for a in accounts if "DEMO_WF_APPROVER" in a["roles"]] or accounts[:10]
    initiators = [a for a in accounts if "DEMO_WF_INITIATOR" in a["roles"]] or accounts[:30]

    defs = [
        ("MDM_CHANGE_APPROVAL", "主数据变更审批", [("SUBMIT", "发起申请"), ("MDM_REVIEW", "主数据审核"),
                                            ("MDM_APPROVE", "主数据批准"), ("END", "结束")]),
        ("EXPENSE_REIMBURSE", "费用报销审批", [("SUBMIT", "发起报销"), ("DEPT_APPROVE", "部门负责人审批"),
                                           ("FINANCE_APPROVE", "财务复核"), ("END", "结束")]),
        ("PURCHASE_REQUEST", "采购申请审批", [("SUBMIT", "发起申请"), ("DEPT_APPROVE", "部门审批"),
                                          ("PURCHASE_APPROVE", "采购审批"), ("FINANCE_APPROVE", "财务审批"),
                                          ("END", "结束")]),
        ("LEAVE_REQUEST", "请假审批", [("SUBMIT", "发起请假"), ("DEPT_APPROVE", "直属主管审批"), ("END", "结束")]),
    ]
    drows, brows = [], []
    did, bid = seq(600001), seq(601001)
    def_ids = {}
    for key, name, nodes in defs:
        i = next(did)
        def_ids[key] = i
        djson = j({"defKey": key, "name": name, "nodes": [{"code": c, "name": n,
                                                            "type": "START" if c == "SUBMIT" else ("END" if c == "END" else "APPROVAL")}
                                                           for c, n in nodes],
                   "edges": [{"from": nodes[k][0], "to": nodes[k + 1][0]} for k in range(len(nodes) - 1)]})
        drows.append((i, TENANT, key, name, 1, "PUBLISHED", 1, djson, lowcode["id"], dt(330),
                      lowcode["id"], dt(12), 0, 1))
    add("wf_process_def", dcols, drows)
    bind_map = [("CUSTOMER", "MDM_CHANGE_APPROVAL"), ("SUPPLIER", "MDM_CHANGE_APPROVAL"),
                ("PRODUCT", "MDM_CHANGE_APPROVAL"), ("MATERIAL", "MDM_CHANGE_APPROVAL")]
    for ec, key in bind_map:
        brows.append((next(bid), TENANT, ec, key, "SUBMIT", 1, lowcode["id"], dt(330), lowcode["id"],
                      dt(12), 0, 1))
    add("wf_binding", bcols, brows)

    S = SCALE[SCALE_NAME]
    irows, arows, trows, nrows = [], [], [], []
    iid, tid, nid = seq(610001), seq(620001), seq(640001)
    PHASE_POOL = ["RUNNING"] * 25 + ["APPROVED"] * 55 + ["REJECTED"] * 12 + ["WITHDRAWN"] * 5 + ["SUSPENDED"] * 3
    for gi in range(S["wf_instance"]):
        golden_wf = (gi == 0)
        if golden_wf:
            key = "MDM_CHANGE_APPROVAL"       # 黄金动线：主数据变更审批（李静发起 → 王芳审批）
            starter = GOLDEN["steward"]
            appr = GOLDEN["approver"]
            phase = "RUNNING"
            started = dt(1, 9, 30)
        else:
            key = R.choice(list(def_ids))
            starter = R.choice(initiators)
            appr = R.choice([a for a in approvers if a["id"] != starter["id"]])
            phase = R.choice(PHASE_POOL)
            started = dt(R.randint(1, 150), R.randint(9, 18), R.randint(0, 59))
        nodes = dict((d[0], d[2]) for d in defs)[key]
        sd = datetime.datetime.strptime(started, "%Y-%m-%d %H:%M:%S")
        if golden_wf:
            rec_id, ent_code = 300001, "CUSTOMER"
        else:
            rec_id = R.choice(recs[R.choice(["customer", "supplier", "product", "material"])])[0] \
                if recs.get("customer") else None
            ent_code = key.split("_")[0]
        i_id = next(iid)
        if phase == "RUNNING":
            cur = nodes[1][0] if golden_wf else nodes[R.randrange(1, len(nodes) - 1)][0]
            finished = None
        else:
            cur = "END"
            finished = (sd + datetime.timedelta(days=R.randint(1, 8))).strftime("%Y-%m-%d %H:%M:%S")
        irows.append((i_id, TENANT, ent_code, rec_id, key, 1, cur, phase,
                      starter["id"], started, 1,
                      (sd + datetime.timedelta(hours=1)).strftime("%Y-%m-%d %H:%M:%S"),
                      1 if phase == "REJECTED" else 0, finished,
                      finished or started, starter["id"], started, starter["id"], 0, 1))
        if phase == "RUNNING":
            arows.append((TENANT, ent_code, rec_id, i_id, started, started, 0))
        # 任务：每个审批节点一条
        seq_nodes = [n for n in nodes if n[0] not in ("SUBMIT", "END")]
        for ni, (ncode, nname) in enumerate(seq_nodes):
            t_id = next(tid)
            if golden_wf:
                tstate = "PENDING"
            elif phase == "RUNNING":
                tstate = "APPROVED" if ni < R.randint(0, len(seq_nodes)) else "PENDING"
            elif phase == "REJECTED":
                tstate = "REJECTED" if ni == R.randint(0, len(seq_nodes) - 1) else "APPROVED"
            elif phase == "WITHDRAWN":
                tstate = "CANCELLED"
            else:
                tstate = "APPROVED"
            decided = (sd + datetime.timedelta(hours=R.randint(2, 72))).strftime("%Y-%m-%d %H:%M:%S") \
                if tstate != "PENDING" else None
            comment = {"APPROVED": "同意，符合公司制度与预算范围",
                       "REJECTED": "资料不完整，请补充后重新提交",
                       "CANCELLED": "发起人撤回申请"}.get(tstate)
            trows.append((t_id, TENANT, i_id, ncode, appr["id"], tstate,
                          tstate if tstate in ("APPROVED", "REJECTED") else None,
                          comment,
                          (sd + datetime.timedelta(days=3)).strftime("%Y-%m-%d %H:%M:%S"), 1,
                          decided, started, decided or started, starter["id"], appr["id"], 0, 1))
            if tstate == "PENDING":
                nrows.append((next(nid), TENANT, appr["id"], "TODO", i_id, ent_code, rec_id, ncode,
                              "待审批：%s（%s）" % (dict(defs)[key] if False else defs[list(def_ids).index(key)][1], nname),
                              "发起人：%s，请于 3 个工作日内处理" % starter["name"], 0,
                              started, started, starter["id"], starter["id"], 0, 1))
    add("wf_instance", icols, irows)
    add("wf_active_instance", acols, arows)
    add("wf_task", tcols, trows)
    add("wf_notification", ncols, nrows)
    # 站内心跳消息
    mrows = []
    mid = seq(911001)
    for n in nrows[:200]:
        mrows.append((next(mid), n[2], n[8], n[9], "INFO", 0, n[11], TENANT))
    add("ntf_message", ["id", "user_id", "title", "content", "level", "is_read", "created_at", "tenant_id"], mrows)


def build_integration(orders):
    conn_cols = ["id", "tenant_id", "name", "type", "config", "status", "created_by", "updated_by",
                 "created_at", "updated_at", "deleted", "version"]
    flow_cols = ["id", "tenant_id", "name", "description", "status", "created_by", "updated_by",
                 "created_at", "updated_at", "deleted", "version"]
    node_cols = ["id", "tenant_id", "flow_id", "name", "type", "config", "position_x", "position_y",
                 "created_by", "updated_by", "created_at", "updated_at", "deleted"]
    conn2_cols = ["id", "tenant_id", "flow_id", "source_node_id", "target_node_id", "condition",
                  "created_at", "updated_at", "deleted"]
    log_cols = ["id", "tenant_id", "flow_id", "status", "started_at", "ended_at", "input_data",
                "output_data", "error_message", "created_at"]
    ops = GOLDEN["ops"]
    connectors = [("用友 U9 ERP", "ERP"), ("企业微信", "IM"), ("钉钉", "IM"),
                  ("快递 100", "LOGISTICS"), ("抖音开放平台", "ECOMMERCE"), ("微信支付", "PAYMENT")]
    crows, cid = [], seq(700001)
    conn_ids = {}
    for name, tp in connectors:
        i = next(cid)
        conn_ids[name] = i
        crows.append((i, TENANT, name, tp,
                      j({"endpoint": "https://%s.example.com/api" % tp.lower(), "authType": "OAUTH2",
                         "timeoutMs": 10000, "retry": 3}), "ENABLED",
                      ops["id"], ops["id"], dt(320), dt(1), 0, 1))
    add("int_connector", conn_cols, crows)
    flows = [("淘宝订单同步 ERP", "渠道订单实时写入用友 U9 销售订单", "用友 U9 ERP"),
             ("京东订单同步 ERP", "京东自营订单回写 ERP", "用友 U9 ERP"),
             ("主数据变更通知", "主数据发布后同步至企微/钉钉应用", "企业微信"),
             ("物流轨迹回写", "快递 100 拉取轨迹回写订单", "快递 100"),
             ("支付对账", "微信支付账单与订单逐笔对账", "微信支付"),
             ("抖音商品上架同步", "商品主数据同步到抖音小店", "抖音开放平台"),
             ("库存快照归档", "每日库存快照入湖", "用友 U9 ERP"),
             ("客户主数据分发", "客户发布后分发各渠道 CRM", "企业微信"),
             ("采购申请转 ERP 采购单", "审批通过后生成 ERP 采购单", "用友 U9 ERP"),
             ("理赔结算回写", "理赔结算单回写财务系统", "微信支付")]
    frows, nrows, lrows = [], [], []
    fid, nid, lid = seq(710001), seq(711001), seq(720001)
    flow_ids = []
    for name, desc, cn in flows:
        i = next(fid)
        flow_ids.append((i, name))
        frows.append((i, TENANT, name, desc, "ENABLED" if R.random() < 0.85 else "DISABLED",
                      ops["id"], ops["id"], dt(300), dt(2), 0, 1))
        nodes = []
        for k, (nname, ntype) in enumerate([("接收事件", "TRIGGER"), ("字段映射", "TRANSFORM"),
                                            ("调用 %s" % cn, "ACTION"), ("结果回写", "SINK")]):
            nodes.append(next(nid))
            nrows.append((nodes[-1], TENANT, i, nname, ntype, j({"connectorId": conn_ids[cn], "step": k + 1}),
                          100 + k * 200, 100, ops["id"], ops["id"], dt(300), dt(2), 0))
        for k in range(len(nodes) - 1):
            CONNECT.append((next(SEQ_CONN), TENANT, i, nodes[k], nodes[k + 1], None, dt(300), dt(2), 0))
    add("int_flow", flow_cols, frows)
    add("int_flow_node", node_cols, nrows)
    add("int_flow_connection", conn2_cols, CONNECT)
    S = SCALE[SCALE_NAME]
    for li in range(S["exec_log"]):
        fid_, fname = (flow_ids[0] if li == 0 else R.choice(flow_ids))
        ok = True if li == 0 else (R.random() < 0.93)
        st = "SUCCESS" if ok else "FAILED"
        started = dt(R.randint(0, 90), R.randint(0, 23), R.randint(0, 59))
        sd = datetime.datetime.strptime(started, "%Y-%m-%d %H:%M:%S")
        ended = (sd + datetime.timedelta(seconds=R.randint(1, 60))).strftime("%Y-%m-%d %H:%M:%S")
        sample = (orders[0] if (li == 0 and orders) else (R.choice(orders) if orders else None))
        lrows.append((next(lid), TENANT, fid_, st, started, ended,
                      j({"orderNo": sample[1] if sample else None, "source": fname}),
                      j({"erpVoucherNo": "U9-SO-%08d" % R.randint(1, 99999999)}) if ok else None,
                      None if ok else "上游接口超时（read timed out，10000ms）", started))
    add("int_execution_log", log_cols, lrows)


def build_generator(recs, prods):
    ds_cols = ["id", "tenant_id", "name", "db_type", "host", "port", "db_name", "username",
               "password_encrypted", "params", "is_enabled", "last_test_at", "last_test_result",
               "last_test_message", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version"]
    tm_cols = ["id", "tenant_id", "data_source_id", "table_schema", "original_table_name", "custom_entity_name",
               "module_name", "table_comment", "sync_status", "last_sync_at", "created_by", "updated_by",
               "created_at", "updated_at", "deleted", "version"]
    ct_cols = ["id", "tenant_id", "name", "code", "description", "type", "language", "engine",
               "template_version", "content", "sample_output", "status", "published_at", "created_by",
               "updated_by", "created_at", "updated_at", "deleted", "version"]
    gt_cols = ["id", "tenant_id", "task_id", "project_name", "base_package", "module_name", "data_source_id",
               "table_names", "template_ids", "gen_config", "generated_files", "zip_url", "status",
               "error_message", "started_at", "completed_at", "created_by", "created_at", "updated_at",
               "deleted", "version"]
    gh_cols = ["id", "tenant_id", "task_id", "template_id", "template_name", "generation_name",
               "data_source_id", "table_names", "base_package", "module_name", "status", "file_count",
               "execution_time", "output_path", "error_message", "started_at", "completed_at", "created_at", "deleted"]
    cm_cols = ["id", "tenant_id", "table_metadata_id", "original_column_name", "custom_field_name",
               "jdbc_type", "java_type", "column_type", "column_length", "precision_value", "scale_value",
               "is_nullable", "is_primary_key", "is_autoincrement", "default_value", "column_comment",
               "sort_order", "created_by", "updated_by", "created_at", "updated_at", "deleted"]
    lowcode = GOLDEN["lowcode"]
    dsid = 800001
    add("gen_data_source", ds_cols, [(dsid, TENANT, "BONE UAT 主库", "MYSQL", "127.0.0.1", 3306, "bone",
                                      "bone_app", "ENC(AES)****", j({"useSSL": False, "serverTimezone": "Asia/Shanghai"}),
                                      1, dt(1, 2, 0), "SUCCESS", "连接正常，耗时 42ms", lowcode["id"],
                                      lowcode["id"], dt(300), dt(1), 0, 1)])
    tmrows, cmrows = [], []
    tmid, cmid = seq(801001), seq(802001)
    targets = [("mdm_record", "主数据记录", "masterdata"), ("t_order", "销售订单", "commerce"),
               ("t_order_item", "销售订单明细", "commerce"), ("iam_account", "账号", "iam"),
               ("bp_channel_product", "渠道商品", "commerce"), ("bp_inventory", "库存", "commerce"),
               ("mdm_entity", "主数据实体", "masterdata"), ("meta_entity", "业务对象", "metadata")]
    for tbl, ename, mod in targets:
        i = next(tmid)
        tmrows.append((i, TENANT, dsid, "bone", tbl, ename, mod, "%s 表（反向工程）" % ename, "SYNCED",
                       dt(3, 2, 0), lowcode["id"], lowcode["id"], dt(300), dt(3), 0, 1))
        for k, (col, jt) in enumerate([("id", "BIGINT"), ("tenant_id", "BIGINT"), ("record_code", "VARCHAR"),
                                       ("display_name", "VARCHAR"), ("status", "VARCHAR"),
                                       ("created_at", "DATETIME")]):
            cmrows.append((next(cmid), TENANT, i, col, col, jt,
                           ("Long" if jt == "BIGINT" else "String"), jt,
                           64 if jt == "VARCHAR" else None, None, None,
                           0 if col in ("id", "tenant_id") else 1, 1 if col == "id" else 0, 0,
                           None, "%s 字段" % col, k + 1, lowcode["id"], lowcode["id"], dt(300), dt(3), 0))
    add("gen_table_metadata", tm_cols, tmrows)
    add("gen_column_metadata", cm_cols, cmrows)
    tpls = [("实体类模板", "ENTITY_JAVA", "JAVA"), ("DTO 模板", "DTO_JAVA", "JAVA"),
            ("Mapper 模板", "MAPPER_JAVA", "JAVA"), ("前端列表页", "LIST_TSX", "TSX"),
            ("前端表单页", "FORM_TSX", "TSX")]
    trows = []
    tid = seq(803001)
    tpl_ids = {}
    for name, code, lang in tpls:
        i = next(tid)
        tpl_ids[code] = i
        trows.append((i, TENANT, name, code, "%s（基于元数据模型生成）" % name, code.split("_")[-1], lang,
                      "FREEMARKER", "1.0.0", "// ${entity.name} 由 BONE 代码生成器产出\npublic class ${entity.code} {}",
                      None, "PUBLISHED", dt(300), lowcode["id"], lowcode["id"], dt(300), dt(5), 0, 1))
    add("gen_code_template", ct_cols, trows)
    grows, hrows = [], []
    gid, hid = seq(804001), seq(805001)
    for n, (tbl, ename, mod) in enumerate(targets[:5]):
        i = next(gid)
        task_id = "GEN-2026-%04d" % (n + 1)
        started = dt(R.randint(10, 200), 14, 20)
        sd = datetime.datetime.strptime(started, "%Y-%m-%d %H:%M:%S")
        done = (sd + datetime.timedelta(seconds=R.randint(3, 40))).strftime("%Y-%m-%d %H:%M:%S")
        grows.append((i, TENANT, task_id, "bone-%s" % mod, "com.bone.%s" % mod, mod, dsid, j([tbl]),
                      j(sorted(tpl_ids.values())),
                      j({"lombok": True, "swagger": True, "overwrite": False}), j(["Entity.java","DTO.java","Mapper.java","Service.java","Controller.java","list.tsx"][: (12 + n) % 6 + 1]),
                      "https://oss.dinghui-mfg.example.com/gen/%s.zip" % task_id, "SUCCESS", None,
                      started, done, lowcode["id"], started, done, 0, 1))
        for code, tid_ in tpl_ids.items():
            hrows.append((next(hid), TENANT, task_id, str(tid_), dict((t[1], t[0]) for t in tpls)[code],
                          "%s 生成任务" % ename, str(dsid), j([tbl]), "com.bone.%s" % mod, mod, "SUCCESS",
                          R.randint(1, 5), R.randint(200, 4800),
                          "/outputs/gen/%s/%s" % (task_id, code.lower()), None, started, done, started, 0))
    add("gen_generation_task", gt_cols, grows)
    add("gen_code_generation_history", gh_cols, hrows)


def build_platform(accounts):
    """应用注册 / 扩展点 / 控制台看板"""
    acols = ["id", "tenant_id", "name", "code", "description", "icon", "status", "created_by",
             "created_at", "updated_by", "updated_at", "deleted", "version"]
    mcols = ["id", "tenant_id", "app_id", "name", "code", "description", "status", "sort_order",
             "created_by", "created_at", "updated_by", "updated_at", "deleted", "version"]
    pcols = ["id", "tenant_id", "app_id", "user_id", "role", "created_by", "created_at", "updated_by",
             "updated_at", "deleted", "version"]
    epcols = ["id", "tenant_id", "point_name", "point_code", "description", "interface_name", "biz_domain",
              "category", "status", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version"]
    icols = ["id", "tenant_id", "extension_point_id", "impl_name", "impl_code", "description", "class_name",
             "tenant_code", "biz_code", "use_case", "scenario", "user_group", "priority", "config_json",
             "status", "is_default", "rollout_percent", "created_by", "updated_by", "created_at",
             "updated_at", "deleted", "version", "app_id"]
    wcols = ["id", "tenant_id", "user_id", "type", "config", "position_x", "position_y", "width", "height",
             "is_enabled", "sort_order", "created_by", "updated_by", "created_at", "updated_at", "deleted"]
    lowcode = GOLDEN["lowcode"]
    ceo = GOLDEN["ceo"]
    apps = [("智能建模平台", "METADATA"), ("主数据中心", "MASTERDATA"), ("全渠道商务中心", "COMMERCE"),
            ("统一身份与权限", "IAM"), ("集成连接器", "INTEGRATION"), ("代码生成器", "GENERATOR"),
            ("工作流中心", "WORKFLOW"), ("系统管理", "SYSTEM"), ("扩展点市场", "EXTENSION"),
            ("理赔 TPA 演示应用", "CLAIM_TPA")]
    arows, aid = [], seq(921001)
    app_ids = {}
    for name, code in apps:
        i = next(aid)
        app_ids[code] = i
        arows.append((i, TENANT, name, code, "%s · 演示应用" % name, "app-" + code.lower(), 1,
                      lowcode["id"], dt(320), lowcode["id"], dt(10), 0, 1))
    add("bone_application", acols, arows)
    mods = {"METADATA": ["业务对象", "字段模型", "关系图谱"], "MASTERDATA": ["域工作台", "数据治理", "质量中心"],
            "COMMERCE": ["渠道管理", "商品上架", "订单中心", "库存中心"], "IAM": ["组织", "账号", "角色", "权限"],
            "INTEGRATION": ["连接器", "流程编排", "执行日志"], "GENERATOR": ["数据源", "模板", "生成历史"],
            "WORKFLOW": ["流程设计", "我的待办", "实例查询"], "SYSTEM": ["字典", "配置", "定时任务"],
            "EXTENSION": ["扩展点", "实现", "插件版本"], "CLAIM_TPA": ["报案", "核赔", "结算", "额度"]}
    mrows = []
    mid = seq(920001)
    for code, names in mods.items():
        for so, nm in enumerate(names, start=1):
            mrows.append((next(mid), TENANT, app_ids[code], nm, "%s_%d" % (code, so), "%s · %s" % (code, nm),
                          1, so, lowcode["id"], dt(320), lowcode["id"], dt(10), 0, 1))
    add("bone_module", mcols, mrows)
    prows = []
    pid = seq(922001)
    for a in accounts[:60]:
        prows.append((next(pid), TENANT, app_ids[R.choice(list(app_ids))], a["id"],
                      R.choice(["ADMIN", "USER", "VIEWER"]), lowcode["id"], dt(300), lowcode["id"],
                      dt(5), 0, 1))
    add("bone_app_permission", pcols, prows)
    # 扩展点
    eprows = []
    epid = seq(900001)
    points = [("渠道定价扩展", "CHANNEL_PRICING", "com.bone.ext.ChannelPricingExtension", "commerce", "PRICING"),
              ("主数据校验扩展", "MDM_VALIDATION", "com.bone.ext.MdmValidationExtension", "masterdata", "VALIDATION"),
              ("订单支付前钩子", "ORDER_PRE_PAY", "com.bone.ext.OrderPrePayExtension", "commerce", "HOOK"),
              ("发货通知扩展", "SHIPMENT_NOTIFY", "com.bone.ext.ShipmentNotifyExtension", "commerce", "NOTIFY"),
              ("理赔规则扩展", "CLAIM_RULE", "com.bone.ext.ClaimRuleExtension", "claim", "RULE")]
    point_ids = {}
    for nm, code, iface, dom, cat in points:
        i = next(epid)
        point_ids[code] = i
        eprows.append((i, TENANT, nm, code, "%s（%s 域）" % (nm, dom), iface, dom, cat, "ENABLED",
                       lowcode["id"], lowcode["id"], dt(310), dt(8), 0, 1))
    add("exts_extension_point", epcols, eprows)
    impls = [("CHANNEL_PRICING", "抖音渠道特价策略", "DOUYIN_SPECIAL", "抖音大促期间按 92 折让利",
              "com.bone.ext.impl.DouyinSpecialPricing", "douyin-campaign", "全渠道", "抖音小店"),
             ("CHANNEL_PRICING", "会员等级价策略", "MEMBER_TIER_PRICE", "按会员等级 PLATINUM/GOLD 阶梯定价",
              "com.bone.ext.impl.MemberTierPricing", "member", "会员运营", "全渠道"),
             ("MDM_VALIDATION", "统一社会信用代码校验", "USCC_VALIDATE", "GB 32100-2015 校验位验证",
              "com.bone.ext.impl.UsccValidator", "mdm", "主数据准入", "全部域"),
             ("MDM_VALIDATION", "重复客户相似度检测", "DUP_SIMILARITY", "名称相似度 > 0.9 触发人工复核",
              "com.bone.ext.impl.DupSimilarityChecker", "mdm", "主数据去重", "CUSTOMER"),
             ("ORDER_PRE_PAY", "大额订单风控", "BIG_ORDER_RISK", "单笔 > 5 万触发人工风控",
              "com.bone.ext.impl.BigOrderRiskCheck", "risk", "支付风控", "全渠道"),
             ("SHIPMENT_NOTIFY", "企微发货通知", "WECOM_SHIP_NOTIFY", "发货后推送企微应用消息",
              "com.bone.ext.impl.WecomShipNotifier", "notify", "客户通知", "全渠道"),
             ("CLAIM_RULE", "门诊额度超限拦截", "QUOTA_OVER_LIMIT", "超出年度额度自动挂起",
              "com.bone.ext.impl.QuotaOverLimitRule", "claim", "理赔风控", "CLAIM"),
             ("CLAIM_RULE", "高频就诊预警", "FREQ_VISIT_ALERT", "30 天内就诊 > 6 次触发人工审核",
              "com.bone.ext.impl.FreqVisitAlertRule", "claim", "反欺诈", "CLAIM")]
    irows = []
    impid = seq(901001)
    for pc, nm, code, desc, cls, biz, uc, sc in impls:
        irows.append((next(impid), TENANT, point_ids[pc], nm, code, desc, cls, "DEMO", biz, uc, sc,
                      R.choice(["ALL", "VIP", "INTERNAL"]), R.randint(1, 100),
                      j({"enabled": True, "params": {}}),
                      1, 1 if code in ("DOUYIN_SPECIAL", "USCC_VALIDATE") else 0,
                      R.choice([100, 80, 50]), lowcode["id"], lowcode["id"], dt(310), dt(8), 0, 1,
                      app_ids["EXTENSION"]))
    add("exts_extension_impl", icols, irows)
    # 控制台看板
    wrows = []
    wid = seq(910001)
    widgets = [("SALES_TREND", "销售趋势（近 12 月）"), ("ORDER_STATUS", "订单状态分布"),
               ("INVENTORY_ALERT", "库存预警 TOP10"), ("APPROVAL_TODO", "我的审批待办"),
               ("CHANNEL_GMV", "渠道 GMV 对比"), ("MDM_QUALITY", "主数据质量水位"),
               ("CLAIM_SETTLE", "理赔结算进度"), ("INTEGRATION_HEALTH", "集成链路健康度")]
    owners = [ceo] + [a for a in accounts if "DEMO_WF_APPROVER" in a["roles"]][:3]
    for k, (wtype, wname) in enumerate(widgets):
        for oi, owner in enumerate(owners[:3]):
            wrows.append((next(wid), TENANT, owner["id"], wtype,
                          j({"title": wname, "chart": wtype.lower(), "range": "LAST_12_MONTHS"}),
                          (k % 3) * 8, (k // 3) * 6, 8, 6, 1, k + oi, lowcode["id"], lowcode["id"],
                          dt(200), dt(3), 0))
    add("cnsl_dashboard_widget", wcols, wrows)


def build_claim(accounts):
    """垂直演示应用：保险 TPA 门诊理赔（PII 假名化）"""
    ccols = ["id", "claim_no", "idempotency_key", "policy_no", "plan_id", "insured_id_hash", "claim_type",
             "status", "status_sub", "stage", "hang_up_status", "hang_up_type", "invoice_code", "invoice_no",
             "out_insure_time", "source_channel", "source_system", "batch_no", "claim_amount",
             "approved_amount", "first_reviewer_id", "second_reviewer_id", "reject_comment", "reject_type",
             "plan_version", "tenant_id", "created_by", "created_at", "updated_by", "updated_at",
             "closed_at", "version", "deleted"]
    icols = ["id", "claim_id", "item_name", "item_code", "category", "quantity", "unit_price", "amount",
             "social_flag", "medical_type", "pooling_amount", "self_pay_1", "self_pay_2", "self_pay_c",
             "third_party_paid", "valid_amount", "invalid_amount", "tenant_id", "created_by", "created_at",
             "updated_by", "updated_at", "version", "deleted", "ext_str_01", "ext_int_01"]
    ecols = ["id", "claim_id", "doc_type", "file_ref", "content_hash", "ocr_status", "ocr_confidence",
             "verify_valid", "extracted_json", "locked", "locked_at", "tenant_id", "created_by",
             "created_at", "updated_by", "updated_at", "version", "deleted"]
    qcols = ["id", "insured_id_hash", "policy_no", "plan_id", "quota_year", "quota_type", "liability_uuid",
             "quota_initial", "quota_claimed", "quota_frozen", "deductible_used", "cap_used", "insured_state",
             "tenant_id", "created_by", "created_at", "updated_by", "updated_at", "version", "deleted"]
    tcols = ["id", "quota_account_id", "claim_id", "settlement_no", "direction", "txn_type", "amount",
             "before_amount", "after_amount", "idempotency_key", "occurred_at", "tenant_id", "created_at",
             "created_by", "updated_at", "updated_by", "deleted"]
    scols = ["id", "claim_id", "claim_no", "settlement_no", "amount", "funding_stage", "account_type",
             "attempt", "paid_at", "status", "review_first_approver", "tenant_id", "created_by", "created_at",
             "updated_by", "updated_at", "version", "deleted"]
    fcols = ["id", "settlement_id", "claim_id", "settlement_no", "stage", "amount", "operator",
             "account_type", "idempotency_key", "event_time", "tenant_id", "created_at", "created_by",
             "updated_at", "updated_by", "deleted"]
    acols = ["id", "appeal_no", "claim_id", "claim_no", "original_status", "status", "submitted_by",
             "submitted_at", "case_closed_at", "reviewed_by", "reviewed_at", "review_result", "review_reason",
             "tenant_id", "created_at", "updated_at", "version", "created_by", "updated_by", "deleted"]
    dcols = ["id", "trace_id", "claim_id", "execution_id", "node_seq", "node_type", "node_name",
             "input_summary", "output", "action_result", "duration_ms", "tenant_id", "created_at",
             "created_by", "updated_at", "updated_by", "deleted"]

    finance = GOLDEN["finance"]
    approver = GOLDEN["approver"]
    auditor = GOLDEN["auditor"]
    S = SCALE[SCALE_NAME]
    STATUS_POOL = (["FINISHED"] * 45 + ["ADJUSTED"] * 12 + ["ADJUSTING"] * 10 + ["PRE_EXAMINED"] * 8 +
                   ["REVIEWING"] * 8 + ["CLOSED_REJECTED"] * 7 + ["APPEALING"] * 5 + ["ACCEPTED"] * 3 +
                   ["PENDING_ADJUST"] * 2)
    STAGE_OF = {"FINISHED": "FINISH", "ADJUSTED": "AUDITING", "ADJUSTING": "AUDITING",
                "PRE_EXAMINED": "PRE_EXAM", "REVIEWING": "REVIEWING", "CLOSED_REJECTED": "FINISH",
                "APPEALING": "REVIEWING", "ACCEPTED": "PRE_EXAM", "PENDING_ADJUST": "INSPECTION"}
    ITEM_NAMES = [("门诊诊查费", "A001", "门诊"), ("血常规检查", "A012", "检查"), ("DR 数字化摄影", "B031", "检查"),
                  ("头孢类抗生素", "C101", "药品"), ("中成药颗粒", "C205", "药品"), ("清创缝合术", "D012", "治疗"),
                  ("雾化吸入治疗", "D045", "治疗"), ("心电图检查", "B058", "检查"), ("彩超检查", "B072", "检查"),
                  ("静脉输液", "D088", "治疗")]
    crows, irows, erows, qrows, trows, srows, frows, arows, drows = [], [], [], [], [], [], [], [], []
    cid, iid, eid, qid, tid, sid, fid, aid, did = (seq(930001), seq(931001), seq(932001), seq(933001),
                                                   seq(934001), seq(935001), seq(936001), seq(937001),
                                                   seq(938001))
    claims = []
    for n in range(S["claim"]):
        c_id = next(cid)
        status = R.choice(STATUS_POOL)
        created = dt(R.randint(1, 170), R.randint(8, 20), R.randint(0, 59))
        cd = datetime.datetime.strptime(created, "%Y-%m-%d %H:%M:%S")
        insured = "INS-HASH-" + h("insured-%d" % R.randint(1, 400))[:16].upper()
        amt = 0.0
        items = []
        for _ in range(R.randint(1, 4)):
            nm, code, cat = R.choice(ITEM_NAMES)
            qty = R.randint(1, 3)
            up = money(R.uniform(15, 860))
            amount = money(up * qty)
            amt += amount
            valid = amount if R.random() < 0.88 else money(amount * R.uniform(0.3, 0.8))
            pool = money(valid * R.uniform(0.3, 0.7))
            sp1 = money(valid - pool)
            items.append((next(iid), c_id, nm, code, cat, qty, up, amount, "Y" if R.random() < 0.7 else "N",
                          R.choice(["甲类", "乙类", "自费"]), pool, sp1, 0.00, 0.00, 0.00, valid,
                          money(amount - valid), TENANT, finance["id"], created, finance["id"], created, 1, 0,
                          None, None))
        claim_amt = money(amt)
        appr_amt = money(claim_amt * R.uniform(0.55, 0.95)) if status in ("FINISHED", "ADJUSTED", "REVIEWING") else 0.00
        closed = (cd + datetime.timedelta(days=R.randint(2, 20))).strftime("%Y-%m-%d %H:%M:%S") \
            if status in ("FINISHED", "CLOSED_REJECTED") else None
        crows.append((c_id, "CLM-2026-%06d" % (n + 1), "IDEM-CLM-%06d" % (n + 1),
                      "POL-2026-%05d" % R.randint(1, 999), R.randint(2026001, 2026010),
                      insured, "OUTPATIENT", status, status, STAGE_OF[status],
                      0 if status not in ("ADJUSTING",) else R.choice([0, 1]), None,
                      "0%d1%012d" % (R.randint(1, 9), R.randint(0, 999999999999)),
                      "%08d" % R.randint(1, 99999999),
                      (cd - datetime.timedelta(days=R.randint(1, 30))).strftime("%Y-%m-%d %H:%M:%S"),
                      R.choice(["APP", "WECHAT_MINI", "WEB", "TPA_BATCH"]),
                      R.choice(["TPA-PORTAL", "WECHAT-MP", "BATCH-IMPORT"]),
                      "BATCH-%s" % (cd.strftime("%Y%m%d")), claim_amt, appr_amt,
                      approver["id"] if status in ("FINISHED", "ADJUSTED", "REVIEWING", "CLOSED_REJECTED") else "",
                      (auditor["id"] if appr_amt > 5000 and status in ("FINISHED", "ADJUSTED") else ""),
                      ("材料不全，请补充完整病历与费用明细" if status == "CLOSED_REJECTED" else ""),
                      ("MATERIAL_INCOMPLETE" if status == "CLOSED_REJECTED" else ""),
                      "v2.3", TENANT, finance["id"], created, finance["id"], closed or created, closed, 1, 0))
        irows.extend(items)
        claims.append((c_id, "CLM-2026-%06d" % (n + 1), claim_amt, appr_amt, status, created, cd, insured))
        # 证据
        for doc in R.sample(["门诊病历", "费用发票", "费用明细清单", "检查报告单", "处方笺"], R.randint(1, 3)):
            erows.append((next(eid), c_id, doc, "oss://claim-evidence/%s/%s.pdf" % (c_id, h(doc + str(c_id))[:8]),
                          h(doc + str(c_id)), "DONE", round(R.uniform(0.82, 0.99), 2), "Y",
                          j({"docType": doc, "pages": R.randint(1, 4)}), 1,
                          (cd + datetime.timedelta(hours=2)).strftime("%Y-%m-%d %H:%M:%S"),
                          TENANT, finance["id"], created, finance["id"], created, 1, 0))
        # 额度账户（同一被保险人复用）
        q_initial = money(R.choice([10000, 20000, 50000, 100000]))
        claimed = money(min(appr_amt, q_initial * R.uniform(0, 0.8)))
        frozen = money(0 if status in ("FINISHED", "CLOSED_REJECTED") else min(claim_amt * 0.3, q_initial - claimed))
        q_id = next(qid)
        qrows.append((q_id, insured, "POL-2026-%05d" % R.randint(1, 999), R.randint(2026001, 2026010), 2026,
                      "ANNUAL", "LIAB-" + h("liab" + insured)[:12].upper(), q_initial, claimed, frozen,
                      money(R.uniform(0, 1000)), money(claimed / q_initial),
                      R.choice([1, 1, 2]), TENANT, finance["id"], created,
                      finance["id"], created, 1, 0))
        trows.append((next(tid), q_id, c_id, "", "OUT", R.choice(["CLAIM", "FREEZE", "RELEASE"]),
                      claimed, q_initial, money(q_initial - claimed), "IDEM-QTX-%06d" % (n + 1),
                      (cd + datetime.timedelta(days=1)).strftime("%Y-%m-%d %H:%M:%S"), TENANT, created,
                      finance["id"], created, finance["id"], 0))
        # 结算
        if status in ("FINISHED", "ADJUSTED", "REVIEWING") and appr_amt > 0:
            s_id = next(sid)
            sno = "STL-2026-%06d" % (n + 1)
            paid = (cd + datetime.timedelta(days=R.randint(3, 15))).strftime("%Y-%m-%d %H:%M:%S") \
                if status == "FINISHED" else None
            srows.append((s_id, c_id, "CLM-2026-%06d" % (n + 1), sno, appr_amt,
                          "PAID" if status == "FINISHED" else R.choice(["SOFT_RESERVED", "HARD_RESERVED", "REVIEW_FREEZE"]),
                          R.choice(["BANK_CARD", "WECHAT_PAY", "ALIPAY"]), 1, paid,
                          "PAID" if status == "FINISHED" else "REVIEWING",
                          approver["id"] if appr_amt > 5000 else None, TENANT, finance["id"], created,
                          finance["id"], paid or created, 1, 0))
            stages = (["SOFT_RESERVED", "HARD_RESERVED", "REVIEW_FREEZE", "PAID"] if status == "FINISHED"
                      else ["SOFT_RESERVED", "HARD_RESERVED", "REVIEW_FREEZE"])
            et = cd + datetime.timedelta(hours=2)
            for sg in stages:
                frows.append((next(fid), s_id, c_id, sno, sg, appr_amt, finance["name"],
                              "BANK_CARD", "IDEM-FND-%06d-%s" % (n + 1, sg), et.strftime("%Y-%m-%d %H:%M:%S"),
                              TENANT, created, finance["id"], created, finance["id"], 0))
                et += datetime.timedelta(hours=R.randint(4, 30))
        # 申诉
        if status in ("APPEALING", "CLOSED_REJECTED") and R.random() < 0.5:
            submitted = (cd + datetime.timedelta(days=R.randint(1, 10))).strftime("%Y-%m-%d %H:%M:%S")
            decided = R.random() < 0.6
            arows.append((next(aid), "APL-2026-%06d" % (n + 1), c_id, "CLM-2026-%06d" % (n + 1),
                          "CLOSED_REJECTED", "DECIDED" if decided else "SUBMITTED", finance["id"], submitted,
                          (cd + datetime.timedelta(days=(R.randint(12, 30) if decided else 30))).strftime("%Y-%m-%d %H:%M:%S"),
                          auditor["id"] if decided else None,
                          (cd + datetime.timedelta(days=R.randint(11, 28))).strftime("%Y-%m-%d %H:%M:%S") if decided else None,
                          R.choice(["UPHELD", "OVERTURNED"]) if decided else None,
                          "复核认定原结论成立" if decided else None, TENANT, submitted, submitted, 1,
                          finance["id"], finance["id"], 0))
        # AI 裁决轨迹
        if R.random() < 0.25:
            nodes = [("RULE", "责任判定规则引擎"), ("OCR", "票据结构化识别"), ("ML", "欺诈风险评分"),
                     ("POLICY", "条款匹配"), ("DECISION", "自动裁决")]
            for k, (nt, nn) in enumerate(nodes, start=1):
                drows.append((next(did), "TRACE-%06d" % (n + 1), c_id, 790000 + n, k, nt, nn,
                              j({"claimNo": "CLM-2026-%06d" % (n + 1), "amount": claim_amt}),
                              j({"score": round(R.uniform(0.1, 0.95), 2), "hitRules": R.randint(1, 5)}),
                              R.choice(["PASS", "PASS", "REVIEW"]), R.randint(20, 1800), TENANT,
                              created, finance["id"], created, finance["id"], 0))
    add("t_claim_case", ccols, crows)
    add("t_claim_item", icols, irows)
    add("t_claim_evidence", ecols, erows)
    add("t_quota_account", qcols, qrows)
    add("t_quota_transaction", tcols, trows)
    add("settle_order", scols, srows)
    add("settle_funding_event", fcols, frows)
    add("appeal_record", acols, arows)
    add("claim_decision_trace", dcols, drows)
    return claims


# ============================================================ 4. 输出
GOLDEN = {}
CONNECT = []
SEQ_CONN = None


def write_sql():
    os.makedirs(BUILD, exist_ok=True)
    order = ["iam_tenant", "sys_config", "sys_alert_rule", "sys_schedule_task",
             "sys_dict_type", "sys_dict_item", "sys_dict_hierarchy",
             "iam_dept", "iam_role", "iam_role_permission", "iam_account", "iam_account_role",
             "meta_entity", "meta_field",
             "mdm_entity", "mdm_field", "mdm_record", "mdm_category", "mdm_steward", "mdm_steward_scope",
             "mdm_reference_set", "mdm_reference_value", "mdm_quality_issue",
             "bp_channel", "bp_channel_product", "bp_inventory",
             "t_order", "t_order_item", "bp_shipment", "bp_shipment_trace", "bp_payment",
             "wf_process_def", "wf_binding", "wf_instance", "wf_active_instance", "wf_task", "wf_notification",
             "int_connector", "int_flow", "int_flow_node", "int_flow_connection", "int_execution_log",
             "gen_data_source", "gen_table_metadata", "gen_column_metadata", "gen_code_template",
             "gen_generation_task", "gen_code_generation_history",
             "bone_application", "bone_module", "bone_app_permission",
             "exts_extension_point", "exts_extension_impl",
             "cnsl_dashboard_widget", "ntf_message",
             "t_claim_case", "t_claim_item", "t_claim_evidence", "t_quota_account", "t_quota_transaction",
             "settle_order", "settle_funding_event", "appeal_record", "claim_decision_trace"]
    idx = 0
    manifest = []
    for t in order:
        if t not in TABLES:
            continue
        cols, rows = TABLES[t]
        if not rows:
            continue
        idx += 1
        path = os.path.join(BUILD, "%02d_%s.sql" % (idx, t))
        with open(path, "w", encoding="utf-8") as f:
            f.write("-- %s  行数=%d\n" % (t, len(rows)))
            f.write("-- 由 gen.py 确定性生成（seed=%d, scale=%s），请勿手工编辑\n"
            "-- 使用 REPLACE INTO：重复执行幂等，可直接重跑覆盖\n\n" % (SEED, SCALE_NAME))
            collist = ",".join("`%s`" % c for c in cols)
            for i in range(0, len(rows), 300):
                part = rows[i:i + 300]
                vals = ",\n".join("(" + ",".join(sqlval(v) for v in r) + ")" for r in part)
                f.write("REPLACE INTO `%s` (%s) VALUES\n%s;\n" % (t, collist, vals))
        manifest.append((path, t, len(rows)))
    total = sum(m[2] for m in manifest)
    print("\n生成 %d 个 SQL 文件，共 %d 行 -> %s" % (len(manifest), total, BUILD))
    for p, t, n in manifest:
        print("   %-34s %6d 行" % (t, n))
    return manifest


def sqlval(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return str(v)
    s = str(v)
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"


NO_TENANT_PURGE = {
    "t_order_item": "DELETE FROM t_order_item WHERE order_id IN (SELECT id FROM t_order WHERE tenant_id=%d)",
    "iam_role_permission": "DELETE FROM iam_role_permission WHERE role_id IN (SELECT id FROM iam_role WHERE tenant_id=%d)",
}


def apply_db(manifest):
    import _conf
    c = _conf.connect()
    cur = c.cursor()
    cur.execute("SET FOREIGN_KEY_CHECKS=0")
    # 前置清理：保证幂等重跑不残留上一版 ID 的行。
    # 关键：无 tenant_id 的子表必须先快照父表 ID 再删，否则父表被清空后子表无从反查。
    snap = {}
    cur.execute("SELECT id FROM t_order WHERE tenant_id=%s" % TENANT)
    snap["t_order"] = [r[0] for r in cur.fetchall()]
    cur.execute("SELECT id FROM iam_role WHERE tenant_id=%s" % TENANT)
    snap["iam_role"] = [r[0] for r in cur.fetchall()]
    for parent, tbl, col in [("t_order", "t_order_item", "order_id"),
                             ("iam_role", "iam_role_permission", "role_id")]:
        ids = snap.get(parent) or []
        for i in range(0, len(ids), 800):
            part = ids[i:i + 800]
            cur.execute("DELETE FROM `%s` WHERE `%s` IN (%s)" % (
                tbl, col, ",".join(str(x) for x in part)))
    for path, t, n in manifest:
        if t in NO_TENANT_PURGE:
            continue
        try:
            cur.execute("DELETE FROM `%s` WHERE tenant_id=%s" % (t, TENANT))
        except Exception:
            pass
    print("   pre-clean done (orders=%d, roles=%d)" % (len(snap["t_order"]), len(snap["iam_role"])))
    for path, t, n in manifest:
        cols, rows = TABLES[t]
        collist = ",".join("`%s`" % x for x in cols)
        sql = "REPLACE INTO `%s` (%s) VALUES (%s)" % (t, collist, ",".join(["%s"] * len(cols)))
        for i in range(0, len(rows), 1000):
            cur.executemany(sql, rows[i:i + 1000])
        print("   loaded %-28s %6d" % (t, len(rows)))
    cur.execute("SET FOREIGN_KEY_CHECKS=1")
    c.commit()
    c.close()


# ============================================================ 5. 主流程
SCALE_NAME = "medium"


def main():
    global SCALE_NAME, SEQ_CONN
    ap = argparse.ArgumentParser()
    ap.add_argument("--scale", default="medium", choices=list(SCALE))
    ap.add_argument("--apply", action="store_true")
    a = ap.parse_args()
    SCALE_NAME = a.scale
    SEQ_CONN = seq(712001)

    print("BONE UAT 演示数据生成器 · scale=%s · seed=%d · tenant=%d" % (SCALE_NAME, SEED, TENANT))
    build_tenant()
    print("  [1/10] 租户与平台配置")
    dept_ids, accounts = build_org()
    print("  [2/10] 组织 %d 部门 / %d 账号 / 12 角色" % (len(dept_ids), len(accounts)))
    build_dict()
    print("  [3/10] 系统字典")
    ent_ids = build_metadata()
    print("  [4/10] 元数据建模 %d 业务对象" % len(ent_ids))
    mids, recs, prods = build_mdm(ent_ids, accounts)
    print("  [5/10] 主数据 %d 条" % sum(len(v) for v in recs.values()))
    ch_ids, orders = build_commerce(recs, prods, accounts)
    print("  [6/10] 商务 %d 渠道 / %d 订单" % (len(ch_ids), len(orders)))
    build_workflow(mids, accounts, recs)
    print("  [7/10] 工作流")
    build_integration(orders)
    print("  [8/10] 集成")
    build_generator(recs, prods)
    print("  [9/10] 代码生成")
    build_platform(accounts)
    build_claim(accounts)
    print("  [10/10] 平台能力与垂直应用")
    manifest = write_sql()
    if a.apply:
        print("\n灌库 ...")
        apply_db(manifest)
        print("完成。")
    with open(os.path.join(HERE, ".golden.json"), "w", encoding="utf-8") as f:
        json.dump({k: v for k, v in GOLDEN.items()}, f, ensure_ascii=False, indent=1)
    print("黄金动线账号:", {k: v["name"] for k, v in GOLDEN.items()})


if __name__ == "__main__":
    main()
