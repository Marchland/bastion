package dev.jacobandersen.bastion.post.internal.store

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.post.internal.repository.PostRepository
import dev.jacobandersen.bastion.post.types.PostStatus
import dev.jacobandersen.bastion.post.types.PostVisibility
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
    ],
)
class PostOptimisticLockingTest {
    @Autowired
    lateinit var postService: PostService

    @Autowired
    lateinit var postRepository: PostRepository

    @Autowired
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `stale writes are rejected`() {
        val created =
            postService.create(
                "optimistic-${System.nanoTime()}",
                PostStatus.PUBLISHED,
                PostVisibility.PUBLIC,
                deleted = false,
                post = post(),
            )
        val transaction = TransactionTemplate(transactionManager)

        assertThrows(OptimisticLockingFailureException::class.java) {
            transaction.executeWithoutResult {
                val entity = postRepository.findById(created.id).orElseThrow()

                // Simulate another transaction committing while this one holds
                // the old entity version. The native update advances the row
                // without touching the persistence context.
                entityManager
                    .createNativeQuery("update posts set version = version + 1 where id = :id")
                    .setParameter("id", created.id)
                    .executeUpdate()

                entity.post = entity.post.setProperty("name", Mf2Value.String("Stale name"))
                postRepository.saveAndFlush(entity)
            }
        }
    }

    private fun post() =
        Mf2Object(
            type = listOf("h-entry"),
            properties = mutableMapOf("content" to listOf(Mf2Value.String("Body"))),
            children = null,
        )
}
