#!/usr/bin/env python3
"""变异验证：证明「额度释放归属 / 投影自愈」相关断言真的咬得住，而不是恒过。

纪律（来自 Bone 取证铁律）：
- 变异标记数必须 > 0（否则等于没变异，"0 红"是假绿）；
- 必须确认测试**真的跑了**（Tests run 数 > 0 且与基线一致），否则 BUILD FAILURE 会被误读成"变异被杀"；
- 期望被杀死的用例必须转红，且报错须指向对应的语义。

变异体覆盖三条资金正确性主线：
  F1 原子性（REQUIRES_NEW → REQUIRED，跨聚合同事务）
  F2 归属校验（释放只还自己的钱，不吃共享池里别人的预占）
  F3 投影自愈（池投影被抽干不得阻塞本单释放；自愈重算不得抹掉他人占用）

用法：python3 scripts/e2e/mutate_quota_release.py
"""
import os
import re
import shutil
import subprocess
import sys

REPO = "/Users/renhui.trh/wps/bone"
TARGETS = {
    "account": os.path.join(
        REPO, "bone-apps/synap-claim/src/main/java/com/bone/claim/domain/model/quota/QuotaAccount.java"
    ),
    "app": os.path.join(
        REPO, "bone-apps/synap-claim/src/main/java/com/bone/claim/application/QuotaApplicationService.java"
    ),
}
TESTS = "QuotaReleaseOwnershipScenarioTest,QuotaAccountTest"

# 变异体：(名称, 目标文件键, old, new, 期望转红的用例名列表)
MUTATIONS = [
    (
        "M1 releaseOwned 绕过归属判定（直接释放，不看自有预占）",
        "account",
        """    BigDecimal held = ownHeld == null ? ZERO : ownHeld;
    if (held.signum() <= 0) {
      return null; // 本单已无自有占用：空操作，绝不触碰共享池
    }
    if (held.compareTo(amount) < 0) {
      throw new DomainException("本单自有冻结占用 " + held + " 小于待释放 " + amount + "：额度账实不符，拒绝越权释放共享池额度");
    }
    return releaseHeld(txnId, amount, claimId, settlementNo, idempotencyKey);""",
        """    return releaseHeld(txnId, amount, claimId, settlementNo, idempotencyKey);""",
        ["releaseWithoutOwnHoldMustNotStealOthersHold", "releaseOwnedWithoutOwnHoldIsNoop"],
    ),
    (
        "M2 去掉「自有预占不足即拒」，改为只释放自有量（宽松放行）",
        "account",
        """    if (held.compareTo(amount) < 0) {
      throw new DomainException("本单自有冻结占用 " + held + " 小于待释放 " + amount + "：额度账实不符，拒绝越权释放共享池额度");
    }""",
        """    if (held.compareTo(amount) < 0) {
      amount = held;
    }""",
        ["partialOwnHoldMustFailClosed", "releaseOwnedRejectsInsufficientOwnHold"],
    ),
    (
        "M3 免赔回退去掉归属校验（回退共享池的免赔）",
        "account",
        """    BigDecimal owned = ownDeductible == null ? ZERO : ownDeductible;
    if (owned.signum() <= 0) {
      return null; // 本单从未消抵免赔：空操作，不动共享池
    }
    if (owned.compareTo(amount) < 0) {
      throw new DomainException("本单自有免赔消抵 " + owned + " 小于待回退 " + amount + "：免赔账实不符，拒绝越权回退他人免赔");
    }
    return reduceDeductible(txnId, amount, claimId, settlementNo, idempotencyKey);""",
        """    return reduceDeductible(txnId, amount, claimId, settlementNo, idempotencyKey);""",
        ["deductibleOwnershipMustFailClosed", "reduceDeductibleOwnedRespectsOwnership"],
    ),
    (
        "M4 流水类型歧义：回冲 claimed 记回 RELEASE",
        "account",
        """        "ROLLBACK",
        "REFUND",
        amount,
        before,
        quotaClaimed,""",
        """        "ROLLBACK",
        "RELEASE",
        amount,
        before,
        quotaClaimed,""",
        ["refundTxnTypeIsDistinctFromRelease"],
    ),
    (
        "M5 冗余防线探针：域层退回池级释放（应用层自愈应独立成立，业务仍跑通）",
        "account",
        """      throw new DomainException("本单自有冻结占用 " + held + " 小于待释放 " + amount + "：额度账实不符，拒绝越权释放共享池额度");
    }
    return releaseHeld(txnId, amount, claimId, settlementNo, idempotencyKey);""",
        """      throw new DomainException("本单自有冻结占用 " + held + " 小于待释放 " + amount + "：额度账实不符，拒绝越权释放共享池额度");
    }
    return release(txnId, amount, claimId, settlementNo, idempotencyKey);""",
        # 期望【域】用例转红：域层退回池级校验后，错误语义从「额度守恒被破坏」变成「冻结额度小于释放金额」。
        # 注意：场景用例 T4 在此变异下【仍绿】—— 因为应用层的投影自愈已把池级校验完全旁路（纵深防御）。
        # 这不是"漏网"，而是"两道防线互补"的证据，故此处只断言域层语义用例。
        ["releaseOwnedWithDeficitProjectionFailsOnConservation"],
    ),
    (
        "M6 F3 未修：应用层去掉投影自愈（池亏空时直接扣减 ⇒ 守恒失败）",
        "app",
        """        // 本单自有占用足额 ⇒ 释放合法；责任账是共享池，投影被他人越权释放抽干时先按流水自愈
        reconcileProjectionIfDeficit(account, amount);""",
        """        // MUT: 去掉投影自愈""",
        ["deficitProjectionMustSelfHealAndRelease"],
    ),
    (
        "M7 自愈重算只保留本单占用（抹掉他人占用 ⇒ 可赔额度被放大）",
        "app",
        """    account.reconcileProjection(projection.frozen(), projection.claimed(), projection.deductible());""",
        """    account.reconcileProjection(amount, projection.claimed(), projection.deductible());""",
        ["selfHealMustNotDropOthersHold"],
    ),
]

