# snow-logtracing-springboot-starter

> 轻量级 Spring Boot 全链路追踪与日志记录组件，集成分布式 TraceId、请求日志自动采集与敏感数据脱敏，即引即用。

## 项目简介

`snow-logtracing-springboot-starter` 是一个面向 Spring Boot 3.x 的轻量级可观测性组件，提供两大核心能力：

- **全链路追踪**：基于 TraceFilter + TraceContext 实现分布式追踪，自动生成/传播 TraceId，支持跨服务调用链串联，使用 `System.nanoTime()` 精确计时，输出 Span 树状耗时汇总。
- **请求日志记录**：基于 AOP 切面自动拦截 Controller 层请求，记录请求地址、参数、Headers、响应结果等完整信息，支持敏感字段脱敏保护。
- **系统操作日志**：基于 `@SysLog` 注解实现业务操作审计，支持 SpEL 表达式解析动态参数，提供 SPI 接口支持自定义日志持久化和操作人获取。

### 核心特性

- **分布式追踪**：自动生成 TraceId/SpanId，通过 HTTP Header 跨服务传播，MDC 集成让所有日志自动携带追踪信息
- **精确计时**：TraceFilter 使用 `System.nanoTime()` 计时，只包裹业务执行，不计入日志构建等额外开销
- **方法级追踪**：`@Trace` 注解标注 Service/DAO 层方法，自动创建子 Span 记录耗时
- **Span 树输出**：请求结束后输出完整的调用链树状结构，直观展示各层耗时分布
- **业务审计日志**：通过 `@SysLog` 手动标注，记录“谁在什么时间做了什么操作”，支持 SpEL 动态描述（如 `删除用户 #{#id}`）
- **自动记录**：零代码自动拦截所有 Controller 请求，记录完整的请求和响应信息
- **SPI 扩展**：提供 `SysLogHandler` 和 `OperatorProvider` 接口，解耦存储媒介（MySQL/ES/MQ）与用户体系
- **数据脱敏**：内置 password、token、secret 等敏感字段过滤，支持自定义扩展
- **真实行号**：通过 ASM 字节码技术获取方法真实行号，支持 IDE 点击跳转
- **异常安全**：插件内部逻辑全 try-catch 保护，任何插件异常绝不阻塞或中断业务流程
- **跨服务传播**：自动为 RestTemplate、Feign 注入 trace header，无需手动传递
- **异步支持**：自动开启 `@EnableAsync` 并提供 TaskDecorator，支持异步日志投递与追踪上下文传递
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
| **SysLogAspect** | `@SysLog` 注解的 AOP 切面处理，支持类级/方法级拦截 |
| **SysLogExpressionEvaluator** | SpEL 表达式解析器，带编译缓存，用于解析动态描述 |
| **SysLogEventListener** | 异步事件监听器，负责将日志分发给所有 `SysLogHandler` 实现 |

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

- Java 8
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

### 步骤七：使用 @SysLog 记录业务操作日志

`@SysLog` 用于记录具有业务语义的操作日志（如：新增用户、审核订单）。

#### 1. 标注业务方法

支持类级别（拦截所有 public 方法）和方法级别（覆盖类级配置）：

```java
@RestController
@RequestMapping("/user")
@SysLog(module = "用户管理") // 类级注解
public class UserController {

    @SysLog(value = "新增用户", type = OperationType.INSERT)
    @PostMapping("/add")
    public Result add(@RequestBody User user) { ... }

    @SysLog(value = "删除用户 #{#id}", type = OperationType.DELETE) // 支持 SpEL
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) { ... }
}
```

#### 2. 自定义持久化（实现 SysLogHandler）

Starter 默认不存储日志，需由使用者实现接口决定落库方式：

```java
@Component
public class MySysLogHandler implements SysLogHandler {
    @Override
    public void handle(SysLogInfo logInfo) {
        // 异步执行，可以存入 MySQL、ElasticSearch 或发送到 MQ
        System.out.println("收到操作日志：" + logInfo.getDescription());
    }
}
```

#### 3. 关联操作人（实现 OperatorProvider）

实现该接口以自动填充日志中的操作人 ID 和名称：

```java
@Component
public class MyOperatorProvider implements OperatorProvider {
    @Override
    public String getOperatorId() {
        return SecurityUtils.getUserId(); // 对接你的权限框架
    }

    @Override
    public String getOperatorName() {
        return SecurityUtils.getUserName();
    }
}
```

## 进阶技巧：自定义全局异常处理器携带 TraceId

