package com.kibanaTest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import org.apache.http.client.methods.HttpPost

import scala.concurrent.duration.DurationInt
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import java.nio.charset.StandardCharsets

import org.apache.http.entity.StringEntity

import org.kibanaLoadTest.AppConfig

class DemoJourney extends Simulation {
  val httpProtocol = http
    .baseUrl(AppConfig.baseUrl)
    .inferHtmlResources(BlackList(""".*\.js""", """.*\.css""", """.*\.gif""", """.*\.jpeg""", """.*\.jpg""", """.*\.ico""", """.*\.woff""", """.*\.woff2""", """.*\.(t|o)tf""", """.*\.png""", """.*detectportal\.firefox\.com.*"""), WhiteList())
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-GB,en-US;q=0.9,en;q=0.8")
    //.upgradeInsecureRequestsHeader("1")
    .userAgentHeader("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")

  var defaultHeaders = Map(
    "Connection" -> "keep-alive",
    "kbn-version" -> AppConfig.buildVersion,
    "Content-Type" -> "application/json",
    "Accept" -> "*/*",
    "Origin" -> AppConfig.baseUrl,
    "Sec-Fetch-Site" -> "same-origin",
    "Sec-Fetch-Mode" -> "cors",
    "Sec-Fetch-Dest" -> "empty"
  )

  var defaultTextHeaders  = Map("Content-Type" -> "text/html; charset=utf-8")

  val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )

  if (AppConfig.isSecurityEnabled) {
    defaultHeaders += ("Cookie" -> "${Cookie}")
    defaultTextHeaders += ("Cookie" -> "${Cookie}")
  }

  val scn = scenario("Discover & Dashboard Journey")
    .doIf(AppConfig.isSecurityEnabled) {
      exec(http("login")
        .post("/internal/security/login")
        .headers(loginHeaders)
        .body(StringBody(AppConfig.loginPayload)).asJson
        .check(headerRegex("set-cookie", ".+?(?=;)").saveAs("Cookie"))
        .check(status.is(204)))
    }
    .exitHereIfFailed
    .pause(5)
    .exec(http("visit Home")
      .get("/app/home")
      .headers(defaultTextHeaders))
    .pause(5)
    .exec(http("visit Discover")
      .get("/app/discover")
      .headers(defaultTextHeaders))
    .exec(http("default Discover query")
      .post("/internal/search/es")
      .headers(defaultHeaders)
      .header("Referer", AppConfig.baseUrl + "/app/discover")
      .body(ElFileBody("data/discoverPayload.json")).asJson)
    .pause(5)
    .exec(http("visit Dashboards")
      .get("/app/dashboards")
      .headers(defaultTextHeaders))
    .exec(http("query indexPattern")
      .get("/api/saved_objects/_find")
      .queryParam("fields", "title")
      .queryParam("per_page", "10000")
      .queryParam("type", "index-pattern")
      .headers(defaultHeaders)
      .header("Referer", AppConfig.baseUrl + "/app/dashboards")
      .check(jsonPath("$.saved_objects[?(@.type=='index-pattern')].id").saveAs("indexPatternId")))
    .exec(http("query dashboard list")
      .get("/api/saved_objects/_find")
      .queryParam("default_search_operator", "AND")
      .queryParam("page", "1")
      .queryParam("per_page", "1000")
      .queryParam("search_fields", "title%5E3")
      .queryParam("search_fields", "description")
      .queryParam("type", "dashboard")
      .headers(defaultHeaders)
      .header("Referer", AppConfig.baseUrl + "/app/dashboards")
      .check(jsonPath("$.saved_objects[:1].id").saveAs("dashboardId")))
    .pause(5)
    .exec(http("query panels list")
      .post("/api/saved_objects/_bulk_get")
      .body(StringBody(
        """
          [
            {
              "id":"${dashboardId}",
              "type":"dashboard"
            }
          ]
        """
      )).asJson
      .headers(defaultHeaders)
      .header("Referer", AppConfig.baseUrl + "/app/dashboards")
      .check(
        jsonPath("$.saved_objects[0].references[?(@.type=='visualization')]")
          .findAll
          .transform(_.map(_.replaceAll("\"name(.+?),", ""))) //remove name attribute
          .saveAs("vizVector"))
      .check(
        jsonPath("$.saved_objects[0].references[?(@.type=='map' || @.type=='search')]")
          .findAll
          .transform(_.map(_.replaceAll("\"name(.+?),", ""))) //remove name attribute
          .saveAs("searchAndMapVector")))
    .exec(session =>
      //convert Vector -> String
      session.set("vizListString", session("vizVector").as[Seq[String]].mkString(",")))
    .exec(session => {
      //convert Vector -> String
      session.set("searchAndMapString", session("searchAndMapVector").as[Seq[String]].mkString(","))
    })
    .exec(http("query visualizations")
      .post("/api/saved_objects/_bulk_get")
      .body(StringBody("[" +
        "${vizListString}"
          .concat(", { \"id\":\"${indexPatternId}\", \"type\":\"index-pattern\"  }]"))).asJson
      .headers(defaultHeaders)
      .header("Referer", AppConfig.baseUrl + "/app/dashboards"))
    .exec(http("query search & map")
      .post("/api/saved_objects/_bulk_get")
      .body(StringBody(
        """[ ${searchAndMapString} ]""".stripMargin)).asJson
      .headers(defaultHeaders)
      .header("Referer", AppConfig.baseUrl + "/app/dashboards"))
    .exec(http("query timeseries data")
      .post("/api/metrics/vis/data")
      .body(ElFileBody("data/timeSeriesPayload.json")).asJson
      .headers(defaultHeaders)
      .header("Referer", AppConfig.baseUrl + "/app/dashboards"))
    .exec(http("query gauge data")
      .post("/api/metrics/vis/data")
      .body(ElFileBody("data/gaugePayload.json")).asJson
      .headers(defaultHeaders)
      .header("Referer", AppConfig.baseUrl + "/app/dashboards"))


  before {
    // load sample data
    val httpClient = HttpClientBuilder.create.build

    if (AppConfig.isSecurityEnabled) {
      val loginRequest = new HttpPost(AppConfig.baseUrl + "/internal/security/login")
      loginHeaders foreach {case (key, value) => loginRequest.addHeader(key, value)}
      loginRequest.setEntity(new StringEntity(AppConfig.loginPayload))
      val loginResponse = httpClient.execute(loginRequest)

      if (loginResponse.getStatusLine.getStatusCode != 204) {
        throw new RuntimeException("Login to Kibana failed")
      }
    }

    val sampleDataRequest = new HttpPost(AppConfig.baseUrl + "/api/sample_data/ecommerce")
    sampleDataRequest.addHeader("Connection", "keep-alive")
    sampleDataRequest.addHeader("kbn-version", AppConfig.buildVersion)

    val sampleDataResponse = httpClient.execute(sampleDataRequest)

    if (sampleDataResponse.getStatusLine.getStatusCode != 200) {
      throw new RuntimeException("Loading sample data failed")
    }

    println("Response body: " + EntityUtils.toString(sampleDataResponse.getEntity, StandardCharsets.UTF_8))

    // close connection
    httpClient.close()
  }

  setUp(
    scn.inject(
      nothingFor(5 seconds),
      atOnceUsers(20),
      rampUsers(10) during (5 seconds),
      constantUsersPerSec(15) during (20 seconds),
      rampUsersPerSec(5) to 10 during (1 minutes)
    ).protocols(httpProtocol)
  )
}