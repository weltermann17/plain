package com.ibm

package plain

import java.io.{ BufferedOutputStream, File, IOException, InputStream, OutputStream, Reader, Writer }
import java.net.URLClassLoader
import java.nio.ByteBuffer
import java.nio.channels.{ FileChannel, ReadableByteChannel, WritableByteChannel }
import java.nio.channels.Channels.newChannel
import java.nio.file.{ Files, Path, Paths }
import java.util.zip.GZIPInputStream

import org.apache.commons.io.FileUtils

import concurrent.spawn
import config.{ CheckedConfig, config2RichConfig }
import logging.createLogger
import io.{ ByteBufferInputStream, ByteBufferOutputStream, GzipOutputStream, Io, NullOutputStream }

package object io

    extends CheckedConfig {

  import config._
  import config.settings._

  /**
   * Copies the entire inputstream to outputstream, then flushs the outputstream.
   */
  final def copyBytes(in: InputStream, out: OutputStream, buffersize: Int = defaultBufferSize) {
    copyBytesNio(newChannel(in), newChannel(out), buffersize)
    out.flush
  }

  /**
   * Copies the entire inputstream to outputstream, then flushs the outputstream.
   */
  final def copyBytesIo(in: InputStream, out: OutputStream, buffersize: Int = defaultBufferSize) {
    val buffer = new Array[Byte](buffersize)
    var bytesread = 0
    while (-1 < { bytesread = in.read(buffer, 0, buffersize); bytesread }) {
      out.write(buffer, 0, bytesread)
    }
    out.flush
  }

  /**
   * Copies the entire readable channel to the writable channel.
   */
  final def copyBytesNio(in: ReadableByteChannel, out: WritableByteChannel, buffersize: Int = defaultBufferSize) {
    if (in.isInstanceOf[FileChannel]) {
      val f = in.asInstanceOf[FileChannel]
      f.transferTo(0, f.size, out)
    } else if (out.isInstanceOf[FileChannel]) {
      val f = out.asInstanceOf[FileChannel]
      f.transferFrom(in, 0, Long.MaxValue)
    } else {
      val b = aio.bestFitByteBuffer(buffersize)
      try {
        var bytesread = 0
        while (-1 < { bytesread = in.read(b); bytesread }) {
          b.flip
          out.write(b)
          b.clear
        }
      } finally aio.releaseByteBuffer(b)
    }
  }

  /**
   * Copies the entire reader to writer, then flushs the writer.
   */
  final def copyText(in: Reader, out: Writer, buffersize: Int = defaultBufferSize) {
    var bytesread = 0
    val buffer = new Array[Char](buffersize)
    while (-1 < { bytesread = in.read(buffer, 0, buffersize); bytesread }) {
      out.write(buffer, 0, bytesread)
    }
    out.flush
  }

  /**
   * Copies the entire reader to writer line by line to fix newline problems, then flushs the writer.
   */
  final def copyLines(in: Reader, out: Writer, buffersize: Int = defaultBufferSize) {
    val reader = new java.io.LineNumberReader(in)
    val writer = new java.io.PrintWriter(out)
    var line: String = null
    while (null != { line = reader.readLine; line }) {
      writer.println(line)
    }
    writer.flush
  }

  /**
   * Copies the entire inputstream to a ByteArrayOutputStream
   */
  final def copyFully(in: InputStream, buffersize: Int = defaultBufferSize) = {
    val out = new java.io.ByteArrayOutputStream
    copyBytes(in, out)
    out
  }

  /**
   * Compress a ByteBuffer in place using GZIP.
   */
  final def gzip(buffer: ByteBuffer): ByteBuffer = {
    val in = new ByteBufferInputStream(buffer)
    val out = new GzipOutputStream(new BufferedOutputStream(new ByteBufferOutputStream(buffer)), defaultBufferSize)
    copyBytesIo(in, out)
    out.close
    buffer
  }

  /**
   * Decompress a ByteBuffer in place using GZIP. It assumes that the gunzipped content will fit into its capacity.
   */
  final def gunzip(buffer: ByteBuffer): ByteBuffer = {
    val in = new GZIPInputStream(new ByteBufferInputStream(buffer))
    val out = new BufferedOutputStream(new ByteBufferOutputStream(buffer))
    copyBytesIo(in, out)
    out.close
    buffer
  }

  /**
   * Create classpath from a given ClassLoader
   */
  final def classPathFromClassLoader(classloader: URLClassLoader): String = {
    val b = new StringBuilder
    val sep = java.io.File.pathSeparator
    var cl = classloader
    while (null != cl) {
      cl.getURLs.foreach { url ⇒
        if (0 < b.length) b.append(sep)
        url.toString match {
          case u if u.startsWith("file:") ⇒ ignore(b.append(new File(url.toURI).getCanonicalPath))
          case u if u.startsWith("jndi:") ⇒ b.append(u.drop(5))
          case u ⇒ b.append(u)
        }
      }
      cl = ignoreOrElse(cl.getParent.asInstanceOf[URLClassLoader], null)
    }
    b.toString
  }

  /**
   * If not set differently this will result to 2k which proved to provide best performance under high load.
   */
  final val defaultBufferSize = getBytes("plain.io.default-buffer-size", 2 * 1024).toInt

  final val defaultLargeBufferSize = getBytes("plain.io.default-large-buffer-size", 64 * 1024).toInt

  /**
   * To make deleteDirectory more robust.
   */
  final val deleteDirectoryRetries = getInt("plain.io.delete-directory-retries", 5)

  final val deleteDirectoryPauseBetweenRetries = getMilliseconds("plain.io.delete-directory-pause-between-retries", 10000)

  final val tempPurgeEmptyDirectoriesOnStartup = getBoolean("plain.io.temp-purge-empty-directories-on-startup", true)

  final val tempPurgeFilesOnStartup = getBoolean("plain.io.temp-purge-files-on-startup", true)

  final val temp = {
    val tempdir = try {
      val tmp = getString("plain.io.temp", System.getenv("TMP"))
      createDirectory(Paths.get(tmp))
      System.setProperty("java.io.tmpdir", tmp)
      Paths.get(tmp)
    } catch {
      case _: Throwable ⇒
        Paths.get(System.getProperty("java.io.tmpdir"))
    }
    tempdir.toFile.listFiles.foreach { f ⇒
      if (f.isFile && tempPurgeFilesOnStartup)
        f.delete
      else if (f.isDirectory && tempPurgeEmptyDirectoriesOnStartup && 0 == f.listFiles.size)
        f.delete
    }
    tempdir
  }

  /**
   * Create a temporary file somewhere in the default location. It will be deleted at JVM shutdown.
   */
  final def temporaryFile = {
    val f = Files.createTempFile(temp, null, null).toFile
    deleteOnExit(f)
    f
  }

  /**
   * Create a temporary file in the given directory. It will be deleted at JVM shutdown.
   */
  final def temporaryFileInDirectory(directory: File) = {
    val f = Files.createTempFile(directory.toPath, null, null).toFile
    deleteOnExit(f)
    f
  }

  /**
   * Create a temporary directory somewhere in the default location. It will be deleted at JVM shutdown together with all files it includes.
   */
  final def temporaryDirectory = {
    val d = Files.createTempDirectory(temp, null).toFile
    deleteOnExit(d)
    d
  }

  final def createDirectory(directory: Path): Path = createDirectory(directory.toFile)

  final def createDirectory(directory: File): Path = { directory.mkdirs; directory.toPath }

  /**
   * Delete a directory and all of its contents in a background thread. Use delete-directory-retries and delete-directory-timeout to make this method more robust.
   * If directory is just a file it will silently be deleted.
   */
  final def deleteDirectory(directory: File, background: Boolean) = if (background && directory.isDirectory) spawn {
    try {
      if (directory.exists) FileUtils.deleteDirectory(directory)
    } catch {
      case e: IOException ⇒
        var retries = deleteDirectoryRetries
        while (0 < retries) {
          try {
            Thread.sleep(deleteDirectoryPauseBetweenRetries)
            FileUtils.deleteDirectory(directory)
            retries = 0
          } catch {
            case e: IOException ⇒ retries -= 1
          }
        }
      case e: Throwable ⇒ createLogger(this).warn("Could not delete directory : " + e)
    }
  }
  else try {
    if (directory.isFile) directory.delete else FileUtils.deleteDirectory(directory)
  } catch {
    case e: Throwable ⇒ createLogger(this).warn("Could not delete file : " + e)
  }

  /**
   * The file given will be automatically deleted at JVM shutdown.
   */
  final def deleteOnExit(file: File) = Io.instance.add(file)

  /**
   * This can be accessed as a static field (eg. for Derby database, as a null logger).
   */
  final val devnull = NullOutputStream

  /**
   * Objects with a method close() have this method automatically called when going out of scope, normally and in case of an exception.
   * @since 0.9.4
   * Example: autoClose(new FileWriter("test")) { f => f.write("Hello") }
   */
  final val autoClose = WithClosable

  /**
   * check requirements
   */
  require(null != temp, "Neither plain.temp config setting nor TMP environment variable nor java.io.tmpdir property are set.")

}
