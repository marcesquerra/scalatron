package scalatron.webServer.servelets

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO

import akka.actor.{ActorSystem, Props, Actor}
import com.sun.jersey.core.util.Base64
import org.eclipse.jetty.websocket.api.{Session, WebSocketAdapter}
import org.eclipse.jetty.websocket.servlet._

import scalatron.botwar.renderer.WSActor


class EventSocket(system: ActorSystem) extends WebSocketAdapter {

  override def onWebSocketConnect(sess: Session) {
    super.onWebSocketConnect(sess);
    system.actorOf(WSClientActor.props(sess))
    println("Socket Connected: " + sess);
  }

  override def onWebSocketText(message: String) {
    super.onWebSocketText(message);
    System.out.println("Received TEXT message: " + message);
  }

  override def onWebSocketClose(statusCode: Int, reason: String) {
    super.onWebSocketClose(statusCode, reason)
    System.out.println("Socket Closed: [" + statusCode + "] " + reason)
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
//    factory.register(new EventSocket(system))

}

object WSClientActor {

  def props(session: Session) = Props(new WSClientActor(session))

//  case class Send(img: BufferedImage)
}

class WSClientActor(session: Session) extends Actor {

  import WSActor._
  import WSClientActor._

  override def preStart(): Unit = {
    val drawing = context.actorSelection("/user/drawing")
    println(s"drawing: $drawing")
    drawing ! Register
  }

  override def receive: Receive = {
    case WSActor.Image(img) =>
      println(s"got an image in WSClientActor")

      val baos = new ByteArrayOutputStream()
      ImageIO.write( img, "jpg", baos )
      baos.flush()
      val bytes = baos.toByteArray
      val header = "%09d".format(Base64.encode(bytes).length).getBytes

//      val header = (String.format("%09d", Base64.encode(bytes).length)).getBytes
//      println("hoho" + String.format("%09d", Base64.encode(bytes).length))
      session.getRemote.sendBytes(ByteBuffer.wrap(header))
      session.getRemote.flush()
      session.getRemote.sendBytes(ByteBuffer.wrap(Base64.encode(bytes)))
      session.getRemote.flush()
  }

}
