package com.ibm.plain

package lib

package http

import java.nio.ByteBuffer

import aio.Io
import aio.Renderable
import aio.Renderable._
import Status.ServerError.`505`
import text.ASCII

/**
 * Supported http versions. The current implementation only supports HTTP/1.1.
 */
sealed abstract class Version

  extends Renderable {

  final val version = toString.getBytes(ASCII)

  final def render(implicit io: Io) = r(version) + ^

}

object Version {

  final def apply(version: String)(implicit server: Server): Version = version match {
    case "HTTP/1.0" if server.settings.treat10VersionAs11 ⇒ `HTTP/1.1`
    case "HTTP/1.1" ⇒ `HTTP/1.1`
    case _ ⇒ if (server.settings.treatAnyVersionAs11)
      `HTTP/1.1`
    else {
      throw `505`
    }
  }

  /**
   * We implement support for HTTP/1.1 only, but eventually allow 1.0 and treat it like 1.1.
   */
  case object `HTTP/1.0` extends Version
  case object `HTTP/1.1` extends Version

}