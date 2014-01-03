name := "uns2"

version := "1.0"

organization := "com.wcs"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-optimize", "-Yinline-warnings")

resolvers ++= Seq(
  "sprayrepo" at "http://repo.spray.io",
  "snapshots" at "http://repo.akka.io/snapshots",
  "releases"  at "http://repo.akka.io/releases",
  "typesafe"  at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2.3"
, "com.typesafe.akka" %% "akka-camel" % "2.2.3"
, "com.typesafe.akka" %% "akka-kernel" % "2.2.3"
, "com.typesafe.akka" %% "akka-slf4j" % "2.2.3"
, "org.apache.camel" % "camel-core" % "2.10.3"
, "org.apache.camel" % "camel-jms" % "2.10.3"
, "org.apache.activemq" % "activemq-core" % "5.7.0"
, "org.apache.activemq" % "activemq-camel" % "5.7.0"
, "org.apache.commons" % "commons-email" % "1.3.1"
, "io.spray" % "spray-http" % "1.2.0"
, "io.spray" % "spray-can" % "1.2.0"
, "io.spray" % "spray-routing" % "1.2.0"
, "org.mongodb" %% "casbah" % "2.6.1"
, "io.argonaut" %% "argonaut" % "6.0.1"
, "ch.qos.logback" % "logback-classic" % "1.0.13")

//unmanagedBase <<= baseDirectory { base => base / "lib" }

