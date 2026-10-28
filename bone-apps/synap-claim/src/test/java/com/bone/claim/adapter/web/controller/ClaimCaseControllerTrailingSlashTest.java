package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.adapter.web.dto.request.ClaimPageReq;
import com.bone.claim.adapter.web.dto.request.SubmitClaimReq;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 集合根路径的<b>尾斜杠</b>契约测试。
 *
 * <p><b>为什么值得单独测</b>（2026-10-28 全模块联调实测确诊）：前端 {@code createApiClient('/api/v1/claims')} 发出 {@code
 * axios.get('/')} 时，axios 的 {@code combineURLs} 会<b>恒补一个 '/'</b>，最终请求 {@code /api/v1/claims/}。而
 * Spring Boot 3 默认使用 {@code PathPatternParser} 且<b>不做尾斜杠匹配</b>， 于是：
 *
 * <ul>
 *   <li>核赔工作台「待办列表」→ 404「请求地址不存在」→ 前端弹「待办加载失败」；
 *   <li>理赔申请「提交报案」→ 404 → 报案根本提交不出去。
 * </ul>
 *
 * <p>两者都不报编译错、也不被既有单测覆盖，只在真实点击时暴露。这里把「集合根必须同时登记 {@code ""} 与 {@code "/"}」钉死，把注解改回
 * {@code @PostMapping} / {@code @GetMapping}（无显式路径）即红。
 */
class ClaimCaseControllerTrailingSlashTest {

  @Test
  @DisplayName("POST 报案：集合根必须同时登记 \"\" 与 \"/\"（axios 会拼出尾斜杠）")
  void submitMapsTrailingSlash() throws Exception {
    Method submit =
        ClaimCaseController.class.getMethod(
            "submit", String.class, SubmitClaimReq.class, HttpServletRequest.class);
    PostMapping mapping = submit.getAnnotation(PostMapping.class);
    assertThat(mapping).as("submit 必须标注 @PostMapping").isNotNull();
    assertThat(mapping.value())
        .as("POST /api/v1/claims/ 必须可达：axios 的 base + '/' 会拼出尾斜杠")
        .containsExactlyInAnyOrder("", "/");
  }

  @Test
  @DisplayName("GET 待办列表：集合根必须同时登记 \"\" 与 \"/\"")
  void pageMapsTrailingSlash() throws Exception {
    Method page = ClaimCaseController.class.getMethod("page", ClaimPageReq.class);
    GetMapping mapping = page.getAnnotation(GetMapping.class);
    assertThat(mapping).as("page 必须标注 @GetMapping").isNotNull();
    assertThat(mapping.value())
        .as("GET /api/v1/claims/ 必须可达：axios 的 base + '/' 会拼出尾斜杠")
        .containsExactlyInAnyOrder("", "/");
  }
}
