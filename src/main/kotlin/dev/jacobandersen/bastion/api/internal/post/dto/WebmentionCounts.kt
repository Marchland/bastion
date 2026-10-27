package dev.jacobandersen.bastion.api.internal.post.dto

import dev.jacobandersen.bastion.webmention.read.WebmentionCountsView

/** Per-post counts of verified webmentions by interaction type. */
data class WebmentionCounts(
    val total: Int,
    val reply: Int,
    val like: Int,
    val repost: Int,
    val bookmark: Int,
    val rsvp: Int,
    val mention: Int,
) {
    companion object {
        val EMPTY = WebmentionCounts(0, 0, 0, 0, 0, 0, 0)

        fun from(view: WebmentionCountsView): WebmentionCounts =
            WebmentionCounts(
                total = view.total,
                reply = view.reply,
                like = view.like,
                repost = view.repost,
                bookmark = view.bookmark,
                rsvp = view.rsvp,
                mention = view.mention,
            )
    }
}
