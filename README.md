# snow-logtracing-springboot-starter

> 轻量级 Spring Boot 全链路追踪与日志记录组件，集成分布式 TraceId、请求日志自动采集与敏感数据脱敏，即引即用。

## 项目简介

`snow-logtracing-springboot-starter` 是一个面向 Spring Boot 3.x 的轻量级可观测性组件，提供两大核心能力：

- **全链路追踪**：基于 TraceFilter + TraceContext 实现分布式追踪，自动生成/传播 TraceId，支持跨服务调用链串联，使用 `System.nanoTime()` 精确计时，输出 Span 树状耗时汇总。
- **请求日志记录**：基于 AOP 切面自动拦截 Controller 层请求，记录请求地址、参数、Headers、响应结果等完整信息，支持敏感字段脱敏保护。

### 核心特性

- **分布式追踪**：自动生成 TraceId/SpanId，通过 HTTP Header 跨服务传播，MDC 集成让所有日志自动携带追踪信息
- **精确计时**：TraceFilter 使用 `System.nanoTime()` 计时，只包裹业务执行，不计入日志构建等额外开销
- **方法级追踪**：`@Trace` 注解标注 Service/DAO 层方法，自动创建子 Span 记录耗时
- **Span 树输出**：请求结束后输出完整的调用链树状结构，直观展示各层耗时分布
- **自动记录**：零代码自动拦截所有 Controller 请求，记录完整的请求和响应信息
- **数据脱敏**：内置 password、token、secret 等敏感字段过滤，支持自定义扩展
- **真实行号**：通过 ASM 字节码技术获取方法真实行号，支持 IDE 点击跳转
- **跨服务传播**：自动为 RestTemplate、Feign 注入 trace header，无需手动传递
- **异步支持**：TaskDecorator 自动传递追踪上下文到子线程
- **即引即用**：零配置启动，所有功能默认开启

### 适用场景

- 单体或微服务系统的请求追踪和问题排查
- 接口性能分析和慢接口定位
- 分布式系统调用链追踪和耗时分析
- 安全审计和敏感数据保护

## 技术架构

### 核心组件

| 组件 | 职责 |
|------|------|
| **TraceFilter** | 请求入口 Filter，生成/提取 TraceId，精确计时，输出 Span 树汇总 |
| **TraceContext** | 追踪上下文，基于 ThreadLocal 存储 TraceId 和 Span 栈 |
| **SpanInfo** | Span 数据模型，记录操作名、耗时、标签、父子关系 |
| **@Trace** | 方法级追踪注解，用于 Service/DAO 层耗时追踪 |
| **TraceAnnotationAspect** | `@Trace` 注解的 AOP 切面处理 |
| **HttpRequestLogAspect** | AOP 切面，负责请求参数/响应体的美化日志输出 |
| **RestTemplateTraceInterceptor** | RestTemplate 拦截器，自动注入 trace header |
| **FeignTraceInterceptor** | Feign 拦截器，自动注入 trace header |
| **TraceContextTaskDecorator** | 异步线程上下文传递装饰器 |

### 请求处理流程

```
HTTP Request
    │
    ▼
TraceFilter（入口）
    ├─ 从请求头提取或生成 TraceId
    ├─ 创建根 Span，写入 MDC
    ├─ System.nanoTime() 开始计时
    │       │
    │       ▼
    │   HttpRequestLogAspect（Controller 层）
    │       ├─ 输出请求日志（URL、参数、Headers 等）
    │       │       │
    │       │       ▼
    │       │   @Trace 注解方法（Service/DAO 层）
    │       │       ├─ 创建子 Span
    │       │       ├─ RestTemplate/Feign → 注入 trace header → 下游服务
    │       │       └─ 结束子 Span
    │       │
    │       └─ 输出响应日志（返回结果）
    │
    ├─ System.nanoTime() 结束计时
    ├─ 输出 Span 树状耗时汇总
    └─ 清理 TraceContext + MDC
```

### 技术栈

- **Java 17+**
- **Spring Boot 3.2.0**
- **Spring AOP**：面向切面编程
- **FastJSON2 2.0.31**：JSON 序列化与反序列化
- **Hutool 5.8.43**：Java 工具库
- **ASM**：字节码解析，获取方法真实行号

## 使用方式

### 前置要求

