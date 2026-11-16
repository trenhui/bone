#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-event-envelope-version.py 的契约测试。

门禁判据（消息与事件规范 §2 / §3）：
- §3：实现 ``IntegrationEnvelope`` 的 record 必须带 ``schemaVersion`` 组件；
- §2：Topic 常量须以 ``.v{major}`` 主版本后缀结尾；
- 兼容性 diff：载荷字段减少且 ``schemaVersion`` 主版本未升 ⇒ breaking。

锁定内容：
- ``IMPL_RE``：识别 record/class implements IntegrationEnvelope，且方法入参提及不算事件实现；
- ``RECORD_BLOCK_RE`` / ``record_components``：组件名抽取、同文件多 record 的兜底定位、
  非 record 返回空列表；
- ``schema_version_of`` / ``major_of``：版本字面量抽取与主版本比较（含 None 边界）；
- ``TOPIC_CANDIDATE_RE``：只认「点分 + 蛇形 resource_action」的字符串常量，
  包名 / 配置开关 / 外部 API 方法名 / 大写值不误报；
- ``scan_topics`` / ``scan_events``：在临时夹具上验证 versioned/unversioned 分类与事件采集。

未覆盖：``main()`` 的输出与退出码、baseline 兼容性 diff 的实际比对（读取
``doc/architecture/event-envelope-baseline.json`` 并扫全仓，按测试约定不调用 main()）。

