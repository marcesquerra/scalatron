package scalatron.scalatron.impl

import akka.actor.Props
import akka.actor.Actor
import scalatron.core.TournamentRoundResult

object Auditor {
  def props() = Props(new Auditor)
}
class Auditor extends Actor {

  def receive = {
    case r: TournamentRoundResult => println(s"Auditor says: $r")
  }
}