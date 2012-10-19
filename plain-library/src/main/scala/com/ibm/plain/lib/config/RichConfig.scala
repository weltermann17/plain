package com.ibm.plain

package lib

package config

import scala.collection.JavaConversions.asScalaBuffer
import scala.concurrent.util.Duration

import com.typesafe.config.Config

/**
 * Add a couple of helpers to Config eg. get with default.
 */
class RichConfig(config: Config) {

  def getString(key: String, default: String) = if (config.hasPath(key)) config.getString(key) else default

  def getStringList(key: String, default: List[String]): List[String] = if (config.hasPath(key)) config.getStringList(key).toList else default

  def getInt(key: String, default: Int) = if (config.hasPath(key)) config.getInt(key) else default

  def getMilliseconds(key: String, default: Long): Long = if (config.hasPath(key)) config.getMilliseconds(key) else default

  def getBoolean(key: String, default: Boolean) = if (config.hasPath(key)) config.getBoolean(key) else default

  def getBytes(key: String, default: Long): Long = if (config.hasPath(key)) config.getBytes(key) else default

  /**
   * shadowed by someone
   */
  def getDuration(key: String) = Duration(config.getMilliseconds(key), java.util.concurrent.TimeUnit.MILLISECONDS)

  def getDuration(key: String, default: Duration) = if (config.hasPath(key)) Duration(config.getMilliseconds(key), java.util.concurrent.TimeUnit.MILLISECONDS) else default

}

