package banghak.data.platform.hyperion.repository.entity

enum class MemberRole {
    /** Full system access. */
    ADMIN,

    /** Can submit queries and view the board. */
    USER,

    /** Read-only board access. */
    VIEWER
}

