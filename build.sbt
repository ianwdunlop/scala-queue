lazy val scala_2_13 = "2.13.2"

lazy val IntegrationTest = config("it") extend Test
concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    name                := "queue",
    organization        := "io.mdcatapult.klein",
    scalaVersion        := scala_2_13,
    crossScalaVersions  := scala_2_13 :: Nil,
    useCoursier := false,
    scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-unchecked",
      "-deprecation",
      "-explaintypes",
      "-feature",
      "-Xlint",
      "-Xfatal-warnings",
    ),
    resolvers         ++= Seq(
      "MDC Nexus Releases" at "https://nexus.wopr.inf.mdc/repository/maven-releases/",
      "MDC Nexus Snapshots" at "https://nexus.wopr.inf.mdc/repository/maven-snapshots/"),
    credentials       += {
      sys.env.get("NEXUS_PASSWORD") match {
        case Some(p) =>
          Credentials("Sonatype Nexus Repository Manager", "nexus.wopr.inf.mdc", "gitlab", p)
        case None =>
          Credentials(Path.userHome / ".sbt" / ".credentials")
      }
    },
    libraryDependencies ++= {
      val kleinUtilVersion = "1.2.4"

      val opRabbitVersion = "2.6.4"
      val configVersion = "1.4.1"
      val playWsStandaloneVersion = "2.1.7"
      val akkaVersion = "2.6.18"
      val scalaTestVersion = "3.2.11"
      val scopedFixturesVersion = "2.0.0"

      Seq(
        "io.mdcatapult.klein" %% "util"                  % kleinUtilVersion,

        "org.scalatest" %% "scalatest"                   % scalaTestVersion % "it,test",
        "com.github.pjfanning" %% "op-rabbit-core"       % opRabbitVersion,
        "com.github.pjfanning" %% "op-rabbit-play-json"  % opRabbitVersion,
        "com.github.pjfanning" %% "op-rabbit-json4s"     % opRabbitVersion,
        "com.github.pjfanning" %% "op-rabbit-airbrake"   % opRabbitVersion,
        "com.spingo" %% "scoped-fixtures"                % scopedFixturesVersion % "it,test",
        "com.typesafe" % "config"                        % configVersion,
        "com.typesafe.play" %% "play-ahc-ws-standalone"  % playWsStandaloneVersion,
        "com.typesafe.play" %% "play-ws-standalone-json" % playWsStandaloneVersion,
        "com.typesafe.akka" %% "akka-actor"              % akkaVersion % "it,test",
        "com.typesafe.akka" %% "akka-slf4j"              % akkaVersion % "it,test",
        "com.typesafe.akka" %% "akka-protobuf"           % akkaVersion % "it,test",
        "com.typesafe.akka" %% "akka-stream"             % akkaVersion % "it,test",
        "com.typesafe.akka" %% "akka-testkit"            % akkaVersion % "it,test",
        "com.lightbend.akka" %% "akka-stream-alpakka-amqp" % "3.0.4",
        "org.json4s" %% "json4s-native" % "3.6.7"
      )
    }
  ).
  settings(
    publishSettings: _*
  )

lazy val publishSettings = Seq(
  publishTo := {
    if (isSnapshot.value)
      Some("MDC Maven Repo" at "https://nexus.wopr.inf.mdc/repository/maven-snapshots/")
    else
      Some("MDC Nexus" at "https://nexus.wopr.inf.mdc/repository/maven-releases/")
  },
  credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
)
