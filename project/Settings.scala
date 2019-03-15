
import sbt._
import sbt.Keys._

object Settings {

  def scala212 = "2.12.8"

  lazy val shared = Seq(
    scalaVersion := scala212,
    scalacOptions ++= Seq(
      "-target:jvm-1.8"
    ),
    javacOptions ++= Seq(
      "-source", "1.8",
      "-target", "1.8"
    )
  )

}
