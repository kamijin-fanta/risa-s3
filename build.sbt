name := "risa-3"

version := "0.0.1"

scalaVersion := "2.12.3"

val slickVersion = "3.2.3"

libraryDependencies ++= Seq(
  // cassandra
  "com.outworkers" %% "phantom-dsl" % "2.13.0",

  // db
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-codegen" % slickVersion,
  "mysql" % "mysql-connector-java" % "8.0.11",

  // utils
  //  "org.slf4j" % "slf4j-simple" % "1.7.24",
  "org.json4s" %% "json4s-native" % "3.5.2",
  "com.github.nscala-time" %% "nscala-time" % "2.16.0",
  "com.typesafe" % "config" % "1.3.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",

  // test
  "com.github.seratch" %% "awscala" % "0.7.1" % Test,
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % Test,

  // akka streams
  "com.typesafe.akka" %% "akka-stream" % "2.5.13",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.13" % Test,

  // akka http
  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.1.3" % Test
)

assemblyMergeStrategy in assembly := {
  case ".properties" => MergeStrategy.first
  case "reference.conf" => MergeStrategy.concat
  case PathList("META-INF", xs@_*) => xs map (_.toLowerCase) match {
    case "manifest.mf" :: Nil | "index.list" :: Nil | "dependencies" :: Nil =>
      MergeStrategy.discard
    case ps@(x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
      MergeStrategy.discard
    case "io.netty.versions.properties" :: xs =>
      MergeStrategy.discard
    case "services" :: xs =>
      MergeStrategy.filterDistinctLines
    case "spring.schemas" :: Nil | "spring.handlers" :: Nil =>
      MergeStrategy.filterDistinctLines
    case _ => MergeStrategy.deduplicate
  }
  case _ => MergeStrategy.deduplicate
}


(fork in Test) := true

(javaOptions in Test) += JvmOptions.options

enablePlugins(SlickGen)
(sourceGenerators in Compile) := Seq(SlickGen.slickGenTask.taskValue) // auto scheme generate in compile time
