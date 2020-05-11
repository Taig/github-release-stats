libraryDependencies ++=
  "io.circe" %% "circe-generic" % "0.13.0" ::
    "org.http4s" %% "http4s-blaze-client" % "0.21.4" ::
    "org.http4s" %% "http4s-circe" % "0.21.4" ::
    "org.slf4j" % "slf4j-simple" % "1.7.30" ::
    "org.typelevel" %% "cats-effect" % "2.1.3" ::
    Nil
scalaVersion := "2.13.2"
