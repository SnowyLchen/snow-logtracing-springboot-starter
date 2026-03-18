# snow-logtracing-springboot-starter

> 基于 Spring AOP 实现的系统日志记录以及数据脱敏插件，即引即用，让日志记录更简单

## 📖 项目简介

`snow-logtracing-springboot-starter` 是一个轻量级的 Spring Boot 日志追踪组件，通过 AOP 切面技术自动记录 Controller 层的 HTTP 请求日志。它能够帮助开发者快速定位问题、追踪请求链路，同时支持敏感信息脱敏，保障数据安全。

### ✨ 核心特性

- **自动记录**：无需手动编写日志代码，自动拦截所有 Controller 请求
- **详细信息**：记录请求地址、参数、headers、响应结果、执行时间等完整信息
- **数据脱敏**：支持敏感字段（如密码、手机号等）自动脱敏处理
- **彩色日志**：使用 ANSI 颜色码，让日志在控制台更加清晰易读
- **性能优化**：使用方法行号缓存机制，避免重复解析字节码，提升性能
- **真实行号**：通过 ASM 字节码技术获取方法真实行号，精确定位代码位置
- **即引即用**：零配置启动，支持自定义 JSON 解析器（FastJSON2/Jackson）

### 🎯 适用场景

- 生产环境请求追踪和问题排查
- 接口调试和性能分析
- 安全审计和敏感数据保护
- 分布式系统调用链追踪

## 📊 技术架构

### 核心组件

- **HttpRequestLogAspect**：AOP 切面核心，负责拦截和记录 HTTP 请求
- **ServerInfo**：服务器信息自动配置，提供主机名、IP、端口等信息
- **LogUtil**：日志工具类，提供日志格式化、彩色输出等功能
- **FunExecuteTimeUtil**：方法执行时间计算工具

### 技术栈

- **Spring Boot 2.7.9**：核心框架
- **Spring AOP**：面向切面编程
- **FastJSON2 2.0.31**：JSON 序列化与反序列化
- **Hutool 5.8.43**：Java 工具库
- **Lombok 1.18.26**：简化代码
- **ASM**：字节码解析，获取方法真实行号

### 工作原理

1. **拦截请求**：通过 AOP 切面拦截 Controller 层的所有 public 方法
2. **收集信息**：提取请求地址、参数、headers、线程信息等
3. **字节码解析**：使用 ASM 技术解析方法真实行号（带缓存优化）
4. **执行方法**：调用目标方法并记录执行时间
5. **记录响应**：序列化返回结果，过滤敏感字段
6. **格式化输出**：使用 ANSI 颜色码格式化日志，便于阅读

## 🚀 使用方式

### 前置要求

- Java 1.8+
- Spring Boot 2.x
- Maven 3.0+

### 引入依赖

```xml
<!-- 引入日志追踪组件 -->
<dependency>
  <groupId>com.snow.logtracing</groupId>
  <artifactId>snow-logtracing-springboot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```
### 配置日志输出字段（可选）

通过配置 `snow.logtracing` 相关参数，可以自定义需要输出的日志字段：

```yaml
# application.yml
snow:
  logtracing:
    # 是否启用日志追踪，默认 true
    enable: true
    # 需要输出的日志字段，不配置则默认输出所有字段
    fields:
      - requestUrl    # 请求地址
      - methodInfo    # 类名方法
      - lineInfo      # 类名快捷跳转（行号）
      - remoteIp      # 远程地址
      - headers       # 请求头信息
      - params        # 请求的参数
      - response      # 返回的结果
      - costTime      # 耗时信息
```

#### 🔥 通配符支持

**1. 使用 `*` 匹配所有字段**

```yaml
snow:
  logtracing:
    fields:
      - "*"   # 输出所有字段（等同于不配置 fields）
```

**2. 使用部分通配符（前缀匹配）**

```yaml
snow:
  logtracing:
    fields:
      - "req*"    # 匹配 requestUrl
      - "rem*"    # 匹配 remoteIp
      - "resp*"   # 匹配 response
```

**3. 使用部分通配符（后缀匹配）**

```yaml
snow:
  logtracing:
    fields:
      - "*Url"    # 匹配 requestUrl
      - "*Info"   # 匹配 methodInfo、lineInfo
      - "*Time"   # 匹配 costTime
```

**示例 1：只输出请求地址和参数**

```yaml
snow:
  logtracing:
    fields:
      - requestUrl
      - params
```

**示例 2：关闭日志追踪**

```yaml
snow:
  logtracing:
    enable: false
```

**示例 3：不输出响应结果和耗时**

```yaml
snow:
  logtracing:
    fields:
      - requestUrl
      - methodInfo
      - remoteIp
      - params
      - headers
```

### 高级配置（可选）

#### 1️⃣ 自定义切点范围

默认拦截所有 `*Controller` 的 public 方法：


#### 2️⃣ 自定义敏感信息脱敏规则

默认会脱敏 `password`、`token`、`secret`、`credentials`、`privateKey` 字段，可以添加更多需要脱敏的字段：

```yaml
snow:
  logtracing:
    # 添加需要脱敏的字段（在默认基础上追加）
    sensitive-fields:
      - idCard          # 身份证号
      - phone           # 手机号
      - bankCard        # 银行卡号
      - email           # 邮箱
      - address         # 地址
      - realname        # 真实姓名
```

