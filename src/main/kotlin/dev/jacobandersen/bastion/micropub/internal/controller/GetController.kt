package dev.jacobandersen.bastion.micropub.internal.controller

import dev.jacobandersen.bastion.micropub.internal.query.GetDispatchService
import dev.jacobandersen.bastion.micropub.internal.type.GetQueryOption
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.internal.type.resp.toResponseEntity
import dev.jacobandersen.bastion.micropub.internal.util.MicropubParamNormalizer
import org.springframework.http.ResponseEntity
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/micropub")
class GetController(
    val service: GetDispatchService,
) {
    @GetMapping
    fun handle(
        @RequestParam params: MultiValueMap<String, String>,
    ): ResponseEntity<*> {
        val params = MicropubParamNormalizer.normalizeDuplicates(params)

        val command =
            GetQueryOption.fromString(params["q"]?.firstOrNull())
                ?: return ApiResponse.Error
                    .InvalidRequest(
                        errorDescription = "Unknown query command",
                    ).toResponseEntity()

        params.remove("q")

        return service.handleMicropubGet(command, params).toResponseEntity()
    }
}
