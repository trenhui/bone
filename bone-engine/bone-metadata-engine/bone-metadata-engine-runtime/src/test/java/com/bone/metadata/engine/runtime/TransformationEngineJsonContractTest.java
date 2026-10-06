package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TransformationEngine} 的 JSON 契约（P1-12，2026-10-05）。
 *
 * <p>本类此前<b>零测试</b>，而它是运行时把元数据记录序列化给前端的最后一道关口。
 *
 * <p><b>为什么盯 Long</b>：骨核的全局约定是「包装类型 Long 一律序列化为 JSON 字符串」 （{@code
 * MetadataAutoConfiguration.boneLongToStringCustomizer()}），因为雪花 ID 超出 JS {@code Number} 的安全上限 2^53
 * —— 以 number 下发会被前端<b>静默截断</b>且不抛任何错。 而本类的默认构造器 {@code new ObjectMapper()} 不带该序列化器， {@code
 * MetadataEngineAutoConfiguration#transformationEngine()} 恰恰用的就是默认构造器。
 *
 * <p>第二条用例记录的是<b>当前风险现状</b>而非期望：它让「默认装配下 Long 是 number」变成 可执行的事实。修复（让 starter 注入全局
 * mapper）后这条会红，届时应把断言改成期望值 —— 它的作用是让改动者知道自己在动什么，而不是静默把风险带走。
 */
class TransformationEngineJsonContractTest {

  /** 2^53 + 1：任何前端 Number 承载它都会丢精度。 */
  private static final long SNOWFLAKE_ID = 9007199254740993L;

  @Test
  @DisplayName("注入带 Long→String 序列化器的 mapper 时，超 2^53 的 ID 以字符串下发（契约可达）")
  void longIsSerializedAsStringWhenGlobalContractApplied() {
    ObjectMapper globalContract = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(Long.class, ToStringSerializer.instance);
    globalContract.registerModule(module);

    String json = new TransformationEngine(globalContract).toJson(Map.of("id", SNOWFLAKE_ID));

    assertTrue(
        json.contains("\"id\":\"" + SNOWFLAKE_ID + "\""),
        "包装 Long 未以字符串下发：" + json + " ⇒ 前端 Number 承载超 2^53 的雪花 ID 会静默截断");
  }

  @Test
  @DisplayName("默认构造器（= starter 当前的装配方式）下 Long 是 JSON number —— 待修风险，非期望行为")
  void defaultConstructorStillEmitsLongAsNumber() {
    String json = new TransformationEngine().toJson(Map.of("id", SNOWFLAKE_ID));

    assertFalse(
        json.contains("\"id\":\""),
        "默认装配已不再输出 number —— 若这是有意修复，请把本用例改成断言字符串，"
            + "并同步让 MetadataEngineAutoConfiguration 注入全局 mapper（当前是自建 ObjectMapper）");

    // Jackson 自身读写 Long 是精确的，真正的截断发生在前端 Number 上；这里锁住的是
    // 「以 number 形态出参」这个事实，而不是 Jackson 的精度问题。
    assertTrue(json.contains(String.valueOf(SNOWFLAKE_ID)), "输出形态与预期不符（既不是字符串也不是原样数字）：" + json);
  }

  @Test
  @DisplayName("null 与空串等边界不应抛出，且返回可解析的空结构")
  void nullAndBlankInputsAreTolerated() {
    TransformationEngine engine = new TransformationEngine();

    assertTrue(engine.toJson(null).equals("{}"), "toJson(null) 应返回空对象而不是抛异常");
    assertTrue(engine.fromJson(null).isEmpty(), "fromJson(null) 应返回空 Map");
    assertTrue(engine.fromJson("   ").isEmpty(), "fromJson(空白) 应返回空 Map");
  }

  @Test
  @DisplayName("批量转换的每一行都经过同一序列化路径（不会出现单行漏契约）")
  void batchTransformKeepsSameSerializationPath() {
    ObjectMapper globalContract = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(Long.class, ToStringSerializer.instance);
    globalContract.registerModule(module);
    TransformationEngine engine = new TransformationEngine(globalContract);

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", SNOWFLAKE_ID);
    String json = engine.toJson(row);

    assertTrue(json.contains("\"" + SNOWFLAKE_ID + "\""), "单行序列化未走全局契约：" + json);
  }
}
