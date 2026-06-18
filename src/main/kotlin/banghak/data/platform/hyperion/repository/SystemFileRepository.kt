package banghak.data.platform.hyperion.repository

import banghak.data.platform.hyperion.repository.entity.FileType
import banghak.data.platform.hyperion.repository.entity.SystemFile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SystemFileRepository : JpaRepository<SystemFile, Long> {

    fun findBySystemId(systemId: Long): List<SystemFile>

    fun findBySystemIdAndFileType(systemId: Long, fileType: FileType): List<SystemFile>

    fun findBySystemIdAndOriginalFilename(systemId: Long, originalFilename: String): SystemFile?

    /** Used by the upload service to enforce [MAX_FILES_PER_TYPE]. */
    fun countBySystemIdAndFileType(systemId: Long, fileType: FileType): Long

    fun existsBySystemIdAndOriginalFilename(systemId: Long, originalFilename: String): Boolean

    @Modifying
    @Query("delete from SystemFile f where f.system.id = :systemId")
    fun deleteAllBySystemId(@Param("systemId") systemId: Long): Int

    companion object {
        /** Per-system upload cap, applied independently to each [FileType]. */
        const val MAX_FILES_PER_TYPE = 5
    }
}