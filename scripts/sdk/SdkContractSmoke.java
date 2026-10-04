package com.bone.smoke;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对外 Java SDK 的契约烟测驱动器。
 *
 * <p><b>为什么必须真发 HTTP</b>：只 new 出 Api 类、核对方法名，是「能编译不能用」的假绿 ——
 * okhttp 在 404/500 上也不抛异常，不真跑一遍就发现不了。为此本类起一个本地 stub server，
 * 让每个 tag 的 API 至少真实往返一次并断言 HTTP 码。
 *
 * <p><b>断言各自对应一类真实缺陷</b>：
 *
 * <ol>
 *   <li>逐条递增的请求计数 ⇒ 生成物的方法真把请求发出去了（basePath 没设 / 路径拼错 / 提前 return）；
 *   <li>每个响应 200 ⇒ 响应解析与反序列化可用；
 *   <li>需要认证的调用必须带 {@code Authorization: Bearer} ⇒ 规范的 {@code securitySchemes}
 *       真被生成器接上了。这条不能省：规范声明了 bearerAuth 但生成物若忽略 security 段，
 *       不带 token 的调用照样"成功"，测不出来。<b>而 {@code security: []} 的公开端点
 *       （/login、/sso/callback）反过来不能要求带头</b> —— 清单第 6 列 {@code auth=none} 标注。
 * </ol>
 *
 * <p><b>参数由本类按生成物的实际签名合成</b>，清单不列参数。理由：参数的
 * path/query/header/body 相对顺序是 openapi-generator 的实现细节（实测 body 恒排最后），
 * 把它写进清单等于把生成器版本钉死在契约烟测里，生成器一升级就全线假红。
 * 契约侧的"path 参数是否声明"由静态门禁 check-openapi-path-params.py 把关。
 */
public final class SdkContractSmoke {

  private static final List<String> failures = new ArrayList<>();
  private static final AtomicInteger received = new AtomicInteger();
  private static final Map<String, AtomicInteger> authFailures = new ConcurrentHashMap<>();
  private static String baseUrl = "";
  /**
   * 本次调用是否应当带 Authorization。
   *
   * <p>必须是**调用级**而非全局：规范里 {@code POST /login}、{@code GET /sso/callback}
   * 显式声明 {@code security: []}（公开），对它们要求带头是把正确行为判成失败；
   * 而其余端点漏带头又必须被抓出来。一个全局开关表达不了这个差异。
   * stub 在独立线程上处理请求，只能靠"按到达顺序消费"与调用一一对应 ——
   * 驱动器是串行执行的（一次一个 withHttpInfo），所以顺序假设成立。
   */
  private static volatile boolean currentCallExpectsAuth = true;

  private SdkContractSmoke() {}

  public static void main(String[] args) throws Exception {
    Path manifest = Path.of(args.length > 0 ? args[0] : "target/sdk/smoke-manifest.tsv");

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", SdkContractSmoke::handle);
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

    int calls;
    try {
      calls = run(manifest);
    } finally {
      server.stop(0);
    }

    if (received.get() == 0) {
      failures.add("stub 一次都没收到请求 ⇒ 烟测是假绿");
    }
    for (Map.Entry<String, AtomicInteger> e : authFailures.entrySet()) {
      failures.add("缺少 Authorization: Bearer 头（path=" + e.getKey() + "，共 " + e.getValue() + " 次）");
    }

    if (!failures.isEmpty()) {
      System.out.println("SDK 契约烟测 FAIL（" + failures.size() + " 项）");
      for (String f : failures) {
        System.out.println("    - " + f);
      }
      System.exit(1);
    }
    System.out.println("SDK 契约烟测 PASS：" + calls + " 个 (domain,tag) 全部真实往返 200，认证头断言全过");
  }