- Java 17+
- Spring Boot 3.x
- Maven 3.0+

### 步骤一：引入依赖

```xml
<dependency>
    <groupId>io.github.snowylchen</groupId>
    <artifactId>snow-logtracing-springboot-starter</artifactId>
    <version>1.0.0-jdk17</version>
</dependency>
```

引入依赖后，组件会通过 Spring Boot 自动配置机制自动生效，**零配置即可使用**。

### 步骤二：启动项目，验证效果

启动 Spring Boot 应用后，访问任意 Controller 接口，控制台将自动输出：

**请求日志**（由 HttpRequestLogAspect 输出）：

```
========================== 请求开始 ==========================
2024-03-18 10:30:45  [http-nio-8080-exec-1]  io.github.snowylchen.aspect.HttpRequestLogAspect  :
traceId: a1b2c3d4e5f6789012345678abcdef00
请求地址: GET http://localhost:8080/api/user/123
类名方法: (com.example.controller.UserController#getUserById)
类名快捷跳转: (UserController.java:45)
远程地址: 192.168.1.100
请求头信息: {"host":"localhost:8080","content-type":"application/json"}
请求的参数: {"id":123}
```

**响应日志**（由 HttpRequestLogAspect 输出）：

```
返回的结果: {"code":200,"data":{"id":123,"name":"张三"}}
```

**追踪汇总**（由 TraceFilter 输出）：

```
[traceId=a1b2c3d4e5f6789012345678abcdef00] 请求结束 GET /api/user/123 总耗时: 45ms
```

### 步骤三：使用 @Trace 注解追踪 Service/DAO 层

在需要追踪的方法上添加 `@Trace` 注解：

```java
import io.github.snowylchen.annotation.Trace;

@Service
public class UserService {

    @Trace("查询用户详情")
    public User getUserById(Long id) {
        return userDao.selectById(id);
    }

    @Trace  // 默认使用 "UserService#listUsers" 作为操作名
    public List<User> listUsers() {
        return userDao.selectAll();
    }
}

@Repository
public class UserDao {

    @Trace("数据库查询-用户表")
    public User selectById(Long id) {
        // ...
    }
}
```

添加 `@Trace` 注解后，TraceFilter 的汇总日志将展示完整的 Span 树：

```
[traceId=a1b2c3d4e5f6789012345678abcdef00] 请求结束 GET /api/user/123 总耗时: 45ms
  ├─ [INTERNAL] 查询用户详情 -- 32ms
  │  └─ [INTERNAL] 数据库查询-用户表 -- 18ms
  └─ [INTERNAL] UserService#formatResult -- 3ms
```

### 步骤四：配置 logback 输出 traceId（推荐）

TraceFilter 会自动将 `traceId` 写入 SLF4J MDC，只需在 logback 配置中添加 `%X{traceId}` 即可让所有业务日志自动携带 traceId：

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

配置后，所有日志输出都会携带 traceId，便于通过日志系统（ELK 等）检索同一请求的完整日志：

```
10:30:45.123 [http-nio-8080-exec-1] [a1b2c3d4e5f6789012345678abcdef00] INFO  UserService - 开始查询用户
10:30:45.135 [http-nio-8080-exec-1] [a1b2c3d4e5f6789012345678abcdef00] INFO  UserDao - 执行SQL查询
10:30:45.155 [http-nio-8080-exec-1] [a1b2c3d4e5f6789012345678abcdef00] INFO  UserService - 查询完成
```

### 步骤五：跨服务追踪（微服务场景）

在微服务架构中，TraceId 会通过 HTTP Header 自动传播。组件会自动为 RestTemplate 添加追踪拦截器，无需手动配置：

```java
@Service
public class OrderService {

    @Autowired
    private RestTemplate restTemplate;

    @Trace("查询用户信息")
    public User getUser(Long userId) {
        // RestTemplate 调用时会自动在请求头中注入:
        // X-Trace-Id: a1b2c3d4...
        // X-Span-Id: 1a2b3c4d
        return restTemplate.getForObject(
            "http://user-service/api/user/" + userId, User.class);
    }
}
```

下游服务如果也引入了本组件，TraceFilter 会自动从请求头提取 TraceId，实现跨服务的调用链串联。

**Feign 调用**同样支持，需要在配置中开启：

