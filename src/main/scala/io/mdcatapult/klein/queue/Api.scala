package io.mdcatapult.klein.queue

import java.net.URLEncoder
import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.json.{JsString, JsValue}
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.ahc._
import play.api.libs.ws.WSAuthScheme

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.Future

class Api() {

  sealed case class CacheEntry(ttl: LocalDateTime, queues: List[String])

  val cache: mutable.Map[String, CacheEntry] = mutable.Map[String, CacheEntry]()

  import scala.concurrent.ExecutionContext.Implicits._
  implicit val system: ActorSystem = ActorSystem("consumer-prefetch-http-client")
  implicit val materialiser: ActorMaterializer = ActorMaterializer()

  val config: Config = ConfigFactory.load().getConfig("op-rabbit.connection")
  val host: String = config.getStringList("hosts").asScala.head
  val port: Int = config.getInt("management-port")
  val vhost: String = URLEncoder.encode(config.getString("virtual-host"), "UTF-8")
  val httpClient = StandaloneAhcWSClient()

  def list_queues(exchange: String, ttl: Option[Int] = Some(3600)): Future[List[String]] = {

    val now = LocalDateTime.now()
    val key = s"$vhost/$exchange"
    val entry = cache.getOrElse(key, CacheEntry(now, List[String]()))

    if (entry.ttl.isBefore(now) || entry.queues.isEmpty) {
      val endpoint = f"http://$host:$port/api/exchanges/$key/bindings/source"
      httpClient.url(endpoint).withAuth(
        config.getString("username"),
        config.getString("password"),
        WSAuthScheme.BASIC
      ).get().map({
        r =>
          val qs: List[String] = (r.body[JsValue] \\ "destination").toList.flatMap({
            case v: JsString => Some(v.value)
            case _ ⇒ None
          })
          // caches for an hour
          cache(key) = CacheEntry(LocalDateTime.now().plusHours(1), qs)
          qs
      })
    } else {
      Future.successful(entry.queues)
    }
  }
}
