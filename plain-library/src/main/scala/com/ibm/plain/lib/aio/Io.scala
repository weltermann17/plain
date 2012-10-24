package com.ibm.plain

package lib

package aio

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.{ AsynchronousServerSocketChannel ⇒ ServerChannel, AsynchronousSocketChannel ⇒ Channel, CompletionHandler ⇒ Handler }
import java.nio.charset.Charset

import scala.math.min
import scala.util.continuations.{ shift, suspendable }
import scala.concurrent.duration.Duration

import Iteratee.{ Cont, Done, Error }
import aio.Input.{ Elem, Empty, Eof }
import concurrent.{ OnlyOnce, scheduleOnce }
import logging.HasLogger

/**
 * Helper for Io with all low-level ByteBuffer methods.
 */
abstract sealed class IoHelper[E <: Io] {

  private[this] val self: E = this.asInstanceOf[E]

  import self._

  final def decode(implicit cset: Charset): String = resetBuffer(
    buffer.remaining match {
      case 0 ⇒ Io.emptyString
      case 1 ⇒ String.valueOf(buffer.get.toChar)
      case _ ⇒ new String(readBytes, cset)
    })

  final def length: Int = buffer.remaining

  final def take(n: Int): Io = {
    markLimit
    buffer.limit(min(buffer.limit, buffer.position + n))
    self
  }

  final def peek(n: Int): Io = {
    markPosition
    take(n)
  }

  final def drop(n: Int): Io = {
    buffer.position(min(buffer.limit, buffer.position + n))
    self
  }

  final def indexOf(b: Byte): Int = {
    val p = buffer.position
    val l = buffer.limit
    var i = p
    while (i < l && b != buffer.get(i)) i += 1
    if (i == l) -1 else i - p
  }

  final def span(p: Int ⇒ Boolean): (Int, Int) = {
    val pos = buffer.position
    val l = buffer.limit
    var i = pos
    while (i < l && p(buffer.get(i))) i += 1
    (i - pos, l - i)
  }

  final def readBytes: Array[Byte] = buffer.remaining match {
    case 0 ⇒ Io.emptyArray
    case n ⇒ Array.fill(buffer.remaining)(buffer.get)
  }

  @inline private[this] final def markLimit = limitmark = buffer.limit

  @inline private[this] final def markPosition = positionmark = buffer.position

  @inline private[this] final def resetBuffer[A](a: A): A = {
    buffer.limit(limitmark)
    if (-1 < positionmark) { buffer.position(positionmark); positionmark = -1 }
    a
  }

  private[this] final var limitmark = -1

  private[this] final var positionmark = -1

}

/**
 * Io represents the context of an asynchronous i/o operation.
 */
final class Io private (

  var server: ServerChannel,

  val channel: Channel,

  var buffer: ByteBuffer,

  var iteratee: Iteratee[Io, _],

  var k: Io.IoCont,

  var readwritten: Int,

  var expected: Long)

  extends IoHelper[Io] {

  import Io._

  /**
   * The trick method of the entire algorithm, it should be called only when the buffer is too small and on start with Io.empty.
   */
  final def ++(that: Io): Io = if (0 == this.length) {
    that
  } else if (0 == that.length) {
    this
  } else {
    warnOnce
    val len = this.length + that.length
    val b = ByteBuffer.allocate(len)
    b.put(this.readBytes)
    this.releaseBuffer
    b.put(that.buffer)
    that.releaseBuffer
    b.flip
    that + b
  }

  @inline def ++(server: ServerChannel) = { this.server = server; this }

  @inline def ++(channel: Channel) = new Io(server, channel, buffer, iteratee, k, readwritten, expected)

  @inline def ++(iteratee: Iteratee[Io, _]) = { this.iteratee = iteratee; this }

  @inline def ++(k: IoCont) = { this.k = k; this }

  @inline def ++(readwritten: Int) = { this.readwritten = readwritten; this }

  @inline def ++(expected: Long) = { this.expected = expected; this }

  @inline def ++(buffer: ByteBuffer) = if (0 < this.buffer.remaining) {
    new Io(server, channel, buffer, iteratee, k, readwritten, expected)
  } else {
    this + buffer
  }

  @inline private def +(buffer: ByteBuffer) = {
    if (this.buffer ne emptyBuffer) releaseByteBuffer(this.buffer)
    this.buffer = buffer
    this
  }

  @inline private def releaseBuffer = if (buffer ne emptyBuffer) {
    releaseByteBuffer(buffer)
    buffer = emptyBuffer
  }

  @inline private def clear = buffer.clear

  @inline private def release = {
    releaseBuffer
    if (channel.isOpen) channel.close
  }

  @inline private def error(e: Throwable) = {
    e match {
      case _: IOException ⇒
      case e ⇒ logger.debug(e.toString)
    }
    releaseBuffer
  }

}

