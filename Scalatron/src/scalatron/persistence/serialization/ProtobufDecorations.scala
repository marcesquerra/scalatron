package scalatron.persistence.serialization

import scala.collection.immutable.Seq
import scalatron.core.Simulation.OutwardState
import scalatron.core.Simulation.OutwardState.Decoration
import scalatron.persistence.StepRecorder.StepAdded
import scalatron.serialization.Protobuf._

trait ProtobufDecorations {

  def getDecorations(s: StepAdded) = {
    s.outwardState.decorations.map {
      case d: Decoration.Bonk =>
        val bonk = BonkFormat.newBuilder().build()
        DecorationFormat.newBuilder()
          .setType(DecorationFormat.Type.Bonk)
          .setX(d.x).setY(d.y)
          .setExtension(BonkFormat.extension, bonk)
          .build()
      case d: Decoration.Bonus =>
        val bonus = BonusFormat.newBuilder()
          .setE(d.e).build()
        DecorationFormat.newBuilder()
          .setType(DecorationFormat.Type.Bonus)
          .setX(d.x).setY(d.y)
          .setExtension(BonusFormat.extension, bonus)
          .build()
      case d: Decoration.Explosion =>
        val explosion = ExplosionFormat.newBuilder()
          .setR(d.r).build()
        DecorationFormat.newBuilder()
          .setType(DecorationFormat.Type.Explosion)
          .setX(d.x).setY(d.y)
          .setExtension(ExplosionFormat.extension, explosion)
          .build()
      case d: Decoration.Text =>
        val text = TextFormat.newBuilder()
          .setText(d.text).build()
        DecorationFormat.newBuilder()
          .setType(DecorationFormat.Type.Text)
          .setX(d.x).setY(d.y)
          .setExtension(TextFormat.extension, text)
          .build()
    }
  }

  def getDecorations(parsed: StepAddedFormat): Seq[OutwardState.Decoration] = {
    import scala.collection.JavaConversions._

    parsed.getOutwardState.getDecorationsList.map(b => {
      b.getType match {
        case DecorationFormat.Type.Bonk =>
          val x = b.getExtension(BonkFormat.extension)
          Decoration.Bonk(b.getX, b.getY)
        case DecorationFormat.Type.Bonus =>
          val x = b.getExtension(BonusFormat.extension)
          Decoration.Bonus(b.getX, b.getY, x.getE)
        case DecorationFormat.Type.Explosion =>
          val x = b.getExtension(ExplosionFormat.extension)
          Decoration.Explosion(b.getX, b.getY, x.getR)
        case DecorationFormat.Type.Text =>
          val x = b.getExtension(TextFormat.extension)
          Decoration.Text(b.getX, b.getY, x.getText)
      }
    }).toVector
  }
}
