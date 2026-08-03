package com.habit.app.ui.emoji

import java.util.Locale

enum class EmojiCategory(val label: String) {
    RECENT("最近"),
    STUDY("学习"),
    SPORT("运动"),
    DAILY("生活"),
    FOOD("饮食"),
    HEALTH("健康"),
    HOBBY("兴趣"),
}

data class EmojiOption(
    val emoji: String,
    val label: String,
    val category: EmojiCategory,
    val keywords: Set<String> = emptySet(),
) {
    val key: String = "emoji:$emoji"
}

object EmojiCatalog {
    val options: List<EmojiOption> = listOf(
        option("📚", "读书", EmojiCategory.STUDY, "书本", "阅读"),
        option("📝", "笔记", EmojiCategory.STUDY, "写作", "记录"),
        option("🧪", "实验", EmojiCategory.STUDY, "实验室", "科研"),
        option("🔬", "显微镜", EmojiCategory.STUDY, "研究", "实验"),
        option("💻", "编程", EmojiCategory.STUDY, "电脑", "代码"),
        option("🎧", "听力", EmojiCategory.STUDY, "耳机", "英语"),
        option("🗣️", "口语", EmojiCategory.STUDY, "语言", "表达"),
        option("🧠", "思考", EmojiCategory.STUDY, "大脑", "复习"),
        option("📖", "背单词", EmojiCategory.STUDY, "单词", "英语"),
        option("🎓", "课程", EmojiCategory.STUDY, "学习", "毕业"),
        option("📐", "数学", EmojiCategory.STUDY, "计算", "作业"),
        option("⏱️", "专注", EmojiCategory.STUDY, "番茄钟", "计时"),

        option("🏃", "跑步", EmojiCategory.SPORT, "慢跑", "运动"),
        option("🏸", "羽毛球", EmojiCategory.SPORT, "球拍", "运动"),
        option("🏀", "篮球", EmojiCategory.SPORT, "球类"),
        option("⚽", "足球", EmojiCategory.SPORT, "球类"),
        option("🏊", "游泳", EmojiCategory.SPORT, "泳池"),
        option("🚴", "骑行", EmojiCategory.SPORT, "自行车"),
        option("🧘", "瑜伽", EmojiCategory.SPORT, "拉伸", "冥想"),
        option("🏋️", "力量训练", EmojiCategory.SPORT, "健身", "举重"),
        option("🥾", "徒步", EmojiCategory.SPORT, "爬山", "远足"),
        option("🛹", "滑板", EmojiCategory.SPORT, "运动"),
        option("🏓", "乒乓球", EmojiCategory.SPORT, "球类"),
        option("🤸", "体操", EmojiCategory.SPORT, "拉伸"),

        option("🌱", "早起", EmojiCategory.DAILY, "成长", "起床"),
        option("🛏️", "早睡", EmojiCategory.DAILY, "睡觉", "作息"),
        option("🧹", "打扫", EmojiCategory.DAILY, "清洁", "整理"),
        option("🧺", "洗衣", EmojiCategory.DAILY, "家务"),
        option("🚿", "洗澡", EmojiCategory.DAILY, "清洁"),
        option("🪥", "刷牙", EmojiCategory.DAILY, "口腔"),
        option("📷", "自拍", EmojiCategory.DAILY, "拍照", "相机"),
        option("🗓️", "计划", EmojiCategory.DAILY, "日历", "安排"),
        option("✅", "待办", EmojiCategory.DAILY, "完成", "任务"),
        option("🪴", "照顾植物", EmojiCategory.DAILY, "浇花", "绿植"),
        option("🐾", "遛宠物", EmojiCategory.DAILY, "小狗", "散步"),
        option("🧴", "护肤", EmojiCategory.DAILY, "保养"),

        option("🍚", "正餐", EmojiCategory.FOOD, "米饭", "吃饭"),
        option("🥗", "蔬菜", EmojiCategory.FOOD, "沙拉", "健康餐"),
        option("🍎", "水果", EmojiCategory.FOOD, "苹果"),
        option("🥛", "牛奶", EmojiCategory.FOOD, "乳制品"),
        option("☕", "咖啡", EmojiCategory.FOOD, "饮品", "美式"),
        option("🧋", "奶茶", EmojiCategory.FOOD, "饮品", "珍珠"),
        option("💧", "喝水", EmojiCategory.FOOD, "饮水", "补水"),
        option("🍵", "喝茶", EmojiCategory.FOOD, "茶水"),
        option("🥣", "早餐", EmojiCategory.FOOD, "早饭"),
        option("🍳", "做饭", EmojiCategory.FOOD, "烹饪"),
        option("🥜", "坚果", EmojiCategory.FOOD, "零食"),
        option("🚫", "戒零食", EmojiCategory.FOOD, "控制", "忌口"),

        option("💛", "心情", EmojiCategory.HEALTH, "情绪", "爱心"),
        option("😴", "睡眠", EmojiCategory.HEALTH, "休息", "困"),
        option("💊", "吃药", EmojiCategory.HEALTH, "药物", "提醒"),
        option("🩺", "体检", EmojiCategory.HEALTH, "医生", "健康"),
        option("🧘‍♀️", "冥想", EmojiCategory.HEALTH, "放松", "正念"),
        option("🦷", "牙齿", EmojiCategory.HEALTH, "口腔", "牙医"),
        option("👀", "护眼", EmojiCategory.HEALTH, "眼睛", "休息"),
        option("☀️", "晒太阳", EmojiCategory.HEALTH, "户外", "阳光"),
        option("⚖️", "体重", EmojiCategory.HEALTH, "称重", "管理"),
        option("🫁", "呼吸", EmojiCategory.HEALTH, "肺", "放松"),
        option("🧢", "防晒", EmojiCategory.HEALTH, "皮肤", "护肤"),
        option("🛌", "午休", EmojiCategory.HEALTH, "睡眠", "休息"),

        option("⭐", "收藏", EmojiCategory.HOBBY, "星星", "兴趣"),
        option("🎨", "画画", EmojiCategory.HOBBY, "绘画", "艺术"),
        option("🎹", "钢琴", EmojiCategory.HOBBY, "音乐", "乐器"),
        option("🎸", "吉他", EmojiCategory.HOBBY, "音乐", "乐器"),
        option("🎬", "电影", EmojiCategory.HOBBY, "观影", "视频"),
        option("🎮", "游戏", EmojiCategory.HOBBY, "娱乐"),
        option("🧶", "手工", EmojiCategory.HOBBY, "编织", "制作"),
        option("✍️", "写作", EmojiCategory.HOBBY, "日记", "创作"),
        option("🎤", "唱歌", EmojiCategory.HOBBY, "音乐", "K歌"),
        option("🧩", "拼图", EmojiCategory.HOBBY, "益智", "解谜"),
        option("🌿", "园艺", EmojiCategory.HOBBY, "植物", "种花"),
        option("✈️", "旅行", EmojiCategory.HOBBY, "出游", "飞机"),
    )

