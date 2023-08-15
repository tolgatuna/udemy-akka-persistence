package part2eventsourcing

import akka.actor.{ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.persistence.PersistentActor

import java.util.Date

object PersistentActors extends App {
  /*
    Scenario: we have a business and an accountant which keeps track of our invoices
   */

  // COMMAND
  private case class Invoice(recipient: String, date: Date, amount: Int)
  private case class InvoiceBulk(invoices: List[Invoice])
  // OUR SPECIAL SHUTDOWN COMMAND
  private case object Shutdown

  // EVENT
  private case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)


  private class Accountant extends PersistentActor with ActorLogging {
    private var latestInvoiceId = 0
    private var totalAmount = 0

    override def persistenceId: String = "simple-accountant" // best practice: make it unique

    /**
     * The "normal" receive method of the Actor
     */
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        log.info(s"Receive invoice for amount: $amount")
        /*
          When you receive a command
            #1 - you create an EVENT to persist into the store
            #2 - you persist the event, the pass in a callback that will get triggered once the event is written
            #3 - we update the actor's state when the event has persisted
         */
        val event = InvoiceRecorded(latestInvoiceId, recipient, date, amount)
        persist(event) { savedEvent =>
          // SAFE to access mutable state in persist!
          // time gap: all other messages sent to this actor are STASHED!
          latestInvoiceId += 1
          totalAmount += savedEvent.amount

          // correctly identify the sender of the command!
          sender() ! "PersistenceACK"
          log.info(s"Persisted $savedEvent as invoice #${savedEvent.id}, for total amount $totalAmount")
        }
      case InvoiceBulk(invoices) =>
        /*
          When you receive a command
            #1 - Create events (plural :d)
            #2 - Persist all the events
            #3 - Update the actor's state when the events are persisted via persistAll
         */
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map {pair =>
          val id = pair._2
          val invoice = pair._1
          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }
        persistAll(events) { event =>
          latestInvoiceId += 1
          totalAmount += event.amount
          log.info(s"Persisted SINGLE $event as invoice #${event.id}, for total amount $totalAmount")
        }
      case Shutdown => context.stop(self)
      case "print" =>
        // you can use PersistentActor also as normal actor! No need to persist everything
        log.info(s"Latest invoice id: $latestInvoiceId and total amount: $totalAmount")
    }

    /**
     * Handler that will be called on recovery
     * Best practice: follow the logic in the persist steps of receive command which effects Actor!
     */
    override def receiveRecover: Receive = {
      case InvoiceRecorded(id, _, _, amount) =>
        log.info(s"Recovered invoice #$id for amount $amount, total amount $totalAmount")
        latestInvoiceId = id
        totalAmount += amount
    }

    /*
      This method is called if persisting failed.
      The actor will be STOPPED!

      Best practice: start the actor again after a while.
      (use Backoff supervisor)
     */
    override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    /*
      This method is called if the JOURNAL fails to persist the event
      The actor is RESUMED.

      Best practice: start the actor again after a while.
      (use Backoff supervisor)
     */
    override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistenceActors")
  private val accountant: ActorRef = system.actorOf(Props[Accountant])

//  for(i <- 1 to 10) {
//    accountant ! Invoice("The Sofa Company", new Date, i * 1000)
//  }

  /*
    Persistence Failures (Check in the Actor code):
      - override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit
      - override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit
   */

  /**
   * Persisting multiple events:
   *  - persistAll
   */
  private val newInvoices = for(i <- 1 to 5) yield Invoice("A chair", new Date, i * 200)
//  accountant ! InvoiceBulk(newInvoices.toList)

  /*
    IMPORTANT! NEVER EVER CALL PERSIST OR PERSIST-ALL FROM FUTURES!
   */

  /**
   * Shutdown of persistent actors
   *
   * Best practice: Define your own way to shutdown!
   */
//  accountant ! PoisonPill // DON'T USE IT!
  accountant ! Shutdown // It will done at the end of message box! so it is different from PoisonPill

}
