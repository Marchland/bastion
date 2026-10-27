package dev.jacobandersen.bastion.post.internal.syndication

/**
 * The syndication lifecycle actions a target may support. A target only ever
 * receives posts for actions it explicitly declares; Bastion sends downstream
 * syndication for the [CREATE], [UPDATE] and [DELETE] actions.
 */
enum class SyndicationAction {
    CREATE,
    DELETE,
    UPDATE,
}
