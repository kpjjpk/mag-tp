package org.tpmag

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

import com.softwaremill.macwire.wire
import com.softwaremill.tagging.{ @@ => @@ }

import ProductionSupervisor.EmployeeCount
import ProductionSupervisor.PeriodLength
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import breeze.linalg.support.CanTraverseValues
import breeze.linalg.support.CanTraverseValues.ValuesVisitor
import breeze.numerics.sqrt
import breeze.stats.MeanAndVariance
import breeze.stats.meanAndVariance
import breeze.stats.meanAndVariance.reduce_Double

object ProductionSupervisor {
  case class Produce(time: Time)
  case object FireLazies

  // Needed for statistics
  implicit object IterableAsTraversable extends CanTraverseValues[Iterable[Double], Double] {
    def traverse(from: Iterable[Double], fn: ValuesVisitor[Double]): Unit = from.map(fn.visit)
    def isTraversableAgain(from: Iterable[Double]): Boolean = true
  }

  trait PeriodLength
  trait EmployeeCount

  def props(initialTime: Time,
            periodLength: Int @@ PeriodLength,
            maxDeviationsAllowed: Double,
            employeeCount: Int @@ EmployeeCount,
            timerFreq: FiniteDuration): Props =
    Props(wire[ProductionSupervisor])
}

class ProductionSupervisor(
  initialTime: Time,
  periodLength: Int @@ PeriodLength,
  maxDeviationsAllowed: Double,
  employeeCount: Int @@ EmployeeCount,
  val timerFreq: FiniteDuration)
    extends ChainingActor
    with Timer
    with Scheduled {

  import Employee._
  import ProductionSupervisor._

  def timerMessage = FireLazies

  val producersPerTime = mutable.Map[Time, mutable.Set[ActorRef]]()

  def time: Time =
    producersPerTime.keys.foldLeft(initialTime)((t, maxT) => if (t > maxT) t else maxT)

  def foo: Receive = {
    case Produce(time) =>
      val producersForTime = producersPerTime.getOrElseUpdate(time, mutable.Set())
      producersForTime += sender

    case FireLazies if !producersPerTime.isEmpty =>
      val registeredTimes = producersPerTime.keys.toSeq
      val periodTimes = registeredTimes.sorted.take(periodLength)
      val (from, to) = (periodTimes.min, periodTimes.max)
      val laziesFound = lazies(from, to)
      println(s"\nFound ${laziesFound.size} lazies, will fire them")
      laziesFound.foreach(_ ! Fire) // TODO: maybe use a Router here?
      producersPerTime --= (from to to)
  }

  private[this] def lazies(from: Time, to: Time) = {
    def producersBetween(from: Time, to: Time): Iterable[ActorRef] =
      producersPerTime.collect { case (t, producers) if t >= from && t < to => producers }.flatten

    val relevantProducers = producersBetween(from, to)
    val producePerProducer = relevantProducers.groupBy(identity).mapValues(_.size.toDouble)
    val MeanAndVariance(meanProduce, produceVariance, _) = meanAndVariance(producePerProducer.values)
    val tolerance = maxDeviationsAllowed * sqrt(produceVariance)

    producePerProducer.collect {
      case (producer, produce) if Math.abs(produce - meanProduce) > tolerance => producer
    }
  }

  registerReceive(foo)
}
