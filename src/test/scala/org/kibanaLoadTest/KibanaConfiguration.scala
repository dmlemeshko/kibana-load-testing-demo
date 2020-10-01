package org.kibanaLoadTest

import com.typesafe.config.ConfigFactory

class KibanaConfiguration {

  var baseUrl = ""
  var buildVersion = ""
  var isSecurityEnabled = true
  var username = ""
  var password = ""
  var loginPayload = ""
  var loginStatusCode = 200
  var isAbove79x = false

  def this(configName: String) {
    this()
    val is = getClass.getClassLoader.getResourceAsStream(configName)
    val source = scala.io.Source.fromInputStream(is).mkString
    val config = ConfigFactory.parseString(source)
    this.baseUrl = Option(System.getenv("host")).getOrElse(config.getString("app.host"))
    this.buildVersion = Option(System.getenv("version")).getOrElse(config.getString("app.version"))
    this.isSecurityEnabled = config.getBoolean("security.on")
    this.username = Option(System.getenv("username")).getOrElse(config.getString("auth.username"))
    this.password = Option(System.getenv("password")).getOrElse(config.getString("auth.password"))
    val newLoginPayload = s"""{"providerType":"${config.getString("auth.providerType")}","providerName":"${config.getString("auth.providerName")}","currentURL":"${this.baseUrl}/login","params":{"username":"${this.username}","password":"${this.password}"}}"""
    val oldLoginPayload = s"""{"username":"${this.username}","password":"${this.password}"}"""
    this.isAbove79x = new Version(this.buildVersion).isAbove79x
    this.loginPayload = if (this.isAbove79x) newLoginPayload else oldLoginPayload
    this.loginStatusCode = if (this.isAbove79x)  200 else 204
  }
}
