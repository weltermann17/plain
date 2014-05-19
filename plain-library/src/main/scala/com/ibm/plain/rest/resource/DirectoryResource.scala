package com.ibm

package plain

package rest

package resource

import java.nio.file.{ Path, Paths }
import java.nio.file.Files.{ exists ⇒ fexists, isDirectory, isRegularFile, readAllBytes, size ⇒ fsize }

import org.apache.commons.io.FilenameUtils.getExtension
import org.apache.commons.io.filefilter.RegexFileFilter

import com.ibm.plain.aio.Exchange
import com.ibm.plain.rest.{ Context, Resource }
import com.typesafe.config.Config

import scala.collection.JavaConversions.asScalaBuffer
import scala.language.implicitConversions
import scala.reflect.runtime.universe

import aio.conduits.FileConduit.forReading
import aio.Exchange
import http.{ ContentType, Entity }
import http.Entity.{ ArrayEntity, AsynchronousByteChannelEntity }
import http.MimeType.{ `application/octet-stream`, `application/tar`, forExtension }
import http.Status.ClientError
import logging.Logger

/**
 *
 */
final class DirectoryResource

  extends Resource {

  import DirectoryResource._

  Get { get(context.config.getStringList("roots"), context.remainder.mkString("/"), exchange) }

  Get { _: String ⇒ get(context.config.getStringList("roots"), context.remainder.mkString("/"), exchange) }

  //  Get { _: String ⇒ getZipFile(exchange) }

}

/**
 *
 */
object DirectoryResource

  extends Logger {

  private final def get(list: Seq[String], remainder: String, exchange: Exchange[Context]) = {
    val roots = list.iterator
    var found = false
    var result: Entity = null
    def entity(path: Path): Entity = {
      found = true
      val length = fsize(path)
      val contenttype = ContentType(forExtension(getExtension(path.toString)).getOrElse(`application/octet-stream`))
      if (length <= exchange.available) {
        ArrayEntity(readAllBytes(path), contenttype)
      } else {
        val source = forReading(path)
        exchange.transferFrom(source)
        AsynchronousByteChannelEntity(
          source,
          contenttype,
          length,
          contenttype.mimetype.encodable)
      }
    }
    while (!found && roots.hasNext) {
      val root = roots.next
      result = Paths.get(root).toAbsolutePath.resolve(remainder) match {
        case path if path.toString.contains("..") ⇒ throw ClientError.`406`
        case path if fexists(path) && isRegularFile(path) ⇒ entity(path)
        case path if fexists(path) && isDirectory(path) ⇒
          path.toFile.listFiles(welcomefilter).filter(f ⇒ f.exists && f.isFile).headOption match {
            case Some(file) ⇒
              trace("Matched welcomefile : " + file)
              entity(file.toPath)
            case _ ⇒ throw ClientError.`406`
          }
        case _ ⇒ null
      }
    }
    if (!found) {
      trace("Not found: " + remainder + "; " + roots.mkString(", "))
      throw ClientError.`404`
    } else {
      result
    }
  }

  private final def getZipFile(exchange: Exchange[Context]) = {
    val source = aio.conduits.TarConduit(new java.io.File("/Users/guido/Development/Others/spray"))
    val contenttype = ContentType(`application/tar`)
    exchange.transferFrom(source)
    AsynchronousByteChannelEntity(
      source,
      contenttype,
      -1,
      contenttype.mimetype.encodable)

  }

  private[this] final val welcomefiles = "([iI]ndex\\.((htm[l]*)|(jsp)))|([dD]efault\\.((htm[l]*)|(jsp)))"

  private[this] final val welcomefilter: java.io.FileFilter = new RegexFileFilter(welcomefiles)

}
