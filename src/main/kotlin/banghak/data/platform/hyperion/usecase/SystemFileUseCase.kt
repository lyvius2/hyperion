package banghak.data.platform.hyperion.usecase

import banghak.data.platform.hyperion.controller.dto.system.CreateSystemRequest
import banghak.data.platform.hyperion.controller.dto.system.CreateSystemResponse
import banghak.data.platform.hyperion.controller.dto.system.SystemDetailResponse
import banghak.data.platform.hyperion.controller.dto.system.SystemListItemResponse
import banghak.data.platform.hyperion.controller.dto.system.UpdateSystemRequest
import banghak.data.platform.hyperion.controller.dto.system.UpdateSystemResponse

interface SystemFileUseCase {
    fun create(request: CreateSystemRequest, uploads: List<UploadFile>, memberId: Long): CreateSystemResponse

    fun update(systemId: Long, request: UpdateSystemRequest, uploads: List<UploadFile>, memberId: Long): UpdateSystemResponse

    fun findById(systemId: Long): SystemDetailResponse

    fun findAll(): List<SystemListItemResponse>

    fun softDelete(systemId: Long, memberId: Long)
}