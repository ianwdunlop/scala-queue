lazy val scala_2_13 = "2.13.12"

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

val configVersion = "1.4.2"
val scalaLoggingVersion = "3.9.4"
val playWsStandaloneVersion = "3.0.2"
val pekkoVersion = "1.0.2"
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

// Registry ID is the project ID of the project where the package is published, this should be set in the CI/CD environment
val registryId = sys.env.get("REGISTRY_HOST_PROJECT_ID").getOrElse("")

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
        "org.playframework" %% "play-ahc-ws-standalone"  % playWsStandaloneVersion,
        "org.playframework" %% "play-ws-standalone-json" % playWsStandaloneVersion,
        "org.apache.pekko" %% "pekko-connectors-amqp"    % "1.0.2",
        "org.apache.pekko" %% "pekko-stream"             % pekkoVersion,
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
      "org.apache.pekko" %% "pekko-testkit"  % pekkoVersion,
      "org.apache.pekko" %% "pekko-actor"    % pekkoVersion,
      "org.scalatest" %% "scalatest"         % scalaTestVersion,
      "org.apache.pekko" %% "pekko-slf4j"    % pekkoVersion,
      "io.monix" %% "monix"                  % monixVersion
        )
    }
  )
  .dependsOn(root)