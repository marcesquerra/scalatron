package scalatron.persistence.serialization

import akka.serialization.Serializer
import com.google.protobuf.ExtensionRegistry

import scala.collection.JavaConversions._
import scala.collection.immutable._
import scalatron.core.Simulation.OutwardState
import scalatron.persistence.LiveView.{RoundResult, RoundResults}
import scalatron.persistence.StepRecorder.{RoundEnded, RoundStarted, Snap, StepAdded}
import scalatron.serialization.Protobuf._

class ProtobufSerializer extends Serializer with ProtobufBots with ProtobufDecorations {

  val StepAddedClass = classOf[StepAdded]
  val RoundStartedClass = classOf[RoundStarted]
  val RoundEndedClass = classOf[RoundEnded]
  val SnapClass = Snap.getClass
  val RoundResultsClass = classOf[RoundResults]
  val registry = ExtensionRegistry.newInstance()
  registerAllExtensions(registry)

  override def identifier: Int = 20

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case Some(c) => c match {
      case StepAddedClass => stepAdded(bytes)
      case RoundStartedClass =>
        val parsed = RoundStartedFormat.parseFrom(bytes)
        RoundStarted(parsed.getRoundId, parsed.getDateTime)
      case RoundEndedClass =>
        val parsed = RoundEndedFormat.parseFrom(bytes)
        RoundEnded(parsed.getRoundId, parsed.getDateTime, parsed.getComplete)
      case SnapClass =>
        val parsed = SnapFormat.parseFrom(bytes)
        Snap
      case RoundResultsClass =>
        val parsed = RoundResultsFormat.parseFrom(bytes)
        RoundResults(parsed.getRoundResultsList.map(r => {
          RoundResult(r.getRoundId, r.getDateTime, r.getComplete, r.getResultsList.map(r1 => (r1.getName, r1.getEnergy)).toVector)
        }).toVector)
      case _ =>
        throw new IllegalArgumentException(s"can't deserialize object of type $c")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case s: StepAdded => stepAdded(s)
    case r: RoundStarted => RoundStartedFormat.newBuilder()
      .setRoundId(r.roundId).setDateTime(r.dateTime).build().toByteArray
    case r: RoundEnded => RoundEndedFormat.newBuilder()
      .setRoundId(r.roundId).setDateTime(r.dateTime).setComplete(r.complete).build().toByteArray
    case Snap => SnapFormat.newBuilder().build().toByteArray
    case rs: RoundResults =>
      val rb = ResultFormat.newBuilder()
      val b = RoundResultsFormat.newBuilder()
      b.addAllRoundResults(rs.roundResults.map(r => {
        RoundResultFormat.newBuilder()
          .setComplete(r.complete)
          .setDateTime(r.dateTime)
          .setRoundId(r.roundId)
          .addAllResults(r.results.map(x => rb.setName(x._1).setEnergy(x._2).build())).build()
      })).build().toByteArray

    case _ => throw new IllegalArgumentException(s"can't serialize object of type ${o.getClass}")
  }

  def stepAdded(s: StepAdded): Array[Byte] = {
    val ob = OutwardStateFormat.newBuilder()
    val xy = XY.newBuilder()
      .setX(s.outwardState.size.x).setY(s.outwardState.size.y)
    ob.addAllBots(getBots(s))
      .addAllDecorations(getDecorations(s))
      .setRounds(s.outwardState.rounds)
      .setSize(xy)
      .setTime(s.outwardState.time)
    StepAddedFormat.newBuilder()
      .setOutwardState(ob)
      .build().toByteArray
  }


  private def stepAdded(bytes: Array[Byte]): AnyRef = {
    val parsed = StepAddedFormat.parseFrom(bytes, registry)
    val rounds = parsed.getOutwardState.getRounds
    val size = OutwardState.XY(parsed.getOutwardState.getSize.getX,
      parsed.getOutwardState.getSize.getY)
    val time = parsed.getOutwardState.getTime
    val bots: Seq[OutwardState.Bot] = getBots(parsed)
    val decorations = getDecorations(parsed)
    StepAdded(OutwardState(rounds, size, time, bots, decorations))
  }
}
