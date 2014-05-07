package com.ibm

package plain

package aio

package conduits

import java.nio.ByteBuffer
import java.nio.channels.{ AsynchronousByteChannel ⇒ Channel }
import java.util.zip.Inflater

/**
 *
 */
final class DeflateConduit private (

  protected[this] val underlyingchannel: Channel)

  extends DeflateSourceConduit

  with DeflateSinkConduit {

  protected[this] final val nowrap = false

}

/**
 *
 */
object DeflateConduit {

  final def apply(underlyingchannel: Channel) = new DeflateConduit(underlyingchannel)

}

/**
 * Source conduit.
 */
trait DeflateSourceConduit

  extends FilterSourceConduit[Channel] {

  protected[this] def filter(processed: Integer, buffer: ByteBuffer): Integer = {
    if (0 >= processed) {
      inflater.end
      processed
    } else {
      if (inflater.needsInput) {
        inflater.setInput(innerbuffer.array, innerbuffer.position, innerbuffer.remaining)
      }
      val (array, offset, length) = if (buffer.hasArray) {
        (buffer.array, buffer.position, buffer.remaining)
      } else {
        if (null == inflatearray) inflatearray = new Array[Byte](buffer.capacity)
        (inflatearray, 0, buffer.remaining)
      }
      val e = inflater.getRemaining
      val len = inflater.inflate(array, offset, length)
      skip(e - inflater.getRemaining)
      if (!buffer.hasArray) buffer.put(array, 0, len)
      len
    }
  }

  protected[this] def hasSufficient = 0 < inflater.getRemaining

  protected[this] val nowrap: Boolean

  protected[this] final val inflater = new Inflater(nowrap)

  protected[this] final var inflatearray: Array[Byte] = null

}

/**
 * Sink conduit.
 */
trait DeflateSinkConduit

  extends FilterSinkConduit[Channel] {

}
