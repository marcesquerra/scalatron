package scalatron.botwar.renderer

import akka.actor.{Actor, ActorRef, Props, Terminated}


object WSActor {

  case object Register

  case class Image(header: Array[Byte], img: Array[Byte])

  def props = Props[WSActor]
}

class WSActor extends Actor {

  import scalatron.botwar.renderer.WSActor._

  var count = 0L
  val participants = collection.mutable.Set[ActorRef]()

  override def receive: Receive = {
    case Register =>
      participants += sender()
      context.watch(sender())

    case Terminated(_) => participants -= sender()

    case i: Image =>
      count += 1
      participants.foreach(_ ! i)
  }

}
