package banghak.data.platform.hyperion.service

import banghak.data.platform.hyperion.controller.dto.admin.CreateSystemRequest
import banghak.data.platform.hyperion.controller.dto.admin.UpdateSystemRequest
import banghak.data.platform.hyperion.dto.EncryptedSystemInfo
import banghak.data.platform.hyperion.infra.crypto.TokenEncryptor
import banghak.data.platform.hyperion.infra.storage.StorageProperties
import banghak.data.platform.hyperion.repository.SystemFileRepository
import banghak.data.platform.hyperion.repository.TargetSystemRepository
import banghak.data.platform.hyperion.repository.entity.Member
import banghak.data.platform.hyperion.repository.entity.TargetSystem
import banghak.data.platform.hyperion.service.exception.SystemNameDuplicateException
import banghak.data.platform.hyperion.service.exception.SystemNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/**
 * CRUD service for [TargetSystem]. Responsible for:
 * - Uniqueness checks on `name` and the derived `chromaCollection`.
 * - Encrypting DB credentials and Git tokens before persistence.
 * - Generating the per-system `rootPath` and `chromaCollection` identifiers.
 *
 * File-level concerns (upload, ingestion) are handled by separate services and only
 * touched here for cascade delete of [banghak.data.platform.hyperion.repository.entity.SystemFile].
 */
@Service
class TargetSystemService(
    private val systemRepository: TargetSystemRepository,
    private val systemFileRepository: SystemFileRepository,
    private val tokenEncryptor: TokenEncryptor,
    private val storageProperties: StorageProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(request: CreateSystemRequest, createdBy: Member): TargetSystem {
        if (systemRepository.existsByName(request.name)) {
            throw SystemNameDuplicateException(request.name)
        }
        val hash = generateHash(request.name)
        val rootPath = "${storageProperties.systemsRoot.trimEnd('/')}/${request.name}_$hash"
        val chromaCollection = "sys_${request.name}_$hash"
        val encrypted = generateEncValue(request.dbUsername, request.dbPassword, request.gitAccessToken)
        val entity = TargetSystem.of(request, createdBy, rootPath, chromaCollection, encrypted)
        return systemRepository.save(entity)
    }

    fun findById(id: Long): TargetSystem =
        systemRepository.findById(id).orElseThrow { SystemNotFoundException(id) }

    @Transactional(readOnly = true)
    fun findByName(name: String): TargetSystem? = systemRepository.findByName(name)

    @Transactional(readOnly = true)
    fun findAll(): List<TargetSystem> = systemRepository.findAll()

    @Transactional
    fun update(id: Long, request: UpdateSystemRequest): TargetSystem {
        val current = findById(id)
        val encrypted = generateEncValue(request.dbUsername, request.dbPassword, request.gitAccessToken)
        val updated = current.copy(request = request, encrypted = encrypted)
        return systemRepository.save(updated)
    }

    @Transactional
    fun delete(id: Long) {
        val target = findById(id)
        val deletedFiles = systemFileRepository.deleteAllBySystemId(id)
        systemRepository.delete(target)
        log.info(
            "Target system deleted: id={}, name={}, cascadedFiles={}",
            target.id, target.name, deletedFiles
        )
    }

    private fun generateHash(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$name:${System.nanoTime()}".toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    private fun generateEncValue(dbUsername: String?, dbPassword: String?, gitAccessToken: String?): EncryptedSystemInfo {
        val dbUsernameEnc = dbUsername?.let(tokenEncryptor::encrypt) ?: ""
        val dbPasswordEnc = dbPassword?.let(tokenEncryptor::encrypt) ?: ""
        val gitAccessTokenEnc = gitAccessToken?.let(tokenEncryptor::encrypt) ?: ""
        return EncryptedSystemInfo(dbUsernameEnc, dbPasswordEnc, gitAccessTokenEnc)
    }
}