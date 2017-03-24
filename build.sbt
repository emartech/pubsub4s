lazy val commonSettings = Seq(
  organization := "io.fullstackanalytics",
  version := "0.1.8",
  scalaVersion := "2.12.1"
)

lazy val root = (project in file(".")).
  settings(commonSettings).
  settings(
    name := "pubsub4s",
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.4.17",
      "com.typesafe.akka" %% "akka-slf4j" % "2.4.17",
      "com.typesafe.akka" %% "akka-stream" % "2.4.17",
      "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.17",
      "com.typesafe.akka" %% "akka-testkit" % "2.4.17",
      "com.google.apis" % "google-api-services-pubsub" % "v1-rev11-1.22.0"
    )
  )

publishTo := Some(Resolver.file("releases", new File("releases")))
