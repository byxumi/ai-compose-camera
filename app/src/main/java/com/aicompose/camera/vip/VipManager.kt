package com.aicompose.camera.vip

/**
 * 会员管理器 —— 本地免费版：全部功能永久解锁，无付费墙、无云端限制。
 */
object VipManager {
    val isVip: Boolean get() = true
    val vipLevel: String get() = "终身会员"
    val vipExpire: String get() = "永久"

    /** 所有需解锁的能力 */
    val unlockedFeatures = listOf(
        "AI 智能构图分析",
        "实时构图引导线",
        "三分法 / 对称 / 引导线评分",
        "构图裁剪建议",
        "人像背景虚化",
        "专业滤镜",
        "高分辨率导出",
        "无广告体验"
    )

    fun featureUnlocked(_feature: String): Boolean = true
}
