package com.ibm.plain
package config

import java.util.concurrent.TimeUnit.MILLISECONDS

import scala.collection.JavaConversions.{ asScalaBuffer, asScalaSet }
import scala.concurrent.duration.Duration

import com.typesafe.config.{ Config, ConfigList }

/**
 * Add a couple of helpers to Config eg. get with default.
 */
class RichConfig(config: Config)

    extends logging.Logger {

  def root = config.root

  def entrySet = config.entrySet

  /**
   * with logging
   */
  def getBoolean(key: String) = if (config.hasPath(key)) config.getBoolean(key) else { error(s"No configuration value found for key : $key"); config.getBoolean(key) }

  def getInt(key: String) = if (config.hasPath(key)) config.getInt(key) else { error(s"No configuration value found for key : $key"); config.getInt(key) }

  def getObject(key: String) = if (config.hasPath(key)) config.getObject(key) else { error(s"No configuration value found for key : $key"); config.getObject(key) }

  def getString(key: String) = if (config.hasPath(key)) config.getString(key) else { error(s"No configuration value found for key : $key"); config.getString(key) }

  def getStringList(key: String) = if (config.hasPath(key)) config.getStringList(key) else { error(s"No configuration value found for key : $key"); config.getStringList(key) }

  def getConfig(key: String) = if (config.hasPath(key)) config.getConfig(key) else { error(s"No configuration value found for key : $key"); config.getConfig(key) }

  def getConfigList(key: String) = if (config.hasPath(key)) config.getConfigList(key) else { error(s"No configuration value found for key : $key"); config.getConfigList(key) }

  /**
   * with default
   */
  def getString(key: String, default: String) = if (config.hasPath(key)) config.getString(key) else default

  def getStringList(key: String, default: List[String]): List[String] = if (config.hasPath(key)) config.getStringList(key).toList else default

  def getIntList(key: String, default: List[Int]): List[Int] = if (config.hasPath(key)) config.getIntList(key).toList.map(_.toInt) else default

  def getInt(key: String, default: Int) = if (config.hasPath(key)) config.getInt(key) else default

  def getMilliseconds(key: String, default: Long): Long = if (config.hasPath(key)) getDuration(key, Duration.Zero) else default

  def getBoolean(key: String, default: Boolean) = if (config.hasPath(key)) config.getBoolean(key) else default

  def getBytes(key: String, default: Long): Long = if (config.hasPath(key)) config.getBytes(key) else default

  def getDuration(key: String, default: Duration): Long = if (config.hasPath(key)) config.getDuration(key, MILLISECONDS) else default.toMillis

  def getInstanceFromClassName[A](key: String): A = Class.forName(config.getString(key)).newInstance.asInstanceOf[A]

  def getMap(key: String, default: Map[String, Config]): Map[String, Config] = if (config.hasPath(key)) {
    val c = config.getConfig(key)
    c.root.entrySet.toList.map(e ⇒ (e.getKey, c.getConfig(e.getKey))).toMap
  } else {
    default
  }

  def getConfigList(key: String, default: List[Config]): List[Config] = if (config.hasPath(key)) {
    config.getConfigList(key).toList
  } else {
    default
  }

  def getList(key: String, default: ConfigList): ConfigList = if (config.hasPath(key)) {
    config.getList(key)
  } else {
    default
  }

  def getConfig(key: String, default: Config): Config = if (config.hasPath(key)) {
    config.getConfig(key)
  } else {
    default
  }

  def toMap: Map[String, Any] = config.root.entrySet.map(e ⇒ (e.getKey, e.getValue.unwrapped)).toMap

}
