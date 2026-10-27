package dev.jacobandersen.bastion.micropub.internal.command

import dev.jacobandersen.bastion.micropub.internal.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.internal.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.internal.type.req.MicropubUpdatePayload
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.commands.PostCommandException
import dev.jacobandersen.bastion.post.commands.PostCommandService
import dev.jacobandersen.bastion.post.commands.SyndicationTargetChange
import dev.jacobandersen.bastion.post.commands.UpdatePostCommand
import dev.jacobandersen.bastion.post.query.PostQueryResult
import dev.jacobandersen.bastion.post.query.PostQueryService
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import dev.jacobandersen.microformats2.plainTextOrNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class UpdateService(
    private val postCommandService: PostCommandService,
    private val postQueryService: PostQueryService,
    private val commandResolver: MicropubCommandResolver,
) {
    @PreAuthorize("hasAuthority('UPDATE')")
    fun update(payload: MicropubPayload): ApiResponse<*> {
        val update =
            try {
                payload.asUpdatePayload()
            } catch (e: Exception) {
                return ApiResponse.Error.InvalidRequest(errorDescription = "Failed to parse payload as update: ${e.message}")
            }

        if (update.replacements == null && update.additions == null && update.removals == null) {
            return ApiResponse.Error.InvalidRequest(
                errorDescription = "Update payload must include at least one of replace, add, or delete",
            )
        }

        val post =
            when (val result = postQueryService.byUrl(update.url)) {
                is PostQueryResult.Found -> {
                    result.post
                }

                is PostQueryResult.Gone -> {
                    result.post
                }

                is PostQueryResult.Invalid -> {
                    return ApiResponse.Error.InvalidRequest(errorDescription = result.message)
                }

                PostQueryResult.NotFound -> {
                    return ApiResponse.Error.InvalidRequest(errorDescription = "Post not found for URL: ${update.url}")
                }
            }

        val commands =
            commandResolver.resolve { key ->
                update.replacements?.get(key) ?: update.additions?.get(key)
            } ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid command parameters in update")

        var postObj = post.post

        update.replacements?.forEach { (key, values) ->
            if (!MicropubCommand.isCommandProperty(key)) {
                postObj = if (values.isEmpty()) postObj.deleteProperty(key) else postObj.setProperty(key, values)
            }
        }

        update.additions?.forEach { (key, values) ->
            if (!MicropubCommand.isCommandProperty(key)) {
                postObj = postObj.addProperty(key, values)
            }
        }

        update.removals?.let { removals ->
            postObj = applyRemovals(postObj, removals)
        }

        val result =
            try {
                postCommandService.update(
                    UpdatePostCommand(
                        url = update.url,
                        post = postObj,
                        requestedSlug = commands.slug,
                        status = commands.status,
                        visibility = commands.visibility,
                        syndicationTargets = syndicationChange(update),
                    ),
                )
            } catch (e: PostCommandException) {
                return ApiResponse.Error.InvalidRequest(errorDescription = e.message ?: "Invalid post command")
            }

        return if (result.slugChanged) {
            ApiResponse.Success.Created(result.url)
        } else {
            ApiResponse.Success.NoContent
        }
    }

    private fun syndicationChange(update: MicropubUpdatePayload): SyndicationTargetChange? {
        val replace = targetUids(update.replacements?.get(MicropubCommand.MP_SYNDICATE_TO))
        val add = targetUids(update.additions?.get(MicropubCommand.MP_SYNDICATE_TO)).orEmpty()

        var remove = emptyList<String>()
        var removeAll = false
        when (val removals = update.removals) {
            is MicropubUpdatePayload.Removals.All -> {
                removeAll = MicropubCommand.MP_SYNDICATE_TO in removals.properties
            }

            is MicropubUpdatePayload.Removals.Many -> {
                remove = targetUids(removals.properties[MicropubCommand.MP_SYNDICATE_TO]).orEmpty()
            }

            null -> {
                Unit
            }
        }

        return when {
            replace != null -> SyndicationTargetChange(replace = replace)
            add.isEmpty() && remove.isEmpty() && !removeAll -> null
            else -> SyndicationTargetChange(add = add, remove = remove, removeAll = removeAll)
        }
    }

    private fun targetUids(values: List<Mf2Value>?): List<String>? {
        if (values == null) return null
        return values.mapNotNull { it.plainTextOrNull }.filter { it.isNotBlank() }
    }

    private fun applyRemovals(
        postObj: Mf2Object,
        removals: MicropubUpdatePayload.Removals,
    ): Mf2Object =
        when (removals) {
            is MicropubUpdatePayload.Removals.All -> {
                removals.properties.fold(postObj) { current, property -> current.deleteProperty(property) }
            }

            is MicropubUpdatePayload.Removals.Many -> {
                removals.properties.entries.fold(postObj) { current, (key, values) ->
                    val remaining = current.getProperty(key).filter { it !in values }
                    if (remaining.isNotEmpty()) current.setProperty(key, remaining) else current.deleteProperty(key)
                }
            }
        }
}
