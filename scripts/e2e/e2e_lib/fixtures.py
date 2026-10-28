# -*- coding: utf-8 -*-
"""生产化测试数据 —— e2e 造出来的数据要「可读、像生产、能串成链」。

口径对齐 scripts/seed/uat/gen.py 的设计红线（词表为其精简镜像，见下）：
  A 拟真可读：显示名/编码全部来自受控中文词典，无 UUID、无 test123、无纯随机串
  B 幂等确定：同一 seed ⇒ 同一份名称/金额/拆分（复跑可比对、残留可归因）
  F 数据老化：业务日期由运行日倒推，读起来是「最近发生过的业务」
  G 金额守恒：split() 拆出的明细 Σ 恒等于总额（两位小数精确守恒），
             与 95_validate.py G2「订单总额 = Σ明细小计」同一口径
  H 单号可读：BX-20261028-0173 这类「业务前缀-日期-序号」，一眼可读可 grep

与 gen.py 的关系：gen.py 造 UAT 演示大盘（数千条、固定 SEED），本模块造
e2e 小数据（每次运行几十条、按运行后缀取种子），职责不同不共库；词表是
精简镜像（UAT 侧文件由他人维护、不改其文件），新增词条两处同步。

用法：
    from e2e_lib import fixtures as fx

    ds = fx.dataset(SUFFIX)              # 同后缀 ⇒ 同数据（名称/金额/拆分恒定）
    ds.doc_no("BX")                      # BX-20261028-0173（业务前缀-日期-序号）
    ds.amount()                          # 1376.42 —— 非圆整、带业务量级
    ds.split(2863.50, 3)                 # Σ == 2863.50 的明细拆分（守恒）
    ds.split(2863.50, 3, break_=True)    # Σ != 总额（submitRules 422 负向专用）
    ds.person() / ds.company() / ds.sku() / ds.tax_no() / ds.equipment()
    ds.city / ds.dept                      # 属性（构造期定死）：城市、部门
    ds.days_ago(7)                         # "2026-10-21"（运行日倒推的业务日期）
    ds.customer / ds.supplier            # 跨单据共享的业务身份（编码+名称，能串链）

设计约束：
  * 词表只读，生成走 random.Random(seed)，不碰全局 random（防互相污染）。
  * 金额一律 round 2 且带分位；整分金额会被 +0.13 打散（避免 140/1000 圆整数）。
  * 业务身份（customer/supplier/channel…）在构造时一次性确定并挂到实例上，
    主表、子表、动作入参引用同一对象 ⇒ 天然串成一条链。
"""
import datetime
import random

# ---- 词表（scripts/seed/uat/gen.py 的精简镜像，两处同步维护）----
SURNAMES = list("王李张刘陈杨黄赵周吴徐孙马朱胡林郭何高罗郑梁谢宋唐许韩冯邓曹彭曾肖田董袁潘蒋蔡余杜叶程苏魏吕丁任沈姚卢姜崔钟")
GIVEN = ["伟", "芳", "娜", "敏", "静", "丽", "强", "磊", "军", "洋", "勇", "艳", "杰", "娟", "涛",
         "明", "超", "霞", "平", "建华", "文博", "思远", "嘉怡", "子轩", "浩然", "梓涵", "雨欣",
         "俊杰", "心怡", "宇航", "佳怡", "泽宇", "诗涵", "一诺", "沐辰", "若曦", "晨曦", "博文",
         "雅静", "天佑", "瑞泽", "皓轩", "欣妍", "建国", "秀英", "桂兰", "志强", "晓东", "海燕",
         "雪梅", "春林"]

CITY_WORD = ["瀚海", "云枢", "恒昇", "启元", "中科", "华创", "锐驰", "博远", "鼎新", "同辉", "安捷",
             "天工", "汇通", "本源", "灵犀", "长风", "弘远", "智联", "普瑞", "嘉合", "越洋", "沐光",
             "川流", "青云", "蓝图", "金诚", "立行", "广联", "纳川", "协同", "元启", "德茂", "联创",
             "万象", "新宇", "恒信", "智源", "致远", "开源"]