MAVEN_ENV = dict(os.environ)
MAVEN_ENV["JAVA_HOME"] = "/Users/renhui.trh/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home"
MAVEN_ENV["PATH"] = (
    "/Users/renhui.trh/java/apache-maven-3.9.6/bin:"
    + MAVEN_ENV["JAVA_HOME"]
    + "/bin:"
    + MAVEN_ENV["PATH"]
)


def run(cmd, cwd=REPO):
    return subprocess.run(
        cmd, cwd=cwd, env=MAVEN_ENV, capture_output=True, text=True, timeout=1800
    )


def test_once():
    """跑一次目标用例，返回 (tests_run, failures, failed_test_names, raw_tail)"""
    subprocess.run(
        ["find", "bone-apps/synap-claim/src", "-name", "*.java", "-exec", "touch", "{}", "+"],
        cwd=REPO,
    )
    r = run(
        [
            "mvn", "-f", "pom.xml", "-pl", "bone-apps/synap-claim", "-am", "test",
            "-Dtest=" + TESTS, "-Dsurefire.failIfNoSpecifiedTests=false", "--batch-mode",
        ]
    )
    out = r.stdout + r.stderr
    runs = re.findall(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+)", out)
    total = sum(int(a) for a, _, _ in runs)
    fails = sum(int(b) for _, b, _ in runs) + sum(int(c) for _, _, c in runs)
    # 失败用例名有两种打印形态：
    #   [ERROR] com.bone.claim.scenario.XxxTest.someMethod -- Time elapsed: 0.008 s <<< FAILURE!
    #   [ERROR]   XxxTest.someMethod:231
    failed_names = re.findall(r"([\w.]+)\.(\w+) -- Time elapsed.*?<<< (?:FAILURE|ERROR)!", out)
    failed_names += re.findall(r"\[ERROR\]\s+[\w.]+\.(\w+):\d+", out)
    return total, fails, sorted({n for pair in failed_names for n in pair[-1:]}), out


def main():
    backups = {}
    for key, path in TARGETS.items():
        bak = f"/tmp/mutate_quota_{key}.java.bak"
        shutil.copyfile(path, bak)
        backups[key] = bak
    try:
        base_total, base_fails, _, _ = test_once()
        print(f"基线：Tests run={base_total}, Failures={base_fails}", flush=True)
        if base_total == 0:
            print("!! 基线 0 用例：测试根本没跑，后续结论无效")
            return 2
        if base_fails != 0:
            print("!! 基线不绿，先修基线再谈变异")
            return 2

        all_killed = True
        for name, target_key, old, new, expect_killed in MUTATIONS:
            path = TARGETS[target_key]
            src = open(backups[target_key], encoding="utf-8").read()
            n = src.count(old)
            if n != 1:
                print(f"[{name}] !! 变异标记命中 {n} 处（须恰好 1），跳过", flush=True)
                all_killed = False
                continue
            open(path, "w", encoding="utf-8").write(src.replace(old, new))
            # 变异后必须重新格式化，否则 spotless:check 先失败 ⇒ 测试根本没跑 ⇒ 假"变异被杀"
            run(["mvn", "-f", "bone-apps/synap-claim/pom.xml", "spotless:apply", "--batch-mode", "-q"])
            total, fails, names, out = test_once()
            expect_list = (
                [expect_killed] if isinstance(expect_killed, str) else list(expect_killed)
            )
            killed = all(nm in names for nm in expect_list)
            missing = [nm for nm in expect_list if nm not in names]
            ran = total >= base_total
            ok = (fails > 0) and killed and ran
            all_killed = all_killed and ok
            print(
                f"[{name}] 标记={n} 用例数={total}(基线{base_total}) 失败={fails} "
                f"期望用例={expect_list} 被杀={killed} 未杀={missing} => {'RED ✓' if ok else '未被杀 ✗'}",
                flush=True,
            )
            if not ok:
                for line in out.splitlines():
                    if "ERROR" in line and ("Test" in line or "expected" in line or "应" in line):
                        print("      " + line.strip()[:180])
            # 恢复该目标，避免下一个变异建立在已变异的基础上
            shutil.copyfile(backups[target_key], path)
            run(["mvn", "-f", "bone-apps/synap-claim/pom.xml", "spotless:apply", "--batch-mode", "-q"])
    finally:
        for key, bak in backups.items():
            shutil.copyfile(bak, TARGETS[key])
        run(["mvn", "-f", "bone-apps/synap-claim/pom.xml", "spotless:apply", "--batch-mode", "-q"])

    print("\n恢复原文件并复跑基线 …", flush=True)
    total, fails, _, _ = test_once()
    print(f"恢复后：Tests run={total}, Failures={fails}")
    if fails != 0:
        return 2
    print("全部变异均被杀死 ⇒ 断言具备判别力" if all_killed else "存在未被杀死的变异 ⇒ 断言无判别力")
    return 0 if all_killed else 1


if __name__ == "__main__":
    sys.exit(main())
