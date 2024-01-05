ThisBuild / organization := "cc.blackquill"
ThisBuild / scalaVersion := "3.3.1"
ThisBuild / packageTimestamp := Package.keepTimestamps

val http4sVersion = "1.0.0-M40"

lazy val root = (project in file("."))
.enablePlugins(ScalaJSPlugin)
.settings(
  name := "shades",
  exportJars := true,
  scalaJSUseMainModuleInitializer := true,
  libraryDependencies ++= Seq(
    // "core" module - IO, IOApp, schedulers
    // This pulls in the kernel and std modules automatically.
    "org.typelevel" %% "cats-effect" % "3.3.12",
    // concurrency abstractions and primitives (Concurrent, Sync, Async etc.)
    "org.typelevel" %% "cats-effect-kernel" % "3.3.12",
    // standard "effect" library (Queues, Console, Random etc.)
    "org.typelevel" %% "cats-effect-std" % "3.3.12",
    "co.fs2" %% "fs2-core" % "3.9.3",
    "co.fs2" %% "fs2-io" % "3.9.3",
    "org.http4s" %% "http4s-ember-client" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.typelevel" %% "log4cats-slf4j" % "2.6.0",
    "io.circe" %% "circe-generic" % "0.14.6",
    "io.circe" %% "circe-testing" % "0.14.6",
    "org.scalacheck" %% "scalacheck" % "1.17.0",
    "org.typelevel" %% "discipline-core" % "1.5.0",
    "com.armanbilge" %%% "calico" % "0.2.2",
    "org.typelevel" %%% "cats-parse" % "0.3.9",
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,
    "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test,
    "org.typelevel" %% "discipline-munit" % "2.0.0-M2" % Test,
    "org.typelevel" %% "discipline-munit" % "2.0.0-M2" % Test,
  ),
  Compile / sourceGenerators += Def.task {
    val dataFile = baseDirectory.value / "data.tsv"

    val sourceDir = (Compile / sourceManaged).value
    val sourceFile = sourceDir / "Data.scala"

    if (!sourceFile.exists() ||
        sourceFile.lastModified() < dataFile.lastModified()) {
      val content = IO.read(dataFile).replaceAllLiterally("$", "$$")

      val scalaCode =
        s"""
        package Shades

        object Data {
          final val content = raw\"\"\"$content\"\"\"
        }
        """

      IO.write(sourceFile, scalaCode)
    }

    Seq(sourceFile)
  }.taskValue
)
