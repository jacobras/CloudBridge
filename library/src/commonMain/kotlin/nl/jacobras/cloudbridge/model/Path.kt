package nl.jacobras.cloudbridge.model

import nl.jacobras.cloudbridge.util.ensurePrefix
import kotlin.jvm.JvmInline

public interface Path

/**
 * A file, always starting with a slash.
 * Example value: "/Directory/file.txt" or just "/file.txt" for a file in the root folder.
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
     * Returns the directory this file is in.
     * Example value: for "/Folder/Nested/file.txt" this returns "/Folder/Nested".
     */
    public fun toDirectoryPath(): DirectoryPath {
        return value.substringBeforeLast('/').asDirectoryPath()
    }
}

public fun String.asFilePath(): FilePath {
    return FilePath(ensurePrefix("/"))
}

/**
 * A directory, always starting with a slash and never ending with one.
 * Example value: "/Directory" or just "/" for root.
 *
 * @property value The raw path, starting with a forward slash.
 */
@JvmInline
public value class DirectoryPath(internal val value: String) : Path {

    /**
     * Returns the name of the directory, which is the last part of the path.
     * For example: "/" returns "", "/A" returns "A" and "/A/B" returns "B".
     */
    public val name: String
        get() {
            return value.substringAfterLast('/')
        }

    /**
     * How many levels this directory contains.
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
    public val parent: DirectoryPath
        get() {
            return if (isRoot) {
                this
            } else {
                DirectoryPath(value.substringBeforeLast('/').ifEmpty { "/" })
            }
        }

    /**
     * Whether this path is a root ("/").
     */
    public val isRoot: Boolean
        get() = value == "/"

    init {
        require(value.startsWith('/')) { "Directory path '$value' should start with a /" }
        require(value.length == 1 || !value.endsWith('/')) { "Directory path '$value' should not end with a /" }
    }
}

public fun String.asDirectoryPath(): DirectoryPath {
    return DirectoryPath(removeSuffix("/").ensurePrefix("/"))
}