# TDD 测试编写标准

## ## 1. 结构化你的测试 (AAA 模式)
每个测试必须清晰地划分为三个阶段：
* **Arrange (准备)**: 设置测试所需的前置条件、数据和对象。
* **Act (执行)**: 调用被测的公共方法或触发行为。
* **Assert (断言)**: 验证输出或状态是否符合预期。

## ## 2. 命名规范
测试名称必须是**行为描述性**的，而不是技术描述性的。
* ❌ 错误：`testCalculateMethod()`
* ❌ 错误：`testUserStatusWhenActiveIsTrue()`
* `Order_Should_ApplyTenPercentDiscount_When_TotalIsOverOneHundred()`
* `User_Should_BeBlocked_After_ThreeFailedLoginAttempts()`

## ## 3. 示例 (以 Java 为例)

```java
// 1. RED: 先写这个测试，确保它因为缺少实现或断言失败而变红
@Test
public void should_ApplyDiscount_When_CartValueExceedsThreshold() {
    // Arrange
    ShoppingCart cart = new ShoppingCart();
    cart.addItem(new Item("Mac mini M4", 120.0)); // 超过 100 基准线
    
    // Act
    double finalPrice = cart.calculateTotal();
    
    // Assert
    assertEquals(108.0, finalPrice, 0.001); // 应该享受 10% 折扣
}