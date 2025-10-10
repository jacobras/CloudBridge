package nl.jacobras.cloudbridge.model

import nl.jacobras.cloudbridge.util.ensurePrefix
import kotlin.jvm.JvmInline

public interface Path

/**
 * A file, always starting with a slash.
 * Example value: "/Folder/file.txt" or just "/file.txt" for a file in the root folder.
 */
@JvmInline
public value class FilePath(internal val value: String) : Path {

    /**
     * Last part of the path.
     * For example: "/A.txt" returns "A.txt" and "/A/B.txt" returns "B.txt".
     */
    private val lastPart: String
        get() {
            return value.substringAfterLast('/')
        }

    /**
     * Name without extension.
     * For example: "/file.txt" and "/folder/file.txt" both return "file".
     */
    public val nameWithoutExtension: String
        get() {
            return lastPart.substringBeforeLast('.')
        }

    init {
        require(value.startsWith('/')) { "File path '$value' should start with a /" }
    }

    /**
     * Returns the folder this file is in.
     * Example value: for "/Folder/Nested/file.txt" this returns "/Folder/Nested".
     */
    public fun toFolderPath(): FolderPath {
        return value.substringBeforeLast('/').asFolderPath()
    }
}

public fun String.asFilePath(): FilePath {
    return FilePath(ensurePrefix("/"))
}

/**
 * A folder, always starting with a slash and never ending with one.
 * Example value: "/Folder" or just "/" for root.
 *
 * @property value The raw path, starting with a forward slash.
 */
@JvmInline
public value class FolderPath(internal val value: String) : Path {

    /**
     * Returns the name of the folder, which is the last part of the path.
     * For example: "/" returns "", "/A" returns "A" and "/A/B" returns "B".
     */
    public val name: String
        get() {
            return value.substringAfterLast('/')
        }

    /**
     * How many levels this folder contains.
     * For example: "/a/b" contains two levels, "/a" just one.
     */
    public val levelCount: Int
        get() {
            return if (value == "/") {
                0
            } else {
                value.removePrefix("/").removeSuffix("/").count { it == '/' } + 1
            }
        }

    /**
     * Path of parent folder.
     * For example: "/" returns "/", "/A" returns "/" and "/A/B" returns "/A".
     */
    public val parent: FolderPath
        get() {
            return if (isRoot) {
                this
            } else {
                FolderPath(value.substringBeforeLast('/').ifEmpty { "/" })
            }
        }

    /**
     * Whether this path is a root ("/").
     */
    public val isRoot: Boolean
        get() = value == "/"

    init {
        require(value.startsWith('/')) { "Folder path '$value' should start with a /" }
        require(value.length == 1 || !value.endsWith('/')) { "Folder path '$value' should not end with a /" }
    }
}

public fun String.asFolderPath(): FolderPath {
    return FolderPath(removeSuffix("/").ensurePrefix("/"))
}