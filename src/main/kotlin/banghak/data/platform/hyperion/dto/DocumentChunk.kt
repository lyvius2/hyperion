package banghak.data.platform.hyperion.dto

/**
 * A single text chunk produced by a DocumentChunker, ready for embedding.
 *
 * id       — unique chunk identifier (e.g. "{systemName}::{relPath}::{index}")
 * text     — the text that will be passed to the embedding model (with search_document: prefix)
 * metadata — arbitrary key-value pairs stored in ChromaDB alongside the vector
 * sourceHash — SHA-256 of the originating file; used for incremental change detection
 */
data class DocumentChunk(
    val id: String,
    val text: String,
    val metadata: Map<String, String>,
    val sourceHash: String
)

