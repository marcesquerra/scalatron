package scalatron.persistence.serialization

import akka.serialization.Serializer
import com.google.protobuf.ExtensionRegistry

import scala.collection.JavaConversions._
import scala.collection.immutable._
import scalatron.core.Simulation.OutwardState
import scalatron.persistence.ResultRecorder.{RoundResultAdded, RoundResults}
import scalatron.persistence.TickRecorder.{RoundEnded, StepAdded}
import scalatron.serialization.Protobuf._

class ProtobufSerializer extends Serializer with ProtobufBots with ProtobufDecorations {

  val StepAddedClass = classOf[StepAdded]
  val RoundEndedClass = classOf[RoundEnded]
  val RoundResultClass = classOf[RoundResultAdded]

  val registry = ExtensionRegistry.newInstance()
  registerAllExtensions(registry)

  override def identifier: Int = 20

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = manifest match {
    case Some(c) => c match {
      case StepAddedClass => stepAdded(bytes)
      case RoundEndedClass =>
        val parsed = RoundEndedFormat.parseFrom(bytes)
        RoundEnded(parsed.getRoundId, parsed.getDateTime, parsed.getComplete)
      case RoundResultClass =>
        val parsed = RoundResultAddedFormat.parseFrom(bytes)
        RoundResultAdded(parsed.getRoundId, parsed.getDateTime, parsed.getComplete, parsed.getResultsList
          .map(r => (r.getName, r.getEnergy)).toVector)

      case _ =>
        throw new IllegalArgumentException(s"can't deserialize object of type $c")
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case s: StepAdded => stepAdded(s)
    case r: RoundEnded => RoundEndedFormat.newBuilder()
      .setRoundId(r.roundId).setDateTime(r.dateTime).setComplete(r.complete).build().toByteArray
    case rs: RoundResultAdded =>
      val rb = ResultFormat.newBuilder()
      RoundResultAddedFormat.newBuilder()
      .setComplete(rs.complete)
        .setDateTime(rs.dateTime)
        .setRoundId(rs.roundId)
        .addAllResults(rs.results.map(x => rb.setName(x._1).setEnergy(x._2).build())).build()
        .toByteArray
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
