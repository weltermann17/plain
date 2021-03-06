package com.ibm

package plain

import scala.collection.JavaConversions._
import scala.collection.JavaConversions.asScalaBuffer

import config.settings.{ getConfig, getString, getStringList }

package object logging

    extends config.CheckedConfig {

  import config._
  import config.settings._

  final def createLogger(any: Any) = Logging.instance.createLogger(any)

  final def createLogger(name: String) = Logging.instance.createLogger(name)

  final def defaultLogger = Logging.instance.createLogger(Logging.instance.name)

  /**
   * Enable "Disruptor" technology for log4j2.
   */
  final val useAsyncLogging = {
    System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector")
    System.setProperty("AsyncLogger.WaitStrategy", "Block")
    System.setProperty("AsyncLogger.RingBufferSize", (32 * 1024).toString)
    true
  }

  final def setLoggingLevel(level: String): Unit = try {
    canonicalLevel(level) match {
      case value ⇒
        Logging.instance.setLevel(value)
        System.setProperty("rootLevel", value)
        logginglevel = value
    }
  } catch {
    case e: Throwable ⇒ defaultLogger.error("Failed to set the logging level to '" + level + "' : " + e)
  }

  final def loggingLevel = logginglevel

  private[logging] final def canonicalLevel(level: String) = level.toUpperCase match { case "WARNING" ⇒ "WARN" case level ⇒ level }

  private[this] final def getLevel(path: String, default: String, propertyname: String) = canonicalLevel(getString(path, default)) match {
    case value ⇒
      System.setProperty(propertyname, value)
      value
  }

  @volatile private[this] final var logginglevel = getLevel("plain.logging.level", "TRACE", "rootLevel")

  final val metaLoggingLevel = getLevel("plain.logging.meta-level", "TRACE", "metaLevel")

  final val loggingConsole = LogSettings("plain.logging.console")

  final val loggingFile = LogSettings("plain.logging.file")

  final val loggingHtml = LogSettings("plain.logging.html")

  final val filterDebugNames: List[String] = try {
    getStringList("plain.logging.filter-debug-names").toList
  } catch {
    case _: Throwable ⇒ List.empty
  }

  final case class LogSettings(path: String) {

    private[this] val cfg = getConfig(path)

    final def enabled = cfg.getBoolean("enabled", false)

    final def fileName = cfg.getString("file-name", "")

    final def pattern = cfg.getString("pattern", "")

    final def rollingPattern = cfg.getString("rolling-pattern", "")

    System.setProperty(path + ".enabled", enabled.toString)

    final val toFile = if (enabled) cfg.getString("file-name", "") match {
      case "" | "." | null ⇒ false
      case v ⇒
        val logfile = new java.io.File(v)
        logfile.toPath.getParent.toFile.mkdirs
        System.setProperty(path + ".file-name", v)
        true
    }
    else false

    if (enabled) cfg.getString("pattern", "") match {
      case "" | null ⇒
      case v ⇒ System.setProperty(path + ".pattern", v)
    }

    if (enabled) cfg.getString("rolling-pattern", "") match {
      case "" | null ⇒
      case v ⇒ System.setProperty(path + ".rolling-pattern", v)
    }

    override final def toString = System.getProperties.propertyNames.toSeq.filter(_.toString.startsWith(path)).map(n ⇒ n + "=" + System.getProperty(n.toString)).mkString(", ")

  }

}
