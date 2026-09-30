# 部署与编排 — DRAFT，待架构师审批 (L3: 部署编排)

> **现状澄清（关键）**：仓库**已有完整的镜像构建与 CI 能力**，无需从零补齐——
> - `docker/Dockerfile`：多阶段、参数化（`MODULE_PATH`/`ARTIFACT_ID`）、非 root、`HEALTHCHECK` 完善。
> - `.github/workflows/ci.yml` 的 `docker-build` job：9 个服务矩阵构建 + **Trivy 漏洞扫描**；`backend-security` 已含 **gitleaks 密钥扫描 + OWASP 依赖检查**；另有集成测试、OpenAPI 契约检查。
> - `docker-compose.yml` 仅用于本地拉起 mysql/redis 中间件，不部署业务服务（这是设计，非缺失）。
>
> 因此**构建 / 镜像 / 安全扫描环节不缺**。本目录补的是 CI 之上、镜像之后的**部署编排（K8s）缺口**：当前仓库无 Deployment/Service/Ingress manifests、无 Helm、无探针/HPA 配置、CI 也只 `build` 未 `push` 到 registry。

## 可部署服务与端口（供 K8s 编排参考）
| 服务 | Maven 模块 | 默认端口 |
|---|---|---:|
| gateway | `bone-platform/bone-gateway` | 8888 |
| iam | `bone-platform/bone-iam` | 8080 |
| system | `bone-platform/bone-system` | 8083 |
| masterdata | `bone-platform/bone-masterdata` | 8084 |
| integration | `bone-platform/bone-integration` | 8085 |
| file | `bone-platform/bone-file` | 8107 |
| notification | `bone-platform/bone-notification` | 8100 |
| blueprint | `bone-blueprint` | 8082 |
| studio-generator | `bone-engine/studio-generator` | 8086 |
| extension-studio | `bone-engine/bone-extension-engine/bone-extension-studio` | 8088 |
| metadata-server | `bone-engine/bone-metadata-server` | 8081 |

> 端口以各 `application*.yml` 的 `server.port` 为准；prod 可用环境变量覆盖（如 `BONE_GATEWAY_PORT`）。

## 依赖中间件
- MySQL（`BONE_DB_*`）、Redis（`BONE_REDIS_*`）—— 见各 `application-prod.yml`。
- Nacos 配置中心（见 `doc/deployment/nacos-config-guide.md`）。

## K8s 编排草稿（bone-iam 示例）
```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: bone-iam, labels: { app: bone-iam } }
spec:
  replicas: 2
  selector: { matchLabels: { app: bone-iam } }
  template:
    metadata: { labels: { app: bone-iam } }
    spec:
      containers:
        - name: bone-iam
          image: ${BONE_REGISTRY}/bone/bone-iam:${TAG}   # TODO: registry 待定，CI 当前未 push
          ports: [{ containerPort: 8080 }]
          envFrom:
            - secretRef: { name: bone-iam-secrets }   # BONE_DB_PASSWORD / BONE_CONFIG_ENCRYPT_KEY 等
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: 8080 }
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: 8080 }
            initialDelaySeconds: 30
            periodSeconds: 15
          resources:
            requests: { cpu: "500m", memory: "512Mi" }
            limits:   { cpu: "1",    memory: "1Gi" }
      terminationGracePeriodSeconds: 30   # 配合优雅停机
---
apiVersion: v1
kind: Service
metadata: { name: bone-iam }
spec:
  selector: { app: bone-iam }
  ports: [{ port: 8080, targetPort: 8080 }]
```
> 探针端点需确认各服务暴露了 `/actuator/health/readiness` / `/liveness`（当前 `management.endpoint.health.show-details: when_authorized` 已配，建议补充 readiness/liveness group）。

## 待办（需架构师/运维确认）
- [ ] 镜像仓库（registry）地址，并补 CI `docker-build` 的 `docker push` + K8s `imagePullSecret`。
- [ ] 是否用 Helm chart 收敛 11 个服务的 Deployment/Service/Ingress。
- [ ] Ingress / 网关层 TLS 终止与强制 HTTPS（`application*.yml` 当前无 `server.ssl`）。
- [ ] HPA（CPU/自定义指标）、PDB、资源配额。
- [ ] 多租户数据隔离下的分库分表与迁移归属（见 `db/migration/README.md`）。
