package banghak.data.platform.hyperion.dto

data class EncryptedSystemInfo(
    val dbUsernameEnc: String,
    val dbPasswordEnc: String,
    val gitAccessTokenEnc: String
)
