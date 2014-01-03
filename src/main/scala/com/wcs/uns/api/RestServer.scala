package com.wcs.uns.api

import akka.actor.Actor
import akka.pattern.ask
import spray.routing._
import spray.routing.authentication.BasicAuth
import spray.http.MediaTypes._
import com.wcs.uns.model._
import com.wcs.uns.model.JsonCodecs._
import shapeless.HNil
import com.wcs.uns.model.DeleteMsgRejected
import com.wcs.uns.model.GetMsgInProgress
import com.wcs.uns.model.MsgCleared
import com.wcs.uns.model.UserMessage
import com.wcs.uns.model.GetMsgSent
import com.wcs.uns.model.GetMsgRejected
import com.wcs.uns.model.Notification

class RestActor extends Actor with RestServer {
  def actorRefFactory = context
  def receive = runRoute(route)
}

trait RestServer extends HttpService {
  implicit def executionContext = actorRefFactory.dispatcher
  implicit val timeout = akka.util.Timeout(5000)
  val notifier = actorRefFactory.actorSelection("../notifier")
  val bookKeeper = actorRefFactory.actorSelection("../notifier/book-keeper")
  def rejectMismatchedSysName(sysname: String, sys: String): Directive[HNil] = if (sysname == sys) pass else reject
  val route = {
    authenticate(BasicAuth()) { user =>
      val sysname = user.username
      path("abc") {
        (detach() & complete) { (bookKeeper ? GetMsgInProgress(sysname)).mapTo[List[Notification]] }
      } ~
      rejectEmptyResponse {
        respondWithMediaType(`application/json`) {
          path("msg" / "outbox" / Segment) { sys =>
            rejectMismatchedSysName(sysname, sys) {
              parameterMap { params =>
                put {
                  entity(as[UserMessage]) { msg =>
                    val id = java.util.UUID.randomUUID().toString
                    val ts = System.currentTimeMillis().toString
                    (detach() & complete) {
                      (notifier ? SendNotification(Notification(id, sysname, ts, "0", "", msg))).mapTo[Notification]
                    }
                  }
                }
              }
            }
          } ~
          path("msg" / "inprogress" / Segment) { sys =>
            rejectMismatchedSysName(sysname, sys) {
              get {
                (detach() & complete) {
                  (bookKeeper ? GetMsgInProgress(sys)).mapTo[List[Notification]]
                }
              }
            }
          } ~
          path("msg" / "sent" / Segment) { sys =>
            rejectMismatchedSysName(sysname, sys) {
              parameterMap { params =>
                val ts = params.getOrElse("ts", "")
                get {
                  (detach() & complete) {
                    (bookKeeper ? GetMsgSent(sys, ts)).mapTo[List[Notification]]
                  }
                }
              }
            }
          } ~
          path("msg" / "ignored" / Segment) { sys =>
            rejectMismatchedSysName(sysname, sys) {
              parameterMap { params =>
                val ts = params.getOrElse("ts", "")
                get {
                  (detach() & complete) {
                    (bookKeeper ? GetMsgIgnored(sys, ts)).mapTo[List[Notification]]
                  }
                }
              }
            }
          } ~
          path("msg" / "rejected" / Segment) { sys =>
            rejectMismatchedSysName(sysname, sys) {
              parameterMap { params =>
                val ts = params.getOrElse("ts", "")
                get {
                  (detach() & complete) {
                    (bookKeeper ? GetMsgRejected(sys, ts)).mapTo[List[Notification]]
                  }
                } ~
                delete {
                  (detach() & complete) {
                    (bookKeeper ? DeleteMsgRejected(sys, ts)).mapTo[MsgCleared]
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
