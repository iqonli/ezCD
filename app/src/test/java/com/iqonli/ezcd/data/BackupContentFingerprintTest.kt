package com.iqonli.ezcd.data
import org.junit.Assert.*
import org.junit.Test
class BackupContentFingerprintTest {
    @Test fun whitespaceAndObjectKeyOrderDoNotTriggerAnotherBackup() {
        assertEquals(BackupContentFingerprint.of("downloads","""{"events":[{"id":"1","title":"a"}],"pool":{"left":"还有","today":""}}"""),
            BackupContentFingerprint.of("downloads",""" {"pool": {"today":"", "left":"还有"}, "events":[{"title":"a", "id":"1"}]} """))
    }
    @Test fun changedContentOrderAndDestinationRequireANewBackup() {
        val original=BackupContentFingerprint.of("downloads","""{"items":[1,2],"name":"a"}""")
        assertNotEquals(original,BackupContentFingerprint.of("downloads","""{"items":[2,1],"name":"a"}"""))
        assertNotEquals(original,BackupContentFingerprint.of("downloads","""{"items":[1,2],"name":"b"}"""))
        assertNotEquals(original,BackupContentFingerprint.of("tree:another","""{"items":[1,2],"name":"a"}"""))
    }
}
