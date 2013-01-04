package com.ibm

package plain

package rest

package resource

import java.nio.file.Files._
import java.nio.file.Paths

import com.typesafe.config.{ Config, ConfigFactory }

import config.CheckedConfig
import http.{ Request, Response }
import http.Status._
import logging.HasLogger

/**
 *
 */
class DirectoryResource

  extends Resource {

  import DirectoryResource._

  // needs more thinking first, configuration must only be parsed once
  def handle2(request: Request, context: Context): Nothing = {
    import settings._
    rootDirectory.resolve(context.remainder.mkString("/")) match {
      case file if exists(file) && isRegularFile(file) ⇒
        println("size " + size(file));
        completed(Response(Success.`200`), context)
      case file if exists(file) ⇒ throw ClientError.`406`
      case _ ⇒ throw ClientError.`404`
    }
  }

  private[this] val settings = DirectoryResourceConfiguration("plain")

}

/**
 *
 */
object DirectoryResource

  extends HasLogger {

  /**
   * A per-resource provided configuration.
   */
  final case class DirectoryResourceConfiguration(

    path: String)

    extends CheckedConfig {

    import DirectoryResourceConfiguration._

    override def handleError(e: Throwable) = {
      error(e.toString)
    }

    final val cfg: Config = config.settings.getConfig(path).withFallback(fallback)

    import cfg._

    final val displayName = getString("display-name")

    final val rootDirectory = Paths.get(getString("root-directory"))

    require(exists(rootDirectory), "directory-resource.root-directory does not exist : " + rootDirectory)

  }

  object DirectoryResourceConfiguration {

    final val fallback = ConfigFactory.parseString("""
        
    display-name = default
        
    root-directory = "/Users/guido/Development/Projects/plain"
        
    """)

  }

}
