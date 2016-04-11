package scalatron.webServer.akkahttp

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.stage.{Context, PushStage, SyncDirective, TerminationDirective}

import scala.util.{Failure, Success}
import scalatron.persistence.{LiveView, ReplayView}

object Server {

  private var counter = 0

  def nextId = {
    counter += 1
    counter
  }

  def websocketFlow(id: Int, roomView: RoomView): Flow[Message, Message, NotUsed] = Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒ msg // unpack incoming WS text messages...
      }
      .via(roomView.viewFlow(id)) // ... and route them through the viewFlow ...
      .map {
            case msg: RoomView.RoundResults =>
              TextMessage.Strict(Json.print(msg.results))

            case msg: RoomView.Message =>
              TextMessage.Strict(msg.toString) // ... pack outgoing messages into WS JSON messages ...
      }
    .via(reportErrorsFlow) // ... then log any processing errors on stdin

  def reportErrorsFlow[T]: Flow[T, T, NotUsed] =
    Flow[T]
      .transform(() ⇒ new PushStage[T, T] {
        def onPush(elem: T, ctx: Context[T]): SyncDirective = ctx.push(elem)

        override def onUpstreamFailure(cause: Throwable, ctx: Context[T]): TerminationDirective = {
          println(s"WS stream failed with $cause")
          super.onUpstreamFailure(cause, ctx)
        }
      })

  def live(implicit system: ActorSystem) = {
    println("live!!!!!")
    val src: Source[Message, Any] = LiveView.source.map(outwardState => TextMessage.Strict(Json.print(outwardState)))

    Flow.fromSinkAndSource(Sink.ignore, src).via(reportErrorsFlow)
  }

  def replay(roundId: String)(implicit system: ActorSystem) = {
    println("replay!!!!")
    val src: Source[Message, Any] = ReplayView.source(roundId).map(outwardState => TextMessage.Strict(Json.print(outwardState)))

    Flow.fromSinkAndSource(Sink.ignore, src).via(reportErrorsFlow)
  }

  def start()(implicit system: ActorSystem): RoomView = {

    implicit val materializer = ActorMaterializer()

    implicit val ec = system.dispatcher

    val roomView = RoomView.create(system)

    val wsRoute = path("ws") {
      handleWebSocketMessages(websocketFlow(nextId, roomView))
    } ~
      path("ws" / "live") {
        handleWebSocketMessages(live)
      } ~
      path("ws" / Segment) { roundId =>
        handleWebSocketMessages(replay(roundId))
      }

    //    val websocketRoute: Route = path("room") {
    //      handleWebSocketMessages(websocketFlow(nextId, roomView))
    //    } ~ path("live") {
    //      handleWebSocketMessages(live)
    //    }

    val routes = wsRoute

    Http().bindAndHandle(routes, "0.0.0.0", 8888).onComplete {
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
