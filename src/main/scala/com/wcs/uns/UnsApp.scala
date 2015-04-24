package com.wcs.uns

import akka.actor.{Props, ActorSystem}
import akka.camel.CamelExtension
import akka.io.IO
import com.typesafe.config.ConfigFactory
import com.wcs.uns.api.RestActor
import com.wcs.uns.model.{UserMessage, Notification, SendNotification}
import com.wcs.uns.service.{JmsAdapter, Notifier}
import org.apache.activemq.camel.component.ActiveMQComponent
import spray.can.Http

/**
 * Created by Yuan on 2015/4/24.
 */
object UnsApp {
  val config = ConfigFactory.load()
  implicit val system = ActorSystem(s"uns2-${config.getString("env.tag")}")

  def main(args: Array[String]) {
    startup
  }

  def startup = {
    val camel = CamelExtension(system)
    camel.context.addComponent("activemq", ActiveMQComponent.activeMQComponent(config.getString("activemq.url")))
    val notifier = system.actorOf(Props[Notifier], "notifier")
    val jmsAdapter = system.actorOf(Props[JmsAdapter], "jms-adapter")
    notifier ! SendNotification(Notification(java.util.UUID.randomUUID().toString, "cmdpms", System.currentTimeMillis().toString, "0", "", UserMessage("cmdpms", "QWEASD123", "1", "zhencai.yuan@sunlights.cc", "18321718279", "", s"UNS2邮件发送 - ${config.getString("env.tag")}", s"消息来自: ${java.net.InetAddress.getLocalHost} - ${config.getString("env.tag")}", "")))
    val server = system.actorOf(Props[RestActor], "server")
    IO(Http) ! Http.Bind(server, config.getString("rest.listening"), config.getInt("rest.port"))
  }

  def shutdown = {
    system.shutdown()
  }
}
