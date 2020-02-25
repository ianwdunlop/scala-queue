lazy val scala_2_12 = "2.12.10"

lazy val opRabbitVersion = "2.1.0"
lazy val configVersion = "1.3.2"
lazy val playVersion = "2.0.7"
lazy val akkaVersion = "2.5.26"

lazy val IntegrationTest = config("it") extend Test
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    name                := "queue",
    organization        := "io.mdcatapult.klein",
    scalaVersion        := scala_2_12,
    crossScalaVersions  := scala_2_12 :: Nil,
    useCoursier := false,
    scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-unchecked",
      "-deprecation",
      "-explaintypes",
      "-feature",
      "-Xlint",
    ),
    resolvers         ++= Seq(
      "MDC Nexus Releases" at "https://nexus.mdcatapult.io/repository/maven-releases/",
      "MDC Nexus Snapshots" at "https://nexus.mdcatapult.io/repository/maven-snapshots/"),
    credentials       += {
      val nexusPassword = sys.env.get("NEXUS_PASSWORD")
      if ( nexusPassword.nonEmpty ) {
        Credentials("Sonatype Nexus Repository Manager", "nexus.mdcatapult.io", "gitlab", nexusPassword.get)
      } else {
        Credentials(Path.userHome / ".sbt" / ".credentials")
      }
    },
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest"                  % "3.1.0" % "it,test",
      "com.spingo" %% "op-rabbit-core"                % opRabbitVersion,
      "com.spingo" %% "op-rabbit-play-json"           % opRabbitVersion,
      "com.spingo" %% "op-rabbit-json4s"              % opRabbitVersion,
      "com.spingo" %% "op-rabbit-airbrake"            % opRabbitVersion,
      "com.spingo" %% "op-rabbit-akka-stream"         % opRabbitVersion,
      "com.spingo" %% "scoped-fixtures"         % "2.0.0" % "it,test",
      "ch.qos.logback" % "logback-classic"            % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
      "com.typesafe" % "config"                        % configVersion,
      "com.typesafe.play" %% "play-ahc-ws-standalone"  % playVersion,
      "com.typesafe.play" %% "play-ws-standalone-json" % playVersion,
      "com.typesafe.akka" %% "akka-actor"             % akkaVersion % "it,test",
      "com.typesafe.akka" %% "akka-slf4j"             % akkaVersion % "it,test",
    )
  ).
  settings(
    publishSettings: _*
  )

lazy val publishSettings = Seq(
  publishTo := {
    if (isSnapshot.value)
      Some("MDC Maven Repo" at "https://nexus.mdcatapult.io/repository/maven-snapshots/")
    else
      Some("MDC Nexus" at "https://nexus.mdcatapult.io/repository/maven-releases/")
  },
  credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
)
