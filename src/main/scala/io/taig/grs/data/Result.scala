package io.taig.grs.data

import java.time.Instant

import io.circe.generic.JsonCodec

@JsonCodec(encodeOnly = true)
final case class Result(repository: String, release: Instant)
