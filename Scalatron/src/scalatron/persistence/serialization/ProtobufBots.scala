package scalatron.persistence.serialization

import scala.collection.immutable.Seq
import scalatron.core.Simulation.OutwardState
import scalatron.core.Simulation.OutwardState.Bot._
import scalatron.persistence.TickRecorder.StepAdded
import scalatron.serialization.Protobuf._

trait ProtobufBots {

  def getBots(s: StepAdded) = {
    s.outwardState.bots.map {
      case b: BadBeast =>
        val badBeast = BadBeastFormat.newBuilder()
          .setId(b.id).build()
        BotFormat.newBuilder()
          .setType(BotFormat.Type.BadBeast)
          .setX(b.x).setY(b.y)
          .setExtension(BadBeastFormat.extension, badBeast)
          .build()
      case b: BadPlant =>
        val badPlant = BadPlantFormat.newBuilder()
          .build()
        BotFormat.newBuilder()
          .setType(BotFormat.Type.BadPlant)
          .setX(b.x).setY(b.y)
          .setExtension(BadPlantFormat.extension, badPlant)
          .build()
      case b: GoodBeast =>
        val goodBeast = GoodBeastFormat.newBuilder()
          .setId(b.id).build()
        BotFormat.newBuilder()
          .setType(BotFormat.Type.GoodBeast)
          .setX(b.x).setY(b.y)
          .setExtension(GoodBeastFormat.extension, goodBeast)
          .build()
      case b: GoodPlant =>
        val goodPlant = GoodPlantFormat.newBuilder().build()
        BotFormat.newBuilder()
          .setType(BotFormat.Type.GoodPlant)
          .setX(b.x).setY(b.y)
          .setExtension(GoodPlantFormat.extension, goodPlant)
          .build()
      case b: MasterPlayer =>
        val masterPlayer = MasterPlayerFormat.newBuilder()
          .setId(b.id).setCpu(b.cpu).setE(b.e).setName(b.name).build()
        BotFormat.newBuilder()
          .setType(BotFormat.Type.MasterPlayer)
          .setX(b.x).setY(b.y)
          .setExtension(MasterPlayerFormat.extension, masterPlayer)
          .build()
      case b: Occluded =>
        val occluded = OccludedFormat.newBuilder()
          .build()
        BotFormat.newBuilder()
          .setX(b.x).setY(b.y)
          .setExtension(OccludedFormat.extension, occluded)
          .build()
      case b: SlavePlayer =>
        val slavePlayer = SlavePlayerFormat.newBuilder()
          .setMId(b.mId).build()
        BotFormat.newBuilder()
          .setType(BotFormat.Type.SlavePlayer)
          .setX(b.x).setY(b.y)
          .setExtension(SlavePlayerFormat.extension, slavePlayer)
          .build()
      case b: Wall =>
        val xy = XY.newBuilder()
        val wall = WallFormat.newBuilder()
          .setE(xy.setX(b.e.x).setY(b.e.y)).build()
        BotFormat.newBuilder()
          .setType(BotFormat.Type.Wall)
          .setX(b.x).setY(b.y)
          .setExtension(WallFormat.extension, wall)
          .build()
    }
  }

  def getBots(parsed: StepAddedFormat): Seq[OutwardState.Bot] = {
    import scala.collection.JavaConversions._

    parsed.getOutwardState.getBotsList.map(b => {
      b.getType match {
        case BotFormat.Type.BadBeast =>
          val x = b.getExtension(BadBeastFormat.extension)
          OutwardState.Bot.BadBeast(b.getX, b.getY, x.getId)
        case BotFormat.Type.BadPlant =>
          OutwardState.Bot.BadPlant(b.getX, b.getY)
        case BotFormat.Type.GoodBeast =>
          val x = b.getExtension(GoodBeastFormat.extension)
          OutwardState.Bot.GoodBeast(b.getX, b.getY, x.getId)
        case BotFormat.Type.GoodPlant =>
          OutwardState.Bot.GoodPlant(b.getX, b.getY)
        case BotFormat.Type.MasterPlayer =>
          val x = b.getExtension(MasterPlayerFormat.extension)
          OutwardState.Bot.MasterPlayer(b.getX, b.getY, x.getId, x.getCpu, x.getName, x.getE)
        case BotFormat.Type.Occluded =>
          OutwardState.Bot.Occluded(b.getX, b.getY)
        case BotFormat.Type.SlavePlayer =>
          val x = b.getExtension(SlavePlayerFormat.extension)
          OutwardState.Bot.SlavePlayer(b.getX, b.getY, x.getMId)
        case BotFormat.Type.Wall =>
          val x = b.getExtension(WallFormat.extension)
          val xy = OutwardState.XY(x.getE.getX, x.getE.getY)
          OutwardState.Bot.Wall(b.getX, b.getY, xy)
      }
    }).toVector
  }
}