由于插件不再强制绑定异常返回格式，你可以非常方便地在自己的 `@ControllerAdvice` 中引入 `traceId`，从而保持全站响应结构一致：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception e) {
        // 1. 调用插件提供的上下文工具获取当前 traceId
        String traceId = TraceContext.currentTraceId();
        
        // 2. 返回你项目自定义的 Result 对象
        return Result.fail(500, e.getMessage(), traceId);
    }
}
```

## 配置参考

### 完整配置项

```yaml
snow:
  logtracing:
    # ===== 基础配置 =====
    enable: true                    # 总开关，控制整个 starter 是否激活，默认 true

    # HTTP 请求日志输出控制（独立于追踪和计时模块）
    request-log:
      enabled: true                 # 是否输出 Controller 层请求/响应日志（参数、Headers、返回结果等），默认 true

    # 需要输出的日志字段，不配置则输出所有字段，支持通配符 *
    fields:
      - requestUrl                  # 请求地址（如 GET http://localhost:8080/api/user）
      - methodInfo                  # 类名方法（如 UserController#getUser）
      - lineInfo                    # 类名快捷跳转，IDE 可点击（如 UserController.java:45）
      - remoteIp                    # 客户端远程 IP 地址
      - headers                     # 请求头信息（已排除无关 header，已脱敏）
      - params                      # 请求参数（JSON 格式，已脱敏）
      - response                    # 返回结果（JSON 格式，已脱敏）
      - costTime                    # 耗时信息

    # 敏感信息脱敏字段（追加到内置的 password/token/secret/credentials/privateKey 之上）
    sensitive-fields:
      - idCard                      # 自定义脱敏字段
      - phone
      - bankCard

    # 排除的请求头（追加到内置的 content-length/connection/accept 等之上）
    exclude-headers:
      - authorization               # 自定义排除 header
      - cookie

    # ===== 模块一：接口计时统计 =====
    # 独立控制请求耗时测量和慢接口检测，与追踪模块互不影响
    timing:
      enabled: true                 # 计时功能开关，默认 true
      slow-threshold: -1            # 慢接口阈值（毫秒），超过此值标记为慢接口并警告，-1 表示不检测

    # ===== 模块二：分布式追踪 =====
    # 独立控制 TraceId/Span 链路追踪，与计时模块互不影响
    trace:
      enabled: true                 # 追踪功能开关，默认 true
      auto-trace-service: false     # 自动追踪 Service 层（*..service..*Service）方法，默认关闭
      span-tree-log: true           # 请求结束时输出 Span 树状调用链汇总，默认 true

      # 跨服务传播配置（仅追踪模块生效时有意义）
      propagation:
        rest-template: true         # 自动为 RestTemplate 注入追踪 header，默认 true
        feign: false                # 自动为 Feign 注入追踪 header，默认 false（需 classpath 有 Feign）

      # 异步线程上下文传递
      async:
        enabled: true               # 提供 TaskDecorator Bean，用于线程池传递 traceId，默认 true

    # ===== 模块三：系统操作日志 (@SysLog) =====
    sys-log:
      enabled: true                 # 是否启用系统操作日志功能，默认启用
```

> **配置独立性**：共有四个独立开关，互不影响：
> - `enable`：总开关，控制整个 starter 是否激活
> - `request-log.enabled`：HTTP 请求日志输出开关（Controller 层参数/响应日志）
> - `timing.enabled`：接口计时统计开关（请求耗时、慢接口检测）
> - `trace.enabled`：分布式追踪开关（TraceId/Span/调用链）
>
> 可以灵活组合使用，如：
> - 只开 request-log（仅输出请求日志）
> - 只开 timing（仅计时）
> - 只开 trace（仅追踪）
> - 或同时开启多个获得完整体验

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
- 如果使用 Feign，需要配置 `snow.logtracing.trace.propagation.feign: true`

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

### v1.3.0

**核心升级：新增业务审计日志模块 (@SysLog)**
- 新增 `@SysLog` 注解，支持类级别与方法级别标注，支持 SpEL 表达式解析动态参数
- 引入 SPI 扩展架构：通过 `SysLogHandler` 解耦日志存储，通过 `OperatorProvider` 解耦用户体系
- 极致性能优化：SpEL 编译缓存、注解查找缓存、脱敏过滤器缓存，切面执行微秒级
- 异步驱动：基于 Spring Event + `@Async` 实现日志投递，绝不阻塞业务线程
- 异常安全增强：插件内部逻辑全方位 try-catch 保护，插件故障绝不影响业务运行
- 自动开启异步：自动配置类注册 `@EnableAsync`，确保日志投递与异步上下文传播正常工作

### v1.2.1

**配置精细化升级：**
- HTTP 请求日志输出从 `snow.logtracing.enable` 总开关中分离出来，新增独立配置 `snow.logtracing.request-log.enabled`
- `enable` 现在仅控制整个 starter 是否激活
- `request-log.enabled` 控制 Controller 层请求/响应日志（参数、Headers、返回结果）的输出
- 四个开关完全独立：`enable`（总）、`request-log.enabled`（请求日志）、`timing.enabled`（计时）、`trace.enabled`（追踪）

### v1.2.0

**配置架构升级：**
- 接口计时统计（timing）和分布式追踪（trace）拆分为独立配置模块，各自拥有独立开关和参数
- 新增 `timing` 配置节点：`enabled`（开关）、`slow-threshold`（慢接口阈值）
- 新增 `trace.span-tree-log` 配置，控制 Span 树汇总日志的输出
- `propagation` 和 `async` 配置从顶层迁移至 `trace` 下（`trace.propagation.*`、`trace.async.*`）
- 仅开启 timing 时自动生成 `requestId` 写入 MDC，方便日志关联
- 自动配置拆分为 `LogTracingAutoConfiguration`（公共层）和 `TraceAutoConfiguration`（追踪模块）
- 移除 `@ComponentScan`，所有 bean 由 AutoConfiguration 显式注册管理

**Breaking Change：**
- 配置路径变更：`snow.logtracing.propagation.*` -> `snow.logtracing.trace.propagation.*`
- 配置路径变更：`snow.logtracing.async.*` -> `snow.logtracing.trace.async.*`

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

## 作者信息

- **Author**: snowylchen
- **Email**: 491429856@qq.com
- **GitHub**: [SnowyLchen/snow-logtracing-springboot-starter](https://github.com/SnowyLchen/snow-logtracing-springboot-starter)
- **GitHub**: [SnowyLchen/snow-logtracing-springboot-starter](https://github.com/SnowyLchen/snow-logtracing-springboot-starter)
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

## 开源协议

[Apache License 2.0](LICENSE)

## 作者信息

- **Author**: snowylchen
- **Email**: 491429856@qq.com
- **GitHub**: [SnowyLchen/snow-logtracing-springboot-starter](https://github.com/SnowyLchen/snow-logtracing-springboot-starter)
