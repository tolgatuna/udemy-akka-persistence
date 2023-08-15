package part1recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy}
import akka.util.Timeout

import scala.concurrent.Future
import scala.language.postfixOps


object AkkaRecap extends App {

  private class SimpleActor extends Actor with ActorLogging with Stash{

    override def preStart(): Unit = log.info("I am starting...")

    override def receive: Receive = {
      case "createChild" =>
        val myChild = context.actorOf(Props[SimpleActor], "myChild")
        myChild ! "Hello kido"
      case "stashThis" =>
        stash()
      case "change" =>
        unstashAll()
        context.become(anotherHandler)
      case message =>
        println(s"I have received $message")
    }

    private def anotherHandler: Receive = {
      case message => println(s"Another Handler: $message")
    }

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _ => Stop
    }
  }

  // actor encapsulation
  private val system: ActorSystem = ActorSystem("AkkaRecap")
  // #1: you can only instantiate an actor through the actor system
  private val simpleActor: ActorRef = system.actorOf(Props[SimpleActor])
  // #2: Sending message
  simpleActor ! "Hello Simple Actor"

  /*
    - messages are sent asynchronously
    - many actors (in the millions) can share a few dozen threads
    - each message is process/handled ATOMICALLY
    - no need for locks
   */

  // changing actor behavior + stashing
  // actors can spawn another actor
  /*
    Guardians:
    /system, /user, / = root guardian
   */
  // actors have a defined lifecycle = they can be started, stopped, suspended, resumed, restarted

  // some special messages
//  simpleActor ! PoisonPill

  // logging
  // supervision

  // configure Akka infrastructure: dispatchers, routers, mailboxes

  // schedulers
  import scala.concurrent.duration._
  import system.dispatcher
  system.scheduler.scheduleOnce(2 seconds) {
    simpleActor ! "delayed message!"
  }

  // Akka patterns including FSM + ask pattern
  import akka.pattern.ask
  implicit val timeout: Timeout = Timeout(3 seconds)
  private val future = simpleActor ? "question"

  // the pipe pattern
  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor)
}
