package scalatron.core

/** This material is intended as a community resource and is licensed under the
  * Creative Commons Attribution 3.0 Unported License. Feel free to use, modify and share it.
  */

import akka.actor.ActorSystem

import scala.collection.immutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}


/** Traits for generic simulations, of which a game like BotWar is an example.
  */
object Simulation {
  type Time = Long

  object Time {
    val MaxValue = Long.MaxValue
    val SomtimeInThePast = -1
  }


  /** Base trait for entities (bots, etc.) exposed by a simulation. */
  trait Entity {
    /** Returns the unique ID of this entity. */
    def id: Int

    /** Returns the unique name of this entity, e.g. "Master" or "Slave_12345". */
    def name: String

    /** Returns true if this entity is a master (bot), false if it is a slave (mini-bot). */
    def isMaster: Boolean

    /** Returns the input string that was most recently received by the control function of
      * this entity. Note that for Master bots this may be out of date, since they are only
      * called every second cycle. */
    def mostRecentControlFunctionInput: String

    /** Returns the list of commands that was most recently returned by the control function
      * of this entity. Note that for Master bots this may be out of date, since they are only
      * called every second cycle. Also note that this is NOT the command string returned by
      * the bot; rather, it is the collection of commands actually recognized and accepted by
      * the server. The returned structure is: Iterable[(opcode,Iterable[(paramName,value])]
      */
    def mostRecentControlFunctionOutput: Iterable[(String, Iterable[(String, String)])]

    /** Returns the debug log output most recently made by the bot. */
    def debugOutput: String
  }

  object OutwardState {

    case class XY(x: Int, y: Int)

    sealed trait Entity {
      def t: Char

      def x: Int

      def y: Int
    }

    sealed trait Bot extends Entity

    object Bot {

      case class Occluded(x: Int, y: Int, t: Char = '?') extends Bot

      case class GoodPlant(x: Int, y: Int, t: Char = 'P') extends Bot

      case class BadPlant(x: Int, y: Int, t: Char = 'p') extends Bot

      case class GoodBeast(x: Int, y: Int, t: Char = 'B') extends Bot

      case class BadBeast(x: Int, y: Int, t: Char = 'b') extends Bot

      case class Wall(x: Int, y: Int, t: Char = 'W') extends Bot

      case class MasterPlayer(x: Int, y: Int, id: Long, cpu: Long, name: String, e: Long, t: Char = 'M') extends Bot

      case class SlavePlayer(x: Int, y: Int, mId: Long, name: String, t: Char = 'S') extends Bot

    }

    sealed trait Decoration extends Entity

    case object Decoration {

      case class Explosion(x: Int, y: Int, r: Int, t: Char = 'E') extends Decoration

      case class Bonk(x: Int, y: Int, t: Char = 'X') extends Decoration

      case class Bonus(x: Int, y: Int, e: Long, t: Char = 'Y') extends Decoration

      case class Text(x: Int, y: Int, text: String, t: Char = 'T') extends Decoration

      case class Annihilation(x: Int, y: Int, t: Char = 'A') extends Decoration

      case class MarkedCell(x: Int, y: Int, color: String, t: Char = 'C') extends Decoration

      case class Line(x: Int, y: Int, to: XY, color: String, t: Char = 'L') extends Decoration

    }

  }

  case class OutwardState(rounds: Int, size: OutwardState.XY, time: Long, bots: immutable.Seq[OutwardState.Bot], decorations: immutable.Seq[OutwardState.Decoration])

  /** Simulation.UntypedState: non-polymorphic base trait for State that simplifies passing State to contexts
    * where we don't want to introduce the types of S and R.
    */
  trait UntypedState {
    def outWardState: OutwardState

    /** @return the time value associated with this simulation state; a monotonically increasing, zero-based, Long integer step counter. */
    def time: Time

