package banghak.data.platform.hyperion.repository

import banghak.data.platform.hyperion.controller.dto.system.UpdateSystemRequest
import banghak.data.platform.hyperion.dto.EncryptedSystemInfo
import banghak.data.platform.hyperion.repository.entity.System
import banghak.data.platform.hyperion.repository.jpa.SystemJpaRepository
import banghak.data.platform.hyperion.repository.query.SystemFileQueryRow
import banghak.data.platform.hyperion.repository.query.SystemQueryAdapter
import banghak.data.platform.hyperion.repository.query.SystemQueryRow
import banghak.data.platform.hyperion.service.exception.SystemNotFoundException
import banghak.data.platform.hyperion.service.port.SystemPort
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SystemRepositoryAdapter(
    private val jpaRepository: SystemJpaRepository,
    private val queryRepository: SystemQueryAdapter,
) : SystemPort {
    override fun save(system: System): SystemQueryRow {
        return SystemQueryRow.of(jpaRepository.save(system))
    }

    override fun update(systemId: Long, request: UpdateSystemRequest, encrypted: EncryptedSystemInfo): SystemQueryRow {
        val target = jpaRepository.findById(systemId)
            .orElseThrow { SystemNotFoundException(systemId) }
        return save(target.copy(request = request, encrypted = encrypted))
    }

    override fun findAll(): List<SystemQueryRow> {
        return queryRepository.findAllSummaries()
    }

    override fun findById(systemId: Long): SystemQueryRow? {
        return queryRepository.findDetailById(systemId)
    }

    override fun findByName(name: String): SystemQueryRow? {
        return queryRepository.findDetailByName(name)
    }

    override fun findFilesBySystemId(systemId: Long): List<SystemFileQueryRow> {
        return queryRepository.findFilesBySystemId(systemId)
    }

    override fun existsByName(name: String): Boolean {
        return jpaRepository.existsByName(name)
    }

    override fun existsByChromaCollection(chromaCollection: String): Boolean {
        return jpaRepository.existsByChromaCollection(chromaCollection)
    }

    override fun softDelete(systemId: Long) {
        val target = jpaRepository.findById(systemId)
            .orElseThrow { SystemNotFoundException(systemId) }
        val deactivated = target.copy(useYn = "N", updatedAt = LocalDateTime.now())
        jpaRepository.save(deactivated)
    }
}