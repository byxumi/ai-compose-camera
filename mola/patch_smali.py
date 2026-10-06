#!/usr/bin/env python3
"""Mola 相机限制解除 smali 补丁"""
import sys

def patch_mainactivity(path):
    s = open(path).read()
    old = """    move-result-wide v7

    .line 128
    if-eqz v3, :cond_3"""
    new = """    move-result-wide v7

    .line 128
    # [MOD] 会员永久化
    sget-object v3, Lzv1;->n:Lzv1;
    const-wide v7, 0x7fffffffffffffffL
    if-eqz v3, :cond_3"""
    assert old in s, "MainActivity block missing"
    open(path, "w").write(s.replace(old, new, 1))

def patch_e4(path):
    s = open(path).read()
    s = s.replace(
        "    const/4 v1, 0x5\n\n    .line 4\n    invoke-direct {v0, v1}, Lgb2;-><init>(I)V",
        "    const v1, 0x7fffffff\n\n    .line 4\n    invoke-direct {v0, v1}, Lgb2;-><init>(I)V", 1)
    s = s.replace(
        "    const/4 v1, 0x5\n\n    .line 26\n    invoke-virtual {v0, v1}, Lgb2;->i(I)V",
        "    const v1, 0x7fffffff\n\n    .line 26\n    invoke-virtual {v0, v1}, Lgb2;->i(I)V", 1)
    open(path, "w").write(s)

if __name__ == "__main__":
    patch_mainactivity(sys.argv[1])
    patch_e4(sys.argv[2])
    print("patched")
