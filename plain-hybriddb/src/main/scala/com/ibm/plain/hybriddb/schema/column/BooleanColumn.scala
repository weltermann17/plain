package com.ibm

package plain

package hybriddb

package schema

package column

/**
 *
 */
object BooleanColumn {

  type BitSet = scala.collection.mutable.BitSet

}

import BooleanColumn.BitSet

/**
 *
 */
final class BooleanColumn private[column] (

  val name: String,

  val length: IndexType,

  private[this] final val trues: BitSet,

  private[this] final val falses: BitSet)

  extends Column[Boolean]

  with Lookup[Boolean] {

  final def get(index: IndexType) = trues.contains(index)

  final def lookup(value: Boolean): IndexIterator = if (value) trues.iterator else falses.iterator

}

/**
 *
 */
final class BooleanColumnBuilder(

  name: String,

  capacity: IndexType)

  extends ColumnBuilder[Boolean, BooleanColumn] {

  final def next(value: Boolean) = if (value) trues.add(nextIndex) else falses.add(nextIndex)

  final def get = new BooleanColumn(name, trues.size + falses.size, trues, falses)

  private[this] final val trues = new BitSet(capacity)

  private[this] final val falses = new BitSet(capacity)

}

