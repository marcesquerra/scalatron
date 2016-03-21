package scalatron.webServer.akkahttp

import scalatron.core.Scalatron.LeaderBoard
import scalatron.core.Simulation.OutwardState

/**
 *
 */
object Json {

  import io.circe._, io.circe.generic.auto._, io.circe.jawn._, io.circe.syntax._

  implicit private val botEncoder: Encoder[OutwardState.Bot] = new Encoder[OutwardState.Bot] {
    override def apply(a: OutwardState.Bot): Json = a match {
      case b: OutwardState.Bot.BadBeast => b.asJson
      case b: OutwardState.Bot.BadPlant => b.asJson
      case b: OutwardState.Bot.GoodBeast => b.asJson
      case b: OutwardState.Bot.GoodPlant => b.asJson
      case b: OutwardState.Bot.MasterPlayer => b.asJson
      case b: OutwardState.Bot.Occluded => b.asJson
      case b: OutwardState.Bot.SlavePlayer => b.asJson
      case b: OutwardState.Bot.Wall => b.asJson
    }
  }
  implicit private val decencoder: Encoder[OutwardState.Decoration] = new Encoder[OutwardState.Decoration] {
    override def apply(a: OutwardState.Decoration): Json = a match {
      case b: OutwardState.Decoration.Annihilation => b.asJson
      case b: OutwardState.Decoration.Bonk => b.asJson
      case b: OutwardState.Decoration.Bonus => b.asJson
      case b: OutwardState.Decoration.Explosion => b.asJson
      case b: OutwardState.Decoration.Line => b.asJson
      case b: OutwardState.Decoration.MarkedCell => b.asJson
      case b: OutwardState.Decoration.Text => b.asJson
    }
  }

  def print(state: OutwardState): String = {
    state.asJson.noSpaces
  }

  def print(leaderBoard: LeaderBoard): String = {
    leaderBoard.asJson.noSpaces
  }

}
