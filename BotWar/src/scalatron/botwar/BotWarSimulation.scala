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

    lazy val decorations = gameState.board.decorations.map(x => decoration(x._2)).toVector

    lazy val bots = gameState.board.botsFiltered(_ => true).map(bot).toVector

    def bot(b: Bot) = {
      val out = b.variety match {
        case Bot.Occluded => OutwardState.Bot(t = '?')
        case Bot.GoodPlant => OutwardState.Bot(t = 'P')
        case Bot.BadPlant => OutwardState.Bot(t = 'p')
        case Bot.GoodBeast => OutwardState.Bot(t = 'B')
        case Bot.BadBeast => OutwardState.Bot(t = 'b')
        case Bot.Wall => OutwardState.Bot(t = 'W')
        case p: Bot.Player if p.isMaster => OutwardState.Bot(id = b.id, cpu = Some(p.cpuTime), t = 'M', name = Some(b.name), energy = Some(b.energy))
        case p: Bot.Player => OutwardState.Bot(t = 'm', mId = Some(p.masterId))
      }
      out.copy(pos = OutwardState.XY(x = b.pos.x, y = b.pos.y))
    }

    def decoration(d: Decoration) = {
      val out = d.variety match {
        case Decoration.Explosion(blastRadius) => OutwardState.Decoration(t = 'E', meta = Some(blastRadius.toString))
        case Decoration.Bonk => OutwardState.Decoration('B')
        case Decoration.Bonus(energy) => OutwardState.Decoration('E', Some(energy.toString))
        case Decoration.Text(text) => OutwardState.Decoration('T', Some(text))
        case Decoration.Annihilation => OutwardState.Decoration('A')
        case Decoration.MarkedCell(color) => OutwardState.Decoration('M', Some(color))
        case Decoration.Line(toPos, color) => OutwardState.Decoration('L', meta = Some(color), to = Some(OutwardState.XY(toPos.x, toPos.y)))
      }
      out.copy(pos = OutwardState.XY(d.pos.x, d.pos.y))
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