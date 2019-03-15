
import Settings._

inThisBuild(List(
  organization := "io.get-coursier",
  homepage := Some(url("https://github.com/coursier/spark-submit")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "alexarchambault",
      "Alexandre Archambault",
      "alexandre.archambault@gmail.com",
      url("https://github.com/alexarchambault")
    )
  )
))

lazy val cli = project
  .enablePlugins(PackPlugin)
  .settings(
    shared,
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "case-app" % "2.0.0-M6",
      "io.get-coursier" %% "coursier" % "1.1.0-M13-1",
      "io.get-coursier" %% "coursier-bootstrap" % "1.1.0-M13-1",
      "com.lihaoyi" %% "utest" % "0.6.6" % Test
    ),
    fork.in(Test) := true,
    testFrameworks += new TestFramework("utest.runner.Framework"),
    javaOptions.in(Test) ++= Seq("-Xmx3g")
  )

skip.in(publish) := true