/**
 * The Io object contains all the complex continuations stuff, it is sort of an 'Io' monad.
 */
object Io

  extends HasLogger

  with OnlyOnce {

  import Iteratee._

  @inline private[aio] final def empty = new Io(null, null, emptyBuffer, null, null, -1, -1)

  final private[aio]type IoCont = Io ⇒ Unit

  final private[aio] val emptyArray = new Array[Byte](0)

  final private[aio] val emptyBuffer = ByteBuffer.wrap(emptyArray)

  final private[aio] val emptyString = new String

  final private def warnOnce = onlyonce { warning("Chunked input found. Enlarge aio.default-buffer-size : " + defaultBufferSize) }

  final private val logger = log

  /**
   * Aio handling.
   */
  private[this] final case class AcceptHandler(pauseinmilliseconds: Long)

    extends Handler[Channel, Io] {

    @inline final def completed(c: Channel, io: Io) = {
      import io._
      if (0 == pauseinmilliseconds)
        server.accept(io, this)
      else
        scheduleOnce(pauseinmilliseconds)(server.accept(io, this))
      k(io ++ c ++ defaultByteBuffer)
    }

    @inline final def failed(e: Throwable, io: Io) = {
      import io._
      if (server.isOpen) {
        /**
         * Do not pause here, in case of failure we want to be back online asap.
         */
        server.accept(io, this)
        e match {
          case _: IOException ⇒
          case e: Throwable ⇒ warning("accept failed : " + io + " " + e)
        }
      }
    }

  }

  private[this] final val iohandler = new Handler[Integer, Io] {

    @inline final def completed(processed: Integer, io: Io) = {
      import io._
      buffer.flip
      k(io ++ processed)
    }

    @inline final def failed(e: Throwable, io: Io) = {
      import io._
      k(io ++ Error[Io](e))
    }

  }

  final def accept(server: ServerChannel, pausebetweenaccepts: Duration): Io @suspendable =
    shift { k: IoCont ⇒ server.accept(Io.empty ++ server ++ k, AcceptHandler(pausebetweenaccepts.toMillis)) }

  @inline private[this] final def read(io: Io): Io @suspendable = {
    import io._
    shift { k: IoCont ⇒ buffer.clear; channel.read(buffer, io ++ k, iohandler) }
  }

  @inline private[this] final def write(io: Io): Io @suspendable = {
    import io._
    shift { k: IoCont ⇒ buffer.flip; channel.write(buffer, io ++ k, iohandler) }
  }

  @inline private[this] final def unhandled(e: Any) = error("unhandled " + e)

  @inline private[this] final val ignored = ()

  final def loop[E, A](io: Io, processor: AioProcessor[E, A]): Unit @suspendable = {

    val readiteratee = io.iteratee

    @inline def readloop(io: Io): Unit @suspendable = {
      (read(io) match {
        case io if -1 < io.readwritten ⇒
          io.iteratee(Elem(io))
        case io ⇒
          io.iteratee(Eof)
      }) match {
        case (cont @ Cont(_), Empty) ⇒
          readloop(io ++ cont ++ defaultByteBuffer)
        case (e @ Done(_), Elem(io)) ⇒
          processloop(io ++ e)
        case (e @ Error(_), Elem(io)) ⇒
          io.clear
          processloop(io ++ e)
        case (_, Eof) ⇒
          ignored
        case e ⇒
          unhandled(e)
      }
    }

    @inline def processloop(io: Io): Unit @suspendable = {
      (processor.process_(io) match {
        case io ⇒
          io.iteratee
      }) match {
        case Done(_) ⇒
          ok(io)
          write(io)
          readloop(io ++ readiteratee)
        case Error(e) ⇒
          io.error(e)
        case e ⇒
          unhandled(e)
      }
    }

    // sometime we do not get here (uncaught exception -Xss8m ?)
    readloop(io)
    io.release
  }

  /**
   * testing
   */
  private[this] final val response = "HTTP/1.1 200 OK\r\nDate: Mon, 10 Sep 2012 15:06:09 GMT\r\nContent-Type: text/plain\r\nContent-Length: 5\r\nConnection: keep-alive\r\n\r\nPONG!".getBytes

  private[this] final val badrequest = "HTTP/1.1 400 Bad Request\r\nDate: Mon, 10 Sep 2012 15:06:09 GMT\r\nConnection: close\r\n\r\n".getBytes

  private[this] final def ok(io: Io): Unit = {
    import io._
    if (io.buffer ne emptyBuffer) buffer.clear else io ++ defaultByteBuffer
    buffer.put(response)
  }

}

