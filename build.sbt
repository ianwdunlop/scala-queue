lazy val scala_2_13 = "2.13.12"

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
    resolvers += ("gitlab" at "https://gitlab.com/api/v4/projects/50550924/packages/maven"),
    credentials += {
      sys.env.get("GITLAB_PRIVATE_TOKEN") match {
        case Some(token) =>
          Credentials("GitLab Packages Registry", "gitlab.com", "Private-Token", token)
        case None =>
          Credentials(Path.userHome / ".sbt" / ".credentials")
//          Credentials("GitLab Packages Registry", "gitlab.com", "Job-Token", sys.env.get("CI_JOB_TOKEN").get)
      }
    },
    dependencyOverrides += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
    libraryDependencies ++= {
      val kleinUtilVersion = "1.2.6"

      val configVersion = "1.4.2"
      val playWsStandaloneVersion = "2.1.10"
      val akkaVersion = "2.8.1"
      val scalaTestVersion = "3.2.15"
      val scopedFixturesVersion = "2.0.0"

      Seq(
        "io.mdcatapult.klein" %% "util"                  % kleinUtilVersion,

        "org.scalatest" %% "scalatest"                   % scalaTestVersion % "it,test",
        "com.typesafe" % "config"                        % configVersion,
        "com.typesafe.play" %% "play-ahc-ws-standalone"  % playWsStandaloneVersion,
        "com.typesafe.play" %% "play-ws-standalone-json" % playWsStandaloneVersion,
        "com.typesafe.akka" %% "akka-actor"              % akkaVersion % "it,test",
        "com.typesafe.akka" %% "akka-slf4j"              % akkaVersion % "it,test",
        "com.typesafe.akka" %% "akka-stream"             % akkaVersion % "it,test",
        "com.typesafe.akka" %% "akka-testkit"            % akkaVersion % "it,test",
        "com.lightbend.akka" %% "akka-stream-alpakka-amqp" % "6.0.1"
      )
    }
  ).
  settings(
    publishSettings: _*
  )

lazy val publishSettings = Seq(
  publishTo := {
      Some("gitlab" at "https://gitlab.com/api/v4/projects/50550924/packages/maven")
  },
  credentials += {
    sys.env.get("CI_JOB_TOKEN") match {
      case Some(token) =>
        Credentials("GitLab Packages Registry", "gitlab.com", "Job-Token", sys.env.get("CI_JOB_TOKEN").get)
      case None =>
        Credentials(Path.userHome / ".sbt" / ".credentials")
    }
  }
)
