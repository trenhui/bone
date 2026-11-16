package com.bone.masterdata.testsupport.supplier;

import com.bone.masterdata.testsupport.goldenrecord.InMemoryEntityKeyMappingAdapter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 供应商主数据切片的<b>仅测试可见</b>端口装配。
 *
 * <p>这里把「生产尚未有实现」的端口全部接到内存/裁决夹具：<b>出站仓储</b>（Supplier / Vendor / LegalEntity）、 <b>上游事实</b>（退出三域 /
 * 变更请求 / 财务闸门 / 付款冻结复核）与<b>守卫</b>（准入 / 退出 / Vendor 闸门）， 并复用 Golden Record 既有的 {@link
 * InMemoryEntityKeyMappingAdapter} 作为 xref 端口。
 *
 * <p><b>为什么必须存在这份配置</b>：{@code bone.procurement.supplier-mdm.enabled=true} 时 {@code
 * SupplierApplicationService} 要求全部依赖齐备；生产制品里<b>没有</b>这些实现，因此生产打开开关会启动失败 ——
 * 这正是能力开关要守的边界：没有事实来源时宁可起不来，也不允许守卫在没有事实的情况下判定「可以退出 / 可以开通」。
 *
 * <p>返回类型写成具体夹具类（而非端口接口）：Spring 按接口注入时可唯一匹配， 不会因为同一实例注册两次而产生 {@code
 * NoUniqueBeanDefinitionException}。
 */
@TestConfiguration
public class SupplierHttpTestConfiguration {

  @Bean
  public InMemorySupplierRepositoryPort supplierRepositoryPort() {
    return new InMemorySupplierRepositoryPort();
  }

  @Bean
  public InMemoryVendorRepositoryPort vendorRepositoryPort() {
    return new InMemoryVendorRepositoryPort();
  }

  @Bean
  public InMemoryLegalEntityRepositoryPort legalEntityRepositoryPort() {
    return new InMemoryLegalEntityRepositoryPort();
  }

  @Bean
  public InMemoryEntityKeyMappingAdapter supplierEntityKeyMappingPort() {
    return new InMemoryEntityKeyMappingAdapter();
  }

  @Bean
  public InMemorySupplierExitFacts supplierExitFactsPort() {
    return new InMemorySupplierExitFacts();
  }

  @Bean
  public InMemorySupplierOnboardingFacts supplierOnboardingFactsPort() {
    return new InMemorySupplierOnboardingFacts();
  }

  @Bean
  public InMemoryVendorGateFacts vendorGateFactsPort() {
    return new InMemoryVendorGateFacts();
  }

  @Bean
  public InMemoryPaymentBlockReleaseReview paymentBlockReleaseReviewPort() {
    return new InMemoryPaymentBlockReleaseReview();
  }

  @Bean
  public TestSupplierExitGuard supplierExitGuard() {
    return new TestSupplierExitGuard();
  }

  @Bean
  public TestSupplierOnboardingGuard supplierOnboardingGuard() {
    return new TestSupplierOnboardingGuard();
  }

  @Bean
  public TestVendorActivationGuard vendorActivationGuard() {
    return new TestVendorActivationGuard();
  }
}
