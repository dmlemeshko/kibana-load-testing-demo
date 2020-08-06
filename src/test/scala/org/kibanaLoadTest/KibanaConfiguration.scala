package org.kibanaLoadTest

import com.typesafe.config.ConfigFactory

class KibanaConfiguration {

  var baseUrl = ""
  var buildVersion = ""
  var isSecurityEnabled = true
  var loginPayload = ""


  def this(configName: String) {
    this()
    val is = getClass.getClassLoader.getResourceAsStream(configName)
    val source = scala.io.Source.fromInputStream(is).mkString
    val config = ConfigFactory.parseString(source)
    this.baseUrl = config.getString("app.host")
    this.buildVersion = config.getString("app.version")
    this.isSecurityEnabled = config.getBoolean("security.on")
    this.loginPayload = config.getString("auth.loginPayload")
  }

}
