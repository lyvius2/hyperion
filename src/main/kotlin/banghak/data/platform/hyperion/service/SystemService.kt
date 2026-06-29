package banghak.data.platform.hyperion.service

import banghak.data.platform.hyperion.controller.dto.system.CreateSystemRequest
import banghak.data.platform.hyperion.controller.dto.system.SystemDetailResponse
import banghak.data.platform.hyperion.controller.dto.system.SystemFileInfo
import banghak.data.platform.hyperion.controller.dto.system.SystemListItemResponse
import banghak.data.platform.hyperion.controller.dto.system.UpdateSystemRequest
import banghak.data.platform.hyperion.dto.EncryptedSystemInfo
import banghak.data.platform.hyperion.infra.crypto.TokenEncryptor
import banghak.data.platform.hyperion.infra.storage.StorageProperties
import banghak.data.platform.hyperion.repository.entity.System
import banghak.data.platform.hyperion.repository.query.SystemQueryRow
import banghak.data.platform.hyperion.service.exception.SystemNameDuplicateException
import banghak.data.platform.hyperion.service.exception.SystemNameNotFoundException
import banghak.data.platform.hyperion.service.exception.SystemNotFoundException
import banghak.data.platform.hyperion.service.port.SystemPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Service
class SystemService(
    private val systemPort: SystemPort,
    private val tokenEncryptor: TokenEncryptor,
    private val storageProperties: StorageProperties
) {
    fun create(request: CreateSystemRequest, createdBy: Long): SystemQueryRow {
        if (systemPort.existsByName(request.name)) {
            throw SystemNameDuplicateException(request.name)
        }
        val hash = generateHash(request.name)
        val rootPath = "${storageProperties.systemsRoot.trimEnd('/')}/${request.name}_$hash"
        val chromaCollection = "sys_${request.name}_$hash"
        val encrypted = generateEncValue(request.dbUsername, request.dbPassword, request.gitAccessToken)
        val entity = System.of(request, createdBy, rootPath, chromaCollection, encrypted)
        return systemPort.save(entity)
    }

    fun findById(systemId: Long): SystemQueryRow {
        return systemPort.findById(systemId) ?: throw SystemNotFoundException(systemId)
    }

    @Transactional(readOnly = true)
    fun findByName(name: String): SystemQueryRow {
        return systemPort.findByName(name) ?: throw SystemNameNotFoundException(name)
    }

    @Transactional(readOnly = true)
    fun findAll(): List<SystemQueryRow> {
        return systemPort.findAll()
    }

    @Transactional
    fun update(systemId: Long, request: UpdateSystemRequest): SystemQueryRow {
        val encrypted = generateEncValue(request.dbUsername, request.dbPassword, request.gitAccessToken)
        return systemPort.update(systemId, request, encrypted)
    }

    @Transactional
    fun softDelete(systemId: Long) {
        systemPort.softDelete(systemId)
    }

    @Transactional(readOnly = true)
    fun findAllSummaries(): List<SystemListItemResponse> =
        systemPort.findAll().map { row ->
            SystemListItemResponse.of(row, maskUsername(decryptUsername(row.dbUsernameEnc)))
        }

    @Transactional(readOnly = true)
    fun findDetailById(systemId: Long): SystemDetailResponse {
        val row = systemPort.findById(systemId)
            ?: throw SystemNotFoundException(systemId)
        val files = systemPort.findFilesBySystemId(systemId).map { SystemFileInfo.of(it) }
        return SystemDetailResponse.of(
            row = row,
            dbUsernameMasked = maskUsername(decryptUsername(row.dbUsernameEnc)),
            files = files
        )
    }

    private fun generateHash(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$name:${java.lang.System.nanoTime()}".toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    private fun generateEncValue(dbUsername: String?, dbPassword: String?, gitAccessToken: String?): EncryptedSystemInfo {
        val dbUsernameEnc = dbUsername?.let(tokenEncryptor::encrypt)
        val dbPasswordEnc = dbPassword?.let(tokenEncryptor::encrypt)
        val gitAccessTokenEnc = gitAccessToken?.let(tokenEncryptor::encrypt)
        return EncryptedSystemInfo(dbUsernameEnc, dbPasswordEnc, gitAccessTokenEnc)
    }

    private fun decryptUsername(enc: String): String =
        if (enc.isBlank()) "" else tokenEncryptor.decrypt(enc)

    private fun maskUsername(username: String): String = when {
        username.isEmpty() -> ""
        username.length <= 2 -> "***"
        username.length <= 4 -> "${username.first()}***${username.last()}"
        else -> "${username.take(2)}***${username.last()}"
    }
}