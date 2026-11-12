package com.habitramp.core.database

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitramp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class OfflineStorageConfigurationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val domains = setOf("root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref")

    @Test
    fun packagedApplicationDoesNotAllowBackup() {
        assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test
    fun backupAndTransferRulesExcludeAllApplicationStorage() {
        assertExclusions(R.xml.backup_rules, setOf("full-backup-content"))
        assertExclusions(R.xml.data_extraction_rules, setOf("cloud-backup", "device-transfer"))
    }

    private fun assertExclusions(resource: Int, sections: Set<String>) {
        val exclusions = sections.associateWith { mutableSetOf<String>() }
        var section: String? = null
        context.resources.getXml(resource).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when {
                        parser.name in sections -> section = parser.name
                        parser.name == "include" -> fail("Backup includes application data")
                        parser.name == "exclude" && section != null -> {
                            assertEquals(".", parser.getAttributeValue(null, "path"))
                            exclusions.getValue(section!!).add(parser.getAttributeValue(null, "domain"))
                        }
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == section) {
                    section = null
                }
                parser.next()
            }
        }
        exclusions.forEach { (name, actual) -> assertEquals(name, domains, actual) }
    }
}
