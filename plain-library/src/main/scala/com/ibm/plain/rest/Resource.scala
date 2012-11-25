package com.ibm

package plain

package rest

import scala.reflect._
import scala.reflect.runtime.universe._
import scala.language.implicitConversions

import aio.FileByteChannel.forWriting
import aio.transfer
import http.{ Request, Response, Method, Status }
import http.Entity.ContentEntity
import http.Method._
import http.Status._

/**
 *
 */
trait Resource

  extends BaseUniform

  with DelayedInit {

  import Resource._

  override final def delayedInit(init: ⇒ Unit): Unit = {
    resourcemethods.get(getClass) match {
      case Some(methods) ⇒ this.methods = methods
      case None ⇒ methods = Map.empty; init; resourcemethods = resourcemethods ++ Map(getClass -> methods)
    }
  }

  override final def completed(response: Response, context: Context) = {
    try {
      threadlocal.set(context ++ response)
      context.methodbody.completed match {
        case Some(completed) ⇒ completed(response)
        case _ ⇒
      }
    } finally {
      threadlocal.remove
    }
    super.completed(response, context)
  }

  override final def failed(e: Throwable, context: Context) = {
    try {
      threadlocal.set(context ++ e)
      context.methodbody.failed match {
        case Some(failed) ⇒ failed(e)
        case _ ⇒
      }
    } finally {
      threadlocal.remove
    }
    super.failed(e, context)
  }

  final def completed(response: Response): Unit = completed(response, threadlocal.get)

  final def completed(status: Status): Unit = completed(Response(status), threadlocal.get)

  final def failed(e: Throwable): Unit = failed(e, threadlocal.get)

  final def handle(request: Request, context: Context): Nothing = {
    test
    /**
     * ugly and incomplete and stupid "dispatching"
     */
    request.entity match {
      case Some(entity) ⇒
        http.Header.Entity.`Content-Type`(request.headers) match {
          case Some(value) ⇒ println(value)
          case _ ⇒ println("no ct")
        }
      case _ ⇒
    }
    val methodbody = methods.get(request.method) match {
      case Some(m) ⇒ m.toList.head._2.toList.head._2
      case None ⇒ throw ClientError.`405`
    }
    val in: Option[Any] = request.entity match {
      case Some(ContentEntity(length)) if length <= maxEntityBufferSize ⇒ None
      case _ ⇒ None
    }
    /**
     * fill up context with everything we have right now
     */
    context ++ request ++ Response(Success.`200`) ++ methodbody

    try {
      threadlocal.set(context)
      val r = methodbody.body(in.getOrElse(()))
      println(r)
      completed(response, context)
    } finally {
      threadlocal.remove
    }
  }

  def test = ()

  final def Post[E: TypeTag, A: TypeTag](body: E ⇒ A): MethodBody = {
    add(POST, Some(typeOf[E]), Some(typeOf[A]), body)
  }

  final def Post[A: TypeTag](body: ⇒ A): MethodBody = {
    add(POST, None, Some(typeOf[A]), (_: Unit) ⇒ body)
  }

  final def Put[E: TypeTag, A: TypeTag](body: E ⇒ A): MethodBody = {
    add(PUT, Some(typeOf[E]), Some(typeOf[A]), body)
  }

  final def Delete[A: TypeTag](body: ⇒ A): MethodBody = {
    add(DELETE, None, Some(typeOf[A]), (_: Unit) ⇒ body)
  }

  final def Get[A: TypeTag](body: ⇒ A): MethodBody = {
    add(GET, None, Some(typeOf[A]), (_: Unit) ⇒ body)
  }

  final def Get[A: TypeTag](body: Map[String, String] ⇒ A): MethodBody = {
    add(GET, Some(typeOf[Map[String, String]]), Some(typeOf[A]), body)
  }

  final def Head(body: ⇒ Unit): MethodBody = {
    add(HEAD, None, None, (_: Unit) ⇒ body)
  }

  protected[this] final def request = threadlocal.get.request

  protected[this] final def response = threadlocal.get.response

  protected[this] final def context = threadlocal.get

  def m = methods // for TestResource

  private[this] final def add[E, A](method: Method, in: Option[Type], out: Option[Type], body: Body[E, A]): MethodBody = {
    val methodbody = MethodBody(body.asInstanceOf[Body[Any, Any]])
    methods = methods ++ (methods.get(method) match {
      case None ⇒ Map(method -> Map(in -> Map(out -> methodbody)))
      case Some(inout) ⇒ inout.get(in) match {
        case None ⇒ Map(method -> (inout ++ Map(in -> Map(out -> methodbody))))
        case Some(outbody) ⇒ Map(method -> (inout ++ Map(in -> (outbody ++ Map(out -> methodbody)))))
      }
    })
    methodbody
  }

  private[this] final var methods: Methods = null

}

/**
 * Singleton access to all Resources' methods maps.
 */
object Resource {

  class MethodBody private (

    val body: Body[Any, Any],

    var completed: Option[Response ⇒ Unit],

    var failed: Option[Throwable ⇒ Unit]) {

    def onComplete(body: Response ⇒ Unit) = { completed = Some(body); this }

    def onFailure(body: Throwable ⇒ Unit) = { failed = Some(body); this }

  }

  object MethodBody {

    def apply(body: Body[Any, Any]) = new MethodBody(body, None, None)

  }

  private type Body[E, A] = E ⇒ A

  private type Methods = Map[Method, InOut]

  private type InOut = Map[Option[Type], OutMethodBody]

  private type OutMethodBody = Map[Option[Type], MethodBody]

  private final var resourcemethods: Map[Class[_ <: Resource], Methods] = Map.empty

  private final val threadlocal = new ThreadLocal[Context]

}


