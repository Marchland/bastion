package dev.jacobandersen.bastion.micropub.internal.type.resp

import dev.jacobandersen.microformats2.Mf2Object

data class SourceListResponse(
    val items: List<Mf2Object>,
)
