package dev.jacobandersen.bastion.micropub.internal.command

import dev.jacobandersen.bastion.micropub.internal.type.PostAction
import dev.jacobandersen.bastion.micropub.internal.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap
import org.springframework.web.multipart.MultipartFile

@Service
class PostDispatchService(
    val createService: CreateService,
    val updateService: UpdateService,
    val deleteService: DeleteService,
) {
    fun handleMicropubPost(
        action: PostAction,
        payload: MicropubPayload,
        file: MultiValueMap<String, MultipartFile>?,
    ): ApiResponse<*> =
        when (action) {
            PostAction.CREATE -> createService.create(payload, file)
            PostAction.UPDATE -> updateService.update(payload)
            PostAction.DELETE -> deleteService.delete(payload)
            PostAction.UNDELETE -> deleteService.undelete(payload)
        }
}
