package scalatron.persistence

import java.time.Instant
import java.util.UUID

import akka.actor._
import com.rbmhtechnology.eventuate.{EventsourcedActor, EventsourcedView}

import scala.collection.immutable.Seq
import scala.util.{Failure, Success}
import scalatron.core.Simulation.OutwardState

trait StepRecorder {

  import StepRecorder.Command

  def injectCommand(command: Command): Unit

}

import scalatron.webServer.akkahttp.RoomView

object StepRecorder {

  sealed trait Command

  case class AddStep(outwardState: OutwardState) extends Command

  sealed trait Event

  case class StepAdded(outwardState: OutwardState) extends Event

  case class RoundStarted(roundId: String, dateTime: Long) extends Event

  case class RoundEnded(roundId: String, dateTime: Long, complete: Boolean) extends Event

  //snapshot
  case object Snap

  def create(actorSystem: ActorSystem, nodeId: String, eventLog: ActorRef): StepRecorder = {
    val actor = actorSystem.actorOf(Props(new StateRecorderActor(nodeId, eventLog)))
    implicit val as = actorSystem
    new StepRecorder {
      override def injectCommand(command: Command): Unit = actor ! command
    }
  }
}

private class StateRecorderActor(override val id: String, override val eventLog: ActorRef) extends EventsourcedActor {

  import StepRecorder._

  var completed = true

  var roundId = UUID.randomUUID().toString.replace("-", "")

  def newRoundId = {
    roundId = UUID.randomUUID().toString.replace("-", "")
    roundId
  }

  override def onCommand: Receive = {
    case c: AddStep =>
      if (c.outwardState.time == 0) {
        if (!completed) {
          persist(RoundEnded(roundId, Instant.now.toEpochMilli, complete = false)) {
            case Success(evt) =>
            case Failure(t) => t.printStackTrace()
          }
          save(Snap) {
            case Success(meta) =>
            case Failure(t) =>
          }
        }
        persist(RoundStarted(newRoundId, Instant.now.toEpochMilli)) {
          case Success(evt) => completed = false
          case Failure(t) =>
            completed = false
            t.printStackTrace()
        }
      }

      persist(StepAdded(c.outwardState), customDestinationAggregateIds = Set(roundId)) {
        case Success(evt) =>
        case Failure(t) => t.printStackTrace()
      }

      if (c.outwardState.time == c.outwardState.rounds) {
        persist(RoundEnded(roundId, Instant.now.toEpochMilli, complete = true)) {
          case Success(evt) => completed = true
          case Failure(t) =>
            completed = true
            t.printStackTrace()
        }
        save(Snap) {
          case Success(meta) =>
          case Failure(t) =>
        }
      }
  }

  override def onSnapshot: Receive = {
    case s: LiveView.RoundResults =>
    case Snap =>
  }

  override def onEvent: Receive = {
    case _ =>
  }
}

object LiveView {

  sealed trait Command

  case class AddSubscriber(subscriber: ActorRef) extends Command

  case class RemoveSubscriber(subscriber: ActorRef) extends Command

  case class GetResults(subscriber: ActorRef) extends Command

  sealed trait State

  case class RoundResult(roundId: String, dateTime: Long, complete: Boolean, results: Seq[(String, Long)]) extends State

  case class RoundResults(roundResults: Seq[RoundResult] = Seq()) extends State

  def props(nodeId: String, eventLog: ActorRef) = Props(new LiveView(nodeId, eventLog))
}

class LiveView(override val id: String, override val eventLog: ActorRef) extends EventsourcedView {

  import LiveView._
  import StepRecorder._

  var subscribers = Set.empty[ActorRef]
  var state = RoundResults()
  var lastStep: Option[StepAdded] = None

  override def onCommand: Receive = {
    case AddSubscriber(subscriber) => subscribers += subscriber
    case RemoveSubscriber(subscriber) => subscribers -= subscriber
    case GetResults(subscriber) => subscriber ! RoomView.RoundResults(state.roundResults)
    case ReplayView.Replay(round, subscriber) =>
      subscribers -= subscriber
      context.actorOf(ReplayView.props(id, round, eventLog, subscriber))
  }

  override def onEvent: Receive = {
    case evt: StepAdded =>
      lastStep = Some(evt)
      subscribers.foreach(_ ! RoomView.StateMessage(evt.outwardState))
    case evt: RoundStarted =>
    case evt: RoundEnded =>
      lastStep.foreach(s => {
        val result = calculateResult(s, evt.complete, evt.roundId)
        state = RoundResults(result +: state.roundResults)
        subscribers.foreach(_ ! RoomView.RoundResult(result))
        save(state) {
          case Success(meta) =>
          case Failure(t) =>
        }
      })
  }

  override def onSnapshot: Receive = {
    case s: RoundResults => state = s
    case Snap =>
  }

  private def calculateResult(e: StepRecorder.StepAdded, completed: Boolean, roundId: String) = {
    val results: Seq[(String, Long)] = e.outwardState.bots
      .collect { case b: OutwardState.Bot.MasterPlayer => b }
      .map(b => b.name -> b.e)

    RoundResult(roundId, Instant.now.toEpochMilli, completed, results)
  }
}

object ReplayView {

  sealed trait Command

  case class Replay(roundId: String, subscriber: ActorRef) extends Command

  def props(nodeId: String, roundId: String, eventLog: ActorRef, subscriber: ActorRef) = Props(new ReplayView(nodeId, roundId, eventLog, subscriber))
}

class ReplayView(override val id: String, roundId: String, override val eventLog: ActorRef, subscriber: ActorRef) extends EventsourcedView {

  import StepRecorder._

  override val aggregateId = Some(roundId)

  override def replayBatchSize: Int = 8

  override def onCommand: Receive = {
    case _ =>
  }

  override def onRecovery: Receive = {
    case Success(e) =>
      context.stop(self)
    case Failure(t) =>
      t.printStackTrace()
      context.stop(self)
  }

  override def onEvent: Receive = {
    case e: StepAdded =>
      subscriber ! RoomView.StateMessage(e.outwardState)

  }
}
