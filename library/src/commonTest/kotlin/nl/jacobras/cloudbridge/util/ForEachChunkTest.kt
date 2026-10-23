package nl.jacobras.cloudbridge.util

import assertk.assertThat
import assertk.assertions.containsExactly
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import nl.jacobras.cloudbridge.bufferOf
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

    private suspend fun Buffer.collectChunks(chunkSize: Int): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        forEachChunk(chunkSize) { chunk, offset, isLast ->
            chunks += Chunk(chunk.size, offset, isLast)
        }
        return chunks
    }

    private data class Chunk(val size: Int, val offset: Long, val isLast: Boolean)
}