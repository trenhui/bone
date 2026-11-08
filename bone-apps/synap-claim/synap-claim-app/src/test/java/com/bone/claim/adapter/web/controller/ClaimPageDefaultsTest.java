package com.bone.claim.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.claim.adapter.web.assembler.ClaimCaseAssembler;
import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.core.model.PageResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code GET /api/v1/claims} 分页参数的「文档默认值」契约测试。
 *
 * <p><b>为什么需要这个测试</b>：2026-10-30 全模块联调实测，不带 {@code page}/{@code size} 直接调用列表接口 返回 400「Failed to
 * convert value of type 'null' to required type 'int'」。根因是 {@code ClaimPageReq} 把两个分页字段声明成<b>原始类型
 * {@code int}</b>，而 {@code @Schema(defaultValue=...)} 只作用于 OpenAPI 文档、<b>不参与数据绑定</b> ⇒
 * 文档宣称的默认值在运行时根本不存在。此处锁定「不传 = 1/20，传了 = 原样」。
 *
 * <p><b>判别力</b>：把组件类型改回 {@code int}（去掉兜底）后，第一个用例必须转红（400 ≠ 200）。
 */
class ClaimPageDefaultsTest {

  private ClaimCaseApplicationService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(ClaimCaseApplicationService.class);
    when(service.page(any(), isNull(), anyInt(), anyInt()))
        .thenReturn(PageResult.of(List.<ClaimHeadProjection>of(), 0L, 1, 20));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ClaimCaseController(service, null, ClaimCaseAssembler.INSTANCE, null))
            .build();
  }

  @Test
  @DisplayName("不带 page/size 时按文档声明的默认值兜底（此前是 400）")
  void omitsPagination_thenFallsBackToDocumentedDefaults() throws Exception {
    mvc.perform(get("/api/v1/claims")).andExpect(status().isOk());

    ArgumentCaptor<Integer> page = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
    verify(service).page(any(), isNull(), page.capture(), size.capture());
    assertEquals(1, page.getValue(), "不传 page 应缺省 1（@Schema defaultValue=1）");
    assertEquals(20, size.getValue(), "不传 size 应缺省 20（@Schema defaultValue=20）");
  }

  @Test
  @DisplayName("显式传参时原样透传，不被兜底值覆盖")
  void explicitPagination_thenPassedThrough() throws Exception {
    mvc.perform(get("/api/v1/claims").param("page", "3").param("size", "50"))
        .andExpect(status().isOk());

    ArgumentCaptor<Integer> page = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
    verify(service).page(any(), isNull(), page.capture(), size.capture());
    assertEquals(3, page.getValue());
    assertEquals(50, size.getValue());
  }

  @Test
  @DisplayName("兜底不等于取消校验：越界值仍 400")
  void defaultsDoNotDisableValidation() throws Exception {
    mvc.perform(get("/api/v1/claims").param("page", "0")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/claims").param("size", "200")).andExpect(status().isBadRequest());
  }
}
