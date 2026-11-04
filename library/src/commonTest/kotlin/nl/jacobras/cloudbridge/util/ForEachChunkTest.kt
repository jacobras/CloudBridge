package nl.jacobras.cloudbridge.util

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlin.test.Test

class ForEachChunkTest {

    @Test
    fun `empty source gives single empty last chunk`() = runTest {
        val chunks = Buffer().collectChunks(chunkSize = 4)

        assertThat(chunks).containsExactly(Chunk(size = 0, offset = 0, isLast = true))
    }

    @Test
    fun `source smaller than chunk gives single last chunk`() = runTest {
        val chunks = bufferOf(size = 3).collectChunks(chunkSize = 4)

        assertThat(chunks).containsExactly(Chunk(size = 3, offset = 0, isLast = true))
    }

    @Test
    fun `source with exact multiple of chunk size`() = runTest {
        val chunks = bufferOf(size = 8).collectChunks(chunkSize = 4)

        assertThat(chunks).containsExactly(
            Chunk(size = 4, offset = 0, isLast = false),
            Chunk(size = 4, offset = 4, isLast = true)
        )
    }

    @Test
    fun `source with remainder`() = runTest {
        val chunks = bufferOf(size = 10).collectChunks(chunkSize = 4)

        assertThat(chunks).containsExactly(
            Chunk(size = 4, offset = 0, isLast = false),
            Chunk(size = 4, offset = 4, isLast = false),
            Chunk(size = 2, offset = 8, isLast = true)
        )
    }

    @Test
    fun `opened source is closed after reading`() = runTest {
        var closed = false
        val data = bufferOf(size = 10)
        val source = object : RawSource by data {
            override fun close() {
                closed = true
            }
        }.buffered()

        forEachChunk({ source }, chunkSizeBytes = 4) { _, _, _ -> }

        assertThat(closed).isTrue()
    }

    private suspend fun Buffer.collectChunks(chunkSize: Int): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        forEachChunk(chunkSize) { chunk, offset, isLast ->
            chunks += Chunk(chunk.size, offset, isLast)
        }
        return chunks
    }

    private data class Chunk(val size: Int, val offset: Long, val isLast: Boolean)
}