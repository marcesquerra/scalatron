package scalatron.webServer.akkahttp

import cats.data.Xor

import scala.collection.immutable._
import scalatron.core.Simulation.OutwardState
import scalatron.persistence.LiveView
import scalatron.persistence.StepRecorder._
import scalatron.webServer.akkahttp.RoomView.RoundResult

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

  def print(result: LiveView.RoundResult) = {
    result.asJson.noSpaces
  }

  def print(result: LiveView.RoundResults) = {
    result.asJson.noSpaces
  }

  def print(result: Seq[LiveView.RoundResult]) = {
    result.asJson.noSpaces
  }

  def print(state: OutwardState): String = {
    state.asJson.noSpaces
  }

  def print(state: RoomView.StateMessages): String = {
    state.s.asJson.noSpaces
  }

  def print(roundStarted: RoundStarted): String = {
    roundStarted.asJson.noSpaces
  }

  def print(roundEnded: RoundEnded): String = {
    roundEnded.asJson.noSpaces
  }

  def print(stepAdded: StepAdded): String = {
    stepAdded.asJson.noSpaces
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

  implicit val decDecoder: Decoder[OutwardState.Decoration] = Decoder.instance(c =>
    c.downField("t").as[Char].flatMap {
      case 'E' => c.as[OutwardState.Decoration.Explosion]
      case 'X' => c.as[OutwardState.Decoration.Bonk]
      case 'Y' => c.as[OutwardState.Decoration.Bonus]
      case 'T' => c.as[OutwardState.Decoration.Text]
    }
  )

  def parseStepAddad(string: String): StepAdded = {
    decode[StepAdded](string) match {
      case Xor.Right(r) => r
      case Xor.Left(t) => throw t
    }
  }

  def parseRoundResult(string: String): RoundResult = {
    decode[RoundResult](string) match {
      case Xor.Right(r) => r
      case Xor.Left(t) => throw t
    }
  }

  def parseRoundResults(string: String): LiveView.RoundResults = {
    decode[LiveView.RoundResults](string) match {
      case Xor.Right(r) => r
      case Xor.Left(t) => throw t
    }
  }

  def parseRoundEnded(string: String): RoundEnded = {
    decode[RoundEnded](string) match {
      case Xor.Right(r) => r
      case Xor.Left(t) => throw t
    }
  }

  def parseRoundStarted(string: String): RoundStarted = {
    decode[RoundStarted](string) match {
      case Xor.Right(r) => r
      case Xor.Left(t) => throw t
    }
  }

  def parseOutwardState(string: String): OutwardState = {
    decode[OutwardState](string) match {
      case Xor.Right(r) => r
      case Xor.Left(t) => throw t
    }
  }
}
