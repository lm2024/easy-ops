# Mock规范

允许Mock：

- RPC
- MQ
- Redis
- Elasticsearch
- 第三方API

禁止Mock：

- 当前测试目标

优先：

Mockito

例如：

@Mock
@InjectMocks

verify()

ArgumentCaptor()