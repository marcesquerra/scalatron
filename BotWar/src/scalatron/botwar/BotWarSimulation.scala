/** This material is intended as a community resource and is licensed under the
  * Creative Commons Attribution 3.0 Unported License. Feel free to use, modify and share it.
  */
package scalatron.botwar

import akka.actor.ActorSystem

import scala.concurrent.ExecutionContext
import scala.util.Random
import scalatron.core.Simulation.OutwardState
import scalatron.core.{EntityController, Simulation}


/** Implementations of generic Simulation traits for the BotWar game. */
object BotWarSimulation {

  case class SimState(gameState: State) extends Simulation.State[SimState] {
    def time = gameState.time

    def step(actorSystem: ActorSystem, executionContextForUntrustedCode: ExecutionContext) = {
      // to make results reproducible, generate a freshly seeded randomizer for every cycle
      val rnd = new Random(gameState.time)

      // apply the game dynamics to the game state
      Dynamics(gameState, rnd, actorSystem, executionContextForUntrustedCode) match {
        case Left(updatedGameState) => Left(SimState(updatedGameState))
        case Right(gameResult) => Right(gameResult)
      }
    }

    /** Returns a collection containing all entities controlled by the control function implemented in the plug-in
      * (and thus associated with the player) with the given name. */
    def entitiesOfPlayer(name: String) =
      gameState.board.entitiesOfPlayer(name).map(e => new Simulation.Entity {
        def id = e.id

        def name = e.name

        def isMaster = e.isMaster

        def mostRecentControlFunctionInput = e.variety match {
          case player: Bot.Player => player.controlFunctionInput
          case _ => ""
        }

        def mostRecentControlFunctionOutput = e.variety match {
          case player: Bot.Player =>
            val commands = player.controlFunctionOutput
            commands.map(command => (command.opcode, command.paramMap.map(e => (e._1, e._2.toString))))
          case _ => Iterable.empty
        }

        def debugOutput = e.variety match {
          case player: Bot.Player => player.stateMap.getOrElse(Protocol.PropertyName.Debug, "")
          case _ => ""
        }
      })

    lazy val decorations = gameState.board.decorations.filter(x => x._2.variety match {
      case Decoration.Annihilation | _: Decoration.MarkedCell | _: Decoration.Line => false
      case _ => true
    }).map(x => decoration(x._2)).toVector

    lazy val bots = gameState.board.botsFiltered(_ => true).map(bot).toVector

    def bot(b: Bot) = {
      val x = b.pos.x
      val y = b.pos.y

      b.variety match {
        case Bot.Occluded => OutwardState.Bot.Occluded(x, y)
        case Bot.GoodPlant => OutwardState.Bot.GoodPlant(x, y)
        case Bot.BadPlant => OutwardState.Bot.BadPlant(x, y)
        case Bot.GoodBeast => OutwardState.Bot.GoodBeast(x, y, id = b.id)
        case Bot.BadBeast => OutwardState.Bot.BadBeast(x, y, b.id)
        case Bot.Wall => OutwardState.Bot.Wall(x, y, OutwardState.XY(b.extent.x, b.extent.y))
        case p: Bot.Player if p.isMaster => OutwardState.Bot.MasterPlayer(x, y, id = b.id, cpu = p.cpuTime, name = b.name, e = b.energy)
        case p: Bot.Player => OutwardState.Bot.SlavePlayer(x, y, id = b.id, mId = p.masterId)
      }
    }

    def decoration(d: Decoration) = {
      val x = d.pos.x
      val y = d.pos.y

      d.variety match {
        case Decoration.Explosion(blastRadius) => OutwardState.Decoration.Explosion(x, y, r = blastRadius)
        case Decoration.Bonk => OutwardState.Decoration.Bonk(x, y)
        case Decoration.Bonus(energy) => OutwardState.Decoration.Bonus(x, y, energy)
        case Decoration.Text(text) => OutwardState.Decoration.Text(x, y, text)
          // These are filtered above, but keeping them here to prevent compile warning
        case Decoration.Annihilation => OutwardState.Decoration.Annihilation(x, y)
        case Decoration.MarkedCell(color) => OutwardState.Decoration.MarkedCell(x, y, color)
        case Decoration.Line(toPos, color) => OutwardState.Decoration.Line(x, y, OutwardState.XY(toPos.x, toPos.y), color)
      }
    }

    override def outWardState: OutwardState = {
      val size = gameState.config.boardParams.size
      val rounds = gameState.config.permanent.stepsPerRound
      OutwardState(rounds, OutwardState.XY(size.x, size.y), time, bots, decorations)
    }
  }

  case class Factory(config: Config) extends Simulation.Factory[SimState] {
    def createInitialState(randomSeed: Int, entityControllers: Iterable[EntityController], executionContextForUntrustedCode: ExecutionContext) = {
      val state = State.createInitial(config, randomSeed, entityControllers, executionContextForUntrustedCode)
      SimState(state)
    }
  }

}