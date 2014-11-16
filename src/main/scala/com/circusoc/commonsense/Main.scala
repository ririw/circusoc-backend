package com.circusoc.commonsense

import java.io.File
import java.util.concurrent.TimeUnit

import com.codahale.metrics.{ConsoleReporter, MetricRegistry, ScheduledReporter}
import org.apache.spark.mllib.linalg.{Matrix, SingularValueDecomposition}
import org.apache.spark.rdd.RDD
import spray.json._

import scala.collection.mutable

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.mllib.linalg.distributed.{RowMatrix, CoordinateMatrix, MatrixEntry}

object Main extends App{
  val m = new Main()(MainConfig)
}

class Main(implicit config: CommonsenseConfig) {
  val workMeter = config.metrics.meter("relations converted")
  import com.circusoc.commonsense.EdgeJsonProtocol.EdgeJsonSupport
  implicit val jsonReader = EdgeJsonSupport
  lazy val infinifiles: Stream[File] = config.fileLocations.append(infinifiles)
  def inputStreams = infinifiles.take(1).map(scala.io.Source.fromFile)
  def linesStream = inputStreams.flatMap(_.getLines())
  def relationsStream = linesStream.map(_.parseJson.convertTo[Edge])
  config.reporter.start(1, TimeUnit.SECONDS)
  val conf = new SparkConf().setAppName("Simple Application").setMaster("local[*]")
  val sc = new SparkContext(conf)

  val lhsSet = new mutable.HashSet[LHS]()
  val rhsSet = new mutable.HashSet[RHS]()

  relationsStream.grouped(10000).foreach { group =>
    workMeter.mark(group.size)
    val streams = group.map { e =>
      (LHS.fromEdge(e), RHS.fromEdge(e))
    }
    val lhss = streams.flatMap(_._1).toSet
    val rhss = streams.flatMap(_._2).toSet
    lhsSet ++= lhss
    rhsSet ++= rhss
  }

  val rowIndex: Map[LHS, Int] = lhsSet.toList.zipWithIndex.toMap
  val colIndex: Map[RHS, Int] = rhsSet.toList.zipWithIndex.toMap

  val entries: Stream[MatrixEntry] = relationsStream.flatMap{relation =>
    val lhs = LHS.fromEdge(relation)
    val rhs = RHS.fromEdge(relation)
    if (lhs.isDefined) {
      val row = rowIndex(lhs.get)
      val col = colIndex(rhs.get)
      Some(MatrixEntry(row, col, 1.0))
    } else None
  }
  val entriesRDD: RDD[MatrixEntry] = sc.parallelize(entries)
  val mat = new CoordinateMatrix(entriesRDD).toIndexedRowMatrix()
  val svd = mat.computeSVD(20, computeU = true)
  println(svd)
}


trait CommonsenseConfig {
  def fileLocations: Stream[File]
  def metrics: MetricRegistry
  def reporter: ScheduledReporter
}

object MainConfig extends CommonsenseConfig {
  val fileLocations: Stream[File] = Stream(new File("/home/riri/Documents/circusoc/backend/src/test/resources/com/circusoc/commonsense/sample.jsons"))
  val metrics = new MetricRegistry()
  val reporter: ScheduledReporter = ConsoleReporter.forRegistry(metrics)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build()
}

case class LHS(rel: Relation, con: Concept)
object LHS {
  def fromEdge(edge: Edge): Option[LHS] = {
    (edge.start, edge.end) match {
      case (_: Concept, c2: Concept) => Some(LHS(edge.rel, c2))
      case _ => None
    }
  }
}

case class RHS(con: Concept)
object RHS {
  def fromEdge(edge: Edge): Option[RHS] = {
    (edge.start, edge.end) match {
      case (c1: Concept, _: Concept) => Some(RHS(c1))
      case _ => None
    }
  }
}
