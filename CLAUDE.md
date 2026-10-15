# CLAUDE.md

本文件用于指导 Claude Code（claude.ai/code）在本仓库中工作。

## 工具链

- 本项目是使用 Groovy DSL 的 Gradle 多模块工程。Gradle Wrapper 为 9.6.0，Android Gradle Plugin 为 9.4.0；该代码线仅支持 AGP 9。
- 运行 Gradle 和构建 `stringfog-gradle-plugin` 需要 JDK 17。插件产物以 Java 17 为目标版本；Android 无关的 core/interface/xor 库仍以 Java 11 为目标版本。
- 示例应用使用 Android SDK 36 编译并以 API 36 为目标。需要通过已忽略的 `local.properties` 或环境变量配置本机 Android SDK。
- plugin/core/interface/xor 的开发版本已统一为 6.0.0，坐标分别定义在各模块的 `gradle.properties` 中。
- AGP 9 默认使用 built-in Kotlin。Android 模块不要应用旧的 `org.jetbrains.kotlin.android` 插件；纯 JVM 的 Gradle 插件模块继续使用 `org.jetbrains.kotlin.jvm`，版本由 AGP 9 构建类路径提供。

## 常用命令

以下命令使用 Windows PowerShell 语法；在类 Unix 系统上，将 `.\gradlew.bat` 替换为 `./gradlew`。

```powershell
# 验证 Gradle/JDK 版本和项目配置
.\gradlew.bat --version
.\gradlew.bat help

# 检查完整任务图（AGP 9迁移的快速配置检查）
.\gradlew.bat build --dry-run

# 构建所有库与插件模块
.\gradlew.bat :stringfog-gradle-plugin:build :stringfog-core:build :stringfog-interface:build :stringfog-ext:xor:build

# 运行插件测试；包含真实AGP 9 app + 两个library的TestKit功能测试
.\gradlew.bat :stringfog-gradle-plugin:test

# 只运行多模块功能测试
.\gradlew.bat :stringfog-gradle-plugin:test --tests "com.github.megatronking.stringfog.plugin.StringFogPluginFunctionalTest"

# 运行所有宿主机测试或单个示例测试
.\gradlew.bat test
.\gradlew.bat :app:testDebugUnitTest --tests "com.github.megatronking.stringfog.ExampleUnitTest.addition_isCorrect"

# 验证示例应用、Lint和AndroidTest编译
.\gradlew.bat :app:lint :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleDebug

# 校验Gradle插件声明及任务属性
.\gradlew.bat :stringfog-gradle-plugin:validatePlugins

# 发布所有Java产物到~/.m2/repository，供手工消费者测试；不要在普通验证中运行
.\gradlew.bat publishToMavenLocal
```

迁移与验证过程中不要运行 `clean`；增量构建和 Configuration Cache 本身属于需要验证的行为。项目未配置 Checkstyle、ktlint 或独立格式化任务。

## 架构

StringFog 是构建期 Android 字节码转换器，并附带一组精简的运行时 API。整体流程如下：

1. 推荐在根项目应用 `stringfog-root`，通过 `StringFogRootExtension` 一次配置算法实现、运行时依赖、包过滤、key generator、模式和排除模块。它会自动给 application/library/dynamic-feature 子模块应用底层 `stringfog` 插件并添加算法依赖。
2. `StringFogRootPlugin` 只提供聚合配置；实际源码生成和插桩仍由每个子模块中的 `StringFogPlugin` 完成。被 `excludedProjects` 排除的模块不会自动应用插件或添加依赖。
3. 单模块或需要特殊配置的模块仍可直接应用 `stringfog`。`StringFogExtension` 使用 Gradle managed properties；根配置通过 Provider conventions 作为模块默认值，模块显式设置具有更高优先级。
4. `StringFogPlugin` 使用 `pluginManager.withPlugin` 等待 Android 插件，因此不依赖插件应用顺序。它通过 `AndroidComponentsExtension.onVariants` 为每个 variant 独立注册源码生成和插桩。
5. 每个 variant 默认使用 `variant.namespace` 作为生成代码的包名；显式模块级 `stringfog.packageName` 会覆盖它。`SourceGeneratingTask` 生成 `<包名>.StringFog`，并通过 `variant.sources.java.addGeneratedSourceDirectory` 接入编译图。
6. `StringFogInstrumentationParams` 只包含 Gradle 托管输入：实现类名、生成类名、包过滤、模式和可序列化的 key generator。不得重新引入按 applicationId 索引的静态 Map、弱引用、extension/Project 对象或跨模块共享日志。
7. `StringFogTransform` 为当前类从自己的 variant 参数创建转换依赖，然后由 `ClassVisitorFactory` 排除内置白名单（`BuildConfig`、`R`、`R2` 和 `StringFog`）及 `fogPackages` 外的类。
8. `StringFogClassVisitor` 在构建期加密字符串常量，并将字节码常量/字段初始化替换为生成类的 `StringFog.decrypt` 调用。`base64` 模式写入Base64字符串；`bytes` 模式写入字节数组。标注 `@StringFogIgnore` 的类不处理。

