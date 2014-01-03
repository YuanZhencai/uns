package com.wcs.uns.service

import akka.actor.{Props, Actor, ActorLogging}
import com.wcs.uns.model._
import com.typesafe.config.ConfigFactory
import com.mongodb.casbah.Imports._
import com.wcs.uns.model.ProcessMsg
import com.wcs.uns.model.LogEmailSent
import com.wcs.uns.model.LogSmsSent
import com.wcs.uns.model.RejectMsg
import com.wcs.uns.model.DeleteMsg
import com.wcs.uns.model.NewMsg
import com.wcs.uns.model.Notification
import com.wcs.uns.model.JsonCodecs._
import com.mongodb.util.JSON

/**
 * case class NewMsg(notification: Notification)
 * case class ProcessMsg(id: String)  // new design does not care about hybrid messages (email + sms)
 * case class RejectMsg(id: String, reason: String)  // new design does not care about hybrid messages (email + sms)
 * case class DeleteMsg(id: String)
 * case class LogEmailSent(sys: String, ts: String, msgId: String)
 * case class LogSmsSent(sys: String, ts: String, recipient: String, msgId: String)
 *
 * case class GetMsgRejected(sys: String, ts: String) // old design specifies there be a "head" element as {"ts":"..."}. new design deprecates this.
 * case class DeleteMsgRejected(sys: String, ts: String)
 * case class GetMsgInProgress(sys: String, ts: String)
 * case class GetMsgSent(sys: String, ts: String)
 * case class GetMsgIgnored(sys: String, ts: String)
 */
class BookKeeper extends Actor with ActorLogging {
  import argonaut._, Argonaut._
  val config = context.system.settings.config
  val writer = context.actorOf(Props[DbWriter], "db-writer")
  val mc = MongoClient()
  val db = s"uns${config.getString("env.tag")}"
  val notifications = mc(db)("notifications")
  def receive = {
    case NewMsg(notification) =>
      log.info("New notification received: " + notification)
      writer ! notification
    case IgnoreMsg(id) =>
      log.info("Ignored message id: " + id)
      writer ! IgnoreMsg(id)
    case ProcessMsg(id) =>
      log.info("Processing message id: " + id)
      writer ! ProcessMsg(id)
    case RejectMsg(id, reason) =>
      log.info("Rejecting message id: " + id)
      writer ! RejectMsg(id, reason)
    case DeleteMsg(id) =>
      log.info("Rejecting message id: " + id)
      writer ! DeleteMsg(id)
    case LogEmailSent(sys, ts, id) =>
      log.info(s"Logging Email sent by [$sys] at [$ts] with id [$id]")
      writer ! EmailSent(sys, ts, id)
    case LogSmsSent(sys, ts, recipient, aux, id) =>
      log.info(s"Logging SMS sent by [$sys] at [$ts] to [$recipient] with id [$id]")
      writer ! SmsSent(sys, ts, recipient, aux, id)
    case command @ GetMsgRejected(sys, ts) =>
      try {
        val q = MongoDBObject("sys" -> sys) ++ ("status" -> "2") ++ ("ts" $gte ts)
        sender ! notifications.find(q).sort(new BasicDBObject("ts", 1)).limit(500).map(_.toString.decodeOption[Notification]).filter(_.nonEmpty).map(_.get).toList
      } catch {
        case ex: Throwable =>
          log.error(ex, s"$command failed.")
      }
    case command @ DeleteMsgRejected(sys, ts) =>
      try {
        val q = MongoDBObject("sys" -> sys) ++ ("status" -> "2") ++ ("ts" $lte ts)
        val res = notifications.remove(q)
        sender ! MsgCleared(res.getN)
      } catch {
        case ex: Throwable =>
          log.error(ex, s"$command failed.")
      }
    case command @ GetMsgInProgress(sys) =>
      try {
        val q = MongoDBObject("sys" -> sys) ++ ("status" -> "0")
        sender ! notifications.find(q).sort(new BasicDBObject("ts", 1)).map(_.toString.decodeOption[Notification]).filter(_.nonEmpty).map(_.get).toList
      } catch {
        case ex: Throwable =>
          log.error(ex, s"$command failed.")
      }
    case command @ GetMsgSent(sys, ts) =>
      try {
        val q = MongoDBObject("sys" -> sys) ++ ("status" -> "1") ++ ("ts" $gte ts)
        sender ! notifications.find(q).sort(new BasicDBObject("ts", 1)).limit(500).map(_.toString.decodeOption[Notification]).filter(_.nonEmpty).map(_.get).toList
      } catch {
        case ex: Throwable =>
          log.error(ex, s"$command failed.")
      }
    case command @ GetMsgIgnored(sys, ts) =>
      try {
        val q = MongoDBObject("sys" -> sys) ++ ("status" -> "-1") ++ ("ts" $gte ts)
        sender ! notifications.find(q).sort(new BasicDBObject("ts", 1)).limit(500).map(_.toString.decodeOption[Notification]).filter(_.nonEmpty).map(_.get).toList
      } catch {
        case ex: Throwable =>
          log.error(ex, s"$command failed.")
      }
    case x =>
      log.error(s"Unrecognized message received: $x.")
  }

}

