package banghak.data.platform.hyperion.usecase.facade

import banghak.data.platform.hyperion.controller.dto.system.CreateSystemRequest
import banghak.data.platform.hyperion.controller.dto.system.CreateSystemResponse
import banghak.data.platform.hyperion.controller.dto.system.FileUploadResponse
import banghak.data.platform.hyperion.controller.dto.system.SystemDetailResponse
import banghak.data.platform.hyperion.controller.dto.system.SystemFileInfo
import banghak.data.platform.hyperion.controller.dto.system.SystemListItemResponse
import banghak.data.platform.hyperion.controller.dto.system.UpdateSystemRequest
import banghak.data.platform.hyperion.controller.dto.system.UpdateSystemResponse
import banghak.data.platform.hyperion.repository.entity.FileType
import banghak.data.platform.hyperion.repository.entity.SystemFile
import banghak.data.platform.hyperion.service.FileService
import banghak.data.platform.hyperion.service.MemberService
import banghak.data.platform.hyperion.service.SystemService
import banghak.data.platform.hyperion.service.exception.SystemFileLimitExceededException
import banghak.data.platform.hyperion.service.exception.SystemFileRequiredException
import banghak.data.platform.hyperion.service.port.SystemFileRepositoryPort.Companion.MAX_FILES_PER_TYPE
import banghak.data.platform.hyperion.usecase.SystemFileUseCase
import banghak.data.platform.hyperion.usecase.UploadFile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Inbound port implementation — orchestrates the system+files use-case across services.
 *
 * Strict layering: this Facade depends only on Service interfaces. It never reaches
 * a repository / port directly; all persistence concerns are encapsulated in the
 * services. See AGENTS.md Section 3 and Section 6.
 */
@Component
class SystemFileFacade(
    private val systemService: SystemService,
    private val fileService: FileService,
    private val memberService: MemberService
) : SystemFileUseCase {

    @Transactional
    override fun create(request: CreateSystemRequest, uploads: List<UploadFile>, memberId: Long): CreateSystemResponse {
        if (uploads.isEmpty()) {
            throw SystemFileRequiredException()
        }
        validatePerTypeCap(uploads.count { it.fileType == FileType.MARKDOWN }, FileType.MARKDOWN)
        validatePerTypeCap(uploads.count { it.fileType == FileType.SQL_DDL }, FileType.SQL_DDL)

        val createdSystem = systemService.create(request, memberId)
        val uploadedFiles = uploads.map { upload ->
            FileUploadResponse.of(
                fileService.upload(
                    systemId = createdSystem.id,
                    rootPath = createdSystem.rootPath,
                    originalFilename = upload.originalFilename,
                    fileType = upload.fileType,
                    content = upload.content,
                    uploadedBy = memberId
                )
            )
        }
        return CreateSystemResponse.of(createdSystem, uploadedFiles)
    }

    @Transactional
    override fun update(systemId: Long, request: UpdateSystemRequest, uploads: List<UploadFile>, memberId: Long): UpdateSystemResponse {
        val currentFiles = fileService.findBySystemId(systemId)
        validateUpdateCounts(currentFiles, request.deleteFileIds, uploads)

        val updated = systemService.update(systemId, request)
        request.deleteFileIds.forEach { fileService.delete(it) }
        val addedFiles = uploads.map { upload ->
            fileService.upload(
                systemId = updated.id,
                rootPath = updated.rootPath,
                originalFilename = upload.originalFilename,
                fileType = upload.fileType,
                content = upload.content,
                uploadedBy = memberId
            )
        }
        val survivingFiles = (currentFiles.filter { it.id !in request.deleteFileIds } + addedFiles)
            .map { SystemFileInfo.of(it, memberService.findById(it.uploadedBy).displayName) }
        return UpdateSystemResponse.of(updated, survivingFiles)
    }

    @Transactional(readOnly = true)
    override fun findById(systemId: Long): SystemDetailResponse =
        systemService.findDetailById(systemId)

    @Transactional(readOnly = true)
    override fun findAll(): List<SystemListItemResponse> =
        systemService.findAllSummaries()

    @Transactional
    override fun softDelete(systemId: Long, memberId: Long) {
        memberService.requireExists(memberId)
        systemService.softDelete(systemId)
    }

    // ──────────────────────────────────────────────────────────────────
    // Validation helpers
    // ──────────────────────────────────────────────────────────────────

    private fun validatePerTypeCap(adding: Int, fileType: FileType) {
        if (adding > MAX_FILES_PER_TYPE) {
            throw SystemFileLimitExceededException(0L, fileType, MAX_FILES_PER_TYPE)
        }
    }

    private fun validateUpdateCounts(
        currentFiles: List<SystemFile>,
        deleteIds: List<Long>,
        uploads: List<UploadFile>
    ) {
        val deletedMd = currentFiles.count { it.id in deleteIds && it.fileType == FileType.MARKDOWN }
        val deletedSql = currentFiles.count { it.id in deleteIds && it.fileType == FileType.SQL_DDL }
        val currentMd = currentFiles.count { it.fileType == FileType.MARKDOWN } - deletedMd
        val currentSql = currentFiles.count { it.fileType == FileType.SQL_DDL } - deletedSql
        val addMd = uploads.count { it.fileType == FileType.MARKDOWN }
        val addSql = uploads.count { it.fileType == FileType.SQL_DDL }
        val systemId = currentFiles.firstOrNull()?.systemId ?: 0L
        if (currentMd + addMd > MAX_FILES_PER_TYPE) {
            throw SystemFileLimitExceededException(systemId, FileType.MARKDOWN, MAX_FILES_PER_TYPE)
        }
        if (currentSql + addSql > MAX_FILES_PER_TYPE) {
            throw SystemFileLimitExceededException(systemId, FileType.SQL_DDL, MAX_FILES_PER_TYPE)
        }
    }
}