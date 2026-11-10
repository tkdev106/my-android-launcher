package io.github.tkdev106.launcher.core

/**
 * 設定ファイルを読み書きするだけの最小の JSON。
 * org.json は Android の中にしか無く JVM のテストで動かないため、依存を足さずに自前で持つ。
 */
sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json
    data class Arr(val items: List<Json>) : Json
    data class Str(val value: String) : Json
    data class Num(val value: Double) : Json
    data class Bool(val value: Boolean) : Json
    data object Null : Json

    companion object {
        fun parse(text: String): Json {
            val reader = Reader(text)
            val value = reader.value()
            reader.skipSpace()
            if (!reader.atEnd()) throw JsonException("末尾に余計な文字がある（${reader.pos} 文字目）")
            return value
        }

        fun obj(vararg pairs: Pair<String, Json?>): Obj =
            Obj(pairs.filter { it.second != null }.associate { it.first to it.second!! })

        fun arr(items: List<Json>): Arr = Arr(items)
        fun str(value: String?): Json? = value?.let { Str(it) }
        fun num(value: Int?): Json? = value?.let { Num(it.toDouble()) }
    }
}

class JsonException(message: String) : RuntimeException(message)

fun Json.stringify(): String = StringBuilder().also { write(it, 0) }.toString()

private fun Json.write(out: StringBuilder, indent: Int) {
    when (this) {
        is Json.Obj -> {
            if (fields.isEmpty()) { out.append("{}"); return }
            out.append("{\n")
            fields.entries.forEachIndexed { i, (k, v) ->
                out.append("  ".repeat(indent + 1)).append(quote(k)).append(": ")
                v.write(out, indent + 1)
                if (i < fields.size - 1) out.append(',')
                out.append('\n')
            }
            out.append("  ".repeat(indent)).append('}')
        }
        is Json.Arr -> {
            if (items.isEmpty()) { out.append("[]"); return }
            // 数だけの配列（位置と大きさ）は1行に収めて読みやすくする
            if (items.all { it is Json.Num }) {
                items.joinTo(out, ", ", "[", "]") { (it as Json.Num).format() }
                return
            }
            out.append("[\n")
            items.forEachIndexed { i, v ->
                out.append("  ".repeat(indent + 1))
                v.write(out, indent + 1)
                if (i < items.size - 1) out.append(',')
                out.append('\n')
            }
            out.append("  ".repeat(indent)).append(']')
        }
        is Json.Str -> out.append(quote(value))
        is Json.Num -> out.append(format())
        is Json.Bool -> out.append(value)
        Json.Null -> out.append("null")
    }
}

private fun Json.Num.format(): String =
    if (value == Math.rint(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

private fun quote(s: String): String {
    val sb = StringBuilder("\"")
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
    }
    return sb.append('"').toString()
}

private class Reader(val s: String) {
    var pos = 0

    fun atEnd() = pos >= s.length

    fun skipSpace() {
        while (pos < s.length && s[pos].isWhitespace()) pos++
    }

    fun value(): Json {
        skipSpace()
        if (atEnd()) throw JsonException("値が無い")
        return when (val c = s[pos]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> Json.Str(string())
            't' -> literal("true", Json.Bool(true))
            'f' -> literal("false", Json.Bool(false))
            'n' -> literal("null", Json.Null)
            else -> if (c == '-' || c.isDigit()) number() else throw JsonException("読めない文字 '$c'（$pos 文字目）")
        }
    }

    private fun literal(word: String, value: Json): Json {
        if (!s.startsWith(word, pos)) throw JsonException("$word のつもりの値が壊れている（$pos 文字目）")
        pos += word.length
        return value
    }

    private fun obj(): Json.Obj {
        pos++
        val fields = LinkedHashMap<String, Json>()
        skipSpace()
        if (peek() == '}') { pos++; return Json.Obj(fields) }
        while (true) {
            skipSpace()
            if (peek() != '"') throw JsonException("キーが無い（$pos 文字目）")
            val key = string()
            skipSpace()
            expect(':')
            fields[key] = value()
            skipSpace()
            when (next()) {
                ',' -> continue
                '}' -> return Json.Obj(fields)
                else -> throw JsonException("オブジェクトが閉じていない（$pos 文字目）")
            }
        }
    }

    private fun arr(): Json.Arr {
        pos++
        val items = ArrayList<Json>()
        skipSpace()
        if (peek() == ']') { pos++; return Json.Arr(items) }
        while (true) {
            items += value()
            skipSpace()
            when (next()) {
                ',' -> continue
                ']' -> return Json.Arr(items)
                else -> throw JsonException("配列が閉じていない（$pos 文字目）")
            }
        }
    }

    private fun string(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            val c = next()
            when (c) {
                '"' -> return sb.toString()
                '\\' -> when (val e = next()) {
                    '"', '\\', '/' -> sb.append(e)
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (pos + 4 > s.length) throw JsonException("\\u の後が短い")
                        sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                        pos += 4
                    }
                    else -> throw JsonException("読めないエスケープ \\$e")
                }
                else -> sb.append(c)
            }
        }
    }

    private fun number(): Json.Num {
        val start = pos
        while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
        return Json.Num(s.substring(start, pos).toDoubleOrNull() ?: throw JsonException("数が壊れている（$start 文字目）"))
    }

    private fun peek(): Char = if (atEnd()) throw JsonException("途中で終わっている") else s[pos]
    private fun next(): Char = peek().also { pos++ }
    private fun expect(c: Char) {
        if (next() != c) throw JsonException("'$c' が無い（${pos - 1} 文字目）")
    }
}

// 読み出しの補助。形が違えば null を返し、呼び出し側でまとめて「壊れた設定」として扱う
operator fun Json?.get(key: String): Json? = (this as? Json.Obj)?.fields?.get(key)
val Json?.string: String? get() = (this as? Json.Str)?.value
val Json?.int: Int? get() = (this as? Json.Num)?.value?.toInt()
val Json?.list: List<Json> get() = (this as? Json.Arr)?.items ?: emptyList()
