package com.habit.app

import com.habit.app.data.backup.HABIT_BACKUP_SCHEMA_VERSION
import com.habit.app.data.local.MIGRATION_4_5
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationNamespaceIsStable() {
        assertEquals("com.habit.app", HabitApplication::class.java.packageName)
    }

    @Test
    fun releaseMetadataIsHabitZeroPointSixPointOne() {
        assertEquals("0.6.1", BuildConfig.VERSION_NAME)
        assertEquals(13, BuildConfig.VERSION_CODE)
    }

    @Test
    fun releaseStorageAndNetworkContractsAreCurrent() {
        assertEquals(5, HABIT_BACKUP_SCHEMA_VERSION)
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)

        val databaseSource = projectFile("app/src/main/java/com/habit/app/data/local/HabitDatabase.kt").readText()
        assertTrue(databaseSource.contains(Regex("@Database\\([\\s\\S]*?version\\s*=\\s*5[,]?")))

        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.INTERNET\""))
    }

    @Test
    fun releaseUsesStableSigningConfiguration() {
        val buildScript = projectFile("app/build.gradle.kts").readText()
        assertTrue(buildScript.contains("create(\"habitStable\")"))
        assertEquals(
            2,
            Regex("signingConfig\\s*=\\s*signingConfigs\\.getByName\\(\"habitStable\"\\)")
                .findAll(buildScript)
                .count(),
        )
        assertTrue(buildScript.contains("habit.signing.storeFile"))
        assertTrue(buildScript.contains("habit.signing.keyAlias"))
    }

    @Test
    fun releaseDocumentationCoversAiPrivacyAndUpgradeContracts() {
        val readme = projectFile("README.md").readText()
        assertTextIncludes(
            readme,
            "## 0.6.1",
            "AI 功能默认关闭",
            "模型档案与功能绑定",
            "仅发送生成周报所需的结构化习惯与饮食数据",
            "只有用户明确选择照片并点击估算",
            "API Key 不会写入备份",
            "详情页",
        )

        val install = projectFile("docs/INSTALL.md").readText()
        assertTextIncludes(
            install,
            "Habit-0.6.1-release.apk",
            "从 0.5.1 覆盖升级",
            "不要先卸载",
            "API Key",
            "假端点",
            "恢复备份后需要重新输入 API Key",
        )

        val testing = projectFile("docs/TESTING.md").readText()
        assertTextIncludes(
            testing,
            "0.5.1 → 0.6.0",
            "schema 1–4",
            "假端点",
            "AI 关闭回归",
            "恢复备份后需要重新输入 API Key",
        )
    }

    private fun assertTextIncludes(text: String, vararg expectedFragments: String) {
        expectedFragments.forEach { fragment ->
            assertTrue("Missing documentation fragment: $fragment", text.contains(fragment))
        }
    }

    private fun projectFile(relativePath: String): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull(File::isFile)
            ?: error("Could not locate project file: $relativePath")
    }
}
