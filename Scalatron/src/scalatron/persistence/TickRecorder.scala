package scalatron.persistence

import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.actor._
import akka.persistence.PersistentActor
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source

import scala.collection.immutable.Seq
import scalatron.core.Simulation.OutwardState

trait TickRecorder {

  import TickRecorder.Command

  def resultRecorder: ActorRef

  def injectCommand(command: Command): Unit

}

object TickRecorder {

  sealed trait Command

  case class AddStep(outwardState: OutwardState) extends Command

  case class EndRound(complete: Boolean) extends Command

  sealed trait Event

  case class StepAdded(outwardState: OutwardState) extends Event

  case class RoundEnded(roundId: String, dateTime: Long, complete: Boolean) extends Event

  def create(implicit actorSystem: ActorSystem): TickRecorder = {
    val _resultRecorder: ActorRef = actorSystem.actorOf(ResultRecorder.props())
    val tickRecorderManager = actorSystem.actorOf(Props(new TickRecorderManager(_resultRecorder)))

    new TickRecorder {
      override def injectCommand(command: Command): Unit = {
        tickRecorderManager ! command
      }

      override val resultRecorder: ActorRef = _resultRecorder
    }
  }
}

private class TickRecorderManager(resultRecorder: ActorRef) extends Actor {

  import TickRecorder._

  var tickRecorder: ActorRef = context.actorOf(Props(new TickRecorderActor(uuid, resultRecorder)))
  var roundCompleted = true

  def uuid = UUID.randomUUID().toString.replace("-", "")

  override def receive: Actor.Receive = {
    case cmd: AddStep =>
      val completed = cmd.outwardState.time == cmd.outwardState.rounds
      if (completed) {
        tickRecorder ! cmd
        tickRecorder ! EndRound(completed)
        tickRecorder = context.actorOf(Props(new TickRecorderActor(uuid, resultRecorder)))
      }
      else if (cmd.outwardState.time == 0 && !roundCompleted) {
        //aborted
        tickRecorder ! EndRound(completed)
        tickRecorder = context.actorOf(Props(new TickRecorderActor(uuid, resultRecorder)))
        tickRecorder ! cmd
      } else {
        tickRecorder ! cmd
      }
      roundCompleted = completed
  }
}

private class TickRecorderActor(override val persistenceId: String, resultRecorder: ActorRef) extends PersistentActor {

  import TickRecorder._

  case object StopMe

  var lastTick: Option[OutwardState] = None

  override def receiveCommand: Receive = {

    case c: AddStep =>
      persist(StepAdded(c.outwardState)) { evt =>
        println("recorder: " + persistenceId + " " + c)
        println()
        lastTick = Some(evt.outwardState)
      }
    case c@EndRound(completed) =>
      lastTick.foreach(resultRecorder ! calculateResult(_, completed, persistenceId))
      persist(RoundEnded(persistenceId, Instant.now.toEpochMilli, completed)) { evt =>
        self ! StopMe
      }

    case StopMe => context.stop(self)
  }

  override def receiveRecover: Receive = {
    case _ =>
  }

  private def calculateResult(o: OutwardState, completed: Boolean, roundId: String) = {
    val results: Seq[(String, Long)] = o.bots
      .collect { case b: OutwardState.Bot.MasterPlayer => b }
      .map(b => b.name -> b.e)

    ResultRecorder.AddRoundResult(roundId, Instant.now.toEpochMilli, completed, results)
  }
}

object ResultRecorder {

  trait Command

  case class AddRoundResult(roundId: String, dateTime: Long, complete: Boolean, results: Seq[(String, Long)]) extends Command {
    def event = RoundResultAdded(roundId, dateTime, complete, results)
  }

  case class GetResults(subscriber: ActorRef) extends Command

  trait Event

  case class RoundResultAdded(roundId: String, dateTime: Long, complete: Boolean, results: Seq[(String, Long)]) extends Event

  sealed trait State

  case class RoundResults(roundResults: Seq[RoundResultAdded] = Seq()) extends State

  def props() = Props(new ResultRecorder)
}

private class ResultRecorder() extends PersistentActor {

  import ResultRecorder._

  override def persistenceId: String = "results"

  var state = RoundResults()

  override def receiveCommand: Receive = {
    case c: AddRoundResult => persist(c.event) { evt =>
      state = RoundResults(evt +: state.roundResults)
      context.system.eventStream.publish(state)
    }
    case GetResults(subscriber) => subscriber ! state
  }

  override def receiveRecover: Receive = {
    case evt: RoundResultAdded =>
      state = RoundResults(evt +: state.roundResults)
      context.system.eventStream.publish(state)
  }
}

object LiveView {

  def source(implicit system: ActorSystem): Source[OutwardState, NotUsed] = {
    val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
    queries.allPersistenceIds().flatMapConcat(persistentId => queries.eventsByPersistenceId(persistentId))
      .collect { case e@EventEnvelope(offset, persistentId, sequenceNr, event: TickRecorder.StepAdded) => event.outwardState }.buffer(5000, OverflowStrategy.fail)
  }
//  private def queries(implicit system: ActorSystem) = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
}

object ReplayView {

  def source(roundId: String)(implicit system: ActorSystem): Source[OutwardState, NotUsed] = {
    val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
    queries.eventsByPersistenceId(roundId)
      .collect { case EventEnvelope(offset, persistentId, sequenceNr, event: TickRecorder.StepAdded) => event.outwardState }
  }
}
