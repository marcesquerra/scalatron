package scalatron.webServer.servelets

import java.nio.ByteBuffer

import akka.actor._
import com.sun.jersey.core.util.Base64
import org.eclipse.jetty.websocket.api.{Session, WebSocketAdapter}
import org.eclipse.jetty.websocket.servlet._

import scalatron.botwar.renderer.WSActor
import java.io.ByteArrayOutputStream


class EventSocket(system: ActorSystem) extends WebSocketAdapter {

  var wsClientActor: ActorRef = _

  override def onWebSocketConnect(sess: Session) {
    super.onWebSocketConnect(sess);
    wsClientActor = system.actorOf(WSClientActor.props(sess))
  }

  override def onWebSocketText(message: String) {
    wsClientActor ! WSClientActor.Start
    super.onWebSocketText(message);
  }

  override def onWebSocketClose(statusCode: Int, reason: String) {
    wsClientActor ! WSClientActor.Close
    super.onWebSocketClose(statusCode, reason)
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
  case object Start
}

class WSClientActor(session: Session) extends Actor {

  import scalatron.botwar.renderer.WSActor._
  import scalatron.webServer.servelets.WSClientActor._

  val identifyId = 1
  val baos = new ByteArrayOutputStream()

  var count = 0
  var started : Boolean = false

  override def preStart(): Unit = {
    context.actorSelection("/user/drawing") ! Identify(1)
  }

  override def receive: Receive = {

    case ActorIdentity(`identifyId`, Some(ref)) =>
      println("identified")
      context.watch(ref)
      ref ! Register
      context.become(active(ref))

    case Start =>
      println("start")
      started = true
  }

  def active(drawing: ActorRef): Receive = {

    case ImageWithHeader(header, img) if started =>
      count += 1
      try {

        session.getRemote.sendBytes(ByteBuffer.wrap(header))
        session.getRemote.flush()
        session.getRemote.sendBytes(ByteBuffer.wrap(Base64.encode(img)))
        session.getRemote.flush()
      } catch {
        case e: Exception =>
          e.printStackTrace()
          context.stop(self)
      }

    case Close =>
      context.stop(self)

    case Terminated(_) => context.stop(self)
  }
}
