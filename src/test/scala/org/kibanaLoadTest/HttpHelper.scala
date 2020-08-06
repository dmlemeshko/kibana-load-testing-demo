package org.kibanaLoadTest

import org.apache.http.client.methods.{HttpDelete, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder

class HttpHelper(baseUrl: String, isSecurityEnabled: Boolean, loginPayload: String, buildVersion: String){

  private val httpClient = HttpClientBuilder.create.build

  val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )

  def loginIfNeeded(): HttpHelper = {
    if (isSecurityEnabled) {
      val loginRequest = new HttpPost(baseUrl + "/internal/security/login")
      loginHeaders foreach {case (key, value) => loginRequest.addHeader(key, value)}
      loginRequest.setEntity(new StringEntity(loginPayload))
      val loginResponse = httpClient.execute(loginRequest)

      if (loginResponse.getStatusLine.getStatusCode != 204) {
        throw new RuntimeException("Login to Kibana failed")
      }
    }
    this
  }

  def removeSampleData(data: String): HttpHelper = {
    val sampleDataRequest = new HttpDelete(baseUrl + s"/api/sample_data/${data}")
    sampleDataRequest.addHeader("Connection", "keep-alive")
    sampleDataRequest.addHeader("kbn-version", buildVersion)

    val sampleDataResponse = httpClient.execute(sampleDataRequest)

    if (sampleDataResponse.getStatusLine.getStatusCode != 204) {
      println("Deleting sample data failed")
    }
    this
  }

  def addSampleData(data: String): HttpHelper = {
    val sampleDataRequest = new HttpPost(baseUrl + s"/api/sample_data/${data}")
    sampleDataRequest.addHeader("Connection", "keep-alive")
    sampleDataRequest.addHeader("kbn-version", buildVersion)

    val sampleDataResponse = httpClient.execute(sampleDataRequest)

    if (sampleDataResponse.getStatusLine.getStatusCode != 200) {
      println("Adding sample data failed")
    }
    this
  }

  def closeConnection(): Unit = {
    httpClient.close()
  }

}