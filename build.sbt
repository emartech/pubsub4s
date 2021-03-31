lazy val commonSettings = Seq(
  organization := "io.fullstackanalytics",
  version := "0.3.0",
  scalaVersion := "2.13.5",
  crossScalaVersions := List("2.13.5", "2.12.13")
)

val akkaV = "2.6.13"

lazy val root = (project in file(".")).
  settings(commonSettings).
  settings(
    name := "pubsub4s",
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-slf4j" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.google.apis" % "google-api-services-pubsub" % "v1-rev20190826-1.30.1",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.3"
    )
  )

publishTo := Some(Resolver.file("releases", new File("releases")))
