package part2eventsourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

object Snapshots extends App {

  // Commands
  case class ReceivedMessage(contents: String) // message FROM your contact
  case class SentMessage(contents: String) // message TO your contact

  // Events
  case class ReceivedMessageRecord(id: Int, contents: String)
  case class SentMessageRecord(id: Int, contents: String)

  object Chat {
    def props(owner: String, contact: String): Props = Props(new Chat(owner, contact))
  }
  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    private val MAX_MESSAGE_COUNT = 10

    private var commandsWithoutCheckpoint = 0
    private val lastMessages = new mutable.Queue[(String, String)]()
    private var currentMessageId = 0

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceivedMessageRecord(currentMessageId, contents)) { msg =>
          log.info(s"Received Message: $contents")
          addMessageToQueue(contact, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case SentMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) { msg =>
          log.info(s"Sent Message: $contents")
          addMessageToQueue(owner, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case "print" =>
        log.info(s"Most recent messages: $lastMessages")
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Saving snapshot succeeded: $metadata")
      case SaveSnapshotFailure(metadata, reason) =>
        log.warning(s"Saving snapshot failed: $metadata with reason $reason")
    }

    override def receiveRecover: Receive = {
      case ReceivedMessageRecord(id, contents) =>
        log.info(s"Recovered Received Message: $id -> $contents")
        addMessageToQueue(contact, contents)
        currentMessageId = id
      case SentMessageRecord(id, contents) =>
        log.info(s"Recovered Sent Message: $id -> $contents")
        addMessageToQueue(owner, contents)
        currentMessageId = id
      case SnapshotOffer(metadata, snapshot) =>
        log.info(s"Recovered snapshot $metadata")
        snapshot.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    private def addMessageToQueue(sender: String, contents: String): Unit = {
      if (lastMessages.size >= MAX_MESSAGE_COUNT) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender, contents))
    }

    private def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1
      if(commandsWithoutCheckpoint >= MAX_MESSAGE_COUNT) {
        log.info("Saving Snapshot")
        saveSnapshot(lastMessages)
        commandsWithoutCheckpoint = 0
      }
    }
  }

  val system = ActorSystem("SnapshotsDemo")
  private val chat = system.actorOf(Chat.props("tolga1234", "cem222"))
//  for(i <- 1 to 2){
//    chat ! ReceivedMessage(s"Akka Rocks ${i}")
//    chat ! SentMessage(s"Akka is amazing!! ${i}")
//  }

  /*
    snapshots (there is one configuration in application.conf)
   */
  chat ! "print"

}
