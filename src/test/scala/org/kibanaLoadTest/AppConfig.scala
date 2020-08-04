package org.kibanaLoadTest

object AppConfig {
  val baseUrl = "http://localhost:5620"
  val buildVersion = "8.0.0"
  val isSecurityEnabled = true
  val loginPayload = """{"username":"elastic","password":"changeme"}"""
}
