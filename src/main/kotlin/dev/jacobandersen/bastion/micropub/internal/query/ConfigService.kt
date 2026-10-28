package dev.jacobandersen.bastion.micropub.internal.query

import dev.jacobandersen.bastion.micropub.internal.controller.MICROPUB_MEDIA_PATH
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.metadata.PostMetadataService
import dev.jacobandersen.bastion.post.syndication.SyndicationTargetQueryService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ConfigService(
    @Value($$"${bastion.public-url}") val publicUrl: String,
    private val syndicationTargets: SyndicationTargetQueryService,
    private val postMetadataService: PostMetadataService,
) {
    fun getConfig(): ApiResponse<*> =
        ApiResponse.Success.Ok(
            mapOf(
                "media-endpoint" to "${publicUrl.trimEnd('/')}$MICROPUB_MEDIA_PATH",
                "syndicate-to" to
                    syndicationTargets.targets().map { target ->
                        mapOf("uid" to target.uid, "name" to target.name)
                    },
                "post-types" to postMetadataService.postTypesView(),
            ),
        )
}
