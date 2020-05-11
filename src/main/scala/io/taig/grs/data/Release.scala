package io.taig.grs.data

import java.time.Instant

import io.circe.generic.JsonCodec

@JsonCodec(decodeOnly = true)
final case class Release(id: Long, tag_name: String, created_at: Instant, published_at: Instant)
