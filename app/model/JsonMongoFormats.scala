package model

import play.api.libs.json._


object JsonMongoFormats {

  def mongoReads[T](r: Reads[T]) = {
    __.json.update((__ \ 'id).json.copyFrom((__ \ '_id \ '$oid).json.pick[JsString])) andThen r
  }

  def mongoWrites[T](w: Writes[T]) = {
    w.transform(js => (js \ "id").toOption match {
      case Some(id) => js.as[JsObject] - "id" ++ Json.obj("_id" -> Json.obj("$oid" -> id))
      case None => js
    })
  }

  def mongoFormats[T](format: Format[T]) = Format(mongoReads(format), mongoWrites(format))
}