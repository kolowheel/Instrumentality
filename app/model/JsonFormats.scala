package model

import play.api.libs.json.{Format, Json}
import play.json.extra.Variants

/**
 * Created by yaroslav on 22.11.2015.
 */
object JsonFormats {


  import model.State
  implicit val fileRecordFormat:Format[FileRecord] = Json.format[FileRecord]
  implicit val stateFormat: Format[State] = Variants.format[State]
}
