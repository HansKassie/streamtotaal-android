package nl.streamfix.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkChecksumTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val abcHash =
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun ontbrekendeEnOngeldigeHashWordtGeweigerd() {
        val file = temporaryFolder.newFile().apply { writeText("abc") }
        for (hash in listOf(null, "", "  ", "a".repeat(63), "a".repeat(65), "g".repeat(64))) {
            assertFalse(ApkChecksum.isValid(hash))
            assertFalse(ApkChecksum.matches(file, hash))
        }
    }

    @Test
    fun bekendeSha256KomtOvereen() {
        val file = temporaryFolder.newFile().apply { writeText("abc") }
        assertTrue(ApkChecksum.matches(file, abcHash))
        assertTrue(ApkChecksum.matches(file, "  ${abcHash.uppercase()}\n"))
    }

    @Test
    fun gewijzigdBestandWordtGeweigerd() {
        val file = temporaryFolder.newFile().apply { writeText("abcd") }
        assertFalse(ApkChecksum.matches(file, abcHash))
    }

    @Test
    fun andereGeldigeHashWordtGeweigerd() {
        val file = temporaryFolder.newFile().apply { writeText("abc") }
        assertFalse(ApkChecksum.matches(file, "0".repeat(64)))
    }

    @Test
    fun ontbrekendBestandWordtGeweigerd() {
        assertFalse(ApkChecksum.matches(temporaryFolder.root.resolve("missing.apk"), abcHash))
    }

    @Test
    fun mapInPlaatsVanBestandWordtGeweigerd() {
        assertFalse(ApkChecksum.matches(temporaryFolder.root, abcHash))
    }
}
