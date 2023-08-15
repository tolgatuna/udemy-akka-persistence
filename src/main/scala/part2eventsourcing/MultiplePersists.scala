package part2eventsourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.Date

object MultiplePersists extends App {
  /*
    Diligent accountant: with every invoice, will persist
      - a tax record for the fiscal authority
      - an invoice record for personal logs or some audit authority
   */

  // COMMAND
  case class Invoice(recipient: String, date: Date, amount: Int)

  // EVENTS
  case class TaxRecord(taxId: String, recordId: Int, date: Date, totalAmount: Int)
  case class InvoiceRecord(invoiceRecordId: Int, recipient: String, date: Date, amount: Int)

  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef) = Props(new DiligentAccountant(taxId, taxAuthority))
  }

  class DiligentAccountant(taxId: String, taxAuthority: ActorRef) extends PersistentActor with ActorLogging {

    var latestTaxRecordId = 0
    var latestInvoiceRecordId = 0

    override def persistenceId: String = "diligent-accountant"

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        // journal ! TaxRecord
        persist(TaxRecord(taxId, latestTaxRecordId, date, amount / 4)) { record =>
          taxAuthority ! record
          latestTaxRecordId += 1

          persist("I hereby declare this tax record to be true and complete") { declaration =>
            taxAuthority ! declaration
          }
        }
        // journal ! InvoiceRecord
        persist(InvoiceRecord(latestInvoiceRecordId, recipient, date, amount)) { invoiceRecord =>
          taxAuthority ! invoiceRecord
          latestInvoiceRecordId += 1

          persist("I hereby declare this invoice record to be true and complete") { declaration =>
            taxAuthority ! declaration
          }
        }
    }

    override def receiveRecover: Receive = { // we don't care for this lecture
      case event => log.info(s"Recovered: $event")
    }
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Received $message")
    }
  }

  val system = ActorSystem("MultiplePersistDemo")
  private val taxAuthority: ActorRef = system.actorOf(Props[TaxAuthority], "HMRC")
  private val accountant: ActorRef = system.actorOf(DiligentAccountant.props("UK1312_123", taxAuthority), "Accountant")
  accountant ! Invoice("The Sofa", new Date, 2000)
  accountant ! Invoice("The Chair", new Date, 1000) // it has to wait for all persist finish for the first one! It is guaranteed

  /*
    The message ordering (which is TaxRecord -> InvoiceRecord in our case) is GUARANTEED
    PERSISTENCE IS ALSO BASED ON MESSAGE PASSING!
   */

  // nested persisting - they will done later after first persists completed!


}
