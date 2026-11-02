package com.zigrawr.jbplugin.builtin

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.zigrawr.jbplugin.IdeProcessState
import com.zigrawr.jbplugin.lexer.decodeZigStringLiteral
import com.zigrawr.jbplugin.toolchain.ZigToolchainInfo
import com.zigrawr.zig.foreign.model.TokenTag
import com.zigrawr.zig.foreign.model.Tokens
import java.io.IOException
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.concurrent.atomic.AtomicReferenceArray
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

private const val MAX_REFERENCE_BYTES = 8L * 1024 * 1024
private const val MAX_SECTION_CHARS = 128 * 1024

internal data class ZigBuiltinEntry(
    val name: String,
    val version: String,
    val html: String?,
    val documentationUrl: String,
)

internal class ZigBuiltinCatalog private constructor(
    private val version: String,
    private val names: Array<String>,
    private val reference: String?,
    private val sectionStarts: IntArray,
    private val sectionEnds: IntArray,
    private val referenceUrl: String?,
) {
    private val entries = AtomicReferenceArray<ZigBuiltinEntry>(names.size)

    fun find(name: String): ZigBuiltinEntry? {
        val index = Arrays.binarySearch(names, name)
        if (index < 0) return null
        entries.get(index)?.let { return it }
        val anchor = name.substring(1)
        val start = sectionStarts[index]
        val local = reference != null && referenceUrl != null && start >= 0
        val baseUrl = if (local) referenceUrl else "https://ziglang.org/documentation/$version/"
        val url = "$baseUrl#$anchor"
        val html = if (local) sanitize(reference, start, sectionEnds[index], baseUrl) else null
        entries.compareAndSet(index, null, ZigBuiltinEntry(name, version, html, url))
        return entries.get(index)
    }

    companion object {
        fun load(info: ZigToolchainInfo): ZigBuiltinCatalog {
            val names = readNames(Path.of(info.stdDirectory, "zig", "BuiltinFn.zig"))
            val reference = readReference(info)
            val text = reference?.second
            if (names.isEmpty() && text != null) {
                forEachSection(text) { name, _, _ -> names.add(name) }
            }
            names.sort()
            var count = 0
            for (index in names.indices) {
                if (count == 0 || names[index] != names[count - 1]) names[count++] = names[index]
            }
            val sorted = Array(count) { names[it] }
            val starts = IntArray(count) { -1 }
            val ends = IntArray(count) { -1 }
            if (text != null) {
                forEachSection(text) { name, start, end ->
                    val index = Arrays.binarySearch(sorted, name)
                    if (index >= 0) {
                        starts[index] = start
                        ends[index] = end
                    }
                }
            }
            return ZigBuiltinCatalog(info.version, sorted, text, starts, ends, reference?.first?.toUri()?.toASCIIString())
        }

        private fun readNames(path: Path): ArrayList<String> {
            val source = readFile(path) ?: return ArrayList()
            val tokens = Tokens(IdeProcessState.getInstance().bindings.tokens.parseChars(source))
            val names = ArrayList<String>()
            tokens.forEachToken { index, tag, span ->
                if (index and 255 == 0) ProgressManager.checkCanceled()
                if (tag == TokenTag.STRING_LITERAL && span.end - span.start > 3 && source[span.start + 1] == '@') {
                    val name = decodeZigStringLiteral(source, span.start, span.end)
                    if (name != null && isBuiltinName(name)) names.add(name)
                }
            }
            return names
        }

        private fun readReference(info: ZigToolchainInfo): Pair<Path, String>? {
            val executableReference = Path.of(info.executable).parent?.resolve("doc/langref.html")
            val libraryReference = Path.of(info.stdDirectory).parent?.parent?.resolve("doc/langref.html")
            for (path in arrayOf(executableReference, libraryReference)) {
                if (path == null) continue
                val text = readFile(path) ?: continue
                if (matchesVersion(text, info.version)) return path.toAbsolutePath().normalize() to text
            }
            return null
        }

        private fun readFile(path: Path): String? = try {
            if (Files.isRegularFile(path) && Files.size(path) <= MAX_REFERENCE_BYTES) Files.readString(path) else null
        } catch (_: IOException) {
            null
        }

        private fun matchesVersion(source: String, version: String): Boolean {
            val start = source.indexOf("id=\"zig-version\"")
            if (start < 0) return true
            val end = source.indexOf("</nav>", start)
            if (end < 0) return false
            val navigation = source.substring(start, end)
                .replace(Regex("<a\\b[^>]*>.*?</a>", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<[^>]+>"), "")
            val current = Regex("master|[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.+-]+)?")
                .findAll(navigation).lastOrNull()?.value
            return current == null || current == "master" || current == version
        }

        private inline fun forEachSection(source: String, action: (String, Int, Int) -> Unit) {
            var cursor = source.indexOf("id=\"Builtin-Functions\"")
            if (cursor < 0) return
            val nextChapter = source.indexOf("<h2", cursor)
            val chapterEnd = if (nextChapter < 0) source.length else nextChapter
            while (cursor < chapterEnd) {
                ProgressManager.checkCanceled()
                val header = source.indexOf("<h3", cursor)
                if (header < 0 || header >= chapterEnd) return
                val headerTagEnd = source.indexOf('>', header)
                val id = source.indexOf("id=\"", header)
                val headerEnd = source.indexOf("</h3>", header)
                if (headerTagEnd < 0 || headerEnd < 0) return
                cursor = headerEnd + 5
                if (id < 0 || id >= headerTagEnd) continue
                val nameStart = id + 4
                val nameEnd = source.indexOf('"', nameStart)
                if (nameEnd < 0 || nameEnd > headerTagEnd) continue
                val name = "@" + source.substring(nameStart, nameEnd)
                if (!isBuiltinName(name) || source.indexOf(name, headerTagEnd) !in headerTagEnd until headerEnd) continue
                val nextBuiltin = source.indexOf("<h3", cursor)
                val end = minOf(if (nextBuiltin < 0) source.length else nextBuiltin, chapterEnd)
                action(name, cursor, end)
            }
        }

        private fun isBuiltinName(name: String): Boolean = name.length > 1 && name[0] == '@' &&
            name[1].let { it in 'a'..'z' || it in 'A'..'Z' || it == '_' } &&
            (2 until name.length).all { name[it] in 'a'..'z' || name[it] in 'A'..'Z' || name[it] in '0'..'9' || name[it] == '_' }

        private fun sanitize(source: String, start: Int, end: Int, referenceUrl: String): String {
            val result = StringBuilder(minOf(end - start, MAX_SECTION_CHARS))
            var suppressed = 0
            val callback = object : HTMLEditorKit.ParserCallback() {
                override fun handleStartTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
                        suppressed++
                        return
                    }
                    if (suppressed != 0 || !allowed(tag)) return
                    result.append('<').append(tag.toString())
                    if (tag == HTML.Tag.A) {
                        val href = attributes.getAttribute(HTML.Attribute.HREF)?.toString()
                        val safe = when {
                            href == null -> null
                            href.startsWith('#') -> referenceUrl + href
                            href.startsWith("https://") || href.startsWith("http://") -> href
                            else -> null
                        }
                        if (safe != null) result.append(" href=\"").append(StringUtil.escapeXmlEntities(safe)).append('"')
                    }
                    result.append('>')
                }

                override fun handleEndTag(tag: HTML.Tag, position: Int) {
                    if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) {
                        if (suppressed > 0) suppressed--
                        return
                    }
                    if (suppressed == 0 && allowed(tag)) result.append("</").append(tag.toString()).append('>')
                }

                override fun handleSimpleTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
                    if (suppressed == 0 && (tag == HTML.Tag.BR || tag == HTML.Tag.HR)) {
                        result.append('<').append(tag.toString()).append('>')
                    }
                }

                override fun handleText(data: CharArray, position: Int) {
                    if (suppressed == 0) result.append(StringUtil.escapeXmlEntities(String(data)))
                }
            }
            ParserDelegator().parse(StringReader(source.substring(start, minOf(end, start + MAX_SECTION_CHARS))), callback, true)
            return result.toString()
        }

        private fun allowed(tag: HTML.Tag): Boolean = when (tag) {
            HTML.Tag.P, HTML.Tag.PRE, HTML.Tag.CODE, HTML.Tag.SPAN, HTML.Tag.UL, HTML.Tag.OL, HTML.Tag.LI,
            HTML.Tag.EM, HTML.Tag.STRONG, HTML.Tag.B, HTML.Tag.I, HTML.Tag.A, HTML.Tag.TABLE, HTML.Tag.TR,
            HTML.Tag.TD, HTML.Tag.TH, HTML.Tag.BLOCKQUOTE, HTML.Tag.DL, HTML.Tag.DT, HTML.Tag.DD,
            HTML.Tag.H4, HTML.Tag.H5, HTML.Tag.H6, HTML.Tag.SAMP, HTML.Tag.KBD, HTML.Tag.CITE -> true
            else -> false
        }
    }
}
