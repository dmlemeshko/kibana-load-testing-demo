package org.kibanaLoadTest

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

import com.typesafe.config.ConfigFactory

import scala.io.Source

object Helper {

  def getDate(fieldNumber: Int, shift: Int): String = {
    val c: Calendar = Calendar.getInstance
    c.add(fieldNumber, -7)
    val dtf = DateTimeFormatter
      .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      .withZone(ZoneId.systemDefault())
    dtf.format(c.getTime().toInstant)
  }

  def loadJsonString(filePath: String): String = {
    Source.fromURL(getClass.getClassLoader.getResource(filePath)).getLines.mkString
  }

  def loadKibanaConfig(configName: String): Object = {
    val is = getClass.getClassLoader.getResourceAsStream("config/cloud.conf")
    val source = scala.io.Source.fromInputStream(is).mkString
    val config = ConfigFactory.parseString(source)

    object appConfig {
      val baseUrl = config.getString("app.host")
      val buildVersion = config.getString("app.version")
      val isSecurityEnabled = config.getBoolean("security.on")
      val loginPayload = config.getString("auth.loginPayload")
    }

    appConfig
  }

}
