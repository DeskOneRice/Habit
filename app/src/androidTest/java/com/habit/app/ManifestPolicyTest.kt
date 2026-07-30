package com.habit.app

import android.content.pm.ApplicationInfo
import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class ManifestPolicyTest {
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
