package scalatron.webServer.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage, Message}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, Flow}

import scala.util.{Failure, Success}

object Server {

  def greeter(implicit m: ActorMaterializer): Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage =>
        TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }


  def start(implicit system: ActorSystem): Unit = {
    
    implicit val materializer = ActorMaterializer()

    implicit val ec = system.dispatcher

    val websocketRoute: Route =
      path("greeter") {
        handleWebsocketMessages(greeter)
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

  }
}
