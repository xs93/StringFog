
# StringFog
一款自动对dex/aar/jar文件中的字符串进行加密Android插件工具，正如名字所言，给字符串加上一层雾霭，使人难以窥视其真面目。

- 支持java/kotlin。
- 支持app打包生成的apk加密。
- 支持aar和jar等库文件加密。
- 支持加解密算法的自主扩展。
- 支持配置可选代码加密。
- 完全Gradle自动化集成。
- 不支持InstantRun。

**一些提示**
> 由于我目前主要精力在[Reqable](https://reqable.com)创业项目上，StringFog的Issue处理没那么及时，非常抱歉！
虽然我已经很久不从事Android项目的开发，但是还是会尽力将StringFog一直维护下去，如果您发现了一些可以修复的问题，欢迎提交PR。

### 原理

![](https://github.com/MegatronKing/StringFog/blob/master/assets/flow.png)<br>

- 加密前：
```java
String a = "This is a string!";
```

- 加密后：
```java
String a = StringFog.decrypt(new byte[]{-113, 71...}, new byte[]{-23, 53});

```

- 运行时：
```java
decrypt: new byte[]{-113, 71...} => "This is a string!"
```

### 混淆
StringFog和混淆完全不冲突，也不需要配置反混淆，实际上StringFog配上混淆效果会更好！

### 使用

6.0.0版本仅支持AGP 9，当前构建基线为AGP 9.4.0、Gradle 9.6.0和JDK 17。以下为6.0.0发布后的集成方式；本仓库中的开发验证使用本地产物。

##### 1、在根目录build.gradle中引入插件依赖。
```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:9.4.0'
        classpath 'com.github.megatronking.stringfog:gradle-plugin:6.0.0'
        // 选用加解密算法库，默认实现了xor算法，也可以使用自己的加解密库。
        classpath 'com.github.megatronking.stringfog:xor:6.0.0'
    }
}
```

项目的Gradle Wrapper应使用AGP 9.4.0兼容的9.6.0版本。

##### 2、推荐：在根项目统一配置所有Android子模块

```groovy
apply plugin: 'stringfog-root'

import com.github.megatronking.stringfog.plugin.StringFogMode
import com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator

stringfog {
    // 必要：构建期使用的加解密实现类。
    implementation = 'com.github.megatronking.stringfog.xor.StringFogImpl'
    // 必要：自动添加到每个目标Android模块implementation配置的运行时依赖。
    runtimeDependency = 'com.github.megatronking.stringfog:xor:6.0.0'
    // 可选：默认加密每个模块的全部项目代码。
    fogPackages = ['com.xxx.xxx']
    kg = new RandomKeyGenerator()
    mode = StringFogMode.base64
    // 可选：不自动应用插件、不添加运行时依赖的模块路径。
    excludedProjects = [':sample', ':benchmark']
}
```

根插件会自动处理所有`com.android.application`、`com.android.library`和`com.android.dynamic-feature`子模块。普通子模块只需应用自己的Android插件，无需再声明`stringfog`、`stringfog {}`或xor运行时依赖：

```groovy
plugins {
    id 'com.android.library'
}
```

每个模块仍使用自己的`namespace`生成独立的`StringFog`类，并只转换本模块代码，因此library输出的AAR本身也是已加密产物。`com.android.dynamic-feature`指依附基础app、可按需或安装时交付的App Bundle动态功能模块；项目未使用此类模块时不会产生额外行为。

Kotlin DSL根配置参考：

```kotlin
import com.github.megatronking.stringfog.plugin.StringFogMode
import com.github.megatronking.stringfog.plugin.StringFogRootExtension
import com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator

apply(plugin = "stringfog-root")

configure<StringFogRootExtension> {
    implementation.set("com.github.megatronking.stringfog.xor.StringFogImpl")
    runtimeDependency.set("com.github.megatronking.stringfog:xor:6.0.0")
    fogPackages.set(listOf("com.xxx.xxx"))
    kg.set(RandomKeyGenerator())
    mode.set(StringFogMode.base64)
    excludedProjects.set(setOf(":sample", ":benchmark"))
}
```

根项目模式支持Configuration Cache和并行构建。Gradle Isolated Projects禁止root Project插件跨项目配置，因此该模式暂不支持Isolated Projects。

##### 3、可选：单个模块独立配置

被根配置排除的模块，或不使用`stringfog-root`的单模块项目，仍可直接应用原有插件：

```groovy
apply plugin: 'stringfog'

import com.github.megatronking.stringfog.plugin.StringFogMode
import com.github.megatronking.stringfog.plugin.kg.RandomKeyGenerator

stringfog {
    implementation = 'com.github.megatronking.stringfog.xor.StringFogImpl'
    // 可选：默认使用当前模块namespace；设置后将覆盖namespace。
    packageName = 'com.github.megatronking.stringfog.app'
    enable = true
    fogPackages = ['com.xxx.xxx']
    kg = new RandomKeyGenerator()
    mode = StringFogMode.base64
}

dependencies {
    implementation 'com.github.megatronking.stringfog:xor:6.0.0'
}
```

两种模式都使用`InstrumentationScope.PROJECT`：app不会重复处理依赖库，library和dynamic-feature分别处理自己的代码。AGP 9使用内置Kotlin支持，Android模块不要再应用旧的`org.jetbrains.kotlin.android`插件。

##### 4、通过JitPack使用

仓库发布`6.0.0`标签并由JitPack构建成功后，将JitPack仓库同时加入buildscript和普通依赖仓库：

```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.github.xs93.StringFog:gradle-plugin:6.0.0'
        classpath 'com.github.xs93.StringFog:xor:6.0.0'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

根配置中的运行时依赖相应改为：

```groovy
stringfog {
    implementation = 'com.github.megatronking.stringfog.xor.StringFogImpl'
    runtimeDependency = 'com.github.xs93.StringFog:xor:6.0.0'
}
```

JitPack多模块坐标格式为`com.github.xs93.StringFog:<模块artifactId>:<Git标签>`。本仓库会发布`gradle-plugin`、`core`、`interface`和`xor`四个模块；JitPack构建使用JDK 17，并执行全量`build`和`publishToMavenLocal`。

### 扩展

#### 注解反加密
如果开发者有不需要自动加密的类，可以使用注解StringFogIgnore来忽略：
```java
@StringFogIgnore
public class Test {
    ...
}
```
#### 自定义加解密算法实现
实现IStringFog接口，参考stringfog-ext目录下面的xor算法实现。
注意某些算法在不同平台上会有差异，可能出现在运行时无法正确解密的问题。如何集成请参考下方范例！
```java
public final class StringFogImpl implements IStringFog {

    @Override
    public byte[] encrypt(String data, byte[] key) {
        // 自定义加密
    }

    @Override
    public String decrypt(byte[] data, byte[] key) {
        // 自定义解密
    }

    @Override
    public boolean shouldFog(String data) {
        // 控制指定字符串是否加密
        // 建议过滤掉不重要或者过长的字符串
        return true;
    }

}

```

#### 自定义密钥生成器
实现IKeyGenerator接口，参考RandomKeyGenerator的实现。AGP 9版本会把密钥生成器作为Gradle托管的插桩参数，因此实现类及其全部字段必须可序列化；不要在实现中保存Project、Variant、Task等Gradle配置对象。

#### Mapping文件
**注意⚠️：从5.0版本起该功能仍处于停用状态**
加解密的字符串明文和暗文会自动生成mapping映射文件，位于outputs/mapping/stringfog.txt。

## 范例
- 默认加解密算法集成，参考[sample1](https://github.com/MegatronKing/StringFog-Sample1)
- 自定义加解密算法集成，参考[sample2](https://github.com/MegatronKing/StringFog-Sample2)

## 更新日志

### v6.0.0
- 仅支持AGP 9，构建基线升级到AGP 9.4.0、Gradle 9.6.0、JDK 17和ASM 9.9。
- 源码生成迁移到公开的Android Components Variant/Sources API，移除`BaseVariant`等旧API。
- 插桩配置改为Gradle托管参数，移除进程级单例、弱引用和共享日志，支持多模块并行构建及Configuration Cache。
- 新增`stringfog-root`根项目插件，可自动为所有Android子模块应用插件、下发统一配置并添加运行时算法依赖。
- 新增app + 多library的TestKit集成测试，分别验证base64/bytes模式、Java/Kotlin字节码和模块配置隔离。
- 自定义`IKeyGenerator`及其字段必须可序列化。

### v5.2.0
- 从ASM7升级到ASM9。
- 修复多模块配置问题。

### v5.1.0
- 修复获取无法获取packageName的问题。
- 修复无法指定KeyGenerator的问题。
- 优化生成StringFog.java文件的任务逻辑。
- 暂时移除Mapping文件生成逻辑，可能导致无法删除的问题。

### v5.0.0
- 支持Gradle 8.0。

### v4.0.1
- 修复Base64 API版本兼容问题。

### v4.0.0
- 使用ASM7以支持Android 12。
- 支持AGP(Android Gradle Plugin) 7.x版本。
- DSL新增StringFogMode选项，用于控制字符串加密后在字节码中的存在形式，支持base64和bytes两种模式，默认使用base64。
    - base64模式：将字符串加密后的字节序列使用base64编码，行为同1.x和2.x版本。
    - bytes模式：将字符串加密后的字节序列直接呈现在字节码中，行为同3.x版本。

### v3.0.0
- 密文不再以String形式存在，改为直接字节数组，感谢PR #50。
- 重构公开API相关代码（不兼容历史版本）。
- 删除AES加密实现，考虑到存在bug和性能问题且意义不大。
- xor算法移除base64编码。
- 固定加密字符串key改为随机key，且提供IKeyGenerator接口支持自定义实现。
- 插件依赖的ASM库由5.x升级到9.2。

### v2.2.1
- 修复module-info类导致的报错问题

### v2.2.0
- 支持AGP(Android Gradle Plugin) 3.3.0+版本

### v2.1.0
- 修复kotlin打包的bug

### v2.0.1
- 增加implementation自定义算法实现类详细报错信息

### v2.0.0
- 修改gradle配置（必须配置implementation指定算法实现）。
- 修复大字符串编译失败的问题。
- 新增自定义加解密算法扩展。
- 新增生成mapping映射表文件。

### v1.4.1
- 修复使用Java 8时出现的ZipException编译错误

### v1.4.0
- 新增指定包名加密的配置项：fogPackages
- 移除指定包名不加密的配置项：exclude

### v1.3.0
- 修复gradle 3.0+编译报错的bug

### v1.2.2
- 修复windows下打包后报错的bug

### v1.2.1
- 修复windows下文件分隔符的bug
- 修复applicationId和packageName不一致导致无法编译的bug
- 优化功能，不需要再手动exclude已使用StringFog的库

### v1.2.0
- 支持在library中使用，每个library可以使用不同key
- 支持exclude指定包名不进行加密
- 修复一些已知bug


--------

    Copyright (C) 2016-2023, Megatron King

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
