package com.ibm

package plain

package hybriddb

package schema

package column

import scala.reflect.ClassTag

/**
 *
 */
object MostlyNullColumn {

  type IndexTypeSet = scala.collection.mutable.HashSet[Int]

  type KeyMap[A] = scala.collection.mutable.OpenHashMap[A, IndexTypeSet]

  type BitSet = scala.collection.mutable.BitSet

}

import MostlyNullColumn._

/**
 *
 */
final class MostlyNullColumn[@specialized(Int, Long) A](

  val length: IndexType,

  private[this] final val keys: KeyMap[A],

  private[this] final val values: Array[IndexType],

  private[this] final val distinctvalues: Array[A],

  private[this] final val nulls: BitSet)

  extends Column[Option[A]]

  with Lookup[Option[A]] {

  final def get(index: IndexType): Option[A] = if (nulls.contains(index)) None else Some(distinctvalues(values(index)))

  final def lookup(value: Option[A]): IndexIterator = if (value.isEmpty) nulls.iterator else keys.get(value.get) match {
    case Some(s) ⇒ s.iterator
    case _ ⇒ Set.empty.iterator
  }

}

/**
 *
 */
final class MostlyNullColumnBuilder[A: ClassTag](

  capacity: IndexType)

  extends ColumnBuilder[Option[A], MostlyNullColumn[A]] {

  final def set(index: IndexType, value: Option[A]): Unit = if (value.isEmpty) nulls.add(index) else keys.put(value.get, keys.getOrElse(value.get, new IndexTypeSet) += index)

  final def get = {
    val length = keys.foldLeft(0) { case (s, (_, v)) ⇒ s + v.size }
    val values = {
      val v = new Array[IndexType](length)
      var i = 0
      keys.foreach {
        case (value, key) ⇒
          key.foreach(v.update(_, i))
          i += 1
      }
      v
    }
    val distinctvalues = {
      val d = new Array[A](keys.size)
      var i = 0
      keys.foreach {
        case (value, key) ⇒
          d.update(i, value)
          i += 1
      }
      d
    }
    new MostlyNullColumn[A](length, keys, values, distinctvalues, nulls)
  }

  private[this] final val keys = new KeyMap[A](capacity / 1000)

  private[this] final val values = new Array[IndexType](capacity)

  private[this] final val nulls = new BitSet(capacity)

}
