package banghak.data.platform.hyperion.repository.entity

enum class FileType {
    /** Markdown document (.md) — chunked by MarkdownChunker. */
    MARKDOWN,

    /** SQL DDL file (.sql) — chunked by SqlDdlChunker. */
    SQL_DDL
}

