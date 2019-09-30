lazy val Scala212 = "2.12.8"
lazy val Scala211 = "2.11.12"
lazy val Scala210 = "2.10.7"

lazy val opRabbitVersion = "2.1.0"
lazy val configVersion = "1.3.2"
lazy val playVersion = "2.0.7"

lazy val root = (project in file(".")).
  settings(
    name                := "queue",
    organization        := "io.mdcatapult.klein",
    scalaVersion        := Scala212,
    crossScalaVersions  := Scala212 :: Scala211 :: Scala210 :: Nil,
    version             := "0.0.9",
    scalacOptions += "-Ypartial-unification",
    resolvers         ++= Seq("MDC Nexus" at "https://nexus.mdcatapult.io/repository/maven-releases/"),
    credentials       += {
      val nexusPassword = sys.env.get("NEXUS_PASSWORD")
      if ( nexusPassword.nonEmpty ) {
        Credentials("Sonatype Nexus Repository Manager", "nexus.mdcatapult.io", "gitlab", nexusPassword.get)
      } else {
        Credentials(Path.userHome / ".sbt" / ".credentials")
      }
    },
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
      "com.typesafe.play" %% "play-ahc-ws-standalone"  % playVersion,
      "com.typesafe.play" %% "play-ws-standalone-json" % playVersion
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

