package io.taig.grs.interpreter

import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import io.circe.Json
import io.taig.grs.algebra.GitHub
import io.taig.grs.data.{Release, Repository}
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits._
import org.http4s._

final class ClientGitHub[F[_]](client: Client[F])(token: Option[String])(implicit F: Sync[F]) extends GitHub[F] {
  val Base: Uri = uri"https://api.github.com/"

  def authenticate(request: Request[F]): Request[F] =
    token
      .map(Credentials.Token(AuthScheme.Bearer, _))
      .map(Authorization.apply)
      .map(request.putHeaders(_))
      .getOrElse(request)

  def popularRepositories(page: Int): F[List[Repository]] = {
    val url = (Base / "search" / "repositories")
      .withQueryParam("q", "stars:>1")
      .withQueryParam("sort", "starts")
      .withQueryParam("page", page)
    val request = authenticate(Request[F](Method.GET, url))
    F.delay(println(s"Fetching repositories ($page)")) *>
      client.expect[Json](request).map(_.hcursor.get[List[Repository]]("items")).rethrow
  }

  override val popularRepositories: Stream[F, Repository] =
    // Only top 1000 are available, which means page 35 yields an error
    Stream.emits(1 until 35).flatMap(page => Stream.evalSeq(popularRepositories(page)))

  def releases(user: String, repository: String, page: Int): F[List[Release]] = {
    val url = (Base / "repos" / user / repository / "releases").withQueryParam("page", page)
    val request = authenticate(Request[F](Method.GET, url))
    client.expect[Json](request).map(_.as[List[Release]]).rethrow
  }

  override def releases(user: String, repository: String): Stream[F, Release] =
    Stream.eval(F.delay(println(s"Fetching releases for $user/$repository"))) *>
      Stream
        .iterate(1)(_ + 1)
        .evalMap(page => releases(user, repository, page))
        .takeWhile(_.nonEmpty)
        .flatMap(Stream.emits)
}

object ClientGitHub {
  def apply[F[_]: Sync](client: Client[F])(token: Option[String]): GitHub[F] =
    new ClientGitHub[F](client)(token)
}
