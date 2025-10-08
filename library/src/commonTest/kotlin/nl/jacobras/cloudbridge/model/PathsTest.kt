package nl.jacobras.cloudbridge.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class PathsTest {

    @Test
    fun files() {
        assertThat("file.txt".asFilePath().value).isEqualTo("/file.txt")
        assertThat("folder/file.txt".asFilePath().value).isEqualTo("/folder/file.txt")
        assertThat("folder/file.txt".asFilePath().toDirectoryPath().value).isEqualTo("/folder")
    }

    @Test
    fun fileNameWithoutExtension() {
        assertThat("file.txt".asFilePath().nameWithoutExtension).isEqualTo("file")
        assertThat("folder/file.txt".asFilePath().nameWithoutExtension).isEqualTo("file")
    }

    @Test
    fun directories() {
        assertThat("".asDirectoryPath().value).isEqualTo("/")
        assertThat("/".asDirectoryPath().value).isEqualTo("/")
        assertThat("/myDir/".asDirectoryPath().value).isEqualTo("/myDir")
        assertThat("/myDir".asDirectoryPath().value).isEqualTo("/myDir")
        assertThat("myDir/".asDirectoryPath().value).isEqualTo("/myDir")
    }

    @Test
    fun directoryLevelCount() {
        assertThat("".asDirectoryPath().levelCount).isEqualTo(0)
        assertThat("/".asDirectoryPath().levelCount).isEqualTo(0)
        assertThat("/myDir/".asDirectoryPath().levelCount).isEqualTo(1)
        assertThat("/myDir".asDirectoryPath().levelCount).isEqualTo(1)
        assertThat("/A/B".asDirectoryPath().levelCount).isEqualTo(2)
    }

    @Test
    fun directoryParent() {
        assertThat("".asDirectoryPath().parent.value).isEqualTo("/")
        assertThat("/".asDirectoryPath().parent.value).isEqualTo("/")
        assertThat("/myDir/".asDirectoryPath().parent.value).isEqualTo("/")
        assertThat("/myDir".asDirectoryPath().parent.value).isEqualTo("/")
        assertThat("/A/B".asDirectoryPath().parent.value).isEqualTo("/A")
    }

    @Test
    fun directoryName() {
        assertThat("".asDirectoryPath().name).isEqualTo("")
        assertThat("/".asDirectoryPath().name).isEqualTo("")
        assertThat("/myDir/".asDirectoryPath().name).isEqualTo("myDir")
        assertThat("/myDir".asDirectoryPath().name).isEqualTo("myDir")
        assertThat("/A/B".asDirectoryPath().name).isEqualTo("B")
    }

    @Test
    fun directoryIsRoot() {
        assertThat("".asDirectoryPath().isRoot).isTrue()
        assertThat("/".asDirectoryPath().isRoot).isTrue()
        assertThat("/myDir/".asDirectoryPath().isRoot).isFalse()
    }
}