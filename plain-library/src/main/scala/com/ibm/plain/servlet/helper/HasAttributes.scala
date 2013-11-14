package com.ibm

package plain

package servlet

package helper

import java.util.Enumeration

import scala.collection.JavaConversions.asJavaEnumeration
import scala.collection.concurrent.TrieMap

trait HasAttributes {

  final def getAttribute(name: String): Object = attributes.getOrElse(name, null)

  final def getAttributeNames: Enumeration[String] = attributes.keysIterator

  final def removeAttribute(name: String): Unit = attributes.remove(name)

  final def setAttribute(name: String, value: Object): Unit = attributes.put(name, value)

  private[this] final val attributes = new TrieMap[String, Object]

}
