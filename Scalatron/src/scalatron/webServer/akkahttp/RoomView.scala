package scalatron.webServer.akkahttp

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.collection.immutable._
import scalatron.core.Simulation.OutwardState
import scalatron.persistence.{LiveView, ReplayView}

object RoomView {

  trait Message

  case class StateMessage(s: OutwardState) extends Message

  case class StateMessages(s: Seq[OutwardState]) extends Message

  case class ReceivedMessage(id: Int, message: String) extends Message

  case class RoundResult(result: LiveView.RoundResult) extends Message

  case class RoundResults(results: Seq[LiveView.RoundResult]) extends Message

  case class NewViewer(id: Int, subscriber: ActorRef)

  case class ViewerLeft(id: Int)

  def create(system: ActorSystem, liveView: ActorRef): RoomView = {
    val roomActor = system.actorOf(Props(new Actor {

      var subscribers = Set.empty[(Int, ActorRef)]
      val roundIdRegex = """replay:(.*)""" r

      def receive: Receive = {
        case n@NewViewer(id, subscriber) =>
          println(n)
          context.watch(subscriber)
          subscribers += (id -> subscriber)
        case v@ViewerLeft(id) =>
          println(v)
          val (removed, rest) = subscribers.partition { case (sid, sub) => sid == id }
          removed.headOption.foreach(s => liveView ! LiveView.RemoveSubscriber(s._2))
          subscribers = rest

        case ReceivedMessage(id, roundIdRegex(roundId)) =>
          val subscriber = subscribers.find { case (_id, actor) => id == _id }.get._2
          liveView ! ReplayView.Replay(roundId, subscriber)

        case ReceivedMessage(id, "results") =>
          val subscriber = subscribers.find { case (sid, s) => sid == id }.get._2
          liveView ! LiveView.GetResults(subscriber)

        case ReceivedMessage(id, "live") =>
          val subscriber = subscribers.find { case (sid, s) => sid == id }.get._2
          liveView ! LiveView.AddSubscriber(subscriber)

        case msg: Message => dispatch(msg)

        case Terminated(sub) =>
          // clean up dead subscribers, but should have been removed when `ViewerLeft`
          subscribers = subscribers.filterNot(_._2 == sub)
      }

      def dispatch(msg: Message): Unit = subscribers.foreach(_._2 ! msg)
    }))

    def roomInSink(id: Int) = Sink.actorRef[Message](roomActor, ViewerLeft(id))

    new RoomView {

      override def viewFlow(id: Int): Flow[String, Message, Unit] = {
        val in = Flow[String]
          .map(ReceivedMessage(id, _))
          .to(roomInSink(id))

        val out = Source.actorRef[Message](5000, OverflowStrategy.dropHead)
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

