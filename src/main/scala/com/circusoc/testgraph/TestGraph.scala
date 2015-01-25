package com.circusoc.testgraph

import scala.util.Random

object testgraph {
  implicit class JoinPimp[FromType](collection: List[TestNode[FromType]]) {
    def join: JoinPimped[FromType] = new JoinPimped(collection)
    def markedJoin(marker: Symbol): JoinPimped[FromType] = new JoinPimped(collection, Some(marker))
  }
}

import testgraph._

trait TestNodeFactory[+NodeType] {
  def randomItem(): NodeType
  def randomNode(): TestNode[NodeType] = new TestNode(randomItem())
  def randomNode(marker: Symbol): TestNode[NodeType] = new TestNode(randomItem(), Some(marker))
}

object TestNodeFactory {
  def fromFunction[NodeType](f:() => NodeType): TestNode[NodeType] = new TestNode(f())
  def fromFunction[NodeType](marker: Symbol, f:() => NodeType): TestNode[NodeType] = new TestNode(f(), Some(marker))
}

trait NodeJoiner[FromType, ToType, ResultType] {
  def join(from: FromType, to: ToType): ResultType
  def join(from: TestNode[FromType], to: TestNode[ToType]): TestNodeJoin[FromType, ToType, ResultType] =
    new TestNodeJoin(from, to, join(from.node, to.node))
  def join(from: TestNode[FromType], to: ToType): TestNodeJoin[FromType, ToType, ResultType] = {
    val toNode = new TestNode(to)
    new TestNodeJoin(from, toNode, join(from.node, to))
  }
}

class TestNode[+NodeType](val node: NodeType, val marker: Option[Symbol] = None)
class TestNodeJoin[FromType, ToType, ResultType](val from: TestNode[FromType],
                                                 val to: TestNode[ToType],
                                                 val joinResult: ResultType) extends TestNode(joinResult)

class NodeJoinPimp[FromType, ToType, ResultType](node: TestNode[FromType]) {
  def join(to: TestNode[ToType])
          (implicit joinFunction: NodeJoiner[FromType, ToType, ResultType]):
  TestNodeJoin[FromType, ToType, ResultType] = {
    joinFunction.join(node, to)
  }
}

case class JoinPimped[FromType](collection: List[TestNode[FromType]], fromMarker: Option[Symbol] = None) {
  val matchingCollection = getMatchingNodes(collection, fromMarker)

  def getMatchingNodes[A](fromNodes: List[TestNode[A]], matching: Option[Symbol]): List[TestNode[A]] = {
    matching match {
      case None => fromNodes
      case Some(s) => fromNodes.filter(_.marker == s)
    }
  }
  
    def randomSurjectionJoin[ToType, ResultType](toNodes: List[TestNode[ToType]], toMarker: Option[Symbol] = None)
                                              (implicit joinFunction: NodeJoiner[FromType, ToType, ResultType]): List[TestNodeJoin[FromType, ToType, ResultType]] = {
    assert(matchingCollection.length <= toNodes.length)
    val matchingToNodes = getMatchingNodes(toNodes, toMarker)
    val fromLength = matchingCollection.length
    val shuffledFrom = Random.shuffle(matchingCollection)
    val shuffledTo = Random.shuffle(matchingToNodes)
    val pairedNum = math.min(shuffledFrom.length, shuffledTo.length)
    val extras = shuffledTo.drop(pairedNum)
    val bijection = shuffledFrom.take(pairedNum).join.bijectiveJoin(shuffledTo.take(pairedNum))
    val extraJoins = for {extra <- extras} yield {
      val fromPick = shuffledFrom(Random.nextInt(fromLength))
      joinFunction.join(fromPick, extra)
    }
    bijection ++ extraJoins
  }

  def bijectiveJoin[ToType, ResultType](toNodes: List[TestNode[ToType]], toMarker: Option[Symbol] = None)
                                       (implicit joinFunction: NodeJoiner[FromType, ToType, ResultType]): List[TestNodeJoin[FromType, ToType, ResultType]] = {
    val matchingToNodes = getMatchingNodes(toNodes, toMarker)
    for {
      (left, right) <- matchingCollection.zip(matchingToNodes)
    } yield joinFunction.join(left, right)
  }

  def cartesianJoin[ToType, ResultType](toNodes: List[TestNode[ToType]], toMarker: Option[Symbol] = None)
                                       (implicit joinFunction: NodeJoiner[FromType, ToType, ResultType]): List[TestNodeJoin[FromType, ToType, ResultType]] = {
    val matchingToNodes = getMatchingNodes(toNodes, toMarker)
    for {
      left <- matchingCollection
      right <- matchingToNodes
    } yield joinFunction.join(left, right)
  }

  // TODO: Add args that decide what proportions are left out, and the density of connection
  def randomJoin[ToType, ResultType](toNodes: List[TestNode[ToType]], toMarker: Option[Symbol] = None)
                                    (implicit joinFunction: NodeJoiner[FromType, ToType, ResultType]): List[TestNodeJoin[FromType, ToType, ResultType]] = {
    val matchingToNodes = getMatchingNodes(toNodes, toMarker)
    val connectingFromSubset = Random.shuffle(matchingCollection).take(matchingCollection.length * 10 / 8).toVector
    val connectingToSubset = Random.shuffle(matchingToNodes).take(matchingToNodes.length * 10 / 8).toVector
    val numFrom = connectingFromSubset.length
    val numTo = connectingToSubset.length
    val numConnections = (numFrom*numTo) / 10

    val connections = for {i <- 0 until numConnections} yield {
      val from = connectingFromSubset(Random.nextInt(numFrom))
      val to = connectingToSubset(Random.nextInt(numTo))
      joinFunction.join(from, to)
    }
    connections.toList
  }
}