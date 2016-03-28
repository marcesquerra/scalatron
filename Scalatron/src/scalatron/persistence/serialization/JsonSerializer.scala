package scalatron.persistence.serialization

import java.nio.charset.StandardCharsets

import akka.serialization.Serializer

import scalatron.persistence.LiveView.RoundResults
import scalatron.persistence.StepRecorder.{RoundEnded, RoundStarted, Snap, StepAdded}
import scalatron.webServer.akkahttp.Json

/**
 * This seems to slow :(
 */
class JsonSerializer extends Serializer {

  val StepAddedClass = classOf[StepAdded]
  val RoundStartedClass = classOf[RoundStarted]
  val RoundEndedClass = classOf[RoundEnded]
  val SnapClass = Snap.getClass
  val RoundResultsClass = classOf[RoundResults]

  override def identifier: Int = 20

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case Some(c) => c match {
      case StepAddedClass => Json.parseStepAddad(new String(bytes, StandardCharsets.UTF_8))
      case RoundStartedClass => Json.parseRoundStarted(new String(bytes, StandardCharsets.UTF_8))
      case RoundEndedClass => Json.parseRoundEnded(new String(bytes, StandardCharsets.UTF_8))
      case RoundResultsClass => Json.parseRoundResults(new String(bytes, StandardCharsets.UTF_8))
      case SnapClass => Snap
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case s: StepAdded => Json.print(s).getBytes(StandardCharsets.UTF_8)
    case s: RoundStarted => Json.print(s).getBytes(StandardCharsets.UTF_8)
    case s: RoundEnded => Json.print(s).getBytes(StandardCharsets.UTF_8)
    case s: RoundResults => Json.print(s).getBytes(StandardCharsets.UTF_8)
    case Snap => "".getBytes
  }

}
