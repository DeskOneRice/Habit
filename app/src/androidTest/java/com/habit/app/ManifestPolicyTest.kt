package com.habit.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class ManifestPolicyTest {
    @Test
    fun aiNetworkPolicyAllowsOnlyInternetAndExplicitCleartextTransport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(requestedPermissions.contains("android.permission.INTERNET"))
        assertEquals(
            setOf("android.permission.INTERNET"),
            requestedPermissions.filterTo(mutableSetOf()) { permission ->
                permission.startsWith("android.permission.") &&
                    NETWORK_OR_ACCOUNT_MARKERS.any(permission::contains)
            },
        )
        assertNotEquals(
            "Platform cleartext must be available only for profiles explicitly authorized by URL policy.",
            0,
            context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC,
        )
    }

    @Test
    fun localOnlyAppDisablesAndroidBackupAndDeviceTransfer() {
        val applicationInfo = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationInfo
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = context.resources

        assertEquals(
            "Local-only habit data must not enter Android backup or restore.",
            0,
            applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
        val legacyRulesId = resources.getIdentifier(
            "backup_rules",
            "xml",
            context.packageName,
        )
        assertNotEquals(
            "Legacy Android versions require an explicit full-backup exclusion resource.",
            0,
            legacyRulesId,
        )
        val extractionRulesId = resources.getIdentifier(
            "data_extraction_rules",
            "xml",
            context.packageName,
        )
        assertNotEquals(
            "Android 12+ requires explicit cloud-backup and device-transfer rules.",
            0,
            extractionRulesId,
        )

        assertEquals(
            "Legacy backup rules must exclude every app-data domain.",
            ALL_BACKUP_DOMAINS,
            excludedDomains(resources, legacyRulesId),
        )
        assertEquals(
            "Android 12+ cloud backup must exclude every app-data domain.",
            ALL_BACKUP_DOMAINS,
            excludedDomains(resources, extractionRulesId, "cloud-backup"),
        )
        assertEquals(
            "Android 12+ device transfer must exclude every app-data domain.",
            ALL_BACKUP_DOMAINS,
            excludedDomains(resources, extractionRulesId, "device-transfer"),
        )
        assertEquals(
            "Legacy backup rules must explicitly exclude the AI secret preferences file.",
            setOf(AI_SECRET_PREFERENCES_FILE),
            excludedPaths(resources, legacyRulesId, "sharedpref"),
        )
        assertEquals(
            "Android 12+ cloud backup must explicitly exclude the AI secret preferences file.",
            setOf(AI_SECRET_PREFERENCES_FILE),
            excludedPaths(resources, extractionRulesId, "sharedpref", "cloud-backup"),
        )
        assertEquals(
            "Android 12+ device transfer must explicitly exclude the AI secret preferences file.",
            setOf(AI_SECRET_PREFERENCES_FILE),
            excludedPaths(resources, extractionRulesId, "sharedpref", "device-transfer"),
        )
    }

    private fun excludedPaths(
        resources: Resources,
        xmlResourceId: Int,
        domain: String,
        section: String? = null,
    ): Set<String> {
        val parser = resources.getXml(xmlResourceId)
        return try {
            val paths = mutableSetOf<String>()
            var activeSection = section == null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when {
                        parser.name == section -> activeSection = true
                        parser.name == "exclude" && activeSection &&
                            parser.getAttributeValue(null, "domain") == domain -> {
                            parser.getAttributeValue(null, "path")
                                ?.takeUnless { it == "." }
                                ?.let(paths::add)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == section) activeSection = false
                    }
                }
                parser.next()
            }
            paths
        } finally {
            parser.close()
        }
    }

    private fun excludedDomains(
        resources: Resources,
        xmlResourceId: Int,
        section: String? = null,
    ): Set<String> {
        val parser = resources.getXml(xmlResourceId)
        return try {
            val domains = mutableSetOf<String>()
            var activeSection = section == null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when {
                        parser.name == section -> activeSection = true
                        parser.name == "exclude" && activeSection -> {
                            if (parser.getAttributeValue(null, "path") == ".") {
                                parser.getAttributeValue(null, "domain")?.let(domains::add)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == section) activeSection = false
                    }
                }
                parser.next()
            }
            domains
        } finally {
            parser.close()
        }
    }

    private companion object {
        val NETWORK_OR_ACCOUNT_MARKERS = listOf(
            "INTERNET",
            "NETWORK",
            "WIFI",
            "ACCOUNT",
            "CREDENTIAL",
            "SYNC",
        )
        const val AI_SECRET_PREFERENCES_FILE = "habit_ai_secrets.xml"
        val ALL_BACKUP_DOMAINS = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
    }
}
