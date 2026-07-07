# Java测试规范

优先：

JUnit5
Mockito
AssertJ

测试命名：

should_xxx_when_xxx

例如：

should_return_user_when_id_exists

每个测试包含：

Given
When
Then

必须覆盖：

- 正常流程
- 空值
- 边界值
- 异常流程
- 并发场景

覆盖率要求：

Service > 90%

Controller > 80%

Util > 95%