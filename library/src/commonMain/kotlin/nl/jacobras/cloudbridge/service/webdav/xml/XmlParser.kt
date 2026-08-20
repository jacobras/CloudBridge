package nl.jacobras.cloudbridge.service.webdav.xml

internal object XmlParser {

    @Throws(XmlParseException::class)
    fun parse(xml: String): XmlNode {
        val scanner = Scanner(xml)
        scanner.skipIntro()
        return scanner.parseElement()
    }

    @Suppress("MagicNumber")
    private class Scanner(private val xml: String) {
        var pos = 0

        @Suppress("ComplexCondition")
        fun skipIntro() {
            skipWhitespace()
            while (pos < xml.length && xml[pos] == '<' && pos + 1 < xml.length && xml[pos + 1] == '?') {
                pos = xml.indexOf("?>", pos).let { if (it == -1) xml.length else it + 2 }
                skipWhitespace()
            }
            while (xml.startsWith("<!--", pos)) {
                pos = xml.indexOf("-->", pos).let { if (it == -1) xml.length else it + 3 }
                skipWhitespace()
            }
        }

        fun parseElement(): XmlNode {
            skipWhitespace()
            expect('<')

            val name = readName()
            skipAttributes()
            skipWhitespace()

            // Self-closing tag, e.g. <Foo/>
            if (pos < xml.length && xml[pos] == '/') {
                pos++
                expect('>')
                return XmlNode(name, value = "", children = emptyList())
            }
            expect('>')

            val children = mutableListOf<XmlNode>()
            val textContent = StringBuilder()

            @Suppress("LoopWithTooManyJumpStatements")
            while (true) {
                if (xml.startsWith("<!--", pos)) {
                    pos = xml.indexOf("-->", pos).let { if (it == -1) xml.length else it + 3 }
                    continue
                }

                if (xml.startsWith("</", pos)) {
                    pos += 2
                    val closingName = readName()
                    if (closingName != name) {
                        throw XmlParseException("Mismatched closing tag: expected </$name> but found </$closingName>")
                    }
                    skipWhitespace()
                    expect('>')
                    break
                }

                if (pos < xml.length && xml[pos] == '<') {
                    children.add(parseElement())
                    continue
                }

                val nextTagStart = xml.indexOf('<', pos)
                val end = if (nextTagStart == -1) xml.length else nextTagStart
                textContent.append(xml, pos, end)
                pos = end
                if (nextTagStart == -1) break
            }

            return XmlNode(
                name = name,
                value = decodeEntities(textContent.toString().trim()),
                children = children.toList()
            )
        }

        private fun skipWhitespace() {
            while (pos < xml.length && xml[pos].isWhitespace()) {
                pos++
            }
        }

        private fun expect(char: Char) {
            if (pos < xml.length && xml[pos] == char) {
                pos++
            } else {
                throw XmlParseException("Failed to expect '$char' at position $pos")
            }
        }

        private fun readName(): String {
            val start = pos
            while (pos < xml.length && xml[pos] !in " \t\r\n>/") pos++
            return xml.substring(start, pos)
        }

        private fun skipAttributes() {
            while (true) {
                skipWhitespace()
                if (pos >= xml.length || xml[pos] == '>' || xml[pos] == '/') return

                while (pos < xml.length && xml[pos] !in "= \t\r\n>/") pos++
                skipWhitespace()
                if (pos < xml.length && xml[pos] == '=') {
                    pos++
                    skipWhitespace()
                    val quote = xml[pos]

                    if (quote != '"' && quote != '\'') {
                        throw XmlParseException("Expected quote at position $pos")
                    }

                    pos++
                    while (pos < xml.length && xml[pos] != quote) pos++
                    pos++ // consume closing quote
                }
            }
        }

        private fun decodeEntities(input: String): String {
            if ('&' !in input) return input
            return input
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
        }
    }
}