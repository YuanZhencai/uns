package com.wcs.uns.service

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import com.wcs.uns.model._
import com.wcs.uns.model.SendNotification
import com.wcs.uns.model.NewMsg
import com.wcs.uns.model.SendEmail
import com.wcs.uns.model.Notification
import java.net.{HttpURLConnection, URL}
import org.apache.commons.mail.{EmailException, HtmlEmail}

/**
 * case class SendNotification(notification: Notification)
 * case class SendEmail(notification: Notification)
 * case class SendSms(notification: Notification)
 */
class Notifier extends Actor with ActorLogging {
  val bookKeeper = context.actorOf(Props[BookKeeper], "book-keeper")
  val emailSender = context.actorOf(Props(new EmailSender(bookKeeper)), "email-sender")
  val smsSender = context.actorOf(Props(new SmsSender(bookKeeper)), "sms-sender")
  def receive = {
    case SendNotification(notification) =>
      log.info("Received notification: " + notification)
      try {
        bookKeeper ! NewMsg(notification)
        val keyShouldBe = context.system.settings.config.getString(s"syskeys.${notification.sys}")
        if (notification.msg.key != keyShouldBe) throw ValidationException("1")
        val msgType = Integer.parseInt(notification.msg.`type`)
        val doEmail = (msgType & 1) > 0 && context.system.settings.config.getBoolean("email.on")
        val doSms = (msgType & 2) > 0 && context.system.settings.config.getBoolean("sms.on")
        if (doEmail) emailSender ! SendEmail(notification)
        if (doSms) smsSender ! SendSms(notification)
        if (!doEmail && !doSms) bookKeeper ! IgnoreMsg(notification.id)
        sender ! notification
      } catch {
        case ex @ ValidationException(reason) =>
          log.error(ex, s"Validation failed with ${notification.msg}.")
          bookKeeper ! RejectMsg(notification.id, reason)
        case ex: Throwable =>
          log.error(ex, "")
          bookKeeper ! RejectMsg(notification.id, "4")
      }
    case IgnoreNotification(notification) =>
      log.info(s"Ignored notification: $notification.")
      try {
        bookKeeper ! NewMsg(notification)
        bookKeeper ! IgnoreMsg(notification.id)
      } catch {
        case ex: Throwable =>
          log.error(ex, "")
          bookKeeper ! RejectMsg(notification.id, "4")
      }
    case x =>
      log.error(s"Unrecognized message received: $x.")
  }
}

class EmailSender(bookKeeper: ActorRef) extends Actor with ActorLogging {
  val config = context.system.settings.config
  val hostname = config.getString("email.hostname") // "cnln1a.wilmar-intl.com"
  val mailaddr = config.getString("email.mailaddr") // "do_not_reply@wilmar-intl.com"
  val username = config.getString("email.username") // "do_not_reply"
  val password = config.getString("email.password") // "mju76yhn"
  def receive = {
    case SendEmail(notification: Notification) =>
      log.info("Received (email) notification: " + notification)
      try {
        val email = new HtmlEmail()
        email.setHostName(hostname)
        email.setAuthentication(username, password)
        val msg = notification.msg
        if (config.getBoolean("email.override")) {
          email.addTo(config.getString("email.override-addr"))
        } else {
          msg.email.split("\\|") foreach { addr =>
            email.addTo(addr)
          }
        }
        email.setFrom(mailaddr)
        email.setSubject(msg.subject)
        email.setCharset("GB2312")
        email.setHtmlMsg(msg.body)
        email.send()
        bookKeeper ! LogEmailSent(notification.sys, System.currentTimeMillis().toString, notification.id)
        bookKeeper ! ProcessMsg(notification.id)
      } catch {
        case ValidationException(reason) =>
          log.error("ValidationException occurred: " + reason)
          bookKeeper ! RejectMsg(notification.id, reason)
        case ee: EmailException =>
          log.error(s"EmailException occurred: $ee")
          bookKeeper ! RejectMsg(notification.id, "4")
        case ex: Throwable =>
          log.error(ex, "Unexpected exception occurred.")
          bookKeeper ! RejectMsg(notification.id, "4")
      }
    case x =>
      log.error(s"Unrecognized message received: $x.")
  }
}

