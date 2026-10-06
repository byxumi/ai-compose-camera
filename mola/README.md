# Mola 相机改造（解除限制版）

基于用户提供的 Mola 相机 2.1 APK 二进制改造。

## 删减（限制解除）
| 限制 | 位置 | 修改 |
|---|---|---|
| AI 试用每天 5 次 | `e4.smali`（clinit + c()） | `const/4 v1, 0x5` → `const v1, 0x7fffffff`（无限） |
| 会员到期制 | `MainActivity.smali` 会员恢复段 | 注入 `sget-object v3, Lzv1;->n`（内置顶级会员）+ `const-wide v7, 0x7fffffffffffffff`（永不过期） |

## 方法
1. `apktool d mola.apk` 反编译（smali）
2. python 文本补丁（见 patch_smali.py）
3. `smali a` 汇编回 dex（org.smali:smali 2.5.2 + dexlib2 + guava + jcommander + antlr-runtime）
4. python zipfile 替换 classes.dex + 删旧签名
5. `apksigner sign` 签名（v2）

## 产物
`Mola相机-解除限制版.apk`（签名 v2，可安装）
