package com.ibm

package plain

package rest

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.{ Type, typeOf }
import scala.xml.{ XML, Elem ⇒ Xml }

import json.{ Json, JsonMarshaled }
import json.Json.{ JArray, JObject }
import text.{ `ISO-8859-15`, `UTF-8` }
import xml.XmlMarshaled

import http.Status.ClientError
import http.ContentType
import http.MimeType
import http.MimeType._
import http.Entity
import http.Entity._

private object Matching {

  object Types {
    val entity = typeOf[Entity]
    val unit = typeOf[Unit]
    val array = typeOf[Array[Byte]]
    val string = typeOf[String]
    val form = typeOf[Map[String, String]]
    val multipart = typeOf[Map[String, Object]]
    val json = typeOf[Json]
    val jobject = typeOf[JObject]
    val jarray = typeOf[JArray]
    val jsonmarshaled = typeOf[JsonMarshaled]
    val xml = typeOf[Xml]
    val xmlmarshaled = typeOf[XmlMarshaled]
  }

  import Types._

  type Decoder[A] = Option[Entity] ⇒ A

  type MarshaledDecoder[A] = (Option[Entity], ClassTag[A]) ⇒ A

  val decodeEntity: Decoder[Entity] = (entity: Option[Entity]) ⇒ entity match { case Some(entity) ⇒ entity case _ ⇒ throw ClientError.`415` }

  val decodeUnit: Decoder[Unit] = (entity: Option[Entity]) ⇒ ()

  val decodeArray: Decoder[Array[Byte]] = (entity: Option[Entity]) ⇒ entity match { case Some(a: ArrayEntity) ⇒ a.array case _ ⇒ throw ClientError.`415` }

  val decodeString: Decoder[String] = (entity: Option[Entity]) ⇒ entity match { case Some(a: ArrayEntity) ⇒ new String(a.array, a.contenttype.charsetOrDefault) case _ ⇒ throw ClientError.`415` }

  val decodeForm: Decoder[Map[String, String]] = (entity: Option[Entity]) ⇒ entity match { case Some(a: ArrayEntity) ⇒ Map("foo" -> "bar") case _ ⇒ throw ClientError.`415` }

  val decodeMultipart: Decoder[Map[String, Object]] = (entity: Option[Entity]) ⇒ entity match { case Some(a: ArrayEntity) ⇒ Map("foo" -> "bar") case _ ⇒ throw ClientError.`415` }

  val decodeJson: Decoder[Json] = (entity: Option[Entity]) ⇒ entity match { case Some(a: ArrayEntity) ⇒ Json.parse(decodeString(entity)) case _ ⇒ throw ClientError.`415` }

  val decodeJArray: Decoder[JArray] = (entity: Option[Entity]) ⇒ entity match { case Some(a: ArrayEntity) ⇒ decodeJson(entity).asArray case _ ⇒ throw ClientError.`415` }

  val decodeJObject: Decoder[JObject] = (entity: Option[Entity]) ⇒ entity match { case Some(a: ArrayEntity) ⇒ decodeJson(entity).asObject case _ ⇒ throw ClientError.`415` }

  val decodeXml: Decoder[Xml] = (entity: Option[Entity]) ⇒ entity match { case Some(a: ArrayEntity) ⇒ XML.loadString(decodeString(entity)) case _ ⇒ throw ClientError.`415` }

  def decodeJsonMarshaled[A <: JsonMarshaled]: MarshaledDecoder[A] = (entity: Option[Entity], c: ClassTag[A]) ⇒ JsonMarshaled[A](decodeString(entity))(c)

  def decodeXmlMarshaled[A <: XmlMarshaled]: MarshaledDecoder[A] = (entity: Option[Entity], c: ClassTag[A]) ⇒ XmlMarshaled[A](decodeString(entity))(c)

  type TypeDecoders = Map[Type, AnyRef]

  val decoders: TypeDecoders = List(
    (typeOf[Entity], decodeEntity),
    (typeOf[Unit], decodeUnit),
    (typeOf[Array[Byte]], decodeArray),
    (typeOf[String], decodeString),
    (typeOf[Map[String, String]], decodeForm),
    (typeOf[Map[String, Object]], decodeMultipart),
    (typeOf[Json], decodeJson),
    (typeOf[JArray], decodeJArray),
    (typeOf[JObject], decodeJObject),
    (typeOf[JsonMarshaled], decodeJsonMarshaled),
    (typeOf[Xml], decodeXml),
    (typeOf[XmlMarshaled], decodeXmlMarshaled)).toMap

  type Encoder = Any ⇒ Option[Entity]

  type TypeEncoders = Map[Type, Encoder]

