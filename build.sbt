lazy val commonSettings = Seq(
  organization := "io.fullstackanalytics",
  version := "0.2.0",
  scalaVersion := "2.12.10"
)

lazy val root = (project in file(".")).
  settings(commonSettings).
  settings(
    name := "pubsub4s",
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.25",
      "com.typesafe.akka" %% "akka-slf4j" % "2.5.25",
      "com.typesafe.akka" %% "akka-stream" % "2.5.25",
      "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.25",
      "com.typesafe.akka" %% "akka-testkit" % "2.5.25",
      "com.google.apis" % "google-api-services-pubsub" % "v1-rev20190826-1.30.1"
    )
  )

publishTo := Some(Resolver.file("releases", new File("releases")))