    /** Advances the simulation one step and returns either an updated state or a result.
      * @param actorSystem the actor system to use for trusted computation.
      * @param executionContextForUntrustedCode the execution context to use for untrusted computation.
      * @return either an updated state or a result.
      */
    def step(actorSystem: ActorSystem, executionContextForUntrustedCode: ExecutionContext): Either[UntypedState, TournamentRoundResult]

    /** Returns a collection containing all entities controlled by the control function implemented in the plug-in
      * (and thus associated with the player) with the given name. */
    def entitiesOfPlayer(name: String): Iterable[Entity]
  }


  /** Simulation.State: base traits for simulation state implementations.
    * @tparam S type of the simulation state implementation (extends Simulation.State)
    */
  trait State[S <: State[S]] extends UntypedState {
    def step(actorSystem: ActorSystem, executionContextForUntrustedCode: ExecutionContext): Either[S, TournamentRoundResult]
  }


  /** Simulation.Factory: base traits for simulation state factory implementations.
    * @tparam S type of the simulation state implementation (extends Simulation.State)
    */
  trait Factory[S <: State[S]] {
    def createInitialState(randomSeed: Int, entityControllers: Iterable[EntityController], executionContextForUntrustedCode: ExecutionContext): S
  }

  /** Simulation.Runner: a generic runner for simulations that uses .step() to iteratively
    * compute new simulation states.
    * @tparam S type of the simulation state implementation (extends Simulation.State)
    */
  case class Runner[S <: State[S]](
                                    factory: Factory[S],
                                    stepCallback: S => Boolean, // callback invoked at end of every simulation step; if it returns false, the sim terminates without result
                                    resultCallback: (S, TournamentRoundResult) => Unit) //  callback invoked after the end of very last simulation step
  {
    /** @param entityControllers the collection of entity controllers loaded from external plug-ins that we should bring into the simulation
      * @param randomSeed the random seed to use for initializing the simulation
      * @param actorSystem execution context whose threads are trusted (e.g. actor system)
      * @param executionContextForUntrustedCode execution context whose threads are untrusted (sandboxed by the security manager)
      * @return an optional simulation result (if the simulation was not prematurely aborted)
      */
    def apply(
               entityControllers: Iterable[EntityController],
               randomSeed: Int
               )(
               actorSystem: ActorSystem,
               executionContextForUntrustedCode: ExecutionContext
               ): Option[TournamentRoundResult] = {
      var currentState = factory.createInitialState(randomSeed, entityControllers, executionContextForUntrustedCode) // state at entry of loop turn
      var priorStateOpt: Option[S] = None // result of state.step() at exit of prior loop turn
      var finalResult: Option[TournamentRoundResult] = None

      var running = true
      while (running) {
        // we'll use Akka Futures to compute the next state concurrently with the callback on the prior state.
        // the callback will generally render the prior state to the screen.

        // process state update, returns either next state or result
        val executionContextForTrustedCode = actorSystem.dispatcher
        val stepFuture = Future({
          currentState.step(actorSystem, executionContextForUntrustedCode)
        })(executionContextForTrustedCode) // compute next state

        // process callback (usually rendering) on prior state, returns true if to continue simulating, false if not
        val callbackFuture = Future({
          priorStateOpt match {
            case None => true // there is no state to call back about (e.g. nothing to render)
            case Some(priorState) => stepCallback(priorState)
          }
        })(executionContextForTrustedCode)

        // let the processing complete
        val stepResult = Await.result(stepFuture, Duration.Inf)
        val callbackAllowsContinuation = Await.result(callbackFuture, Duration.Inf)

        // work with the results
        val simulationContinues = stepResult match {
          case Left(updatedState) =>
            priorStateOpt = Some(currentState)
            currentState = updatedState
            true

          case Right(gameResult) =>
            resultCallback(currentState, gameResult)
            finalResult = Some(gameResult)
            false
        }

        running = callbackAllowsContinuation && simulationContinues
      }
      finalResult
    }
  }


}