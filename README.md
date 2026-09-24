# 星云启动器 · Android（Nebula Launcher Android）

PCL/FCL 风格深色界面的 Minecraft Java 版手机启动器 —— **自有代码实现，非第三方克隆**。

## 功能
- 深色简洁 UI：底部四页签（启动 / 版本 / 模组 / 设置），PCL 风格卡片布局
- 版本管理：官方 + BMCLAPI 双源版本清单、原版版本下载安装（客户端 / 库 / 资源）
- 模组中心：已安装管理（启用/禁用/删除）、GitHub Release 模组仓库、Modrinth 模组搜索下载
- 整合包：Modrinth `.mrpack` 下载解析安装（模组 + overrides）
- 光影：Modrinth 光影下载安装（shaderpacks）
- 三种账号：离线账号 / 自定义服务器（authlib-injector，皮肤披风）/ 正版（微软设备码流程）
- 内存滑块分配、JVM 流畅参数（G1GC 调优）
- 游戏文件存储于应用内部存储 `.minecraft`

## 构建
```bash
export JAVA_HOME=<JDK 17>
./gradlew :app:assembleRelease
```
产物：`app/build/outputs/apk/release/app-release.apk`

## 兼容性
- minSdk 21（Android 5.0+），支持 32 位 / 64 位设备与模拟器
- 包名 `com.nebula.launcher`

> 说明：Android 端运行 Java 版游戏需配合「Java 运行栈」组件（开源运行时），下载与安装指引见[官网](https://etqwfd.github.io/NebulaLauncher/)。
