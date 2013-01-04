package com.ibm

package plain

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel

import scala.language.implicitConversions
import scala.util.control.ControlThrowable

import config.CheckedConfig

package object aio

  extends CheckedConfig {

  import config._
  import config.settings._

  /**
   * This should be thrown at the end of process or similar methods when the input was processed successfully, it is used as a success indicator at some points of AIO control flow.
   */
  final case object Completed extends ControlThrowable

  def bestFitByteBuffer(length: Int) = {
    if (length <= tinyBufferSize)
      tinyByteBuffer
    else if (length <= defaultBufferSize)
      defaultByteBuffer
    else if (length <= largeBufferSize)
      largeByteBuffer
    else
      ByteBuffer.allocate(length)
  }

  def defaultByteBuffer = Aio.defaultBufferPool.getBuffer

  def tinyByteBuffer = Aio.tinyBufferPool.getBuffer

  def largeByteBuffer = Aio.largeBufferPool.getBuffer

  /**
   * Quite dangerous, never call this function on a buffer more than once or it could be later used by more than one at the same time.
   */
  def releaseByteBuffer(buffer: ByteBuffer) = buffer.capacity match {
    case `tinyBufferSize` ⇒ Aio.tinyBufferPool.releaseBuffer(buffer)
    case `defaultBufferSize` ⇒ Aio.defaultBufferPool.releaseBuffer(buffer)
    case `largeBufferSize` ⇒ Aio.largeBufferPool.releaseBuffer(buffer)
    case _ ⇒
  }

  /**
   * Shorthand to object AsynchronousChannelTransfer.
   */
  final val transfer = ChannelTransfer

  /**
   * Helper to use AsynchronousFileChannel directly for transfer.
   */
  implicit def filechannel2filebytechannel(filechannel: AsynchronousFileChannel) = FileByteChannel.wrap(filechannel)

  /**
   * If not set differently this will result to 2k which proved to provide best performance under high load.
   */
  final val defaultBufferSize = getBytes("plain.aio.default-buffer-size", 2 * 1024).toInt

  final val defaultBufferPoolSize = getBytes("plain.aio.default-buffer-pool-size", 512).toInt

  /**
   * Something between 16 and 512.
   */
  final val tinyBufferSize = getBytes("plain.aio.tiny-buffer-size", 128).toInt

  final val tinyBufferPoolSize = getBytes("plain.aio.tiny-buffer-pool-size", 1024).toInt

  /**
   * Should be large enough to make an SSL packet fit into it.
   */
  final val largeBufferSize = getBytes("plain.aio.large-buffer-size", 20 * 1024).toInt

  final val largeBufferPoolSize = getBytes("plain.aio.large-buffer-pool-size", 64).toInt

  final val readWriteTimeout = getMilliseconds("plain.aio.read-write-timeout", 5000)

  /**
   * Check requirements.
   */
  require(16 <= defaultBufferSize, "plain.aio.default-buffer-size must be >= " + 16)

  require(16 <= tinyBufferSize, "plain.aio.tiny-buffer-size must be >= " + 16)

  require(2 * 1024 <= largeBufferSize, "plain.aio.large-buffer-size must be >= " + 2 * 1024)

  require((tinyBufferSize <= defaultBufferSize) && (defaultBufferSize <= largeBufferSize), "plain.aio buffer sizes: tiny <= default <= large violated")

}