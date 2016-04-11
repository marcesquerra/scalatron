package scalatron.webServer.akkahttp

import scala.collection.immutable._
import scalatron.core.Simulation.OutwardState
import scalatron.persistence.ResultRecorder

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
  implicit private val decEncoder: Encoder[OutwardState.Decoration] = new Encoder[OutwardState.Decoration] {
    override def apply(a: OutwardState.Decoration): Json = a match {
      case b: OutwardState.Decoration.Bonk => b.asJson
      case b: OutwardState.Decoration.Bonus => b.asJson
      case b: OutwardState.Decoration.Explosion => b.asJson
      case b: OutwardState.Decoration.Text => b.asJson
    }
  }

  //  def print(event: TickRecorder.Event) = {
  //    event.asJson.noSpaces
  //  }

  def print(result: Seq[ResultRecorder.RoundResultAdded]) = {
    result.asJson.noSpaces
  }

  def print(state: OutwardState): String = {
    state.asJson.noSpaces
  }

  implicit val botDecoder: Decoder[OutwardState.Bot] = Decoder.instance(c =>
    c.downField("t").as[Char].flatMap {
      case '?' => c.as[OutwardState.Bot.Occluded]
      case 'P' => c.as[OutwardState.Bot.GoodPlant]
      case 'p' => c.as[OutwardState.Bot.BadPlant]
      case 'B' => c.as[OutwardState.Bot.GoodBeast]
      case 'b' => c.as[OutwardState.Bot.BadBeast]
      case 'W' => c.as[OutwardState.Bot.Wall]
      case 'M' => c.as[OutwardState.Bot.MasterPlayer]
      case 'S' => c.as[OutwardState.Bot.SlavePlayer]
    }
  )
}