COMPANY_SUFFIX = ["科技有限公司", "智能装备有限公司", "智能制造有限公司", "电子科技有限公司",
                  "自动化技术有限公司", "精密机械有限公司", "信息技术有限公司", "数字科技有限公司",
                  "供应链管理有限公司", "工业设备有限公司"]
SUPPLIER_SUFFIX = ["精密机械有限公司", "电子元件有限公司", "新材料有限公司", "模具制造有限公司",
                   "塑胶制品有限公司", "五金制品有限公司", "包装材料有限公司", "电机有限公司",
                   "传感器有限公司", "线束有限公司"]
CITIES = ["上海市浦东新区", "广东省深圳市", "江苏省苏州市", "浙江省杭州市", "北京市海淀区",
          "山东省青岛市", "四川省成都市", "湖北省武汉市", "福建省厦门市", "陕西省西安市"]
DEPTS = ["销售一部", "销售二部", "供应链中心", "制造中心", "质量部", "研发中心", "华东大区", "客服中心"]

# 品类 → (系列名, 规格档位, 价格区间)——取 gen.py PRODUCT_CAT 代表性子集
PRODUCTS = [
    ("智能扫地机器人", ["X1", "X2", "Pro", "Max", "S2"], (1299, 4299)),
    ("无线吸尘器", ["V10", "V12", "V15", "G5"], (699, 2499)),
    ("边缘计算网关", ["EG-2000", "EG-3000", "EG-5000X"], (1899, 6800)),
    ("伺服驱动器", ["SD-400", "SD-750", "SD-1500"], (1280, 5400)),
    ("工业相机", ["IC-500W", "IC-1200W", "IC-2000W"], (2200, 9800)),
    ("温振传感器", ["VS-100", "VS-200", "VS-300H"], (560, 1800)),
    ("HEPA 滤网", ["标准版", "升级版", "除菌版"], (59, 199)),
    ("锂电池组", ["3200mAh", "5200mAh", "6400mAh"], (129, 399)),
]
CHANNELS = [("TAOBAO", "淘宝旗舰店"), ("JD", "京东自营店"), ("DOUYIN", "抖音小店"),
            ("PDD", "拼多多官方店"), ("WECHAT_MINI", "微信小程序商城"), ("OFFICIAL", "官网商城")]
WAREHOUSES = [("WH-SH-PD", "上海浦东中心仓"), ("WH-GD-SZ", "深圳华南仓"), ("WH-JS-SZ", "苏州华东仓")]

# 设备资产（镜像 gen.py ASSET_CAT 名称）：类别码用于生成 EQ-SMT-0347 式可读设备编号
EQUIPMENT = [("生产设备", "SMT", "高速贴片机"), ("生产设备", "CNC", "数控加工中心"),
             ("生产设备", "IMM", "立式注塑机"), ("生产设备", "LSR", "激光切割机"),
             ("生产设备", "TEST", "自动化测试台"), ("IT 设备", "SRV", "机架服务器"),
             ("IT 设备", "SW", "核心交换机"), ("运输工具", "FLT", "电动叉车")]

# 统一社会信用代码假名生成用的固定主体段（纯虚构、不指向任何真实企业）
_TAX_BODY = "91310000MA1FL0"


