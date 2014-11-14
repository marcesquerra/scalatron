package scalatron.webServer.servelets

import java.nio.ByteBuffer

import akka.actor._
import com.sun.jersey.core.util.Base64
import org.eclipse.jetty.websocket.api.{Session, WebSocketAdapter}
import org.eclipse.jetty.websocket.servlet._

import scalatron.botwar.renderer.WSActor


class EventSocket(system: ActorSystem) extends WebSocketAdapter {

  var wsClientActor: ActorRef = _

  override def onWebSocketConnect(sess: Session) {
    super.onWebSocketConnect(sess);
    wsClientActor = system.actorOf(WSClientActor.props(sess))
  }

  override def onWebSocketText(message: String) {
    super.onWebSocketText(message);
  }

  override def onWebSocketClose(statusCode: Int, reason: String) {
    super.onWebSocketClose(statusCode, reason)
    wsClientActor ! WSClientActor.Close
  }

  override def onWebSocketError(cause: Throwable) {
    super.onWebSocketError(cause)
    cause.printStackTrace(System.err)
  }
}

class EventServlet(system: ActorSystem) extends WebSocketServlet {

  override def configure(factory: WebSocketServletFactory): Unit =
      factory.setCreator(new WebSocketCreator {
        override def createWebSocket(servletUpgradeRequest: ServletUpgradeRequest, servletUpgradeResponse: ServletUpgradeResponse): AnyRef = {
          new EventSocket(system)
        }
      })
}

object WSClientActor {

  def props(session: Session) = Props(new WSClientActor(session))

  case object Close
}

class WSClientActor(session: Session) extends Actor {

  import scalatron.botwar.renderer.WSActor._
  import scalatron.webServer.servelets.WSClientActor._

  val identifyId = 1

  var count = 0

  override def preStart(): Unit = {
    context.actorSelection("/user/drawing") ! Identify(1)
  }

  override def receive: Receive = {
    case ActorIdentity(`identifyId`, Some(ref)) =>
      context.watch(ref)
      ref ! Register
      context.become(active(ref))
  }

  def active(drawing: ActorRef): Receive = {

    case WSActor.Image(header, img) =>
      count += 1
      try {
        session.getRemote.sendBytes(ByteBuffer.wrap(header))
        session.getRemote.flush()
        session.getRemote.sendBytes(ByteBuffer.wrap(Base64.encode(img)))
        session.getRemote.flush()
      } catch {
        case e: Exception => context.stop(self)
      }

    case Close =>
      context.stop(self)

    case Terminated(_) => context.stop(self)
  }
}
