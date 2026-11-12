package com.chagokchagok.app.region

/**
 * Minimal pull-style JSON reader over an in-memory string.
 * Just enough for the bundled GeoJSON; avoids a JSON dependency and intermediate object trees.
 */
internal class JsonCursor(private val text: String) {
  private var pos = 0

  /** Calls [onKey] for every key; the callback must consume exactly one value. */
  fun readObject(onKey: (String) -> Unit) {
    expect('{')
    if (peek() == '}') {
      pos++
      return
    }
    while (true) {
      val key = readString()
      expect(':')
      onKey(key)
      if (!nextItem('}')) return
    }
  }

  /** Calls [onItem] for every element; the callback must consume exactly one value. */
  fun readArray(onItem: () -> Unit) {
    expect('[')
    if (peek() == ']') {
      pos++
      return
    }
    while (true) {
      onItem()
      if (!nextItem(']')) return
    }
  }

  fun readString(): String {
    expect('"')
    val start = pos
    // Fast path: no escapes.
    while (true) {
      val c = charAt(pos)
      if (c == '"') return text.substring(start, pos++)
      if (c == '\\') break
      pos++
    }
    val out = StringBuilder(text.substring(start, pos))
    while (true) {
      val c = charAt(pos++)
      when (c) {
        '"' -> return out.toString()
        '\\' -> when (val e = charAt(pos++)) {
          'n' -> out.append('\n')
          't' -> out.append('\t')
          'r' -> out.append('\r')
          'b' -> out.append('\b')
          'f' -> out.append(12.toChar())
          'u' -> {
            if (pos + 4 > text.length) fail("truncated unicode escape")
            out.append(text.substring(pos, pos + 4).toInt(16).toChar())
            pos += 4
          }
          else -> out.append(e) // covers quote, backslash and slash
        }
        else -> out.append(c)
      }
    }
  }

  fun readDouble(): Double {
    skipWhitespace()
    val start = pos
    while (pos < text.length && text[pos] in NUMBER_CHARS) pos++
    if (start == pos) fail("expected a number")
    return text.substring(start, pos).toDouble()
  }

  fun skipValue() {
    when (peek()) {
      '{' -> readObject { skipValue() }
      '[' -> readArray { skipValue() }
      '"' -> readString()
      else -> {
        // number, true, false, null
        val start = pos
        while (pos < text.length && text[pos] !in VALUE_END_CHARS) pos++
        if (start == pos) fail("expected a value")
      }
    }
  }

  /** Number of consecutive '[' at the cursor (ignoring whitespace), without consuming anything. */
  fun arrayDepth(): Int {
    skipWhitespace()
    var i = pos
    var depth = 0
    while (i < text.length) {
      val c = text[i]
      if (c == '[') depth++ else if (!c.isWhitespace()) break
      i++
    }
    return depth
  }

  private fun peek(): Char {
    skipWhitespace()
    return charAt(pos)
  }

  /** After a value: consumes ',' and returns true, or consumes [close] and returns false. */
  private fun nextItem(close: Char): Boolean {
    val c = peek()
    pos++
    return when (c) {
      ',' -> true
      close -> false
      else -> fail("expected ',' or '$close' but found '$c'")
    }
  }

  private fun expect(c: Char) {
    if (peek() != c) fail("expected '$c' but found '${text[pos]}'")
    pos++
  }

  private fun skipWhitespace() {
    while (pos < text.length && text[pos].isWhitespace()) pos++
  }

  private fun charAt(i: Int): Char = if (i < text.length) text[i] else fail("unexpected end of input")

  private fun fail(message: String): Nothing = throw IllegalArgumentException("JSON error at $pos: $message")

  private companion object {
    const val NUMBER_CHARS = "+-0123456789.eE"
    const val VALUE_END_CHARS = ",]} \t\r\n"
  }
}
