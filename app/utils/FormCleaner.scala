package utils

import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

object FormCleaner {
  def clean(form: String) =
    Jsoup.clean(form, whiteList)

  val whiteList = Whitelist.basic()
    .addTags("input", "textarea", "select", "option", "lavel")
    .addAttributes("input", "name")
    .addAttributes("textarea", "name")
    .addAttributes("select", "name")
    .addAttributes("option", "name")
    .addAttributes("input", "name")
    .addAttributes("input", "name")
    .addAttributes("input", "type")
}