class Dataset:
    """一份按 seed 确定的生产化数据集（名称/金额/拆分恒定，日期随运行日老化）。"""

    def __init__(self, seed):
        self.seed = str(seed)
        self.rng = random.Random("bone-e2e:" + self.seed)
        self._seq = (int("".join(c for c in self.seed if c.isdigit()) or "0") % 9000) + 1000
        self.today = datetime.date.today()
        # 跨单据共享的业务身份：主表/子表/动作入参都引用它们 ⇒ 链路可串
        self.customer = self._identity("CUST")
        self.supplier = self._identity("SUP", suffix_pool=SUPPLIER_SUFFIX)
        self.channel_code, self.channel_name = self.rng.choice(CHANNELS)
        self.warehouse_code, self.warehouse_name = self.rng.choice(WAREHOUSES)
        self.city = self.rng.choice(CITIES)
        self.employee = self.person()
        self.manager = self.person()      # 第二个人（SoD 审批人/复核人）
        self.dept = self.rng.choice(DEPTS)

    # ---------------- 身份与名称 ----------------
    def _identity(self, prefix, word_pool=None, suffix_pool=None):
        """业务身份：{code, name}，编码在数据集内唯一且稳定。"""
        word = self.rng.choice(word_pool or CITY_WORD)
        suf = self.rng.choice(suffix_pool or COMPANY_SUFFIX)
        code = "%s-%04d" % (prefix, self.rng.randint(1, 9999))
        return {"code": code, "name": word + suf}

    def person(self):
        return self.rng.choice(SURNAMES) + self.rng.choice(GIVEN)

    def company(self):
        return self.rng.choice(CITY_WORD) + self.rng.choice(COMPANY_SUFFIX)

    # 注：城市/部门是**实例属性**（ds.city / ds.dept，构造期定死、数据集内恒定），
    # 曾误设同名方法被属性遮蔽成死代码，已删——调用方一律按属性取值。

    def sku(self):
        """商品：(名称, 规格, 参考单价) —— 名称/规格/价格同源自洽。"""
        name, tiers, price_range = self.rng.choice(PRODUCTS)
        tier = self.rng.choice(tiers)
        unit = self.amount(lo=price_range[0], hi=price_range[1])
        return name + " " + tier, unit

    def tax_no(self):
        """虚构统一社会信用代码（固定主体段 + 确定性后缀）。"""
        tail = "".join(self.rng.choice("0123456789ABCDEFGHJKLMNPQRTUWXY") for _ in range(8))
        return _TAX_BODY + tail

    def equipment(self):
        """设备资产：(类别, 设备编号, 设备名称) —— EQ-SMT-0347 式可读编号。"""
        cat, code, name = self.rng.choice(EQUIPMENT)
        return cat, "EQ-%s-%04d" % (code, self.rng.randint(1, 9999)), name

    # ---------------- 单号与日期 ----------------
    def doc_no(self, prefix):
        """业务单号：BX-20261028-0173（前缀-日期-确定性序号，可读可 grep）。"""
        self._seq += 1
        return "%s-%s-%04d" % (prefix, self.today.strftime("%Y%m%d"), self._seq % 10000)

    def days_ago(self, n):
        """运行日倒推 n 天的业务日期（数据老化，读起来像最近发生的事）。"""
        return (self.today - datetime.timedelta(days=n)).strftime("%Y-%m-%d")

    # ---------------- 金额 ----------------
    def amount(self, lo=200, hi=9000):
        """非圆整金额（带分位）；整分金额打散，杜绝 140/1000 这类机器味圆整数。"""
        v = round(self.rng.uniform(lo, hi), 2)
        if abs(v - round(v)) < 0.005:          # 整分 ⇒ 加个分位
            v = round(v + self.rng.choice([0.13, 0.27, 0.42, 0.58, 0.66, 0.79]), 2)
        return v

    def split(self, total, n, break_=False):
        """把 total 拆成 n 条明细，Σ 恒等于 total（两位小数精确守恒）。

        break_=True 时让 Σ 与 total 差 1.01，专供「明细合计≠申报 → 422」负向断言，
        差值固定使失败信息可预期。
        """
        total = round(float(total), 2)
        weights = [self.rng.uniform(0.5, 1.5) for _ in range(n)]
        s = sum(weights)
        parts = [round(total * w / s, 2) for w in weights[:-1]]
        last = round(total - sum(parts), 2)
        # 权重∈[0.5,1.5] ⇒ last 恒 > 0（最坏 0.25·total），不设下限兜底以保精确守恒
        parts.append(last)
        if break_:
            parts[0] = round(parts[0] + 1.01, 2)
        return parts


_CACHE = {}


def dataset(seed):
    """取按 seed 确定的数据集（同 seed 同数据；重复调用返回同类新实例）。"""
    key = str(seed)
    if key not in _CACHE:
        _CACHE[key] = Dataset(key)
    return _CACHE[key]