```yaml
snow:
  logtracing:
    propagation:
      feign: true
```

**传播协议**：

| HTTP Header | 用途 | 示例 |
|-------------|------|------|
| `X-Trace-Id` | 全局追踪 ID | `a1b2c3d4e5f6789012345678abcdef00` |
| `X-Span-Id` | 当前 Span ID | `1a2b3c4d` |

下游服务的响应头中也会包含 `X-Trace-Id`，方便前端或调用方关联请求。

### 步骤六：异步线程追踪（可选）

使用线程池执行异步任务时，需要将 `TraceContextTaskDecorator` 配置到线程池中，以保证子线程继承 traceId：

```java
@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolTaskExecutor taskExecutor(TraceContextTaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setTaskDecorator(decorator);  // 关键：设置追踪上下文装饰器
        return executor;
    }
}
```

配置后，异步线程中的日志也会携带与主线程相同的 traceId。

## 配置参考

### 完整配置项

```yaml
snow:
  logtracing:
    # ===== 基础配置 =====
    enable: true                    # 总开关，默认 true

    # 需要输出的日志字段，不配置则输出所有字段
    fields:
      - requestUrl                  # 请求地址
      - methodInfo                  # 类名方法
      - lineInfo                    # 类名快捷跳转（行号）
      - remoteIp                    # 远程地址
      - headers                     # 请求头信息
      - params                      # 请求的参数
      - response                    # 返回的结果
      - costTime                    # 耗时信息

    # 敏感信息脱敏字段（在默认 password/token/secret/credentials/privateKey 基础上追加）
    sensitive-fields:
      - idCard
      - phone
      - bankCard

    # 排除的 Header（在默认 content-length/connection/accept 等基础上追加）
    exclude-headers:
      - authorization
      - cookie

    # ===== 追踪配置 =====
    trace:
      enabled: true                 # 追踪功能开关，默认 true
      sample-rate: 1.0              # 采样率 0.0-1.0，默认 1.0 全采样

    # ===== 跨服务传播配置 =====
    propagation:
      enabled: true                 # 跨服务传播开关，默认 true
      rest-template: true           # 拦截 RestTemplate 调用，默认 true
      feign: false                  # 拦截 Feign 调用，默认 false（需要 classpath 中有 Feign 依赖）

    # ===== 异步支持配置 =====
    async:
      enabled: true                 # 异步上下文传递，默认 true
```

### 字段通配符

`fields` 支持通配符匹配：

```yaml
# 输出所有字段
fields:
  - "*"

# 前缀匹配
fields:
  - "req*"      # 匹配 requestUrl
  - "rem*"      # 匹配 remoteIp

# 后缀匹配
fields:
  - "*Info"     # 匹配 methodInfo、lineInfo
  - "*Time"     # 匹配 costTime
```

### 自定义切点范围

默认切点拦截所有 `*Controller` 类的 public 方法：

```java
@Pointcut("execution(public * *..controller..*Controller.*(..))")
```

匹配规则：
- 类名必须以 `Controller` 结尾
- 类必须位于包含 `controller` 的包路径下
- 只拦截 `public` 方法

如需调整拦截范围，可以继承 `HttpRequestLogAspect` 并重写切点，或者直接使用 `@Trace` 注解对任意方法进行追踪。`@Trace` 不受切点限制，可以标注在任何 Spring Bean 的方法上。

### 最小配置示例

**只输出请求地址和参数**：

```yaml
snow:
  logtracing:
    fields:
      - requestUrl
      - params
```

**关闭追踪功能，只保留请求日志**：

```yaml
snow:
  logtracing:
    trace:
      enabled: false
```

**完全关闭组件**：

```yaml
snow:
  logtracing:
    enable: false
```

## 常见问题

### Q1: 如何关闭日志输出？

```yaml
# 方式一：关闭整个组件
snow:
  logtracing:
    enable: false

# 方式二：只关闭追踪功能，保留请求日志
snow:
  logtracing:
    trace:
      enabled: false

# 方式三：通过日志级别控制
logging:
  level:
    io.github.snowylchen: WARN
```

### Q2: 为什么某些请求没有被记录？

