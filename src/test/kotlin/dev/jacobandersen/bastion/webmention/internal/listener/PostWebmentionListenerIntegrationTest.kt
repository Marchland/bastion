package dev.jacobandersen.bastion.webmention.internal.listener

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.micropub.internal.command.CreateService
import dev.jacobandersen.bastion.micropub.internal.command.DeleteService
import dev.jacobandersen.bastion.micropub.internal.command.UpdateService
import dev.jacobandersen.bastion.micropub.internal.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.webmention.internal.data.domain.WebmentionState
import dev.jacobandersen.bastion.webmention.internal.data.repository.WebmentionNotificationRepository
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.time.Duration

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
    ],
)
@WithMockUser(authorities = ["CREATE", "UPDATE", "DELETE", "UNDELETE"])
class PostWebmentionListenerIntegrationTest {
    @Autowired
    lateinit var createService: CreateService

    @Autowired
    lateinit var updateService: UpdateService

    @Autowired
    lateinit var deleteService: DeleteService

    @Autowired
    lateinit var notificationRepository: WebmentionNotificationRepository

    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    private fun uniqueSlug(prefix: String): String = "$prefix-${System.nanoTime()}"

    private fun createPayload(
        content: String,
        slug: String,
        status: String = "published",
        visibility: String = "public",
    ): MicropubPayload.Json {
        val root =
            mapper.readTree(
                """{"type": ["h-entry"], "properties": {"name": ["$slug"], "content": ["$content"], "mp-slug": ["$slug"], "post-status": ["$status"], "visibility": ["$visibility"]}}""",
            ) as ObjectNode
        return MicropubPayload.Json(root)
    }

    private fun createPublicPost(content: String): String {
        val slug = uniqueSlug("dispatch")
        val response = createService.create(createPayload(content, slug), null)
        return (response as ApiResponse.Success.Created).location
    }

    private fun updateJson(
        url: String,
        update: String,
    ): MicropubPayload.Json {
        val root = mapper.readTree("""{"url": "$url", $update}""") as ObjectNode
        return MicropubPayload.Json(root)
    }

    private fun stateOf(
        sourceUrl: String,
        targetUrl: String,
    ): WebmentionState? = notificationRepository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)?.state

    private fun awaitState(
        expected: WebmentionState?,
        sourceUrl: String,
        targetUrl: String,
    ) {
        await().atMost(Duration.ofSeconds(2)).untilAsserted {
            assertEquals(expected, stateOf(sourceUrl, targetUrl))
        }
    }

    private fun awaitNoState(
        sourceUrl: String,
        targetUrl: String,
    ) {
        await().during(Duration.ofMillis(500)).untilAsserted {
            assertNull(stateOf(sourceUrl, targetUrl))
        }
    }

    @Test
    fun createTracksPublicPostTargetsAsActive() {
        val location = createPublicPost("Check out https://example.com/alpha now")

        awaitState(WebmentionState.ACTIVE, location, "https://example.com/alpha")
    }

    @Test
    fun createDoesNotTrackDraftOrPrivatePosts() {
        val slug = uniqueSlug("dispatch-draft")
        val draft = createService.create(createPayload("See https://example.com/draft", slug, status = "draft"), null)
        val draftLocation = (draft as ApiResponse.Success.Created).location
        awaitNoState(draftLocation, "https://example.com/draft")

        val privateSlug = uniqueSlug("dispatch-private")
        val private =
            createService.create(
                createPayload("See https://example.com/private", privateSlug, visibility = "private"),
                null,
            )
        val privateLocation = (private as ApiResponse.Success.Created).location
        awaitNoState(privateLocation, "https://example.com/private")
    }

    @Test
    fun updateAddsNewTargetsAndRetractsRemovedTargets() {
        val location = createPublicPost("One https://example.com/alpha")
        awaitState(WebmentionState.ACTIVE, location, "https://example.com/alpha")

        val response =
            updateService.update(
                updateJson(location, """"replace": {"content": ["Two https://example.com/beta"]}"""),
            )
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, response)

        awaitState(WebmentionState.INACTIVE, location, "https://example.com/alpha")
        awaitState(WebmentionState.ACTIVE, location, "https://example.com/beta")
    }

    @Test
    fun updateKeepsUnchangedTargetsActive() {
        val location = createPublicPost("One https://example.com/alpha")
        awaitState(WebmentionState.ACTIVE, location, "https://example.com/alpha")

        val response =
            updateService.update(
                updateJson(location, """"replace": {"name": ["Renamed"]}"""),
            )
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, response)

        awaitState(WebmentionState.ACTIVE, location, "https://example.com/alpha")
    }

    @Test
    fun updateToPrivateInactivatesWithoutRetraction() {
        val location = createPublicPost("One https://example.com/alpha")
        awaitState(WebmentionState.ACTIVE, location, "https://example.com/alpha")

        val response =
            updateService.update(
                updateJson(location, """"replace": {"visibility": ["private"]}"""),
            )
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, response)

        awaitState(WebmentionState.INACTIVE, location, "https://example.com/alpha")
    }

    @Test
    fun deleteRetractsActiveTargetsAndUndeleteReactivates() {
        val location = createPublicPost("One https://example.com/alpha")
        awaitState(WebmentionState.ACTIVE, location, "https://example.com/alpha")

        val deleteResponse =
            deleteService.delete(
                MicropubPayload.Json(mapper.createObjectNode().put("url", location)),
            )
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, deleteResponse)
        awaitState(WebmentionState.INACTIVE, location, "https://example.com/alpha")

        val undeleteResponse =
            deleteService.undelete(
                MicropubPayload.Json(mapper.createObjectNode().put("url", location)),
            )
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, undeleteResponse)
        awaitState(WebmentionState.ACTIVE, location, "https://example.com/alpha")
    }
}
