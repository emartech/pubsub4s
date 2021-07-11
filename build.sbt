lazy val commonSettings = Seq(
  organization := "io.fullstackanalytics",
  version := "0.4.0",
  scalaVersion := "2.13.5",
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  scalacOptions ++= Seq("-deprecation")
)

val akkaV = "2.6.15"

lazy val root = (project in file(".")).
  settings(commonSettings).
  settings(
    name := "pubsub4s",
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-slf4j" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.google.apis" % "google-api-services-pubsub" % "v1-rev20190826-1.30.1"
    )
  )

publishTo := Some(Resolver.file("releases", new File("releases")))
