package nl.jacobras.cloudbridge.util

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import nl.jacobras.cloudbridge.CloudBridgeException
import kotlin.test.Test

class SafeSourceOpenerTest {

    @Test
    fun `opens a new source each time`() = runTest {
        val opener = SafeSourceOpener { bufferOf(3) }

        opener.open().readByteArray()
        assertThat(opener.open()).isNotNull()
    }

    @Test
    fun `empty content can be opened again`() = runTest {
        val opener = SafeSourceOpener { Buffer() }

        opener.open()
        assertThat(opener.open()).isNotNull()
    }

    @Test
    fun `same instance throws SourceReadException`() = runTest {
        val source = bufferOf(3)
        val opener = SafeSourceOpener { source }

        opener.open()
        assertFailure { opener.open() }.isInstanceOf<CloudBridgeException.SourceReadException>()
    }

    @Test
    fun `already read stream throws SourceReadException`() = runTest {
        val stream: RawSource = bufferOf(3)
        val opener = SafeSourceOpener { stream.buffered() }

        opener.open().readByteArray()
        assertFailure { opener.open() }.isInstanceOf<CloudBridgeException.SourceReadException>()
    }

    @Test
    fun `failing factory throws SourceReadException`() = runTest {
        val opener = SafeSourceOpener { throw IOException("Can't open") }

        assertFailure { opener.open() }.isInstanceOf<CloudBridgeException.SourceReadException>()
    }
}