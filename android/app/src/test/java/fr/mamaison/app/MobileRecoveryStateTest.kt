package fr.mamaison.app

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class MobileRecoveryStateTest {
    @Test fun certificateFailureNeverBecomesAnOrdinaryNetworkRetry() {
        assertEquals(MobileFailureKind.IDENTITY, mobileFailure(SSLHandshakeException("certificate")).kind)
        assertEquals(MobileConnectionPhase.IDENTITY, MobileConnectionState().failed(mobileFailure(SSLHandshakeException("certificate"))).phase)
    }

    @Test fun wrappedTransportFailureIsRecognized() {
        assertEquals(MobileFailureKind.NETWORK, mobileFailure(Exception("wrapper", SocketTimeoutException())).kind)
        assertEquals(MobileFailureKind.NETWORK, mobileFailure(IOException("Connection reset")).kind)
    }

    @Test fun rejectedSessionAndMissingPermissionAreDifferent() {
        assertEquals(MobileFailureKind.SESSION, mobileFailure(Exception("Appairez votre appareil pour accéder au serveur.")).kind)
        assertEquals(MobileFailureKind.SESSION, mobileFailure(Exception("Appareil non autorisé.")).kind)
        assertEquals(MobileFailureKind.PERMISSION, mobileFailure(Exception("Cette action est réservée au propriétaire.")).kind)
    }

    @Test fun aDownloadServiceProblemDoesNotClaimThatTheWholeServerIsOffline() {
        val failure = mobileFailure(Exception("Service de téléchargements indisponible"), "téléchargements")
        assertEquals(MobileFailureKind.SERVICE, failure.kind)
        assertTrue(failure.message.contains("autres fonctions"))
        val camera = mobileFailure(Exception("La webcam est débranchée ou inaccessible."), "caméra")
        assertEquals(MobileFailureKind.SERVICE, camera.kind)
        assertTrue(camera.message.contains("webcam"))
    }

    @Test fun lastSuccessfulCheckSurvivesFailureAndReconnection() {
        val good = MobileConnectionState().success(1000L)
        val failed = good.begin().failed(mobileFailure(SocketTimeoutException()))
        assertEquals(1000L, failed.lastSuccess)
        assertEquals(MobileConnectionPhase.UNREACHABLE, failed.phase)
        assertEquals(1000L, failed.begin().lastSuccess)
        assertEquals(MobileConnectionPhase.CONNECTED, failed.success(2000L).phase)
        assertEquals(2000L, failed.success(2000L).lastSuccess)
    }

    @Test fun memorySeparatesServersAndFolderPaths() {
        val memory = MobileReadMemory()
        memory.put("server-a", "files?path=one", "first")
        memory.put("server-a", "files?path=two", "second")
        assertEquals("first", memory.get("server-a", "files?path=one"))
        assertEquals("second", memory.get("server-a", "files?path=two"))
        assertNull(memory.get("server-b", "files?path=one"))
    }

    @Test fun memoryNeverStoresAuthenticationOrConfigurationResponses() {
        val memory = MobileReadMemory()
        for (route in listOf("auth/session", "pair", "backup", "settings", "media/catalog/settings", "wireguard/enroll")) {
            memory.put("server", route, "private")
            assertNull(memory.get("server", route))
        }
    }

    @Test fun boundedMemoryEvictsOldestAndCanBeCleared() {
        val memory = MobileReadMemory(40)
        memory.put("server", "rooms", "1234567890")
        memory.put("server", "scenes", "1234567890")
        memory.put("server", "cameras", "1234567890")
        assertNull(memory.get("server", "rooms"))
        assertEquals("1234567890", memory.get("server", "cameras"))
        memory.clear()
        assertNull(memory.get("server", "cameras"))
    }

    @Test fun oversizedEntriesDoNotReplaceThePreviousSuccessfulSnapshot() {
        val memory = MobileReadMemory(40)
        memory.put("server", "library", "old")
        memory.put("server", "library", "012345678901234567890")
        assertEquals("old", memory.get("server", "library"))
    }
}