已知局限（按当前行为锁定）：``record_components`` 只跟踪 ``( [ {`` 的嵌套深度，
不感知 ``<>``，因此泛型参数内的逗号会被当作组件分隔符（见
``test_generic_comma_splits_component``）；这会让字段列表混入类型名噪音，
但不会影响 ``schemaVersion`` 组件存在性判断的主要路径。
"""

import importlib.util
import os
import shutil
import tempfile
import unittest
from pathlib import Path

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-event-envelope-version.py",
)
_spec = importlib.util.spec_from_file_location("check_event_envelope_version", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


class ImplRegexTest(unittest.TestCase):
    """IMPL_RE：谁是集成事件实现。"""

    def test_record_implementation_is_matched(self):
        src = "public record OrderCreated(String id) implements IntegrationEnvelope {"

        m = mod.IMPL_RE.search(src)

        self.assertIsNotNone(m)
        self.assertEqual(m.group(1), "OrderCreated")

    def test_class_implementation_is_matched(self):
        src = "public class OrderCreated implements com.bone.messaging.IntegrationEnvelope {"

        self.assertEqual(mod.IMPL_RE.search(src).group(1), "OrderCreated")

    def test_record_without_implements_is_not_matched(self):
        self.assertIsNone(mod.IMPL_RE.search("public record Plain(String id) {"))

    def test_envelope_as_method_parameter_is_not_an_implementation(self):
        src = "private void handle(IntegrationEnvelope envelope) {"

        self.assertIsNone(mod.IMPL_RE.search(src))


class RecordComponentsTest(unittest.TestCase):
    """record_components：组件名抽取。"""

    def test_simple_components_are_extracted(self):
        src = (
            "public record OrderCreated(\n"
            "    String orderId,\n"
            "    long amount,\n"
            "    String schemaVersion\n"
            ") implements IntegrationEnvelope {\n"
        )

        self.assertEqual(
            mod.record_components(src, "OrderCreated"),
            ["orderId", "amount", "schemaVersion"],
        )

    def test_generic_without_comma_keeps_component_name(self):
        src = "public record X(List<String> tags, int qty) implements IntegrationEnvelope {"

        self.assertEqual(mod.record_components(src, "X"), ["tags", "qty"])

    def test_generic_comma_splits_component(self):
        # 已知局限：<> 不计入深度，泛型内的逗号被当成组件分隔符
        src = "public record X(List<Map<String, Integer>> rows) implements IntegrationEnvelope {"

        self.assertEqual(mod.record_components(src, "X"), ["String", "rows"])

    def test_second_record_in_same_file_is_found(self):
        # 文件里第一个 record 不是目标类时，走兜底循环
        src = (
            "public record Other(String a) implements IntegrationEnvelope {}\n"
            "public record Target(String x) implements IntegrationEnvelope {}\n"
        )

        self.assertEqual(mod.record_components(src, "Target"), ["x"])

    def test_non_record_returns_empty(self):
        src = "public class Legacy implements IntegrationEnvelope {}"

        self.assertEqual(mod.record_components(src, "Legacy"), [])


class SchemaVersionTest(unittest.TestCase):
    """schema_version_of / major_of。"""

    def test_current_schema_version_constant_is_detected(self):
        text = "return new OrderCreated(orderId, amount, CURRENT_SCHEMA_VERSION);"

        self.assertEqual(mod.schema_version_of(text, "OrderCreated"), "CURRENT_SCHEMA_VERSION")

    def test_literal_and_record_declaration_do_not_confuse_extraction(self):
        text = (
            "public record OrderCreated(String a, long b, String c) implements IntegrationEnvelope {}\n"
            'return new OrderCreated(a, b, "2.0");\n'
        )

        self.assertEqual(mod.schema_version_of(text, "OrderCreated"), "2.0")

    def test_missing_version_returns_none(self):
        text = "return new BadEvent(id);"

        self.assertIsNone(mod.schema_version_of(text, "BadEvent"))

    def test_major_of(self):
        for raw, expected in (
            ("1.0", 1),
            ("10.3", 10),
            ("2", 2),
            ("CURRENT_SCHEMA_VERSION", None),
            (None, None),
            ("", None),
        ):
            with self.subTest(raw=raw):
                self.assertEqual(mod.major_of(raw), expected)


class TopicRegexTest(unittest.TestCase):
    """TOPIC_CANDIDATE_RE：Topic 候选与版本后缀。"""

    def _match(self, value):
        src = f'String TOPIC = "{value}";'
        m = mod.TOPIC_CANDIDATE_RE.search(src)
        return m.group(1) if m else None

    def test_versioned_topic_is_matched(self):
        val = self._match("bone.order.order_created.v1")

        self.assertEqual(val, "bone.order.order_created.v1")
        self.assertTrue(val.endswith(".v1"))

    def test_unversioned_topic_is_matched_but_lacks_suffix(self):
        # 负向：蛇形 resource_action 但漏版本后缀 ⇒ §2 违规
        val = self._match("bone.order.order_updated")

        self.assertEqual(val, "bone.order.order_updated")
        self.assertIsNone(mod.re.search(r"\.v\d+$", val))

    def test_non_topic_dotted_strings_are_ignored(self):
        # 包名 / 配置开关 / 指标 / 外部 API 方法名均无蛇形 resource_action 段
        for value in (
            "com.bone.blueprint",
            "bone.blueprint.channel.broadcast",
            "taobao.trade.orders.get",
            "http.server.requests",
        ):
            with self.subTest(value=value):
                self.assertIsNone(self._match(value))

    def test_uppercase_value_is_ignored(self):
        self.assertIsNone(self._match("Bone.Order.ORDER_CREATED.v1"))


class ScanFixtureTest(unittest.TestCase):
    """scan_topics / scan_events：在临时目录上验证采集与分类。"""

    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="event-envelope-")
        self._saved_repo = mod.REPO
        mod.REPO = Path(self.root)

    def tearDown(self):
        mod.REPO = self._saved_repo
        shutil.rmtree(self.root, ignore_errors=True)

    def _write(self, rel, body):
        path = Path(self.root, *rel.split("/"))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")

    def test_topics_are_classified(self):
        self._write(
            "bone-order/src/main/java/Topics.java",
            'class Topics {\n'
            '  String A = "bone.order.order_created.v1";\n'
            '  String B = "bone.order.order_updated";\n'
            '  String C = "com.bone.blueprint";\n'
            "}\n",
        )

        topics = mod.scan_topics()

        self.assertEqual(
            topics,
            {
                "bone.order.order_created.v1": "versioned",
                "bone.order.order_updated": "unversioned",
            },
        )

    def test_event_with_schema_version_component(self):
        self._write(
            "bone-order/src/main/java/OrderCreated.java",
            "public record OrderCreated(\n"
            "    String orderId,\n"
            "    long amount,\n"
            "    String schemaVersion\n"
            ") implements IntegrationEnvelope {\n"
            '  static OrderCreated of(String id, long amount) {\n'
            "    return new OrderCreated(id, amount, CURRENT_SCHEMA_VERSION);\n"
            "  }\n"
            "}\n",
        )

        events = mod.scan_events()

        self.assertEqual(sorted(events), ["OrderCreated"])
        info = events["OrderCreated"]
        self.assertTrue(info["hasSchemaVersion"])
        self.assertEqual(info["schemaVersion"], "CURRENT_SCHEMA_VERSION")
        self.assertEqual(info["fields"], ["orderId", "amount", "schemaVersion"])
        self.assertTrue(info["file"].endswith("OrderCreated.java"))

    def test_event_without_schema_version_component(self):
        # 负向：§3 违规形态 —— 组件缺 schemaVersion，且无版本字面量
        self._write(
            "bone-order/src/main/java/BadEvent.java",
            "public record BadEvent(String id) implements IntegrationEnvelope {}\n",
        )

        info = mod.scan_events()["BadEvent"]

        self.assertFalse(info["hasSchemaVersion"])
        self.assertIsNone(info["schemaVersion"])
        self.assertNotIn("schemaVersion", info["fields"])

    def test_plain_class_is_not_collected_as_event(self):
        self._write(
            "bone-order/src/main/java/NotAnEvent.java",
            "public class NotAnEvent {}\n",
        )

        self.assertEqual(mod.scan_events(), {})

    def test_schema_version_component_is_required_key(self):
        # 直接锁定 main() 里 §3 判据所依赖的布尔值语义
        self._write(
            "bone-order/src/main/java/Empty.java",
            "public record Empty() implements IntegrationEnvelope {}\n",
        )

        info = mod.scan_events()["Empty"]

        self.assertEqual(info["fields"], [])
        self.assertFalse(info["hasSchemaVersion"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