    fun search(
        query: String,
        category: EmojiCategory? = null,
    ): List<EmojiOption> {
        val normalized = query.trim().lowercase(Locale.CHINA)
        return options.filter { option ->
            val categoryMatches = category == null ||
                category == EmojiCategory.RECENT ||
                option.category == category
            val queryMatches = normalized.isEmpty() || listOf(
                option.label,
                option.category.label,
                *option.keywords.toTypedArray(),
            ).any { it.lowercase(Locale.CHINA).contains(normalized) }
            categoryMatches && queryMatches
        }
    }

    fun pickerOptions(
        query: String,
        category: EmojiCategory,
        recentKeys: List<String>,
    ): List<EmojiOption> = if (category == EmojiCategory.RECENT) {
        recentKeys.distinct().map { key ->
            options.firstOrNull { it.key == key }
                ?: EmojiOption(
                    emoji = key.removePrefix("emoji:"),
                    label = "最近使用",
                    category = EmojiCategory.RECENT,
                )
        }.filter { option ->
            query.isBlank() || option.label.contains(query.trim(), ignoreCase = true)
        }
    } else {
        search(query, category)
    }
}

fun pickerItemKey(category: EmojiCategory, key: String): String = "${category.name}:$key"

private fun option(
    emoji: String,
    label: String,
    category: EmojiCategory,
    vararg keywords: String,
) = EmojiOption(emoji, label, category, keywords.toSet())

fun normalizeEmojiKey(raw: String): String? {
    val emoji = raw.trim()
    return emoji.takeIf(::isSingleEmoji)?.let { "emoji:$it" }
}

fun isSingleEmoji(raw: String): Boolean {
    val value = raw.trim()
    if (value.isEmpty()) return false
    val codePoints = buildList {
        var index = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            add(codePoint)
            index += Character.charCount(codePoint)
        }
    }

    val first = codePoints.firstOrNull()
    val isKeycap = codePoints.lastOrNull() == 0x20E3 &&
        (first == 0x23 || first == 0x2A || first in 0x30..0x39)
    if (isKeycap) return codePoints.drop(1).all { it == 0xFE0F || it == 0x20E3 }

    if (codePoints.all(::isRegionalIndicator)) return codePoints.size == 2
    if (codePoints.any { !isEmojiBase(it) && !isEmojiJoiner(it) }) return false

    val bases = codePoints.count(::isEmojiBase)
    val hasJoiner = 0x200D in codePoints
    return when {
        bases == 1 && !hasJoiner -> true
        bases > 1 && hasJoiner -> codePoints.first() != 0x200D && codePoints.last() != 0x200D
        else -> false
    }
}

private fun isEmojiJoiner(codePoint: Int): Boolean =
    codePoint == 0x200D || codePoint == 0xFE0F || codePoint in 0x1F3FB..0x1F3FF

private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

private fun isEmojiBase(codePoint: Int): Boolean = codePoint in 0x1F000..0x1FAFF ||
    codePoint in 0x2600..0x26FF ||
    codePoint in 0x2700..0x27BF ||
    isRegionalIndicator(codePoint) ||
    codePoint in setOf(0x00A9, 0x00AE, 0x203C, 0x2049, 0x3030, 0x303D, 0x3297, 0x3299)
