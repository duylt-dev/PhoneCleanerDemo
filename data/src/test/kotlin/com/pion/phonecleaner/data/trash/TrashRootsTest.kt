package com.pion.phonecleaner.data.trash

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TrashRootsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `failed probe selects same volume shared root outside Movies with nomedia`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val root = f.roots.resolve(f.volume.path)!!
        assertEquals(File(f.volume, ".PhoneCleanerTrash"), root)
        assertFalse(root.path.startsWith(File(f.appDir, "Movies").path))
        assertTrue(File(root, ".nomedia").exists())
        val second = f.roots.resolve(File(f.secondVolume, "video.mp4").path)!!
        assertEquals(File(f.secondVolume, ".PhoneCleanerTrash"), second)
        assertTrue(File(second, ".nomedia").exists())
    }

    @Test fun `successful probe selects app root and cache survives roots instance`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        f.probeSucceeds = true
        assertEquals(File(f.appDir, "trash"), f.roots.resolve(f.volume.path))
        assertTrue(File(f.appDir, "trash/.nomedia").exists())
        f.newRoots().resolve(f.volume.path)
        assertEquals(1, f.probeCalls)
    }

    @Test fun `revoked grant disables cached app root and absent grant is never cached`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        f.granted = false
        assertNull(f.roots.resolve(f.volume.path))
        assertEquals(0, f.probeCalls)
        f.granted = true
        f.probeSucceeds = true
        assertNotNull(f.roots.resolve(f.volume.path))
        f.granted = false
        assertNull(f.roots.resolve(f.volume.path))
        assertEquals(1, f.probeCalls)
    }
}
