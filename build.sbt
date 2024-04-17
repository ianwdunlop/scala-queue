lazy val scala_2_13 = "2.13.12"

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

val configVersion = "1.4.2"
val scalaLoggingVersion = "3.9.4"
val playWsStandaloneVersion = "2.1.10"
val akkaVersion = "2.8.1"
val scalaTestVersion = "3.2.15"
val scopedFixturesVersion = "2.0.0"
val monixVersion = "3.4.0"

lazy val creds = {
  sys.env.get("CI_JOB_TOKEN") match {
    case Some(token) =>
      Credentials("GitLab Packages Registry", "gitlab.com", "gitlab-ci-token", token)
    case _ =>
      Credentials(Path.userHome / ".sbt" / ".credentials")
  }
}

val registryId = sys.env.get("REGISTRY_HOST_PROJECT_ID")

lazy val publishSettings = Seq(
  publishTo := {
    Some("gitlab" at s"https://gitlab.com/api/v4/projects/$registryId/packages/maven")
  },
  credentials += creds
)

lazy val root = (project in file("."))
  .settings(
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
    resolvers += ("gitlab" at s"https://gitlab.com/api/v4/projects/$registryId/packages/maven"),
    credentials += creds,
    dependencyOverrides += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
    libraryDependencies ++= {
      Seq(
        "com.typesafe" % "config"                        % configVersion,
        "com.typesafe.scala-logging" %% "scala-logging"  % scalaLoggingVersion,
        "com.typesafe.play" %% "play-ahc-ws-standalone"  % playWsStandaloneVersion,
        "com.typesafe.play" %% "play-ws-standalone-json" % playWsStandaloneVersion,
        "com.lightbend.akka" %% "akka-stream-alpakka-amqp" % "6.0.1",
        "com.typesafe.akka" %% "akka-stream"             % akkaVersion,
      )
    }
).
settings(
  publishSettings: _*
)

lazy val it = project
.in(file("it"))  //it test located in a directory named "it"
.settings(
  name := "queue-it",
  scalaVersion := "2.13.12",
  libraryDependencies ++= {
    Seq(
      "com.typesafe.akka" %% "akka-testkit"            % akkaVersion,
      "com.typesafe.akka" %% "akka-actor"              % akkaVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "io.monix" %% "monix"                            % monixVersion
        )
    }
  )
  .dependsOn(root)