# 隔离与模拟 (Mocking) 指南

## ## 核心原则
* **少用 Mock**：能用真实对象就用真实对象，特别是内存中的领域模型、值对象。
* **只 Mock 边界**：只对外部系统、不可控因素（如基础设施、第三方支付网关、当前系统时间、数据库连接）进行 Mock。

## ## 好的 Mock vs 坏的 Mock
* ✅ **好的 Mock**：模拟 `PaymentGateway` 接口，让其返回“支付成功”或“网络超时”。
* ❌ **坏的 Mock**：模拟同一个 Service 内部的私有方法，或者 Mock 领域对象内部的 Getters/Setters。

## ## 替代方案：使用 Fake
在可能的情况下，优先使用内存中的伪造实现（如 `InMemoryUserRepository`），而不是在每个测试里都用 Mockito 去 `when(...).thenReturn(...)`。这样能让测试更具可读性。