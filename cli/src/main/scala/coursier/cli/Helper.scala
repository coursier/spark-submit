package coursier.cli

import java.io.InputStream
import java.net.URL
import java.util.jar.{Manifest => JManifest}

import coursier.core.Dependency

import scala.annotation.tailrec

object Helper {

  def baseLoader: ClassLoader = {

    @tailrec
    def rootLoader(cl: ClassLoader): ClassLoader =
      Option(cl.getParent) match {
        case Some(par) => rootLoader(par)
        case None => cl
      }

    rootLoader(ClassLoader.getSystemClassLoader)
  }

  private def manifestPath = "META-INF/MANIFEST.MF"

  def mainClasses(cl: ClassLoader): Map[(String, String), String] = {
    import scala.collection.JavaConverters._

    val parentMetaInfs = Option(cl.getParent).fold(Set.empty[URL]) { parent =>
      parent.getResources(manifestPath).asScala.toSet
    }
    val allMetaInfs = cl.getResources(manifestPath).asScala.toVector

    val metaInfs = allMetaInfs.filterNot(parentMetaInfs)

    val mainClasses = metaInfs.flatMap { url =>
      var is: InputStream = null
      val attributes =
        try {
          is = url.openStream()
          new JManifest(is).getMainAttributes
        } finally {
          if (is != null)
            is.close()
        }

      def attributeOpt(name: String) =
        Option(attributes.getValue(name))

      val vendor = attributeOpt("Implementation-Vendor-Id").getOrElse("")
      val title = attributeOpt("Specification-Title").getOrElse("")
      val mainClass = attributeOpt("Main-Class")

      mainClass.map((vendor, title) -> _)
    }

    mainClasses.toMap
  }

  def retainedMainClass(loader: ClassLoader, mainDependencyOpt: Option[Dependency]): Option[String] = {

    val mainClasses0 = mainClasses(loader)

    if (mainClasses0.size == 1)
      Some(mainClasses0.head._2)
    else {

      // Trying to get the main class of the first artifact
      val mainClassOpt = for {
        dep <- mainDependencyOpt
        module = dep.module
        mainClass <- mainClasses0.collectFirst {
          case ((org, name), mainClass)
            if org == module.organization.value && (
              module.name.value == name ||
                module.name.value.startsWith(name + "_") // Ignore cross version suffix
              ) =>
            mainClass
        }
      } yield mainClass

      def sameOrgOnlyMainClassOpt = for {
        dep <- mainDependencyOpt
        orgMainClasses = mainClasses0
          .collect {
            case ((org, _), mainClass) if org == dep.module.organization.value =>
              mainClass
          }
          .toSet
        if orgMainClasses.size == 1
      } yield orgMainClasses.head

      mainClassOpt.orElse(sameOrgOnlyMainClassOpt)
    }
  }
}