#### 3️⃣ 排除特定 Header 信息

默认会排除一些常见的无关 header，可以添加更多需要排除的 header：

```yaml
snow:
  logtracing:
    # 添加需要排除的 Header（在默认基础上追加）
    exclude-headers:
      - authorization   # 认证信息
      - cookie          # Cookie 信息
      - x-request-id    # 请求 ID
      - user-agent      # 用户代理
```

### 步骤五：开始使用

完成以上配置后，插件会自动生效！所有 `*Controller` 类的 public 方法都会被自动拦截并记录日志。

#### 示例日志输出：

```
========================== 请求开始 ==========================
2024-03-18 10:30:45 [http-nio-8080-exec-1] com.snow.logtracing.aspect.HttpRequestLogAspect : 
请求地址：GET http://localhost:8080/api/user/123
类名方法：(com.example.controller.UserController#getUserById)
类名快捷跳转：(UserController.java:45)
远程地址：192.168.1.100
请求头信息：{"Host":"localhost:8080","User-Agent":"Mozilla/5.0"}
请求的参数：{"id":123}
==================== 请求结束 /api/user/123 总耗时：15ms =====================
返回的结果:{"code":200,"data":{"id":123,"name":"张三"}}
```

## ⚙️ 高级配置

### 自定义敏感信息脱敏规则

编辑 `buildSensitiveInfoFilter()` 方法，添加需要排除的字段：

```java
// 在 HttpRequestLogAspect.java 中
private static SimplePropertyPreFilter buildSensitiveInfoFilter() {
    String[] excludeProperties = {"password", "token", "secret", "idCard", "phone"};
    SimplePropertyPreFilter filters = new SimplePropertyPreFilter();
    for (String str : excludeProperties) {
        filters.getExcludes().add(str);
    }
    return filters;
}
```

### 自定义切点范围

修改 `controllerPointcut()` 切点表达式来调整拦截范围：

```java
// 当前配置：拦截所有 Controller 的 public 方法
@Pointcut("execution(public * *..controller..*Controller.*(..))")
public void controllerPointcut() {}

// 可以修改为：
// @Pointcut("execution(* *..service..*Service.*(..))") // 拦截 Service 层
// @Pointcut("@annotation(com.xxx.MyLogAnnotation)") // 拦截自定义注解
```

### 排除特定 Header 信息

修改 `extractHeadersInfo()` 方法中的 `excludeHeader` 数组：

```java
String[] excludeHeader = {
    "content-length",
    "connection",
    "authorization", // 添加需要排除的 header
};
```

## 🔧 常见问题

### Q1: 如何关闭日志输出？

在 `application.yml` 中配置：

```yaml
snow:
  logtracing:
    enable: false
```

或者设置日志级别：

```yaml
logging:
  level:
    com.snow.logtracing.aspect: WARN
```

### Q2: 为什么某些请求没有被记录？

检查以下几点：
- 请求的方法是否是 `public` 的
- 类名是否以 `Controller` 结尾（默认配置）
- 类是否在 `*..controller..` 包路径下
- 是否配置了 `enable: false`

如需调整拦截范围，请修改切点表达式：

```yaml
snow:
  logtracing:
    pointcut: "execution(public * *..controller..*Controller.*(..))"
```

### Q3: 日志中出现乱码怎么办？

确保你的 IDE 和控制台编码设置为 UTF-8：

```yaml
# application.yml
spring:
  mvc:
    format:
      date-time: yyyy-MM-dd HH:mm:ss
server:
  tomcat:
    uri-encoding: UTF-8
```

### Q4: 如何自定义日志格式？

修改 `LogUtil.java` 中的 `REQUEST_START`、`REQUEST_END` 常量，或者调整 `buildRequestLog()` 方法。

### Q5: 如何添加更多的敏感字段脱敏？

```yaml
snow:
  logtracing:
    sensitive-fields:
      - idCard
      - phone
      - bankCard
      - email
```

### Q6: 如何排除更多的 Header 信息？

```yaml
snow:
  logtracing:
    exclude-headers:
      - authorization
      - cookie
      - x-custom-header
```

## 📝 更新日志

### v1.0.1 (2024-03-18)

**新增功能：**
- ✅ 支持自定义切点范围（pointcut 配置）
- ✅ 支持自定义敏感信息脱敏规则（sensitive-fields 配置）
- ✅ 支持排除特定 Header 信息（exclude-headers 配置）
- ✅ 支持通配符 `*` 配置日志字段输出
- ✅ 新增配置属性类 LogTracingProperties
- ✅ 新增自动配置类 LogTracingAutoConfiguration

### v1.0.0 (2024-03-18)

- ✅ 初始版本发布
- ✅ 支持 Controller 层自动日志记录
- ✅ 支持敏感信息脱敏
- ✅ 支持方法真实行号定位（ASM + 缓存）
- ✅ 支持彩色日志输出
- ✅ 支持 FastJSON2/Jackson 双解析器

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 开源协议

MIT License

## 👤 作者信息

- **Author**: chen
- **Email**: smallchill@163.com
- **Project**: snow-logtracing-springboot-starter

---

**如果这个项目对你有帮助，请给一个 ⭐ Star 支持！**
