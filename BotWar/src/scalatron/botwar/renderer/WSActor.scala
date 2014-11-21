package scalatron.botwar.renderer

import akka.actor.{Actor, ActorRef, Props, Terminated}
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64
import java.io.ByteArrayOutputStream
import scala.concurrent.duration._
import akka.dispatch.Dispatchers


object WSActor {

  case object Register

  case object Sample

  case class Image(bi: BufferedImage)

  case class ImageWithHeader(header: Array[Byte], img: Array[Byte])

  case class Render(renderer: Renderer)

  def props = Props[WSActor]
}

class WSActor extends Actor {

  import scalatron.botwar.renderer.WSActor._

  implicit val ec = context.dispatcher

  var count = 0L
  var last = System.currentTimeMillis()
  var image: Option[BufferedImage] = None
  var renderer: Option[Renderer] = None
  val participants = collection.mutable.Set[ActorRef]()
  val baos = new ByteArrayOutputStream()

  println("created WSActor")

  context.system.scheduler.schedule(0 millisecond, 20 milliseconds, self, Sample)

  override def receive: Receive = {
    case Sample =>
      renderImage(renderer)
//      sendImage(image)

    case Register =>
      participants += sender()
      context.watch(sender())

    case Terminated(_) => participants -= sender()

    case Render(r) => renderer = Some(r)
    case Image(bi) =>
      count += 1
      image =
      if (participants.nonEmpty) Some(bi)
      else None
    //      count += 1
    //      if(System.currentTimeMillis() - last > 1000) {
    //        println(s"$count per s")
    //        last = System.currentTimeMillis()
    //        count = 0
    //      }
    //      println(s"$self sending to client actors $participants")
    //      baos.reset()
    //      ImageIO.write(bi, "png", baos)
    //      baos.flush()
    //      val bytes = baos.toByteArray
    //      val header = "%09d".format(Base64.encode(bytes).length).getBytes
    //      participants.foreach(_ ! ImageWithHeader(header, bytes))

    //      baos.close()
    //    case Image(bi) =>
    //      baos.reset()
    //      ImageIO.write( bi, "png", baos )
    //      baos.flush()
    //      val bytes = baos.toByteArray
    //      val header = "%09d".format(Base64.encode(bytes).length).getBytes
    //
    //      count += 1
    //      participants.foreach(_ ! ImageWithHeader(header, bytes))
  }

  def renderImage(renderer: Option[Renderer]) {
//    println(renderer)
    renderer.foreach(r => sendImage(r.currentImage))
  }

  def sendImage(image: Option[BufferedImage]) {
//    println("sampling")
    image foreach {
      i =>
        baos.reset()
        ImageIO.write(i, "png", baos)
        baos.flush()
        val bytes = baos.toByteArray
        val header = "%09d".format(Base64.encode(bytes).length).getBytes
        participants.foreach(_ ! ImageWithHeader(header, bytes))
    }
  }
}
