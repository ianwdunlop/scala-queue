lazy val Scala212 = "2.12.8"
lazy val Scala211 = "2.11.12"
lazy val Scala210 = "2.10.7"

lazy val opRabbitVersion = "2.1.0"
lazy val configVersion = "1.3.2"

lazy val root = (project in file(".")).
  settings(
    name                := "queue",
    organization        := "io.mdcatapult.klein",
    scalaVersion        := Scala212,
    crossScalaVersions  := Scala212 :: Scala211 :: Scala210 :: Nil,
    version             := "0.0.5",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest"                  % "3.0.3" % Test,
      "com.spingo" %% "op-rabbit-core"                % opRabbitVersion,
      "com.spingo" %% "op-rabbit-play-json"           % opRabbitVersion,
      "com.spingo" %% "op-rabbit-json4s"              % opRabbitVersion,
      "com.spingo" %% "op-rabbit-airbrake"            % opRabbitVersion,
      "com.spingo" %% "op-rabbit-akka-stream"         % opRabbitVersion,
      "ch.qos.logback" % "logback-classic"            % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
      "com.typesafe" % "config"                        % configVersion,
      "com.typesafe.play" %% "play-ahc-ws-standalone"  % "2.1.0-M2",
      "com.typesafe.play" %% "play-ws-standalone-json" % "2.1.0-M2"
    )
  ).
  settings(
    publishSettings: _*
  )

lazy val publishSettings = Seq(
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("MDC Nexus" at "https://nexus.mdcatapult.io/repository/maven-snapshots/;build.timestamp=" + new java.util.Date().getTime)
    else
      Some("MDC Nexus" at "https://nexus.mdcatapult.io/repository/maven-releases/")
  },
  credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
)

