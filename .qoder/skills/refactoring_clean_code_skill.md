# 🧩 Skill: Refactoring & Clean Code（代码重构与可读性优化）

## 📌 Skill 定义
Refactoring（重构）是指在不改变程序外部行为的前提下，对代码结构进行优化，从而提升可读性、可维护性和扩展性。

---

## 🎯 核心能力
- 提升代码可读性（让人快速理解业务逻辑）
- 降低代码复杂度（减少嵌套与冗余）
- 增强可维护性（方便修改和扩展）
- 提高团队协作效率（代码更易交接）

---

## 🛠️ 常见 Refactoring 技巧

### 1. Extract Method（方法拆分）
- 将大方法拆分为多个小方法
- 每个方法只负责单一职责

```java
public void processOrder(Order order) {
    validate(order);
    calculatePrice(order);
    saveOrder(order);
}
```

---

### 2. Rename（命名优化）
- 使用有意义的变量名、方法名
- 避免缩写和模糊命名
- 避免使用硬编码
```java
int rentalDays;
```

---

### 3. Keep Methods Small（小函数原则）
- 方法长度控制在 10–30 行
- 一个方法只做一件事

---

### 4. Remove Duplication（消除重复 / DRY）
- 抽取公共逻辑
- 避免重复代码

```java
if (isAdult(user)) {
    // ...
}
```

---

### 5. Simplify Logic（简化逻辑）
- 使用 Guard Clause 减少嵌套

```java
if (!valid) return;
process();
```

---

### 6. Avoid Magic Values（避免魔法值）
- 使用常量或枚举替代硬编码

```java
private static final int MAX_RETRY = 3;
```

---

### 7. Replace Condition with Polymorphism（用多态替代条件）
- 减少 if-else / switch
- 提高扩展性

```java
strategyMap.get(type).execute();
```

---

### 8. Modularity（模块化设计）
- 将复杂逻辑拆分为多个模块
- 每个模块职责清晰

---

## 🧠 设计原则（支撑该 Skill）
- 单一职责原则（SRP）
- 开闭原则（OCP）
- DRY（Don't Repeat Yourself）
- KISS（Keep It Simple, Stupid）

---

## 📈 能力评估标准

- 方法是否过长？
- 是否存在重复代码？
- 命名是否清晰表达业务含义？
- 是否有超过 2 层嵌套？
- 是否需要额外注释才能理解？

---

## 💼 简历描述（可直接使用）

**Skill: Refactoring & Clean Code**
- 熟练使用 Extract Method、Rename、DRY 等重构技巧  
- 能将复杂业务逻辑优化为高可读、低耦合结构  
- 提升代码可维护性与团队协作效率  

---

## 🧭 一句话总结

Refactoring 的本质是：让代码更容易被人理解，而不是更“聪明”
