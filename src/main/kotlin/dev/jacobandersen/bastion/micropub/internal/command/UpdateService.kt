package dev.jacobandersen.bastion.micropub.internal.command

import dev.jacobandersen.bastion.micropub.internal.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.internal.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.internal.type.req.MicropubUpdatePayload
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.commands.PostCommandConflictException
import dev.jacobandersen.bastion.post.commands.PostCommandException
import dev.jacobandersen.bastion.post.commands.PostCommandService
import dev.jacobandersen.bastion.post.commands.PostPatch
import dev.jacobandersen.bastion.post.commands.SyndicationTargetChange
import dev.jacobandersen.bastion.post.commands.UpdatePostCommand
import dev.jacobandersen.microformats2.Mf2Value
import dev.jacobandersen.microformats2.plainTextOrNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class UpdateService(
    private val postCommandService: PostCommandService,
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

        val commands =
            commandResolver.resolve { key ->
                update.replacements?.get(key) ?: update.additions?.get(key)
            } ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid command parameters in update")

        val result =
            try {
                postCommandService.update(
                    UpdatePostCommand(
                        url = update.url,
                        patch = postPatch(update),
                        requestedSlug = commands.slug,
                        status = commands.status,
                        visibility = commands.visibility,
                        syndicationTargets = syndicationChange(update),
                    ),
                )
            } catch (e: PostCommandConflictException) {
                return ApiResponse.Error.InvalidRequest(
                    errorDescription = e.message ?: "The post was modified by another request. Reload it and retry.",
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

    private fun postPatch(update: MicropubUpdatePayload): PostPatch {
        val replace = update.replacements.orEmpty().filterKeys { !MicropubCommand.isCommandProperty(it) }
        val add = update.additions.orEmpty().filterKeys { !MicropubCommand.isCommandProperty(it) }

        var remove = emptyMap<String, List<Mf2Value>>()
        var removeAll = emptySet<String>()
        when (val removals = update.removals) {
            is MicropubUpdatePayload.Removals.All -> {
                removeAll = removals.properties.filterNot { MicropubCommand.isCommandProperty(it) }.toSet()
            }

            is MicropubUpdatePayload.Removals.Many -> {
                remove = removals.properties.filterKeys { !MicropubCommand.isCommandProperty(it) }
            }

            null -> {
                Unit
            }
        }

        return PostPatch(replace = replace, add = add, remove = remove, removeAll = removeAll)
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
}
