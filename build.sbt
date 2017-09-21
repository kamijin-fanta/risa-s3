name := "risa-3"

version := "0.0.1"

scalaVersion := "2.12.3"


libraryDependencies ++= Seq(
  // cassandra
  "com.outworkers"  %% "phantom-dsl" % "2.13.0",
  "com.stratio.cassandra" % "cassandra-lucene-index-builder" % "3.11.0.0",

  // utils
//  "org.slf4j" % "slf4j-simple" % "1.7.24",
  "org.json4s" %% "json4s-native" % "3.5.2",
  "com.github.nscala-time" %% "nscala-time" % "2.16.0",
  "com.typesafe" % "config" % "1.3.1",
  "com.github.seratch" %% "awscala" % "0.6.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  // test
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % Test,

  // akka streams
  "com.typesafe.akka" %% "akka-stream" % "2.5.3",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.3" % Test,

  // akka http
  "com.typesafe.akka" %% "akka-http" % "10.0.10",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.10" % Test
)


(fork in Test) := true
