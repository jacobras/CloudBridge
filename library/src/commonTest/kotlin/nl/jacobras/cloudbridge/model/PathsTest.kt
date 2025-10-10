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
        assertThat("folder/file.txt".asFilePath().toFolderPath().value).isEqualTo("/folder")
    }

    @Test
    fun fileNameWithoutExtension() {
        assertThat("file.txt".asFilePath().nameWithoutExtension).isEqualTo("file")
        assertThat("folder/file.txt".asFilePath().nameWithoutExtension).isEqualTo("file")
    }

    @Test
    fun folders() {
        assertThat("".asFolderPath().value).isEqualTo("/")
        assertThat("/".asFolderPath().value).isEqualTo("/")
        assertThat("/myDir/".asFolderPath().value).isEqualTo("/myDir")
        assertThat("/myDir".asFolderPath().value).isEqualTo("/myDir")
        assertThat("myDir/".asFolderPath().value).isEqualTo("/myDir")
    }

    @Test
    fun folderLevelCount() {
        assertThat("".asFolderPath().levelCount).isEqualTo(0)
        assertThat("/".asFolderPath().levelCount).isEqualTo(0)
        assertThat("/myDir/".asFolderPath().levelCount).isEqualTo(1)
        assertThat("/myDir".asFolderPath().levelCount).isEqualTo(1)
        assertThat("/A/B".asFolderPath().levelCount).isEqualTo(2)
    }

    @Test
    fun folderParent() {
        assertThat("".asFolderPath().parent.value).isEqualTo("/")
        assertThat("/".asFolderPath().parent.value).isEqualTo("/")
        assertThat("/myDir/".asFolderPath().parent.value).isEqualTo("/")
        assertThat("/myDir".asFolderPath().parent.value).isEqualTo("/")
        assertThat("/A/B".asFolderPath().parent.value).isEqualTo("/A")
    }

    @Test
    fun folderName() {
        assertThat("".asFolderPath().name).isEqualTo("")
        assertThat("/".asFolderPath().name).isEqualTo("")
        assertThat("/myDir/".asFolderPath().name).isEqualTo("myDir")
        assertThat("/myDir".asFolderPath().name).isEqualTo("myDir")
        assertThat("/A/B".asFolderPath().name).isEqualTo("B")
    }

    @Test
    fun folderIsRoot() {
        assertThat("".asFolderPath().isRoot).isTrue()
        assertThat("/".asFolderPath().isRoot).isTrue()
        assertThat("/myDir/".asFolderPath().isRoot).isFalse()
    }
}