package banghak.data.platform.hyperion.dto

/**
 * RAG retrieval context assembled before LLM prompt construction.
 *
 * schemaChunks — DDL / schema-related chunks retrieved from ChromaDB
 * codeChunks   — source-code method chunks retrieved from ChromaDB
 *
 * Both lists contain the raw chunk text after similarity search.
 * LlmOrchestrationService concatenates these into the prompt via PromptBuilder.
 */
data class RagContext(
    val schemaChunks: List<String>,
    val codeChunks: List<String>
) {
    /** Combined schema context text for prompt injection. */
    val schemaContext: String get() = schemaChunks.joinToString("\n\n---\n\n")

    /** Combined code context text for prompt injection. */
    val codeContext: String get() = codeChunks.joinToString("\n\n---\n\n")
}