### 模块边界

- `stringfog-interface`：构建期和运行时共用的 Android 无关契约，包括 `IStringFog`、可序列化的 `IKeyGenerator`、`@StringFogIgnore`、`StringFogWrapper` 和 Base64 实现。
- `stringfog-core`：ASM 9 字节码改写、过滤、内置 key generator 和 Java 源码生成，不依赖 AGP。保留 `ClassVisitorFactory` 的带日志旧重载，但 AGP 9 插件走无共享日志的新重载。
- `stringfog-gradle-plugin`：AGP/Gradle 接入层。`stringfog-root` 负责根项目聚合、自动应用和运行时依赖注入；`stringfog` 负责每个 Android 模块的 Variant、Sources 和 Instrumentation。`java-gradle-plugin` 生成两个插件 descriptor 及 TestKit 元数据。
- `stringfog-ext/xor`：默认的构建期/运行时算法实现。`settings.gradle` 会自动包含 `stringfog-ext` 下的每个目录。
- `app`：最小 Android 构建样例，运行时直接依赖本地 xor project；插件的端到端覆盖由 TestKit fixture 完成。

### 多模块与缓存约束

- 插桩范围固定为 `InstrumentationScope.PROJECT`：app 不会重复处理依赖库，library 会把自身已转换字节码打入 AAR。
- 根配置中的算法类必须位于根 buildscript 插件类路径；`runtimeDependency` 会通过 `DependencyHandler.addProvider` 自动加入每个未排除 Android 模块的 `implementation`。
- `IKeyGenerator` 及其全部字段必须可序列化，因为实例是 Gradle instrumentation 的托管输入。不要在自定义 generator 中保存 `Project`、`Variant`、`Task`、class loader 等配置期对象。
- `RandomKeyGenerator` 的 `SecureRandom` 是 transient 状态，反序列化后按需重建；不要把变化中的随机状态重新纳入 Gradle 输入快照。
- `SourceGeneratingTask.outputDirectory` 必须保持为 `@OutputDirectory DirectoryProperty`，并始终通过 Variant Sources API 连接，不能退回 `registerJavaGeneratingTask` 或直接修改 source set。
- `StringFogPluginFunctionalTest` 保留两条端到端路径：按模块独立配置，以及仅根项目配置/自动应用/自动依赖。两者都在 `--parallel` 和严格 Configuration Cache 下连续构建，并检查生成源码、排除模块及 AAR 中 Java/Kotlin 字节码的 decrypt owner/descriptor 和明文移除。
- `stringfog-root` 是 root Project 聚合插件，支持 Configuration Cache，但其跨项目配置模型不兼容 Gradle Isolated Projects；若要支持隔离项目，应改为 Settings plugin + lifecycle API。
- Mapping 输出自5.0起仍停用；AGP 9路径不会收集或写出共享明文/密文日志。

## 发布

每个可发布模块应用 `maven_local.gradle` 定义现有 `MavenPublication`。普通构建继续使用 `com.github.megatronking.stringfog` 和模块 `VERSION`；当 `JITPACK=true` 时，脚本改用 JitPack 提供的 `GROUP.ARTIFACT` 与 Git 标签 `VERSION`，形成 `com.github.xs93.StringFog:<artifactId>:<tag>`。`jitpack.yml` 固定使用 JDK 17，并执行 `build publishToMavenLocal`。

插件模块的 `java-gradle-plugin.automatedPublishing` 为 `false`，避免改变原有发布坐标；Maven Central 发布配置仍在 `maven_nexus.gradle` 中并由模块注释。普通开发和验证不得启用签名、读取发布凭据或执行远端发布。