检查以下几点：
- 方法是否是 `public` 的（AOP 只能拦截 public 方法）
- 类名是否以 `Controller` 结尾
- 类是否在包含 `controller` 的包路径下（如 `com.example.controller`）
- 是否配置了 `enable: false`
- 同一类内部的方法互相调用不会触发 AOP（Spring 代理机制限制）

如果需要追踪不在 Controller 层的方法，使用 `@Trace` 注解。

### Q3: traceId 没有出现在业务日志中？

确保在 logback 配置中添加了 `%X{traceId}` 占位符：

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
```

### Q4: 异步线程中 traceId 丢失？

需要将 `TraceContextTaskDecorator` 配置到你的线程池中：

```java
@Bean
public ThreadPoolTaskExecutor taskExecutor(TraceContextTaskDecorator decorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(decorator);
    return executor;
}
```

如果使用 `@Async` 注解，确保 `@Async` 指定的线程池配置了上述 decorator。

### Q5: 跨服务调用时下游没有收到 traceId？

检查以下几点：
- 下游服务是否也引入了本组件
- 调用方使用的是否是 Spring 容器管理的 `RestTemplate` Bean（`new RestTemplate()` 创建的不会被自动拦截）
- 如果使用 Feign，需要配置 `snow.logtracing.propagation.feign: true`

### Q6: 如何获取当前请求的 traceId？

在代码中可以通过以下方式获取：

```java
import io.github.snowylchen.trace.TraceContext;

// 获取当前 traceId
String traceId = TraceContext.currentTraceId();

// 获取当前 spanId
String spanId = TraceContext.currentSpanId();
```

也可以从响应头 `X-Trace-Id` 中获取。

### Q7: 日志中出现乱码？

确保项目编码设置为 UTF-8。本组件使用 ANSI 颜色码进行日志着色，如果终端不支持 ANSI，可能显示为乱码字符。在生产环境输出到文件时不受影响。

### Q8: @Trace 注解和 AOP 切面有什么区别？

| 特性 | AOP 切面（自动） | @Trace 注解（手动） |
|------|----------------|-------------------|
| 拦截范围 | Controller 层 | 任意 Spring Bean 方法 |
| 使用方式 | 自动生效 | 需要手动添加注解 |
| 记录内容 | 请求参数、Headers、响应体 | 方法级耗时（Span） |
| 适用层级 | Controller | Service、DAO、任意层 |

两者配合使用：AOP 切面负责记录 HTTP 请求/响应的详细信息，`@Trace` 负责记录内部方法调用的耗时。

## 更新日志

### v1.1.0

**新增功能：**
- 新增 TraceFilter 全链路追踪，基于 `System.nanoTime()` 精确计时
- 新增 TraceContext 追踪上下文，支持 TraceId/SpanId 生成与管理
- 新增 `@Trace` 注解，支持 Service/DAO 层方法级追踪
- 新增 Span 树状结构输出，直观展示调用链耗时分布
- 新增 MDC 集成，所有日志自动携带 traceId
- 新增 RestTemplateTraceInterceptor，支持 RestTemplate 跨服务追踪
- 新增 FeignTraceInterceptor，支持 Feign 跨服务追踪
- 新增 TraceContextTaskDecorator，支持异步线程追踪上下文传递
- 新增采样率配置 `trace.sample-rate`

**改进：**
- 计时逻辑从 AOP 切面迁移到 Filter，解决计时包含日志构建开销的问题
- `System.currentTimeMillis()` 改为 `System.nanoTime()`，提升计时精度
- HttpRequestLogAspect 集成 TraceContext，日志自动输出 traceId

### v1.0.1

- 支持自定义敏感信息脱敏规则（sensitive-fields 配置）
- 支持排除特定 Header 信息（exclude-headers 配置）
- 支持通配符 `*` 配置日志字段输出
- 新增配置属性类 LogTracingProperties
- 新增自动配置类 LogTracingAutoConfiguration

### v1.0.0

- 初始版本发布
- 支持 Controller 层自动日志记录
- 支持敏感信息脱敏
- 支持方法真实行号定位（ASM + 缓存）
- 支持彩色日志输出

## 开源协议

[Apache License 2.0](LICENSE)

## 作者信息

- **Author**: snowylchen
- **Email**: 491429856@qq.com
- **GitHub**: [SnowyLchen/snow-logtracing-springboot-starter](https://github.com/SnowyLchen/snow-logtracing-springboot-starter)
