package coursier.cli.spark

import java.io.File

import coursier.{Dependency, moduleString}
import coursier.cli.options.CommonOptions
import coursier.core.{ModuleName, Type}
import coursier.params.ResolutionParams

object Submit {

  def cp(
    scalaVersion: String,
    sparkVersion: String,
    noDefault: Boolean,
    extraDependencies: Seq[Dependency],
    artifactTypes: Set[Type],
    common: CommonOptions
  ): Seq[File] = {

    var extraCp = Seq.empty[File]

    for (yarnConf <- sys.env.get("YARN_CONF_DIR") if yarnConf.nonEmpty) {
      val f = new File(yarnConf)

      if (!f.isDirectory) {
        Console.err.println(s"Error: YARN conf path ($yarnConf) is not a directory or doesn't exist.")
        sys.exit(1)
      }

      extraCp = extraCp :+ f
    }

    val defaultDependencies = Seq(
      mod"org.apache.spark:spark-core",
      mod"org.apache.spark:spark-yarn"
    ).map(m => Dependency(m.copy(name = ModuleName(m.name.value + "_" + scalaVersion)), sparkVersion))

    val jars = coursier.Fetch()
      .withDependencies(if (noDefault) Nil else defaultDependencies)
      .addDependencies(extraDependencies: _*)
      .withResolutionParams(
        ResolutionParams()
          .withScalaVersion(scalaVersion)
      )
      .run()

    jars ++ extraCp
  }

  def mainClassName = "org.apache.spark.deploy.SparkSubmit"

}
