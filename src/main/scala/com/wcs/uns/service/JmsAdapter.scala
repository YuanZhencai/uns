package com.wcs.uns.service

import akka.actor.ActorLogging
import akka.camel.{ CamelMessage, Consumer }
import argonaut._, Argonaut._
import com.wcs.uns.model.{IgnoreNotification, SendNotification, UserMessage, Notification}

class JmsAdapter extends Consumer with ActorLogging {
  def endpointUri = "activemq:queue:unsQueue"
  val notifier = context.actorSelection("../notifier")
  def receive = {
    case msg: CamelMessage =>
      log.info(s"$msg received from JMS queue.")
      val jmsTimestamp = msg.getHeaders.get("JMSTimestamp").asInstanceOf[Long]
      import com.wcs.uns.model.JsonCodecs.UserMessageCodec
      val messages = msg.bodyAs[String].decodeOption[List[UserMessage]]
      messages match {
        case Some(list) =>
          for (userMessage <- list) {
            val id = java.util.UUID.randomUUID().toString
            val ts = System.currentTimeMillis().toString
            val timediff = System.currentTimeMillis() - jmsTimestamp
            val threshold = context.system.settings.config.getLong("activemq.obsolete-threshold")
            if (timediff > threshold) { // ignore obsolete messages (e.g. older than 1h)
              log.info(s"Obsolete message $msg from JMS queue will be ignored.")
              notifier ! IgnoreNotification(Notification(id, userMessage.sys, ts, "-1", "", userMessage))
            } else {
              notifier ! SendNotification(Notification(id, userMessage.sys, ts, "0", "", userMessage))
            }
          }
        case None =>
          log.error(s"Failed to decode JMS message: $msg.")
      }
    case data: Notification =>
      val msg = data.msg
      log.info(s"$msg succeeded.")
    case x =>
      log.error(s"Unrecognized message received: $x.")
  }
}

class DummyJmsAdapter extends Consumer with ActorLogging {
  def endpointUri = "activemq:queue:dummy"
  def receive = {
    case msg: CamelMessage =>
      log.info(s"$msg received from JMS queue.")
    case x =>
      log.error(s"Unrecognized message received: $x.")
  }
}
