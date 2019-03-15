package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.core._
import coursier.params.ResolutionParams

final case class ResolutionOptions(

  @Help("Keep optional dependencies (Maven)")
    keepOptional: Boolean = false,

  @Help("Maximum number of resolution iterations (specify a negative value for unlimited, default: 100)")
  @Short("N")
    maxIterations: Int = ResolutionProcess.defaultMaxIterations,

  @Help("Force module version")
  @Value("organization:name:forcedVersion")
  @Short("V")
    forceVersion: List[String] = Nil,

  @Help("Force property in POM files")
  @Value("name=value")
    forceProperty: List[String] = Nil,

  @Help("Enable profile")
  @Value("profile")
  @Short("F")
    profile: List[String] = Nil,

  @Help("Default scala version")
  @Short("e")
    scalaVersion: Option[String] = None,

  @Help("Ensure the scala version used by the scala-library/reflect/compiler JARs is coherent, and adjust the scala version for fully cross-versioned dependencies")
    forceScalaVersion: Option[Boolean] = None,

  @Help("Swap the mainline Scala JARs by Typelevel ones")
    typelevel: Boolean = false,

  @Help("Enforce resolution rules")
  @Short("rule")
    rules: List[String] = Nil

) {

  def scalaVersionOrDefault: String =
    scalaVersion.getOrElse(ResolutionParams().selectedScalaVersion)
}

object ResolutionOptions {
  implicit val parser = Parser[ResolutionOptions]
  implicit val help = caseapp.core.help.Help[ResolutionOptions]
}
