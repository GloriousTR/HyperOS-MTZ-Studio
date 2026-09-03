package dev.glorioustr.mtzstudio.tester

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuCommandPolicyTest {
    @Test fun `APatch mount master flag is recognized`() {
        assertTrue(SuCommandPolicy.supportsGlobalMountNamespace("    -M, --mount-master  force run in the global mount namespace"))
    }

    @Test fun `unsupported root manager retains ordinary invocation`() {
        assertFalse(SuCommandPolicy.supportsGlobalMountNamespace("usage: su [-c command]"))
        assertEquals(listOf("su", "-c", "id -u"), SuCommandPolicy.arguments("id -u", false))
    }

    @Test fun `operation stays one argument and is dispatched only once`() {
        val command = "test -d /data/user/0/com.android.thememanager; echo 'ready'"
        assertEquals(listOf("su", "--mount-master", "-c", command), SuCommandPolicy.arguments(command, true))
    }
}
