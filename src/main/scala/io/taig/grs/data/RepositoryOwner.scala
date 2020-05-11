package io.taig.grs.data

import io.circe.generic.JsonCodec

@JsonCodec(decodeOnly = true)
final case class RepositoryOwner(id: Long, login: String)
