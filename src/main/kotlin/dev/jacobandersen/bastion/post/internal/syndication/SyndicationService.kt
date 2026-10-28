package dev.jacobandersen.bastion.post.internal.syndication

import dev.jacobandersen.bastion.post.internal.entity.PostSyndicationEntity
import dev.jacobandersen.bastion.post.internal.model.Post
import dev.jacobandersen.bastion.post.internal.store.PostService
import dev.jacobandersen.bastion.post.internal.url.PostUrlService
import dev.jacobandersen.bastion.util.HttpUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jobrunr.jobs.annotations.Job
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Reconciles syndicated copies against the current post and requested target
 * set. Dispatch is best-effort: failures are logged and never fail the
 * originating post command.
 */
@Service
class SyndicationService(
    private val jobScheduler: JobScheduler,
    private val config: SyndicationConfig,
    private val httpClient: SyndicationHttpClient,
    private val postSyndicationService: PostSyndicationService,
    private val postService: PostService,
    private val urlService: PostUrlService,
) {
    @Transactional
    fun reconcile(postId: UUID) {
        val post = postService.findById(postId) ?: return
        val canonicalUrl = urlService.generatePostUrl(post)

        postSyndicationService.findByPostId(postId).forEach { record ->
            val target = config.targetByUid(record.targetUid)
            if (target == null) {
                postSyndicationService.remove(postId, record.targetUid)
                return@forEach
            }

            if (!record.desired) {
                retractIfNeeded(post, record, target)
                postSyndicationService.remove(postId, record.targetUid)
                return@forEach
            }

            if (!post.publiclyReachable) {
                retractIfNeeded(post, record, target)
                return@forEach
            }

            val sourceUrl = record.sourceUrl
            when {
                record.syndicatedUrl == null -> {
                    if (target.supports(SyndicationAction.CREATE)) {
                        enqueueCreate(post.id, target.uid)
                    }
                }

                sourceUrl != null && sourceUrl != canonicalUrl -> {
                    if (target.supports(SyndicationAction.DELETE) && target.supports(SyndicationAction.CREATE)) {
                        enqueueRebase(post.id, target.uid, sourceUrl)
                    } else {
                        retractIfNeeded(post, record, target)
                    }
                }

                else -> {
                    if (target.supports(SyndicationAction.UPDATE)) {
                        enqueueUpdate(post, target)
                    }
                }
            }
        }
    }

    private fun retractIfNeeded(
        post: Post,
        record: PostSyndicationEntity,
        target: SyndicationConfig.Target,
    ) {
        val sourceUrl = record.sourceUrl ?: urlService.generatePostUrl(post)
        if (record.syndicatedUrl != null && target.supports(SyndicationAction.DELETE)) {
            enqueueDelete(post.id, target.uid, sourceUrl)
        }
        postSyndicationService.markRetracted(post.id, record.targetUid)
    }

    private fun enqueueCreate(
        postId: UUID,
        targetUid: String,
    ) {
        jobScheduler.enqueue<SyndicationService> { it.runCreateJob(postId, targetUid) }
    }

    private fun enqueueDelete(
        postId: UUID,
        targetUid: String,
        sourceUrl: String,
    ) {
        jobScheduler.enqueue<SyndicationService> { it.runDeleteJob(postId, targetUid, sourceUrl) }
    }

    private fun enqueueRebase(
        postId: UUID,
        targetUid: String,
        previousUrl: String,
    ) {
        jobScheduler.enqueue<SyndicationService> { it.runRebaseJob(postId, targetUid, previousUrl) }
    }

    private fun enqueueUpdate(
        post: Post,
        target: SyndicationConfig.Target,
    ) {
        val canonicalUrl = urlService.generatePostUrl(post)
        val update = httpClient.serializeExcerptUpdate(post, canonicalUrl, config.effectiveMaxGraphemes(target))
        jobScheduler.enqueue<SyndicationService> { it.runUpdateJob(post.id, target.uid, update) }
    }

    @Job(retries = 0)
    fun runCreateJob(
        postId: UUID,
        targetUid: String,
    ) {
        try {
            val post = postService.findById(postId)
            if (post == null) {
                logger.warn { "Skipping syndication create: post $postId no longer exists" }
                return
            }
            if (!post.publiclyReachable) {
                logger.warn { "Skipping syndication create for post $postId: post is no longer publicly reachable" }
                return
            }
            val target = config.targetByUid(targetUid)
            if (target == null) {
                logger.warn { "Skipping syndication create: unknown target \"$targetUid\"" }
                return
            }
            if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
                logger.warn { "Skipping syndication create for post $postId to blocked target \"$targetUid\"" }
                return
            }

            val canonicalUrl = urlService.generatePostUrl(post)
            when (val result = httpClient.sendCreate(target, syndicatedPost(post, target, canonicalUrl))) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Syndicated post $postId to target \"$targetUid\" (HTTP ${result.statusCode})" }
                    postSyndicationService.recordOutcome(
                        postId,
                        targetUid,
                        result.location ?: canonicalUrl,
                        canonicalUrl,
                    )
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn { "Syndication create to target \"$targetUid\" for post $postId failed: ${result.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Syndication create to target \"$targetUid\" for post $postId failed unexpectedly" }
        }
    }

    @Job(retries = 0)
    fun runDeleteJob(
        postId: UUID,
        targetUid: String,
        sourceUrl: String,
    ) {
        try {
            val target = config.targetByUid(targetUid)
            if (target == null) {
                logger.warn { "Skipping syndication delete: unknown target \"$targetUid\"" }
                return
            }
            if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
                logger.warn { "Skipping syndication delete for post $postId to blocked target \"$targetUid\"" }
                return
            }

            when (val result = httpClient.sendDelete(target, sourceUrl)) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Syndication delete sent for post $postId to target \"$targetUid\" (HTTP ${result.statusCode})" }
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn { "Syndication delete to target \"$targetUid\" for post $postId failed: ${result.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Syndication delete to target \"$targetUid\" for post $postId failed unexpectedly" }
        }
    }

    @Job(retries = 0)
    fun runRebaseJob(
        postId: UUID,
        targetUid: String,
        previousUrl: String,
    ) {
        try {
            val post = postService.findById(postId)
            if (post == null) {
                logger.warn { "Skipping syndication rebase: post $postId no longer exists" }
                return
            }
            val target = config.targetByUid(targetUid)
            if (target == null) {
                logger.warn { "Skipping syndication rebase: unknown target \"$targetUid\"" }
                return
            }
            if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
                logger.warn { "Skipping syndication rebase for post $postId to blocked target \"$targetUid\"" }
                return
            }

            when (val deleteResult = httpClient.sendDelete(target, previousUrl)) {
                is SyndicationSendResult.Success -> {
                    logger.info {
                        "Syndication rebase removed old copy for post $postId at target \"$targetUid\" (HTTP ${deleteResult.statusCode})"
                    }
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn {
                        "Syndication rebase could not remove old copy at target \"$targetUid\" for post $postId: ${deleteResult.message}"
                    }
                }
            }

            if (!post.publiclyReachable) {
                logger.warn { "Skipping syndication rebase re-create for post $postId: post is no longer publicly reachable" }
                return
            }

            val canonicalUrl = urlService.generatePostUrl(post)
            when (val createResult = httpClient.sendCreate(target, syndicatedPost(post, target, canonicalUrl))) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Syndication rebase re-created post $postId at target \"$targetUid\" (HTTP ${createResult.statusCode})" }
                    postSyndicationService.recordOutcome(
                        postId,
                        targetUid,
                        createResult.location ?: canonicalUrl,
                        canonicalUrl,
                    )
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn { "Syndication rebase create to target \"$targetUid\" for post $postId failed: ${createResult.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Syndication rebase to target \"$targetUid\" for post $postId failed unexpectedly" }
        }
    }

    @Job(retries = 0)
    fun runUpdateJob(
        postId: UUID,
        targetUid: String,
        update: SyndicationUpdate,
    ) {
        try {
            val post = postService.findById(postId)
            if (post == null) {
                logger.warn { "Skipping syndication update: post $postId no longer exists" }
                return
            }
            val target = config.targetByUid(targetUid)
            if (target == null) {
                logger.warn { "Skipping syndication update: unknown target \"$targetUid\"" }
                return
            }
            if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
                logger.warn { "Skipping syndication update for post $postId to blocked target \"$targetUid\"" }
                return
            }

            val canonicalUrl = urlService.generatePostUrl(post)
            when (val result = httpClient.sendUpdate(target, canonicalUrl, update)) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Syndication update sent for post $postId to target \"$targetUid\" (HTTP ${result.statusCode})" }
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn { "Syndication update to target \"$targetUid\" for post $postId failed: ${result.message}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Syndication update to target \"$targetUid\" for post $postId failed unexpectedly" }
        }
    }

    private fun syndicatedPost(
        post: Post,
        target: SyndicationConfig.Target,
        canonicalUrl: String,
    ) = SyndicationContentMapper.build(post, canonicalUrl, config.effectiveMaxGraphemes(target))
}
