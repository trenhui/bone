# Bone 公共 OpenAPI 组件

> 供各服务 `openapi.yaml` 通过 `$ref` 引用，避免 `ApiResponse` / `ProblemDetail` 字段不一致。  
> 规范说明见 [Bone-API-规范.md](../Bone-API-规范.md)（§4 错误信封 / ProblemDetail、§11 OpenAPI）；业务字符串 `errorCode` 台账见 [Bone-错误码登记.md](../Bone-错误码登记.md)。

## 使用方式

```yaml
# bone-iam/src/main/resources/openapi/openapi.yaml
components:
  schemas:
    ApiResponse:
      $ref: '../../../../doc/architecture/openapi/components/ApiResponse.yaml'
    ProblemDetail:
      $ref: '../../../../doc/architecture/openapi/components/ProblemDetail.yaml'
```

> 路径按模块深度调整；长期可发布为 `bone-api-specs` Maven/npm 包。

## 文件列表

| 文件 | 说明 |
|------|------|
| [ApiResponse.yaml](./components/ApiResponse.yaml) | 统一成功/失败信封 |
| [PageResult.yaml](./components/PageResult.yaml) | 分页 |
| [ProblemDetail.yaml](./components/ProblemDetail.yaml) | RFC 7807 对齐错误体（位于 `data`） |
| [FieldError.yaml](./components/FieldError.yaml) | 字段校验错误 |
