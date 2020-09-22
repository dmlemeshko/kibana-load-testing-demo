package org.kibanaLoadTest

import com.typesafe.config.ConfigFactory

class KibanaConfiguration {

  var baseUrl = ""
  var buildVersion = ""
  var isSecurityEnabled = true
  var login = ""
  var password = ""
  var loginPayload = ""


  def this(configName: String) {
    this()
    val is = getClass.getClassLoader.getResourceAsStream(configName)
    val source = scala.io.Source.fromInputStream(is).mkString
    val config = ConfigFactory.parseString(source)
    this.baseUrl = Option(System.getenv("host")).getOrElse(config.getString("app.host"))
    this.buildVersion = Option(System.getenv("version")).getOrElse(config.getString("app.version"))
    this.isSecurityEnabled = config.getBoolean("security.on")
    this.login = Option(System.getenv("login")).getOrElse(config.getString("auth.login"))
    this.password = Option(System.getenv("password")).getOrElse(config.getString("auth.password"))
    this.loginPayload = s"""{"username":"${this.login}","password":"${this.password}"}"""
}

}
