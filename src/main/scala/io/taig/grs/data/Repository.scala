package io.taig.grs.data

import io.circe.generic.JsonCodec

@JsonCodec(decodeOnly = true)
final case class Repository(id: Long, name: String, full_name: String, owner: RepositoryOwner)