  private static int run(Path manifest) throws Exception {
    int calls = 0;
    for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      String[] col = line.split("\t", -1);
      if (col.length != 6) {
        failures.add("清单行字段数异常（期望 6，实际 " + col.length + "）：" + line);
        continue;
      }
      calls++;
      invoke(col);
    }
    return calls;
  }

  private static void invoke(String[] col) {
    String apiClassName = col[2];
    String method = col[4];
    boolean expectsAuth = !"none".equals(col[5].trim());
    String label = apiClassName.substring(apiClassName.lastIndexOf('.') + 1) + "." + method;
    try {
      Class<?> apiClass = Class.forName(apiClassName);
      Class<?> clientClass = Class.forName(col[3]);

      Object apiClient = clientClass.getDeclaredConstructor().newInstance();
      clientClass.getMethod("setBasePath", String.class).invoke(apiClient, baseUrl);
      if (expectsAuth) {
        clientClass.getMethod("setBearerToken", String.class).invoke(apiClient, "smoke-token");
      }

      Method target = null;
      for (Method m : apiClass.getDeclaredMethods()) {
        if (m.getName().equals(method)) {
          target = m;
          break;
        }
      }
      if (target == null) {
        failures.add(label + " → 生成物中找不到该方法（契约与生成物漂移）");
        return;
      }

      Object[] callArgs = synthArgs(target.getParameterTypes());

      currentCallExpectsAuth = expectsAuth;
      Object api = newApi(apiClass, clientClass, apiClient);
      int before = received.get();
      Object resp = target.invoke(api, callArgs);
      int status = (int) resp.getClass().getMethod("getStatusCode").invoke(resp);
      if (status != 200) {
        failures.add(label + " → HTTP " + status);
      }
      if (received.get() <= before) {
        failures.add(label + " → 未真正发出请求");
      }
    } catch (InvocationTargetException e) {
      report(label, "调用异常：" + e.getTargetException(), e.getTargetException());
    } catch (ClassNotFoundException e) {
      report(label, "类不存在：" + e.getMessage(), e);
    } catch (ReflectiveOperationException | RuntimeException e) {
      report(label, String.valueOf(e), e);
    }
  }

  private static void report(String label, String message, Throwable t) {
    failures.add(label + " → " + message);
    if (System.getenv("SMOKE_DEBUG") != null) {
      System.out.println("---- " + label);
      t.printStackTrace(System.out);
    }
  }

  /** 优先用带 ApiClient 的构造器；字段名跨生成器版本可能变，故再兜一层字段注入。 */
  private static Object newApi(Class<?> apiClass, Class<?> clientClass, Object apiClient)
      throws ReflectiveOperationException {
    for (Constructor<?> c : apiClass.getDeclaredConstructors()) {
      Class<?>[] p = c.getParameterTypes();
      if (p.length == 1 && p[0].isAssignableFrom(clientClass)) {
        c.setAccessible(true);
        return c.newInstance(apiClient);
      }
    }
    Object api = apiClass.getDeclaredConstructor().newInstance();
    Field f = findApiClientField(apiClass);
    if (f == null) {
      throw new IllegalStateException("既无 ApiClient 构造器也无 ApiClient 字段，无法注入 basePath");
    }
    f.setAccessible(true);
    f.set(api, apiClient);
    return api;
  }

  private static Field findApiClientField(Class<?> apiClass) {
    for (Class<?> c = apiClass; c != null; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (f.getType().getSimpleName().equals("ApiClient")) {
          return f;
        }
      }
    }
    return null;
  }

  /**
   * 按方法签名合成占位参数。
   *
   * <p>无法合成的类型（SDK 自定义类型）返回 null，让调用按生成器的必填校验抛
   * ApiException —— 驱动器会把它报成失败，这是正确结果：说明"想真调一次就得先造一个
   * 合法请求体"，此时应当给该操作补requestBody schema，而不是让烟测将就。
   */
  private static Object[] synthArgs(Class<?>[] types) {
    Object[] out = new Object[types.length];
    for (int i = 0; i < types.length; i++) {
      out[i] = synthOne(types[i]);
    }
    return out;
  }

  private static Object synthOne(Class<?> t) {
    if (t == String.class) {
      return "smoke-value";
    }
    if (t == int.class || t == Integer.class) {
      return 1;
    }
    if (t == long.class || t == Long.class) {
      return 1L;
    }
    if (t == double.class || t == Double.class) {
      return 1.0d;
    }
    if (t == float.class || t == Float.class) {
      return 1.0f;
    }
    if (t == boolean.class || t == Boolean.class) {
      return Boolean.FALSE;
    }
    if (t == UUID.class) {
      return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
    if (t == OffsetDateTime.class) {
      return OffsetDateTime.parse("2026-01-01T00:00:00Z");
    }
    if (List.class.isAssignableFrom(t)) {
      return Collections.emptyList();
    }
    if (Map.class.isAssignableFrom(t)) {
      // 生成物对 Map 请求体做必填校验，传 null 会抛 ApiException 且请求发不出去；
      // 空Map 才是"最小合法请求体"（metadata-runtime 的动态行模型就是Map）。
      return Collections.singletonMap("id", "smoke-value");
    }
    if (t.isEnum()) {
      Object[] cs = t.getEnumConstants();
      return cs.length > 0 ? cs[0] : null;
    }
    if (t.isPrimitive()) {
      return 0;
    }
    // 生成物里的 model 类：造一个实例让请求体非空（字段全空也能序列化成 {}）
    if (!Modifier.isAbstract(t.getModifiers()) && t.getPackageName().startsWith("com.bone.sdk")) {
      try {
        Constructor<?> c = t.getDeclaredConstructor();
        c.setAccessible(true);
        return c.newInstance();
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }
    return null;
  }

  private static void handle(HttpExchange ex) throws java.io.IOException {
    received.incrementAndGet();
    String path = ex.getRequestURI().getPath();
    if (currentCallExpectsAuth) {
      String auth = ex.getRequestHeaders().getFirst("Authorization");
      if (auth == null || !auth.startsWith("Bearer ")) {
        authFailures.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
      }
    }
    // 必须回符合统一信封的完整结构：components/ApiResponse.yaml 里
    // required = [success, code, message]（另有 timestamp/data）。少字段会被
    // gson-fire 当场判为"缺必填"而抛异常 —— 那说明 required 真在生效。
    byte[] body = ("{\"success\":true,\"code\":200,\"message\":\"OK\","
            + "\"data\":null,\"timestamp\":\"2026-01-01T00:00:00Z\"}")
        .getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
    ex.sendResponseHeaders(200, body.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(body);
    }
  }
}