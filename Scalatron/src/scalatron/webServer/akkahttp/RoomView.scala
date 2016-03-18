package scalatron.webServer.akkahttp

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Source, Sink, Flow}

import scalatron.core.Simulation.OutwardState

object RoomView {

  trait Message

  case class StateMessage(s: OutwardState) extends Message

  case class ReceivedMessage(id: Int, message: String) extends Message

  case class NewViewer(id: Int, subscriber: ActorRef)

  case class ViewerLeft(id: Int)

  def create(system: ActorSystem): RoomView = {
    val roomActor = system.actorOf(Props(new Actor {
      var subscribers = Set.empty[(Int, ActorRef)]

      def receive: Receive = {
        case n@NewViewer(id, subscriber) =>
          println(n)
          context.watch(subscriber)
          subscribers += (id -> subscriber)
        case msg: Message => dispatch(msg)
        case v@ViewerLeft(id) =>
          println(v)
          subscribers = subscribers.filterNot(_._1 == id)
        case Terminated(sub) =>
          // clean up dead subscribers, but should have been removed when `ViewerLeft`
          subscribers = subscribers.filterNot(_._2 == sub)
      }

      def dispatch(msg: Message): Unit = subscribers.foreach(_._2 ! msg)
    }))

    def roomInSink(id: Int) = Sink.actorRef[Message](roomActor, ViewerLeft(id))

    new RoomView {
      override def viewFlow(id: Int): Flow[String, Message, Unit] = {
        val in =
          Flow[String]
            .map(ReceivedMessage(id, _))
            .to(roomInSink(id))

        val out = Source.actorRef[Message](10, OverflowStrategy.dropHead)
          .mapMaterializedValue(roomActor ! NewViewer(id, _))

        Flow.fromSinkAndSource(in, out)
      }

      override def injectMessage(message: Message): Unit = roomActor ! message
    }
  }
}

trait RoomView {

  import RoomView._

  def viewFlow(id: Int): Flow[String, Message, Unit]

  def injectMessage(message: Message): Unit
}