class DbWriter extends Actor with ActorLogging {
  import argonaut._, Argonaut._
  val config = context.system.settings.config
  val mc = MongoClient()
  val db = s"uns${config.getString("env.tag")}"
  val notifications = mc(db)("notifications")
  val emaillog = mc(db)("emaillog")
  val smslog = mc(db)("smslog")
  def receive = {
    case notification: Notification =>
      try {
        val r = JSON.parse(notification.asJson.toString).asInstanceOf[DBObject]
        notifications.insert(r)
      } catch {
        case x: Throwable =>
          log.error(x, s"Failed to parse or update data $notification")
      }
      log.info(s"New notification saved: $notification.")
    case IgnoreMsg(id) =>
      try {
        val q = new BasicDBObject("id", id)
        val u = new BasicDBObject("$set", new BasicDBObject("status", "-1"))
        notifications.update(q, u)
      } catch {
        case x: Throwable =>
          log.error(x, s"Failed to parse or update notification [$id].")
      }
      log.info(s"Message id [$id] processed.")
    case ProcessMsg(id) =>
      try {
        val q = new BasicDBObject("id", id)
        val u = new BasicDBObject("$set", new BasicDBObject("status", "1"))
        notifications.update(q, u)
      } catch {
        case x: Throwable =>
          log.error(x, s"Failed to parse or update notification [$id].")
      }
      log.info(s"Message id [$id] processed.")
    case RejectMsg(id, reason) =>
      try {
        val q = new BasicDBObject("id", id)
        val u = new BasicDBObject("$set", new BasicDBObject("status", "2").append("reason", reason))
        notifications.update(q, u)
      } catch {
        case x: Throwable =>
          log.error(x, s"Failed to parse or update notification [$id].")
      }
      log.info(s"Message id [$id] rejected.")
    case DeleteMsg(id) =>
      try {
        val q = new BasicDBObject("id", id)
        val r = notifications.findOne(q).flatMap(_.toString.decodeOption[Notification])
        notifications.findAndRemove(q)
      } catch {
        case x: Throwable =>
          log.error(x, s"Failed to parse or update notification [$id].")
      }
      log.info(s"Message id [$id] deleted.")
    case event @ EmailSent(sys, ts, id) =>
      try {
        val r = JSON.parse(event.asJson.toString).asInstanceOf[DBObject]
        emaillog.insert(r)
      } catch {
        case x: Throwable =>
          log.error(x, s"Failed to parse or update data $event")
      }
      log.info(s"Event $event saved.")
    case event @ SmsSent(sys, ts, recipient, aux, id) =>
      try {
        val r = JSON.parse(event.asJson.toString).asInstanceOf[DBObject]
        smslog.insert(r)
      } catch {
        case x: Throwable =>
          log.error(x, s"Failed to parse or update data $event")
      }
      log.info(s"Event $event saved.")
    case x =>
      log.error(s"Unrecognized message received: $x.")
  }

}