  val encodeEntity: Encoder = ((entity: Entity) ⇒ Some(entity)).asInstanceOf[Encoder]

  val encodeUnit: Encoder = ((u: Unit) ⇒ None).asInstanceOf[Encoder]

  val encodeArray: Encoder = ((array: Array[Byte]) ⇒ Some(ArrayEntity(array, `application/octet-stream`))).asInstanceOf[Encoder]

  val encodeString: Encoder = ((s: String) ⇒ Some(ArrayEntity(s.getBytes(`ISO-8859-15`), `text/plain`))).asInstanceOf[Encoder]

  val encodeForm: Encoder = ((form: Map[String, String]) ⇒ Some(ArrayEntity(null, `application/x-www-form-urlencoded`))).asInstanceOf[Encoder]

  val encodeMultipart: Encoder = ((form: Map[String, Object]) ⇒ Some(ArrayEntity(null, `multipart/form-data`))).asInstanceOf[Encoder]

  val encodeJson: Encoder = ((json: Json) ⇒ Some(ArrayEntity(Json.build(json).getBytes(`UTF-8`), `application/json`))).asInstanceOf[Encoder]

  val encodeJObject: Encoder = ((json: JObject) ⇒ Some(ArrayEntity(Json.build(json).getBytes(`UTF-8`), `application/json`))).asInstanceOf[Encoder]

  val encodeJArray: Encoder = ((json: JArray) ⇒ Some(ArrayEntity(Json.build(json).getBytes(`UTF-8`), `application/json`))).asInstanceOf[Encoder]

  val encodeXml: Encoder = ((xml: Xml) ⇒ Some(ArrayEntity(xml.buildString(true).getBytes(`UTF-8`), `application/xml`))).asInstanceOf[Encoder]

  val encodeJsonMarshaled: Encoder = ((marshaled: JsonMarshaled) ⇒ Some(ArrayEntity(marshaled.toJson.getBytes(`UTF-8`), `application/json`))).asInstanceOf[Encoder]

  val encodeXmlMarshaled: Encoder = ((marshaled: XmlMarshaled) ⇒ Some(ArrayEntity(marshaled.toXml.getBytes(`UTF-8`), `application/xml`))).asInstanceOf[Encoder]

  val encoders: TypeEncoders = List(
    (typeOf[Entity], encodeEntity),
    (typeOf[Unit], encodeUnit),
    (typeOf[Array[Byte]], encodeArray),
    (typeOf[String], encodeString),
    (typeOf[Map[String, String]], encodeForm),
    (typeOf[Map[String, Object]], encodeMultipart),
    (typeOf[Json], encodeJson),
    (typeOf[JObject], encodeJObject),
    (typeOf[JArray], encodeJArray),
    (typeOf[Xml], encodeXml),
    (typeOf[JsonMarshaled], encodeJsonMarshaled),
    (typeOf[XmlMarshaled], encodeXmlMarshaled)).toMap

  type PriorityList = List[(MimeType, List[Type])]

  val inputPriority: PriorityList =
    List(
      (`application/x-scala-unit`, List(unit, entity)),
      (`application/octet-stream`, List(array, entity)),
      (`application/json`, List(jsonmarshaled, jobject, jarray, json, string, array, entity)),
      (`application/xml`, List(xmlmarshaled, xml, string, array, entity)),
      (`application/x-www-form-urlencoded`, List(form, string, array, entity)),
      (`multipart/form-data`, List(multipart, string, array, entity)),
      (`text/plain`, List(string, form, xmlmarshaled, jsonmarshaled, xml, jobject, jarray, json, array, entity)))

  val outputPriority: PriorityList =
    List(
      (`application/x-scala-unit`, List(unit, entity)),
      (`application/octet-stream`, List(array, entity)),
      (`application/json`, List(jsonmarshaled, jobject, jarray, json, string, array, entity)),
      (`application/xml`, List(xmlmarshaled, xml, string, array, entity)),
      (`application/x-www-form-urlencoded`, List(form, string, array, entity)),
      (`multipart/form-data`, List(multipart, string, array, entity)),
      (`text/plain`, List(string, form, xmlmarshaled, jsonmarshaled, xml, jobject, jarray, json, array, entity)),
      (`*/*`, List(string, form, xmlmarshaled, jsonmarshaled, xml, jobject, jarray, json, array, entity)))

  val priorities: List[((MimeType, MimeType), (Type, Type))] = for {
    (inmimetype, intypelist) ← inputPriority
    intype ← intypelist
    (outmimetype, outtypelist) ← outputPriority
    outtype ← outtypelist
  } yield ((inmimetype, outmimetype), (intype, outtype))

}
