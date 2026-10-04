package org.dergigi.ants

import org.antlr.v4.runtime.*
import org.dergigi.ants.query.generated.AntsQueryLexer
import org.dergigi.ants.query.generated.AntsQueryParser

internal data class QueryNode(
    val type: String, val value: String = "", val field: String = "",
    val quoted: Boolean = false, val position: Int = 0, val children: List<QueryNode> = emptyList(),
) {
    fun fail(message: String): Nothing = throw IllegalArgumentException("$message (character ${position + 1}).")
}

/** ANTLRInputStream deliberately uses UTF-16 indices, like the shared web contract. */
@Suppress("DEPRECATION")
internal fun parseQueryTree(input: String): QueryNode {
    require(input.isNotBlank()) { "Enter a search first." }
    require(input.length <= 2000) { "Please keep searches under 2,000 characters." }
    val errors = object : BaseErrorListener() {
        override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
            charPositionInLine: Int, msg: String?, e: RecognitionException?) {
            val offset = (offendingSymbol as? Token)?.startIndex ?: (recognizer as? Lexer)?.charIndex ?: 0
            throw IllegalArgumentException("Invalid search syntax at character ${offset + 1}: $msg")
        }
    }
    val lexer = AntsQueryLexer(ANTLRInputStream(input)).apply { removeErrorListeners(); addErrorListener(errors) }
    val tokens = CommonTokenStream(lexer).apply { fill() }
    var depth = 0
    tokens.tokens.forEach {
        if (it.type == AntsQueryLexer.LPAREN) { depth++; require(depth <= 16) { "Use at most 16 nested groups." } }
        if (it.type == AntsQueryLexer.RPAREN) depth--
    }
    val parser = AntsQueryParser(tokens).apply { removeErrorListeners(); addErrorListener(errors) }
    var nodes = 0
    fun node(type: String, value: String = "", field: String = "", quoted: Boolean = false,
        position: Int, children: List<QueryNode> = emptyList()): QueryNode {
        require(++nodes <= 256) { "Use at most 256 query nodes." }
        return QueryNode(type, value, field, quoted, position, children)
    }
    fun decoded(value: String) = value.substring(1, value.length - 1).replace(Regex("\\\\([\\\\\"])"), "$1")
    lateinit var expression: (AntsQueryParser.ExpressionContext, String?) -> QueryNode
    fun primary(p: AntsQueryParser.PrimaryContext, scope: String?): QueryNode {
        val pos = p.start.startIndex
        if (p is AntsQueryParser.GroupContext || p is AntsQueryParser.ScopedFieldContext) {
            require(++nodes <= 256) { "Use at most 256 query nodes." }
        }
        return when (p) {
            is AntsQueryParser.ScopedFieldContext -> {
                require(scope == null) { "Fields cannot be nested inside a scoped field (character ${pos + 1})." }
                expression(p.expression(), p.WORD().text.lowercase())
            }
            is AntsQueryParser.FieldContext -> {
                require(scope == null) { "Fields cannot be nested inside a scoped field (character ${pos + 1})." }
                val raw = p.value().text
                node("leaf", if (raw.startsWith('"')) decoded(raw) else raw, p.WORD().text.lowercase(), raw.startsWith('"'), pos)
            }
            is AntsQueryParser.GroupContext -> expression(p.expression(), scope)
            is AntsQueryParser.PhraseContext -> node("leaf", decoded(p.STRING().text), scope.orEmpty(), true, pos)
            is AntsQueryParser.TermContext -> node("leaf", p.WORD().text, scope.orEmpty(), position = pos)
            else -> error("Unknown query node")
        }
    }
    expression = { e, scope ->
        val alternatives = e.conjunction().map { c ->
            val children = c.primary().map { primary(it, scope) }
            if (children.size == 1) children.single() else node("and", position = c.start.startIndex, children = children)
        }
        if (alternatives.size == 1) alternatives.single() else node("or", position = e.start.startIndex, children = alternatives)
    }
    return expression(parser.query().expression(), null)
}

private val queryAliases: Map<String, String> by lazy {
    checkNotNull(QueryNode::class.java.getResourceAsStream("/query-replacements.txt")).bufferedReader().useLines { lines ->
        lines.filter { !it.startsWith('#') && "=>" in it }.map { it.substringBefore("=>").trim().lowercase() to it.substringAfter("=>").trim() }
            .filter { it.first.substringAfter(':').isNotBlank() && it.second.isNotBlank() }.toMap()
    }
}

internal fun queryLeaves(input: String): List<List<QueryNode>> {
    fun expand(n: QueryNode, depth: Int = 0): QueryNode {
        if (depth > 8) n.fail("Recursive alias")
        if (n.type != "leaf") {
            val children = n.children.map { expand(it, depth) }
            val first = children.first()
            if (n.type == "or" && first.field in listOf("kind", "by", "from", "mentions") &&
                children.all { it.type == "leaf" && it.field == first.field && !it.quoted }) {
                return first.copy(value = children.joinToString(",") { it.value })
            }
            return n.copy(children = children)
        }
        if (n.field.isEmpty()) return n
        if (n.field in listOf("http", "https", "ftp")) return n.copy(field = "", value = n.value.removePrefix("//").removePrefix("www."))
        if (n.field == "nostr") return n.copy(field = "")
        queryAliases["${n.field}:${n.value.lowercase()}"]?.let { alias ->
            fun remap(tree: QueryNode): QueryNode = tree.copy(position = n.position, children = tree.children.map(::remap))
            return expand(remap(parseQueryTree(alias)), depth + 1)
        }
        // Keep Android's existing /kinds shortcuts alongside the shared aliases.
        if (n.field == "is") kindAliases[n.value.lowercase()]?.let { kinds ->
            return n.copy(field = "kind", value = kinds.joinToString(","), quoted = false)
        }
        if (n.field == "site") return n.copy(field = "", quoted = false)
        if (n.field !in listOf("by", "from", "mentions", "kind", "since", "until", "p", "a", "license", "domain", "language", "sentiment", "nsfw", "include")) n.fail("Unknown modifier '${n.field}:'")
        return n
    }
    val tree = expand(parseQueryTree(input))
    fun cost(n: QueryNode): Int {
        if (n.type == "leaf") return 1
        var count = if (n.type == "or") 0 else 1
        for (child in n.children) {
            count = if (n.type == "or") count + cost(child) else count * cost(child)
            if (count > 32) n.fail("Search expands to more than 32 branches; narrow the groups")
        }
        return count
    }
    cost(tree)
    fun branches(n: QueryNode): List<List<QueryNode>> = when (n.type) {
        "or" -> n.children.flatMap(::branches)
        "and" -> n.children.fold(listOf(emptyList())) { acc: List<List<QueryNode>>, c -> acc.flatMap { a -> branches(c).map { b -> a + b } } }
        else -> listOf(listOf(n))
    }
    return branches(tree).distinctBy { branch -> branch.map { listOf(it.field, it.value, it.quoted) } }
}
