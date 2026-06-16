package banghak.data.platform.hyperion.repository.entity

enum class MemberStatus {
    /** Normal active account. */
    ACTIVE,

    /** Manually deactivated by admin. */
    INACTIVE,

    /** Locked after 5 consecutive login failures or manual admin action. */
    LOCKED,

    /** Account withdrawn (soft-deleted). */
    WITHDRAWN
}

