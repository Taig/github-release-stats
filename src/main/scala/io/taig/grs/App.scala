package io.taig.grs

import java.nio.file.{Path, Paths, StandardOpenOption}
import java.time.format.TextStyle
import java.time.{DayOfWeek, Instant, Month, OffsetDateTime, ZoneOffset}
import java.util.Locale

import scala.concurrent.ExecutionContext
import cats.Show
import cats.effect.{Blocker, ContextShift, ExitCode, IO, IOApp, Sync}
import cats.implicits._
import fs2.io.file.writeAll
import fs2.text.utf8Encode
import fs2.{Pipe, Stream}
import io.taig.grs.data.Result
import io.taig.grs.interpreter.ClientGitHub
import org.http4s.client.blaze.BlazeClientBuilder

object App extends IOApp {
  val GitHubToken: Option[String] = None

  val TwentyNineteen: Instant = Instant.parse("2019-12-31T23:59:59Z")

  val Flags = List(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

  def write[F[_]: Sync: ContextShift](
      columns: List[String],
      target: Path,
      blocker: Blocker
  ): Pipe[F, List[String], Nothing] =
    lines =>
      (Stream.emit(columns) ++ lines)
        .map(_.mkString(","))
        .intersperse("\n")
        .through(utf8Encode)
        .through(writeAll(target, blocker, Flags)) *> Stream.empty

  def group[F[_], A: Show](sort: Map[A, Int] => List[(A, Int)]): Pipe[F, A, List[String]] =
    _.fold(Map.empty[A, Int]) { (registry, element) =>
      registry.updatedWith(element) {
        case Some(count) => Some(count + 1)
        case None        => Some(1)
      }
    }.flatMap(registry => Stream.emits(sort(registry)))
      .map { case (key, value) => List(key.show, value.show) }

  def releases[F[_]: Sync: ContextShift](blocker: Blocker): Pipe[F, Result, Nothing] = { results =>
    val columns = List("repository", "releases")
    val target = Paths.get("releases.csv")
    results
      .map(_.repository)
      .through(group[F, String](_.toList.sortWith { case ((_, x), (_, y)) => x > y }))
      .through(write(columns, target, blocker))
  }

  def dayOfYear[F[_]: Sync: ContextShift](blocker: Blocker): Pipe[F, Result, Nothing] = { results =>
    val columns = List("day", "count")
    val target = Paths.get("dayOfYear.csv")
    results
      .map(_.release.atOffset(ZoneOffset.UTC).getDayOfYear)
      .through(group[F, Int](_.toList.sortBy(_._1)))
      .through(write(columns, target, blocker))
  }

  def monthOfYear[F[_]: Sync: ContextShift](blocker: Blocker): Pipe[F, Result, Nothing] = { results =>
    implicit val show: Show[Month] = Show.show[Month](_.getDisplayName(TextStyle.FULL, Locale.US))
    val columns = List("month", "count")
    val target = Paths.get("monthOfYear.csv")

    results
      .map(_.release.atOffset(ZoneOffset.UTC).getMonth)
      .through(group[F, Month](_.toList.sortBy(_._1)))
      .through(write(columns, target, blocker))
  }

  def dayOfWeek[F[_]: Sync: ContextShift](blocker: Blocker): Pipe[F, Result, Nothing] = { results =>
    implicit val show: Show[DayOfWeek] = Show.show[DayOfWeek](_.getDisplayName(TextStyle.FULL, Locale.US))
    val columns = List("day", "count")
    val target = Paths.get("dayOfWeek.csv")

    results
      .map(_.release.atOffset(ZoneOffset.UTC).getDayOfWeek)
      .through(group[F, DayOfWeek](_.toList.sortBy(_._1)))
      .through(write(columns, target, blocker))
  }

  def timeOfDay[F[_]: Sync: ContextShift](blocker: Blocker): Pipe[F, Result, Nothing] = { results =>
    val columns = List("time", "count")
    val target = Paths.get("timeOfDay.csv")
    results
      .map(_.release.atOffset(ZoneOffset.UTC).getHour)
      .through(group[F, Int](_.toList.sortBy(_._1)))
      .through(write(columns, target, blocker))
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val blocker = Blocker[IO]
    val client = BlazeClientBuilder[IO](ExecutionContext.global).resource

    (blocker, client).tupled.use {
      case (blocker, client) =>
        val github = ClientGitHub[IO](client)(GitHubToken)

        github.popularRepositories
          .parEvalMapUnordered(8) { repository =>
            github
              .releases(repository.owner.login, repository.name)
              .map(release => Result(repository.full_name, release.published_at))
              .compile
              .toList
              .map(_.filter(_.release.isBefore(TwentyNineteen)))
          }
          .flatMap { results =>
            if (results.length < 5) Stream.empty else Stream.emits(results)
          }
          .broadcastThrough(
            releases[IO](blocker),
            dayOfWeek[IO](blocker),
            timeOfDay[IO](blocker),
            monthOfYear[IO](blocker),
            dayOfYear[IO](blocker)
          )
          .compile
          .drain
    } *> IO(ExitCode.Success)
  }
}
