package dev.jacobandersen.bastion.micropub.internal.query

import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.syndication.SyndicationTargetQueryService
import org.springframework.stereotype.Service

@Service
class SyndicationTargetsService(
    private val syndicationTargets: SyndicationTargetQueryService,
) {
    fun getSyndicationTargets(): ApiResponse<*> =
        ApiResponse.Success.Ok(
            mapOf(
                "syndicate-to" to
                    syndicationTargets.targets().map { target ->
                        mapOf("uid" to target.uid, "name" to target.name)
                    },
            ),
        )
}
