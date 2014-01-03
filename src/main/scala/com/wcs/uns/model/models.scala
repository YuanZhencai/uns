package com.wcs.uns.model

import spray.httpx.marshalling.Marshaller
import spray.http.MediaTypes._
import spray.http.HttpEntity
import spray.httpx.unmarshalling.Unmarshaller

case class UserMessage(sys: String, key: String, `type`: String, email: String, telno: String, pernr: String, subject: String, body: String, aux: String)
case class Notification(id: String, sys: String, ts: String, status: String, rejectReason: String, msg: UserMessage)
case class EmailSent(sys: String, ts: String, id: String)
case class SmsSent(sys: String, ts: String, recipient: String, aux: String, id: String)

// for RESTful responses (DeleteMsgRejected)
case class MsgCleared(cleared: Int)

case class ValidationException(reason: String) extends Exception(reason)

object JsonCodecs {
  import argonaut._, Argonaut._
  implicit def UserMessageCodec = casecodec9(UserMessage.apply, UserMessage.unapply)("sys", "key", "type", "email", "telno", "pernr", "subject", "body", "aux")
  implicit def NotificationCodec = casecodec6(Notification.apply, Notification.unapply)("id", "sys", "ts", "status", "reason", "msg")
  implicit def MsgClearedCodec = casecodec1(MsgCleared.apply, MsgCleared.unapply)("cleared")
  implicit def EmailSentCodec = casecodec3(EmailSent.apply, EmailSent.unapply)("sys", "ts", "id")
  implicit def SmsSentCodec = casecodec5(SmsSent.apply, SmsSent.unapply)("sys", "ts", "recipient", "aux", "id")
  implicit val NotificationMarshaller = Marshaller.of[Notification](`application/json`) { (value, contentType, ctx) => ctx.marshalTo(HttpEntity(contentType, value.asJson.toString)) }
  implicit val NotificationUnmarshaller = Unmarshaller.delegate[String, Notification](`application/json`) { str => str.decodeOption[Notification].get }
  implicit val NotificationListMarshaller = Marshaller.of[List[Notification]](`application/json`) { (value, contentType, ctx) => ctx.marshalTo(HttpEntity(contentType, value.asJson.toString)) }
  implicit val UserMessageMarshaller = Marshaller.of[UserMessage](`application/json`) { (value, contentType, ctx) => ctx.marshalTo(HttpEntity(contentType, value.asJson.toString)) }
  implicit val UserMessageUnmarshaller = Unmarshaller.delegate[String, UserMessage](`application/json`) { str => str.decodeOption[UserMessage].get }
  implicit val MsgClearedMarshaller = Marshaller.of[MsgCleared](`application/json`) { (value, contentType, ctx) => ctx.marshalTo(HttpEntity(contentType, value.asJson.toString)) }
}

