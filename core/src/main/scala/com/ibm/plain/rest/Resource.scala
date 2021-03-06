package com.ibm

package plain

package rest

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.{ Type, TypeTag, typeOf }

import com.typesafe.config.Config

import aio.{ Exchange, ExchangeHandler }
import http.{ Request, Response, Status, Entity, Method, MimeType, Accept }
import http.Entity._
import http.MimeType.{ `application/x-scala-unit`, `*/*` }
import http.Method.{ DELETE, GET, HEAD, POST, PUT }
import http.Status.{ ClientError, ServerError, Success }
import http.Header.Request.{ `Accept` ⇒ AcceptHeader }
import Matching.{ Encoder, Decoder, MarshaledDecoder }

/**
 *
 */
trait Resource

    extends Uniform

    with DelayedInit {

  import Resource._

  final def delayedInit(initialize: ⇒ Unit): Unit = {
    resourcemethods.get(getClass) match {
      case Some(methods) ⇒ this.methods = methods
      case None ⇒
        methods = Map.empty
        initialize
        methods = methods.map { case (method, Left(bodies)) ⇒ (method, Right(resourcePriorities(method, bodies))) case _ ⇒ null }
        resourcemethods = resourcemethods ++ Map(getClass -> methods)
    }
  }

  final def process(exchange: Exchange[Context], handler: ExchangeHandler[Context]) = exchange.attachment match {
    case Some(context) ⇒ try {
      methods.get(context.request.method) match {
        case Some(Right(resourcepriorities)) ⇒ execute(exchange, handler, resourcepriorities)
        case _ ⇒ throw ClientError.`405`
      }
    } catch {
      case e: Throwable ⇒ failed(e, exchange, handler)
    }
    case _ ⇒ throw ServerError.`500`
  }

  /**
   * DSL implementation methods.
   */

  /**
   * Post.
   */
  final def Post[E: TypeTag, A: TypeTag](body: E ⇒ A): MethodBody = {
    add[E, A](POST, typeOf[E], typeOf[A], body)
  }

  final def Post[A: TypeTag](body: ⇒ A): MethodBody = {
    add[Unit, A](POST, typeOf[Unit], typeOf[A], (_: Unit) ⇒ body)
  }

  /**
   * Put.
   */
  final def Put[E: TypeTag, A: TypeTag](body: E ⇒ A): MethodBody = {
    add[E, A](PUT, typeOf[E], typeOf[A], body)
  }

  final def Put[A: TypeTag](body: ⇒ A): MethodBody = {
    add[Unit, A](PUT, typeOf[Unit], typeOf[A], (_: Unit) ⇒ body)
  }

  /**
   * Delete.
   */
  final def Delete[A: TypeTag](body: ⇒ A): MethodBody = {
    add[Unit, A](DELETE, typeOf[Unit], typeOf[A], (_: Unit) ⇒ body)
  }

  final def Delete[E: TypeTag, A: TypeTag](body: E ⇒ A): MethodBody = {
    add[E, A](DELETE, typeOf[E], typeOf[A], body)
  }

  /**
   * Get.
   */
  final def Get[A: TypeTag](body: ⇒ A): MethodBody = {
    add[Unit, A](GET, typeOf[Unit], typeOf[A], (_: Unit) ⇒ body)
  }

  final def Get[E: TypeTag, A: TypeTag](body: E ⇒ A): MethodBody = {
    add[E, A](GET, typeOf[E], typeOf[A], body)
  }

  /**
   * Head.
   */
  final def Head(body: ⇒ Any): MethodBody = {
    add[Unit, Unit](HEAD, typeOf[Unit], typeOf[Unit], (_: Unit) ⇒ { body; () })
  }

  final def Head[E: TypeTag](body: E ⇒ Any): MethodBody = {
    add[E, Unit](HEAD, typeOf[E], typeOf[Unit], (e: E) ⇒ { body(e); () })
  }

  protected[this] final def context: Context = threadlocal.get._1

  protected[this] final def exchange: Exchange[Context] = threadlocal.get._2

  protected[this] final def request: Request = threadlocal.get._1.request

  protected[this] final def response: Response = threadlocal.get._1.response

  protected[this] def fromCache(request: Request): Option[CachedMethod] = None

  protected[this] def toCache(request: Request, cachedmethod: CachedMethod) = ()

  /**
   * The most important method in this class.
   */
  private[this] final def execute(

    exchange: Exchange[Context],

    handler: ExchangeHandler[Context],

    resourcepriorities: ResourcePriorities) = {

    exchange.attachment match {
      case Some(context) ⇒
        val request = context.request
        fromCache(request) match {
          case Some((methodbody, input, encode)) ⇒
            context.response ++ encode {
              try {
                threadlocal.set((context, exchange))
                methodbody.body(input)
              } finally threadlocal.remove
            }
            completed(exchange, handler)
          case _ ⇒
            var innerinput: Option[(Any, AnyRef)] = None
            val inentity: Option[Entity] = request.entity
            val inmimetype: MimeType = inentity match { case Some(entity: Entity) ⇒ entity.contenttype.mimetype case _ ⇒ `application/x-scala-unit` }
            val outmimetypes: List[MimeType] = AcceptHeader(request.headers) match {
              case Some(Accept(mimetypes)) ⇒ mimetypes
              case _ ⇒ List(`*/*`)
            }

            def tryDecode(in: Type, decode: AnyRef): Boolean = {
              if (innerinput.isDefined && innerinput.get._2 == decode) return true
              decode match {
                case decode if decode.isInstanceOf[Decoder[_]] ⇒ try2Boolean(innerinput = Some((decode.asInstanceOf[Decoder[_]](inentity), decode)))
                case decode if decode.isInstanceOf[MarshaledDecoder[_]] ⇒ try2Boolean(innerinput = Some((decode.asInstanceOf[MarshaledDecoder[_]](inentity, ClassTag(Class.forName(in.toString))), decode)))
                case _ ⇒ false
              }
            }

            (for {
              o ← outmimetypes
              r ← resourcepriorities
            } yield (o, r)).collectFirst {
              case (outmimetype, (inoutmimetype, (in, methodbody), (decode, encode))) if (inoutmimetype == ((inmimetype, outmimetype))) && (tryDecode(in, decode)) ⇒
                (methodbody, encode)
            } match {
              case Some((methodbody, encode)) ⇒
                val e = encode(innerinput match {
                  case Some((input, _)) ⇒
                    toCache(request, (methodbody, input, encode))
                    try {
                      threadlocal.set((context, exchange))
                      methodbody.body(input)
                    } finally threadlocal.remove
                  case _ ⇒ throw ServerError.`501`
                })
                context.response ++ e
                completed(exchange, handler)
              case _ ⇒ throw ClientError.`400`
            }
        }
      case _ ⇒ throw ServerError.`500`
    }
  }

  /**
   * This is ugly, but it's only called once per Resource and Method, the resulting data structure is very efficient.
   */
  private[this] final def resourcePriorities(method: Method, methodbodies: MethodBodies): ResourcePriorities = {
    val matching = new Matching
    val priorities = for {
      p ← matching.priorities.filter { case (_, (intype, outtype)) ⇒ methodbodies.exists { case ((in, out), _) ⇒ in <:< intype && (!(typeOf[Nothing] =:= out) && out <:< outtype) } }
      m ← methodbodies if m._1._1 <:< p._2._1 && m._1._2 <:< p._2._2
    } yield (p._1, (m._1._1, m._2), (matching.decoders.get(p._2._1).get, matching.encoders.get(p._2._2).get))
    require(methodbodies.map(_._2).toSet == priorities.map(_._2._2).toSet, method + " : At least one method has an invalid input or output type and could not be registered.")
    priorities
  }

  private[this] final def add[E, A](method: Method, in: Type, out: Type, body: Body[E, A]): MethodBody = {
    require(!(out =:= typeOf[Nothing]), getClass.getSimpleName + " " + method + " Nothing is not allowed as output type.")
    val methodbody = MethodBody(body.asInstanceOf[Body[Any, Any]])
    methods = methods ++ Map(method -> Left((methods.get(method) match {
      case None ⇒ Array(((in, out), methodbody))
      case Some(Left(bodies)) ⇒ bodies ++ Array(((in, out), methodbody))
      case _ ⇒ null
    })))
    methodbody
  }

  private[this] final var methods: Methods = null

}

/**
 * Singleton access to all Resources' methods maps.
 */
object Resource {

  final class MethodBody private (

    val body: Body[Any, Any])

      extends AnyVal

  object MethodBody {

    @inline def apply(body: Body[Any, Any]) = new MethodBody(body)

  }

  type ResourcePriorities = Array[ResourcePriority]

  type CachedMethod = (MethodBody, Any, Any ⇒ Option[Entity])

  private type Body[E, A] = E ⇒ A

  private type Methods = Map[Method, Either[MethodBodies, ResourcePriorities]]

  private type MethodBodies = Array[((Type, Type), MethodBody)]

  private type ResourcePriority = ((MimeType, MimeType), (Type, MethodBody), (AnyRef, Encoder))

  private final val threadlocal = new ThreadLocal[(Context, Exchange[Context])]

  private final var resourcemethods: Map[Class[_ <: Resource], Methods] = Map.empty

}
