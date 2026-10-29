# -*- coding: utf-8 -*-
"""生产化测试数据 —— e2e 造出来的数据要「可读、像生产、能串成链」。

口径对齐 scripts/seed/uat/gen.py 的设计红线（词表同读单一真源 wordlist.json，见下）：
  A 拟真可读：显示名/编码全部来自受控中文词典，无 UUID、无 test123、无纯随机串
  B 幂等确定：同一 seed ⇒ 同一份名称/金额/拆分（复跑可比对、残留可归因）
  F 数据老化：业务日期由运行日倒推，读起来是「最近发生过的业务」
  G 金额守恒：split() 拆出的明细 Σ 恒等于总额（两位小数精确守恒），
             与 95_validate.py G2「订单总额 = Σ明细小计」同一口径
  H 单号可读：BX-20261028-0173 这类「业务前缀-日期-序号」，一眼可读可 grep

与 gen.py 的关系：gen.py 造 UAT 演示大盘（数千条、固定 SEED），本模块造
e2e 小数据（每次运行几十条、按运行后缀取种子），职责不同不共库；词表单一
真源是 scripts/seed/uat/wordlist.json（本模块只取其 e2e 口径键与共用键），
改词条只改该 JSON，gen.py / fixtures.py / fixtures.cjs 三处不再互抄同步。

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
import json
import os
import random

# ---- 词表：单一真源 scripts/seed/uat/wordlist.json（gen.py / fixtures.py / fixtures.cjs
# 同读一份，增删词条只改该 JSON）。此处取 e2e 口径键（*_e2e）与三侧共用键；
# 路径按 __file__ 定位（scripts/e2e/e2e_lib → ../../seed/uat），与运行 cwd 无关。
_WL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "seed", "uat", "wordlist.json")
with open(_WL_PATH, encoding="utf-8") as _wl_file:
    _WL = json.load(_wl_file)

SURNAMES = [x[0] for x in _WL["surname"]]
GIVEN = list(_WL["given_e2e"])
CITY_WORD = list(_WL["city_word_e2e"])
COMPANY_SUFFIX = list(_WL["company_suffix"])
SUPPLIER_SUFFIX = list(_WL["supplier_suffix"])
CITIES = list(_WL["cities"])
DEPTS = list(_WL["depts"])

# 品类 → (系列名, 规格档位, 价格区间)——取 gen.py product_cat 的 e2e 精简口径
PRODUCTS = [(name, list(tiers), tuple(price)) for name, tiers, price in _WL["products_e2e"]]
CHANNELS = [tuple(x[:2]) for x in _WL["channels"]]
WAREHOUSES = [tuple(x) for x in _WL["warehouses"]]

# 设备资产（名称与 gen.py asset_cat 同取共享词表）：类别码用于生成 EQ-SMT-0347 式可读设备编号
EQUIPMENT = [tuple(x) for x in _WL["equipment_e2e"]]

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
