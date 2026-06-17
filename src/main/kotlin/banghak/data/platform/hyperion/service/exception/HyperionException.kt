package banghak.data.platform.hyperion.service.exception

import banghak.data.platform.hyperion.repository.entity.FileType

/** Base exception for all Hyperion domain errors. */
open class HyperionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class SystemNotFoundException(id: Long) :
    HyperionException("Target system not found. (id=$id)")

class SystemNameDuplicateException(name: String) :
    HyperionException("Target system name already exists: '$name'.")

class SystemFileLimitExceededException(
    systemName: String,
    fileType: FileType,
    max: Int
) : HyperionException(
    "File limit reached for system '$systemName': $fileType already has $max files (max=$max)."
)

class SystemFileNotFoundException(id: Long) :
    HyperionException("System file not found. (id=$id)")