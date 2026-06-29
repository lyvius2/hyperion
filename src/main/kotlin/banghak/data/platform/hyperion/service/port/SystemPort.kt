package banghak.data.platform.hyperion.service.port

import banghak.data.platform.hyperion.controller.dto.system.UpdateSystemRequest
import banghak.data.platform.hyperion.dto.EncryptedSystemInfo
import banghak.data.platform.hyperion.repository.entity.System
import banghak.data.platform.hyperion.repository.query.SystemFileQueryRow
import banghak.data.platform.hyperion.repository.query.SystemQueryRow

interface SystemPort {
    fun save(system: System): SystemQueryRow
    fun update(systemId: Long, request: UpdateSystemRequest, encrypted: EncryptedSystemInfo): SystemQueryRow
    fun findAll(): List<SystemQueryRow>
    fun findById(systemId: Long): SystemQueryRow?
    fun findByName(name: String): SystemQueryRow?
    fun findFilesBySystemId(systemId: Long): List<SystemFileQueryRow>
    fun existsByName(name: String): Boolean
    fun existsByChromaCollection(chromaCollection: String): Boolean
    fun softDelete(systemId: Long)
}