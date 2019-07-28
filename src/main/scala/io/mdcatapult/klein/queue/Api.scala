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
import scala.concurrent.{ExecutionContext, Future}

class Api(config: Config)(
  implicit actorSystem: ActorSystem, materialiser: ActorMaterializer, ex: ExecutionContext
){

  sealed case class CacheEntry(ttl: LocalDateTime, queues: List[String])

  val cache: mutable.Map[String, CacheEntry] = mutable.Map[String, CacheEntry]()
  lazy val httpClient = StandaloneAhcWSClient(AhcWSClientConfigFactory.forConfig(config))

  def list_queues(exchange: String, ttl: Option[Int] = Some(3600)): Future[List[String]] = {

    val conf: Config = config.getConfig("op-rabbit.connection")
    val host: String = conf.getStringList("hosts").asScala.head
    val port: Int = conf.getInt("management-port")
    val vhost: String = URLEncoder.encode(conf.getString("virtual-host"), "UTF-8")

    val now = LocalDateTime.now()
    val key = s"$vhost/$exchange"
    val entry = cache.getOrElse(key, CacheEntry(now, List[String]()))

    if (entry.ttl.isBefore(now) || entry.queues.isEmpty) {
      val endpoint = f"http://$host:$port/api/exchanges/$key/bindings/source"
      httpClient.url(endpoint).withAuth(
        conf.getString("username"),
        conf.getString("password"),
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
