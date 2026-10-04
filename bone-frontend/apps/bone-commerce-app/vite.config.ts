import { createQiankunViteConfig } from '@bone/shared-config';

/**
 * 端口为什么是 3012 而不是 3010：3010/3011 已被本机的历史 dev 实例占用
 * （3010=generator、3011=system 的旧实例）。Vite 默认 `strictPort: false`，端口被占会
 * **静默顺延**到下一个空闲端口，导致「配置写 3010、实际跑在 3012」——shell 注册的
 * entry 与网关 CORS 白名单全部指向错误端口，现象是微应用加载不出来却没有任何报错。
 *
 * `strictPort: true` 让端口冲突直接启动失败并报错，好过静默漂移。但注意
 * `createQiankunViteConfig` 的 `options.config` 是**顶层浅展开**（`...config`），
 * 传 `{ server: {...} }` 会整体替换掉工厂生成的 server 段（port/proxy 全丢，
 * 实测落到默认 5173）。故此处改传 `proxyTarget` 明确网关地址，strictPort 则
 * 通过读取本文件后再合并的方式无效——改为不覆盖 server，仅靠注释固化端口。
 */
export default createQiankunViteConfig('bone-commerce-app', 3012, {
  // 显式声明代理目标（工厂默认已是 8888，此处写明是为了让「前端→网关」这条边在
  // 配置文件里可见，避免后人误以为子应用直连某个后端实例）
  proxyTarget: 'http://localhost:8888',
});
