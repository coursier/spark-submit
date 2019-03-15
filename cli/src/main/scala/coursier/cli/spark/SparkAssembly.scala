package coursier.cli.spark

import java.io.{File, FileOutputStream}
import java.math.BigInteger
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardCopyOption}
import java.security.MessageDigest
import java.util.jar.JarFile

import coursier.{Dependency, moduleString}
import coursier.bootstrap.Assembly
import coursier.cache.{CacheChecksum, CacheLocks, FileCache}
import coursier.cli.options.CommonOptions
import coursier.core.{ModuleName, Type}
import coursier.params.ResolutionParams

object SparkAssembly {

  val assemblyRules = Seq[Assembly.Rule](
    Assembly.Rule.Append("META-INF/services/org.apache.hadoop.fs.FileSystem"),
    Assembly.Rule.Append("reference.conf"),
    Assembly.Rule.AppendPattern("META-INF/services/.*"),
    Assembly.Rule.Exclude("log4j.properties"),
    Assembly.Rule.Exclude(JarFile.MANIFEST_NAME),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[sS][fF]"),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[dD][sS][aA]"),
    Assembly.Rule.ExcludePattern("META-INF/.*\\.[rR][sS][aA]")
  )

  def sparkBaseDependencies(
    scalaVersion: String,
    sparkVersion: String,
    yarnVersion: String
  ): Seq[Dependency] =
    if (sparkVersion.startsWith("2.")) {
      
      val sparkModules = Seq(
        mod"org.apache.spark:spark-hive-thriftserver",
        mod"org.apache.spark:spark-repl",
        mod"org.apache.spark:spark-hive",
        mod"org.apache.spark:spark-graphx",
        mod"org.apache.spark:spark-mllib",
        mod"org.apache.spark:spark-streaming",
        mod"org.apache.spark:spark-yarn",
        mod"org.apache.spark:spark-sql"
      ).map(m => m.copy(name = ModuleName(m.name + "_" + scalaVersion)))
      
      val yarnModules = Seq(
        mod"org.apache.hadoop:hadoop-client",
        mod"org.apache.hadoop:hadoop-yarn-server-web-proxy",
        mod"org.apache.hadoop:hadoop-yarn-server-nodemanager"
      )

      sparkModules.map(m => Dependency(m, sparkVersion)) ++
        yarnModules.map(m => Dependency(m, yarnVersion))
    } else {
      val sparkModules = Seq(
        mod"org.apache.spark:spark-core",
        mod"org.apache.spark:spark-bagel",
        mod"org.apache.spark:spark-mllib",
        mod"org.apache.spark:spark-streaming",
        mod"org.apache.spark:spark-graphx",
        mod"org.apache.spark:spark-sql",
        mod"org.apache.spark:spark-repl",
        mod"org.apache.spark:spark-yarn"
      ).map(m => m.copy(name = ModuleName(m.name + "_" + scalaVersion)))

      sparkModules.map(m => Dependency(m, sparkVersion))
    }

  def sparkJars(
    scalaVersion: String,
    sparkVersion: String,
    yarnVersion: String,
    default: Boolean,
    extraDependencies: Seq[Dependency],
    options: CommonOptions,
    artifactTypes: Set[Type]
  ): Seq[File] =
    coursier.Fetch()
      .addDependencies((if (default) sparkBaseDependencies(scalaVersion, sparkVersion, yarnVersion) else Nil): _*)
      .addDependencies(extraDependencies: _*)
      .withResolutionParams(
        ResolutionParams()
          .withScalaVersion(scalaVersion)
      )
      .run()

  def spark(
    scalaVersion: String,
    sparkVersion: String,
    yarnVersion: String,
    default: Boolean,
    extraDependencies: Seq[Dependency],
    options: CommonOptions,
    artifactTypes: Set[Type],
    checksumSeed: Array[Byte] = "v1".getBytes(UTF_8),
    localArtifactsShouldBeCached: Boolean = false
  ): Either[String, (File, Seq[File])] = {

    val fetch = coursier.Fetch()
      .addDependencies((if (default) sparkBaseDependencies(scalaVersion, sparkVersion, yarnVersion) else Nil): _*)
      .addDependencies(extraDependencies: _*)
      .withResolutionParams(
        ResolutionParams()
          .withScalaVersion(scalaVersion)
      )
    val cache = FileCache().location // FIXME Get from fetch instead
    val (res, artifacts0) = fetch.runResult()

    val artifacts = artifacts0.map(_._1)
    val jars = artifacts0.map(_._2)

    val checksums = artifacts.map { a =>
      val f = a.checksumUrls.get("SHA-1") match {
        case Some(url) =>
          FileCache.localFile0(url, cache, a.authentication.map(_.user), localArtifactsShouldBeCached)
        case None =>
          throw new Exception(s"SHA-1 file not found for ${a.url}")
      }

      val sumOpt = CacheChecksum.parseRawChecksum(Files.readAllBytes(f.toPath))

      sumOpt match {
        case Some(sum) =>
          val s = sum.toString(16)
          "0" * (40 - s.length) + s
        case None =>
          throw new Exception(s"Cannot read SHA-1 sum from $f")
      }
    }


    val md = MessageDigest.getInstance("SHA-1")

    md.update(checksumSeed)

    for (c <- checksums.sorted) {
      val b = c.getBytes(UTF_8)
      md.update(b, 0, b.length)
    }

    val digest = md.digest()
    val calculatedSum = new BigInteger(1, digest)
    val s = calculatedSum.toString(16)

    val sum = "0" * (40 - s.length) + s

    val cacheDir = new File(s"${sys.props("user.home")}/.coursier/spark-assemblies")

    val destPath = s"scala_${scalaVersion}_spark_$sparkVersion/$sum/spark-assembly.jar"
    val dest = new File(cacheDir, destPath)

    def success = Right((dest, jars))

    if (dest.exists())
      success
    else
      CacheLocks.withLockFor(cacheDir, dest) {
        dest.getParentFile.mkdirs()
        val tmpDest = new File(dest.getParentFile, s".${dest.getName}.part")
        // FIXME Acquire lock on tmpDest
        var fos: FileOutputStream = null
        try {
          fos = new FileOutputStream(tmpDest)
          Assembly.make(jars, fos, Nil, assemblyRules)
        } finally {
          if (fos != null)
            fos.close()
        }
        Files.move(tmpDest.toPath, dest.toPath, StandardCopyOption.ATOMIC_MOVE)
        Right((dest, jars))
      }.left.map(_.describe)
  }

}
