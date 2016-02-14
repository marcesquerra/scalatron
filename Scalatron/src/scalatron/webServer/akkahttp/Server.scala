package scalatron.webServer.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage, Message}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, Flow}
import akka.stream.stage.{TerminationDirective, SyncDirective, Context, PushStage}

import scala.util.{Failure, Success}

object Server {

  private var counter = 0

  def nextId = {
    counter += 1
    counter
  }

  def greeter(implicit m: ActorMaterializer): Flow[Message, Message, Any] = Flow[Message].mapConcat {
    case tm: TextMessage =>
      TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
    case bm: BinaryMessage =>
      // ignore binary messages but drain content to avoid the stream being clogged
      bm.dataStream.runWith(Sink.ignore)
      Nil
  }

  def websocketFlow(id: Int, roomView: RoomView): Flow[Message, Message, Unit] =
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒ msg // unpack incoming WS text messages...
      }
      .via(roomView.viewFlow(id)) // ... and route them through the viewFlow ...
      .map {
        case msg: RoomView.Message ⇒
          TextMessage.Strict(msg.toString) // ... pack outgoing messages into WS JSON messages ...
      }
      .via(reportErrorsFlow) // ... then log any processing errors on stdin

  def reportErrorsFlow[T]: Flow[T, T, Unit] =
    Flow[T]
      .transform(() ⇒ new PushStage[T, T] {
        def onPush(elem: T, ctx: Context[T]): SyncDirective = ctx.push(elem)

        override def onUpstreamFailure(cause: Throwable, ctx: Context[T]): TerminationDirective = {
          println(s"WS stream failed with $cause")
          super.onUpstreamFailure(cause, ctx)
        }
      })

  def start(implicit system: ActorSystem): RoomView = {

    implicit val materializer = ActorMaterializer()

    implicit val ec = system.dispatcher

    val roomView = RoomView.create(system)

    val websocketRoute: Route = path("room") {
      handleWebsocketMessages(websocketFlow(nextId, roomView))
    }

    Http().bindAndHandle(websocketRoute, "0.0.0.0", 8888).onComplete {
      case Success(b) =>
        println(s"Server started")
        sys.addShutdownHook {
          b.unbind()
          system.terminate()
          println("Server stopped")
        }
      case Failure(e) =>
        println(s"Cannot start server")
        e.printStackTrace()
        sys.addShutdownHook {
          system.terminate()
          println("Server stopped")
        }
    }
    roomView
  }
}
