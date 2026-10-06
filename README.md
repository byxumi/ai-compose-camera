# AI 构图相机 · 本地免费版

> 基于「浅影AI相机 2.2.2-china」逆向情报自主研发的 AI 构图相机。
> **全部功能免费、纯本地处理、无云端限制、无付费墙。**

## 功能

- 📷 **相机拍摄**（CameraX 实时预览 + 高画质拍照）
- 🧠 **AI 构图分析**（本地经典 CV 引擎）：
  - 三分法 / 对称 / 引导线 / 水平 / 留白 五项规则评分（0-100）
  - 显著性主体检测 + 实时构图引导线叠加（九宫格 + 主体框 + 三分点）
  - 实时构图建议
- 🏷️ **ML Kit 场景识别**（bundled 模型，离线推理）：
  - 实时低频标注（人物/风景/动物/美食/建筑/花卉/车辆…）
  - 按场景给出针对性的构图建议
- ✂️ **构图编辑**：旋转 / 镜像 / 滤镜（暖调、黑白）/ 背景虚化 / 构图复评 / 场景识别
- 🎖️ **会员**：全部功能永久免费解锁（无支付、无云端校验）

## 技术栈

Kotlin · CameraX · ML Kit ImageLabeling（bundled）· 自研 CV 构图引擎

## 构建（GitHub Actions）

```bash
# push 到 main 即触发 Build APK workflow
# 产物：app/build/outputs/apk/release/*.apk
```

本地构建需 Android SDK + JDK 17：
```bash
./gradlew assembleRelease
```

## 安装

下载 `app-release-unsigned.apk` 后用 `apksigner` 签名，或直接用仓库 Actions 产物 + 自签：
```bash
apksigner sign --ks your.keystore --out app-signed.apk app-release-unsigned.apk
adb install app-signed.apk
```

## 隐私

- 所有分析均在设备本地完成，**无任何网络上传**。
- 仅需相机权限；相册读取仅用于编辑页选图。

## 说明

本项目为个人学习研究产物，功能对标并致敬浅影AI相机，代码完全原创。
