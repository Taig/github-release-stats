package io.taig.grs.algebra

import fs2.Stream
import io.taig.grs.data.{Release, Repository}

abstract class GitHub[F[_]] {
  def popularRepositories: Stream[F, Repository]

  def releases(user: String, repository: String): Stream[F, Release]
}
