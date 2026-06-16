package banghak.data.platform.hyperion.repository

import banghak.data.platform.hyperion.repository.entity.TargetSystem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TargetSystemRepository : JpaRepository<TargetSystem, Long> {

    fun findByName(name: String): TargetSystem?

    fun existsByName(name: String): Boolean

    fun existsByChromaCollection(chromaCollection: String): Boolean
}