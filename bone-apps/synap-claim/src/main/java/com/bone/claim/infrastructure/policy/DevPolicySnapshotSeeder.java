package com.bone.claim.infrastructure.policy;

import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.application.port.out.PolicyQueryPort.PolicySnapshot;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * dev 环境保单快照种子器（存量同步入口的本地替身，§3.5）。
 *
 * <p>一期 {@link PolicyQueryPortAdapter} 为进程内存桩，报案前置校验（保单生效中 + 被保人匹配 +
 * 出险时间在保期内）拒收无快照案件。本地联调需要一条可复现的测试保单，故在 dev profile 下灌入：
 *
 * <ul>
 *   <li>{@value #E2E_POLICY_NO}（planId {@value #E2E_PLAN_ID}）：生效窗口 2020-2030，供正常链路联调；
 *   <li>{@value #EXPIRED_POLICY_NO}（planId {@value #EXPIRED_PLAN_ID}）：已失效，供校验拒绝路径复现。
 * </ul>
 *
 * <p><b>生产不激活</b>：{@code @Profile("dev")} 门控，生产/预发无此 Bean；二期接入真实保单系统后本类随之删除， 与应用层零感知（防腐层 ACL
 * 价值即在此）。键口径 = {@code policyNo + "|" + planId}。
 */
@Component
@Profile("dev")
public class DevPolicySnapshotSeeder implements ApplicationRunner {

  /** 联调测试保单号（生效中）。 */
  public static final String E2E_POLICY_NO = "E2E-POLICY-0001";

  /** 联调测试计划ID（生效中）。 */
  public static final long E2E_PLAN_ID = 9001L;

  /** 联调测试被保人假名化ID（报案请求须原值携带，服务端比对快照）。 */
  public static final String E2E_INSURED_HASH = "e2e-insured-hash-0001";

  /** 联调测试保单号（已失效，验证拒收路径）。 */
  public static final String EXPIRED_POLICY_NO = "E2E-POLICY-EXPIRED";

  /** 联调测试计划ID（已失效）。 */
  public static final long EXPIRED_PLAN_ID = 9002L;

  private final PolicyQueryPort policyQueryPort;

  public DevPolicySnapshotSeeder(PolicyQueryPort policyQueryPort) {
    this.policyQueryPort = policyQueryPort;
  }

  @Override
  public void run(ApplicationArguments args) {
    // 经出站端口灌数：接口只暴露查询，写入走具体适配器的存量同步入口（upsert）；
    // dev 种子器是唯一允许耦合具体适配器的地方（二期删除本类即解除耦合）。
    if (!(policyQueryPort instanceof PolicyQueryPortAdapter adapter)) {
      return;
    }
    adapter.upsert(
        new PolicySnapshot(
            E2E_POLICY_NO,
            E2E_PLAN_ID,
            "v1",
            E2E_INSURED_HASH,
            Instant.parse("2020-01-01T00:00:00Z"),
            Instant.parse("2030-12-31T23:59:59Z")));
    adapter.upsert(
        new PolicySnapshot(
            EXPIRED_POLICY_NO,
            EXPIRED_PLAN_ID,
            "v1",
            E2E_INSURED_HASH,
            Instant.parse("2020-01-01T00:00:00Z"),
            Instant.parse("2020-12-31T23:59:59Z")));
  }
}
