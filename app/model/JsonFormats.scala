package model

import play.json.extra.Variants

/**
 * Created by yaroslav on 22.11.2015.
 */
object JsonFormats {

  import model.State
  import play.api.libs.json._
  implicit val fileRecordFormat: Format[FileRecord] = Json.format[FileRecord]
  implicit val stateFormat: Format[State] = Variants.format[State]((__ \ "type").format[String])
}
