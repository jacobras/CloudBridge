package nl.jacobras.cloudbridge.service.webdav.xml

internal data class XmlNode(
    val name: String,
    val value: String,
    val children: List<XmlNode>
) {

    /**
     * Returns the first child named [name], or null when absent.
     */
    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name.lowercase() }

    /**
     * Returns all children named [name], in document order (which can be random from back-end!).
     */
    fun children(name: String): List<XmlNode> = children.filter { it.name == name.lowercase() }
}