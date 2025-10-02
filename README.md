
# **Bone — Build Once, Natively Everywhere**
## **企业级全栈开源原生快速开发平台**
> **100% 开源免费 · 企业级就绪 · 元数据驱动**
> **“构建可复用的系统，创造可持续的价值。”**
> —— **梅山**

## 🎯 **核心价值**
### 🚀 **提升开发效率，减少70%重复工作**

* **元数据驱动开发**：配置即代码，减少重复编码，提升开发效率
* **智能代码生成**：一键生成前后端代码、API文档与单元测试
* **多端统一适配**：一次配置，自动生成Web、App、小程序等多端代码

### 💼 **企业级功能开箱即用**

* **RBAC 权限体系**：精细化权限控制，多租户隔离，支持SSO单点登录
* **分布式事务保障**：Seata 强一致性支持，保障数据一致性
* **全链路监控**：SkyWalking 链路追踪，性能监控，帮助实时优化系统

---

## 🏗️ **架构哲学**

> **“好的架构，让复杂归于简单；好的开源，让价值自由流动。”**
> —— **梅山**

### **智能元数据引擎**

Bone 提供一个智能化元数据引擎，帮助企业管理和自动化业务模型。通过元数据驱动，系统能够自动识别业务实体及关系，灵活支持多种数据源与业务场景，简化开发流程。

#### **核心能力：**

* 🔍 **智能发现** - 自动识别业务实体关系及数据流动
* 🎯 **统一治理** - 集中管理企业数据资产，优化数据治理
* 📊 **动态查询引擎** - 零代码自动生成复杂查询
* 🔌 **多源适配** - 统一接入异构数据源，简化系统集成

---

## 🛠️ **技术栈（2025 企业级版）**

### **后端架构**

```yaml
微服务框架: Spring Boot 3.2 + Spring Cloud 2023
服务治理: Nacos 2.3 + Sentinel 2.0
数据持久化: metadata-sdk + 动态数据源
消息队列: RocketMQ 5.2
分布式事务: Seata 2.0
缓存方案: Redis 7.2 + Redisson
安全框架: Spring Security 6.2 + JWT
```

### **前端生态**

```yaml
管理后台: 
  - Vue 3.4 + Vite 5.4 + Element Plus 2.8
  - TypeScript 5.5 + Pinia 2.1
移动端:
  - uni-app 3.5 (支持微信/支付宝小程序 + H5 + App)
构建工具:
  - Vite 5.4 (极速热更新)
  - Vitest 2.0 (单元测试)
```

---

## ⚡ **5分钟快速体验**

```bash
# 克隆项目
git clone https://gitee.com/meishan315/bone.git

# 一键启动
cd bone && mvn clean install
java -jar bone-admin/target/bone-admin.jar
```

**访问信息：**

* 管理后台：[http://localhost:8080](http://localhost:8080)
* 默认账号：`admin` / `123456`
* API 文档：[http://localhost:8080/doc.html](http://localhost:8080/doc.html)

---

## 📊 **效能对比**

| 能力维度  | 传统开发     | Bone 平台           |
| ----- | -------- | ----------------- |
| 新功能开发 | 2-3周编码测试 | **3天** 配置生成       |
| 多端适配  | 分别开发维护   | **一次定义** 多端生成     |
| 系统集成  | 定制开发对接   | 标准适配器 + **可视化编排** |
| 架构演进  | 重构成本高    | 元数据调整 **自动同步**    |

---

## 🤝 **加入社区**

### 📚 **学习资源（完善中敬请期待）**

* **详细文档**：[https://bone.com/quick-start/](https://bone.com/quick-start/)
* **视频教程**：[https://bone.com/video/](https://bone.com/video/)
* **最佳实践**：[https://bone.com/best-practices/](https://bone.com/best-practices/)

### 🔧 **参与贡献**

* **代码贡献** - 提交 PR 参与项目共建
* **文档改进** - 协助完善使用指南
* **问题反馈** - 提交 Issue 帮助改进

### 📞 **技术支持**

* **微信公众号**：梅山见道 (MeishanInsight)
* **代码仓库**：[https://gitee.com/meishan315/bone](https://gitee.com/meishan315/bone)
* **技术交流**：实时问题解答与支持

---

## **⭐ 支持我们**

如果 Bone 对您有帮助，请给我们一个 **Star**！
这是开源的最大动力！

[![Star on Gitee](https://gitee.com/meishan315/bone/badge/star.svg)](https://gitee.com/meishan315/bone)

---

## **Bone - 让企业级开发更简单**

*元数据驱动，一次构建，处处运行*
探索企业级开发的无限可能，从 **Bone** 开始

---

### **品牌演进路线：**

#### **第一阶段（现在）**
**主打：** **Build Once, Natively Everywhere**  
**重点：** 建立技术认知，突出原生适配能力  
#### **第二阶段（6-12个月）**  
**补充：** **Business Oriented Native Engine**  
**目标：** 拓展企业市场，强化平台在商业中的价值  
#### **第三阶段（未来）**  
**升华：** **Base of Next Enterprise**  
**愿景：** 定义行业标准，引领技术发展