class SmsSender(bookKeeper: ActorRef) extends Actor with ActorLogging {
  val config = context.system.settings.config
  val worker = context.actorOf(Props(new SmsWorker(bookKeeper)), "sms-worker")
  def receive = {
    case SendSms(notification: Notification) =>
      log.info("Received (sms) notification: " + notification)
      try {
        val recipients = if (config.getBoolean("sms.override")) Array(config.getString("sms.override-no")) else notification.msg.telno.split("\\|")
        val sys = notification.sys
        val aux = notification.msg.aux
        val content = notification.msg.body
        val id = notification.id
        recipients foreach { recipient =>
          // TODO number format validation
          worker ! SendSingleSms(sys, recipient, aux, content, id)
        }
        bookKeeper ! ProcessMsg(notification.id)
      } catch {
        case ValidationException(reason) =>
          log.error("ValidationException occurred: " + reason)
          bookKeeper ! RejectMsg(notification.id, reason)
        case ex: Throwable =>
          log.error(ex, "Unexpected exception occurred.")
          bookKeeper ! RejectMsg(notification.id, "4")
      }
    case x =>
      log.error(s"Unrecognized message received: $x.")
  }
}

class SmsWorker(bookKeeper: ActorRef) extends Actor with ActorLogging {
  val config = context.system.settings.config
  val gw = config.getString("sms.gw") // "http://210.21.237.245/services/Sms"
  val sn = config.getString("sms.sn") // "SH0137A71EAC-6CE4-44A3-83F7-8D91A4438B37"
  def receive = {
    case SendSingleSms(sys, recipient, aux, content, id) =>
      log.info(s"Sending SMS [$id] for [$sys] to [$recipient].")
      val no = config.getString(s"sms.numbers.$sys")
      try {
        val xml =
          <soapenv:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                            xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                            xmlns:chin="http://chinagdn.com">
            <soapenv:Header/>
            <soapenv:Body>
              <chin:InsertDownSms soapenv:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <sn xsi:type="soapenc:string" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/">{sn}</sn>
                <orgaddr xsi:type="soapenc:string" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/">{no}</orgaddr>
                <telno xsi:type="soapenc:string" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/">{recipient}</telno>
                <content xsi:type="soapenc:string" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/">{scala.xml.Unparsed("<![CDATA[%s]]>".format(content))}</content>
                <sendtime xsi:type="soapenc:string" xmlns:soapenc="http://schemas.xmlsoap.org/soap/encoding/"></sendtime>
              </chin:InsertDownSms>
            </soapenv:Body>
          </soapenv:Envelope>
        val conn = new URL(gw).openConnection().asInstanceOf[HttpURLConnection]
        conn.setConnectTimeout(30000)
        conn.setReadTimeout(30000)
        conn.setDoInput(true)
        conn.setDoOutput(true)
        conn.setRequestMethod("POST")
        conn.setRequestProperty("Content-Type", "text/xml;charset=UTF-8")
        conn.setRequestProperty("soapaction", "")
        conn.connect()
        val os = conn.getOutputStream
        log.info(xml.toString())
        os.write(xml.toString.getBytes("UTF-8"))
        os.flush()
        os.close()
        conn.getInputStream
        bookKeeper ! LogSmsSent(sys, System.currentTimeMillis().toString, recipient, aux, id)
        log.info(s"SMS [$id] for [$sys] to [$recipient] has been sent.")
      } catch {
        case ex: Throwable =>
          log.error(ex, s"Unexpected exception occurred while trying to send SMS for [$sys] to [$recipient].")
      }
    case x =>
      log.error(s"Unrecognized message received: $x.")
  }
}