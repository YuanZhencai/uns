package com.wcs.uns

import akka.kernel.Bootable

import spray.can.Http
import com.typesafe.config.ConfigFactory
import akka.actor.{ActorSystem, Props}
import com.wcs.uns.service._
import akka.camel.CamelExtension

import akka.io.IO

import org.apache.activemq.camel.component.ActiveMQComponent
import com.wcs.uns.api.RestActor
import com.wcs.uns.model.{UserMessage, Notification, SendNotification}

class Bootstrapper extends Bootable {

  val config = ConfigFactory.load()
  implicit val system = ActorSystem(s"uns2-${config.getString("env.tag")}")

  def startup = {
    val camel = CamelExtension(system)
    camel.context.addComponent("activemq", ActiveMQComponent.activeMQComponent(config.getString("activemq.url")))
    val notifier = system.actorOf(Props[Notifier], "notifier")
    val jmsAdapter = system.actorOf(Props[JmsAdapter], "jms-adapter")
    notifier ! SendNotification(Notification(java.util.UUID.randomUUID().toString, "cmdpms", System.currentTimeMillis().toString, "0", "", UserMessage("cmdpms", "QWEASD123", "1", "gaoyuxiang@wcs-global.com", "13817145717", "", s"UNS2邮件发送 - ${config.getString("env.tag")}", s"消息来自: ${java.net.InetAddress.getLocalHost} - ${config.getString("env.tag")}", "")))
    val server = system.actorOf(Props[RestActor], "server")
    IO(Http) ! Http.Bind(server, config.getString("rest.listening"), config.getInt("rest.port"))
  }

  def shutdown = {
    system.shutdown()
  }
}
