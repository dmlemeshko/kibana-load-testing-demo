package com.kibanaTest

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.DurationInt
import org.kibanaLoadTest.{Helper, HttpHelper, KibanaConfiguration}
import java.util.Calendar

class DemoJourney extends Simulation {

  val env = Option(System.getenv("env")).getOrElse("local")

  println(s"Running Kibana ${env} config")
  val appConfig = new KibanaConfiguration(s"config/${env}.conf")
  println(appConfig.baseUrl)
  println(appConfig.buildVersion)
  println(appConfig.isSecurityEnabled)
  println(appConfig.loginPayload)

  val discoverPayload = Helper.loadJsonString("data/discoverPayload.json")
  val discoverPayloadQ1 = discoverPayload
    .replaceAll("(?<=\"gte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -1))
    .replaceAll("(?<=\"lte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, 0))

  val discoverPayloadQ2 = discoverPayload
    .replaceAll("(?<=\"gte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -14))
    .replaceAll("(?<=\"lte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, 14))

  val discoverPayloadQ3 = discoverPayload
    .replaceAll("(?<=\"gte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, -1000))
    .replaceAll("(?<=\"lte\":\")(.*)(?=\",)", Helper.getDate(Calendar.DAY_OF_MONTH, 0))


  val httpProtocol = http
    .baseUrl(appConfig.baseUrl)
    .inferHtmlResources(BlackList(""".*\.js""", """.*\.css""", """.*\.gif""", """.*\.jpeg""", """.*\.jpg""", """.*\.ico""", """.*\.woff""", """.*\.woff2""", """.*\.(t|o)tf""", """.*\.png""", """.*detectportal\.firefox\.com.*"""), WhiteList())
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-GB,en-US;q=0.9,en;q=0.8")
    //.upgradeInsecureRequestsHeader("1")
    .userAgentHeader("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")

  var defaultHeaders = Map(
    "Connection" -> "keep-alive",
    "kbn-version" -> appConfig.buildVersion,
    "Content-Type" -> "application/json",
    "Accept" -> "*/*",
    "Origin" -> appConfig.baseUrl,
    "Sec-Fetch-Site" -> "same-origin",
    "Sec-Fetch-Mode" -> "cors",
    "Sec-Fetch-Dest" -> "empty"
  )

  var defaultTextHeaders  = Map("Content-Type" -> "text/html; charset=utf-8")

  val loginHeaders = Map(
    "Content-Type" -> "application/json",
    "kbn-xsrf" -> "xsrf"
  )

  if (appConfig.isSecurityEnabled) {
    defaultHeaders += ("Cookie" -> "${Cookie}")
    defaultTextHeaders += ("Cookie" -> "${Cookie}")
  }

  val scn = scenario("Discover & Dashboard Journey")
    .doIf(appConfig.isSecurityEnabled) {
      exec(http("login")
        .post("/internal/security/login")
        .headers(loginHeaders)
        .body(StringBody(appConfig.loginPayload)).asJson
        .check(headerRegex("set-cookie", ".+?(?=;)").saveAs("Cookie"))
        .check(status.is(204)))
    }
    .exitHereIfFailed
    .exec(http("visit Home")
      .get("/app/home")
      .headers(defaultTextHeaders)
      .check(status.is(200)))
    .pause(5 seconds)
    .exec(http("visit Discover")
      .get("/app/discover")
      .headers(defaultTextHeaders)
      .check(status.is(200)))
    .exec(http("Discover query 1")
      .post("/internal/search/es")
      .headers(defaultHeaders)
      .header("Referer", appConfig.baseUrl + "/app/discover")
        .body(StringBody(discoverPayloadQ1)).asJson
      .check(status.is(200)))
    .pause(5 seconds)
    .exec(http("Discover query 2")
      .post("/internal/search/es")
      .headers(defaultHeaders)
      .header("Referer", appConfig.baseUrl + "/app/discover")
      .body(StringBody(discoverPayloadQ2)).asJson
      .check(status.is(200)))
    .pause(5 seconds)
    .exec(http("Discover query 3")
      .post("/internal/search/es")
      .headers(defaultHeaders)
      .header("Referer", appConfig.baseUrl + "/app/discover")
      .body(StringBody(discoverPayloadQ3)).asJson
      .check(status.is(200)))
    .pause(5 seconds)
    .exec(http("visit Dashboards")
      .get("/app/dashboards")
      .headers(defaultTextHeaders)
      .check(status.is(200)))
    .exec(http("query indexPattern")
      .get("/api/saved_objects/_find")
      .queryParam("fields", "title")
      .queryParam("per_page", "10000")
      .queryParam("type", "index-pattern")
      .headers(defaultHeaders)
      .header("Referer", appConfig.baseUrl + "/app/dashboards")
      .check(status.is(200))
      .check(jsonPath("$.saved_objects[?(@.type=='index-pattern')].id").saveAs("indexPatternId")))
      .exitBlockOnFail {
        exec(http("query dashboard list")
          .get("/api/saved_objects/_find")
          .queryParam("default_search_operator", "AND")
          .queryParam("page", "1")
          .queryParam("per_page", "1000")
          .queryParam("search_fields", "title%5E3")
          .queryParam("search_fields", "description")
          .queryParam("type", "dashboard")
          .headers(defaultHeaders)
          .header("Referer", appConfig.baseUrl + "/app/dashboards")
          .check(jsonPath("$.saved_objects[:1].id").saveAs("dashboardId"))
          .check(status.is(200)))
          .pause(2 seconds)
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
            .header("Referer", appConfig.baseUrl + "/app/dashboards")
            .check(
              jsonPath("$.saved_objects[0].references[?(@.type=='visualization')]")
                .findAll
                .transform(_.map(_.replaceAll("\"name(.+?),", ""))) //remove name attribute
                .saveAs("vizVector"))
            .check(
              jsonPath("$.saved_objects[0].references[?(@.type=='map' || @.type=='search')]")
                .findAll
                .transform(_.map(_.replaceAll("\"name(.+?),", ""))) //remove name attribute
                .saveAs("searchAndMapVector"))
            .check(status.is(200)))
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
            .header("Referer", appConfig.baseUrl + "/app/dashboards")
            .check(status.is(200)))
          .exec(http("query search & map")
            .post("/api/saved_objects/_bulk_get")
            .body(StringBody(
              """[ ${searchAndMapString} ]""".stripMargin)).asJson
            .headers(defaultHeaders)
            .header("Referer", appConfig.baseUrl + "/app/dashboards")
            .check(status.is(200)))
          .exec(http("query timeseries data")
            .post("/api/metrics/vis/data")
            .body(ElFileBody("data/timeSeriesPayload.json")).asJson
            .headers(defaultHeaders)
            .header("Referer", appConfig.baseUrl + "/app/dashboards")
            .check(status.is(200)))
          .exec(http("query gauge data")
            .post("/api/metrics/vis/data")
            .body(ElFileBody("data/gaugePayload.json")).asJson
            .headers(defaultHeaders)
            .header("Referer", appConfig.baseUrl + "/app/dashboards")
            .check(status.is(200)))
      }

  before {
    // load sample data
    new HttpHelper(appConfig)
      .loginIfNeeded()
      .addSampleData("ecommerce")
      .closeConnection()
  }

  after {
    // remove sample data
    new HttpHelper(appConfig)
      .loginIfNeeded()
      .removeSampleData("ecommerce")
      .closeConnection()
  }

  setUp(
    scn.inject(
      nothingFor(5 seconds),
      atOnceUsers(10),
      rampUsers(30) during (15 seconds),
      constantUsersPerSec(10) during (30 seconds),
      rampUsersPerSec(5) to 10 during (1 minutes)
    ).protocols(httpProtocol)
  ).maxDuration(10 minutes)
}