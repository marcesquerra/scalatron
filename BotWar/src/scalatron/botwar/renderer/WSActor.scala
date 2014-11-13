package scalatron.botwar.renderer

import java.awt.image.BufferedImage

import akka.actor.{Props, ActorRef, Actor}



object WSActor {

  case object Register
  case class Image(img: BufferedImage)

  def props = Props[WSActor]
}

class WSActor extends Actor {
  import scalatron.botwar.renderer.WSActor._

  val participants = collection.mutable.Set[ActorRef]()

  override def receive: Receive = {
    case Register =>
      println(s"register: ${sender()}")
      participants += sender()
    case i @ Image(img) =>
      println(s"image!!!: $participants")
      participants.foreach(_ ! i)
  }

}
