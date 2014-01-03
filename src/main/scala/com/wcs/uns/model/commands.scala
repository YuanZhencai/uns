package com.wcs.uns.model

// for Notifier (EmailSender & SmsSender)
case class SendNotification(notification: Notification)
case class IgnoreNotification(notification: Notification)
case class SendEmail(notification: Notification)
case class SendSms(notification: Notification)
case class SendSingleSms(sys: String, recipient: String, aux:String, content: String, id: String)

// for BookKeeper (DbReader & DbWriter)
case class NewMsg(notification: Notification)
case class IgnoreMsg(id: String)
case class ProcessMsg(id: String)  // new design does not care about hybrid messages (email + sms)
case class RejectMsg(id: String, reason: String)  // new design does not care about hybrid messages (email + sms)
case class DeleteMsg(id: String)
case class LogEmailSent(sys: String, ts: String, msgId: String)
case class LogSmsSent(sys: String, ts: String, recipient: String, aux: String, msgId: String)

// for user queries & updates
case class GetMsgRejected(sys: String, ts: String) // old design specifies there be a "head" element as {"ts":"..."}. new design deprecates this.
case class DeleteMsgRejected(sys: String, ts: String)
case class GetMsgInProgress(sys: String)
case class GetMsgSent(sys: String, ts: String)
case class GetMsgIgnored(sys: String, ts: String)

