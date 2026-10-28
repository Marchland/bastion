package dev.jacobandersen.bastion.micropub.internal.controller

import dev.jacobandersen.bastion.micropub.internal.media.MediaEndpointService
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.internal.type.resp.toResponseEntity
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartHttpServletRequest

const val MICROPUB_MEDIA_PATH = "/micropub/media"

@RestController
@RequestMapping(MICROPUB_MEDIA_PATH)
class MediaController(
    private val service: MediaEndpointService,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun handle(request: MultipartHttpServletRequest): ResponseEntity<*> {
        val file =
            request.multiFileMap["file"]?.first()
                ?: return ApiResponse.Error
                    .InvalidRequest(
                        errorDescription = "missing file in `file` parameter",
                    ).toResponseEntity()

        return service.handle(file).toResponseEntity()
    }
}
