package part2eventsourcing

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentActorsExercise extends App {
  /*
    Persistent Actor for a voting station
    Keep:
      - the citizens who voted
      - the poll: mapping between a candidate and the number of received votes so far

    The actor must be able to recover its state if it's shut down or restarted
   */
  // COMMANDS
  case class Vote(citizenPID: String, candidate: String)

  private class VotingStation extends PersistentActor with ActorLogging {
    private var voting: Map[String, String] = Map()
    private var voteId = 0

    override def persistenceId: String = "VotingStation"

    override def receiveCommand: Receive = {
      case vote @ Vote(_, _) =>
        if(voting.contains(vote.citizenPID)) {
          log.warning(s"${vote.citizenPID} has already voted")
        } else {
          persist(vote) { _ => // COMMAND sourcing!
            handleVote(vote)
          }
        }

      case "print" =>
        log.info(s"Total vote count $voteId")
        val results = voting.groupBy(_._2).mapValues(_.size)
        log.info(s"Results: $results")

    }

    override def receiveRecover: Receive = {
      case vote @ Vote(_, _) =>
        handleVote(vote)
    }

    private def handleVote(vote: Vote): Unit = {
      voteId += 1
      voting = voting + (vote.citizenPID -> vote.candidate)
      log.info(s"${vote.citizenPID} voted for ${vote.candidate}")
    }
  }

  private val system: ActorSystem = ActorSystem("PersistentActorsExerciseSystem")
  private val votingManager: ActorRef = system.actorOf(Props[VotingStation])
  votingManager ! Vote("X001", "Rajah")
  votingManager ! Vote("X002", "Tom")
  votingManager ! Vote("X003", "Riddle")
  votingManager ! Vote("X004", "Riddle")
  votingManager ! Vote("X005", "Rajah")
  votingManager ! Vote("X006", "Rajah")
  votingManager ! Vote("X007", "Rajah")

  votingManager ! "print"

}
