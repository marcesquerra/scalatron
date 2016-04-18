package scalatron.webServer.akkahttp

import akka.NotUsed
import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.collection.immutable._
import scalatron.core.Simulation.OutwardState
import scalatron.persistence.{TickRecorder, ResultRecorder, LiveView}

object RoomView {

  trait Message

//  case class StateMessage(s: OutwardState) extends Message
//
//  case class StateMessages(s: Seq[OutwardState]) extends Message
//
  case class ReceivedMessage(id: Int, message: String) extends Message
//
//  case class RoundResult(result: LiveView.RoundResult) extends Message
//
  case class RoundResults(results: Seq[ResultRecorder.RoundResultAdded]) extends Message

  case class NewViewer(id: Int, subscriber: ActorRef)

  case class ViewerLeft(id: Int)

  def create(system: ActorSystem): RoomView = {
    val roomActor = system.actorOf(Props(new Actor {
      system.eventStream.subscribe(self, classOf[ResultRecorder.RoundResults])

      private var subscribers = Set.empty[(Int, ActorRef)]

      def receive: Receive = {
        case r: ResultRecorder.RoundResults =>
          dispatch(RoundResults(r.roundResults))
        case n@NewViewer(id, subscriber) =>
          println(n)
          context.watch(subscriber)
          subscribers += (id -> subscriber)
        case v@ViewerLeft(id) =>
          println(v)
          val (removed, rest) = subscribers.partition { case (sid, sub) => sid == id }
          subscribers = rest
        case msg: Message => dispatch(msg)
        case Terminated(sub) =>
          // clean up dead subscribers, but should have been removed when `ViewerLeft`
          subscribers = subscribers.filterNot(_._2 == sub)
      }

      def dispatch(msg: Message): Unit = subscribers.foreach(_._2 ! msg)
    }))

    def roomInSink(id: Int) = Sink.actorRef[Message](roomActor, ViewerLeft(id))

    new RoomView {

      override def viewFlow(id: Int): Flow[String, Message, NotUsed] = {
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

  def viewFlow(id: Int): Flow[String, Message, NotUsed]

  def injectMessage(message: Message): Unit
}

