package com.circusoc.taglink

import com.circusoc.testgraph._
import org.scalacheck.Gen
import org.slf4j.LoggerFactory


class TagLocationLinker extends NodeJoiner[TaglinkLocation, TaglinkTag, TaglinkTagLocationPair] {
  override def _join(from: TaglinkLocation, to: TaglinkTag): TaglinkTagLocationPair = {
    TaglinkTagLocationPair(from, to)
  }
}

case class TaglinkContent(content: String)
case class TaglinkTag(name: Symbol)
case class TaglinkLocation(name: Symbol)

class TaglinkContentFactory extends TestNodeFactory[TaglinkContent] {
  val logger = LoggerFactory.getLogger(this.getClass.getName)
  override def randomItem(): TaglinkContent = {
    val name: String = Gen.alphaStr.sample.get.take(10)
    logger.info(s"Creating content entry $name")
    TaglinkContent(name)
  }
}

class TaglinkTagFactory extends TestNodeFactory[TaglinkTag] {
  val logger = LoggerFactory.getLogger(this.getClass.getName)
  override def randomItem(): TaglinkTag = {
    val name = Symbol(Gen.alphaStr.sample.get.take(10))
    logger.info(s"Creating tag entry $name")
    TaglinkTag(name)
  }
}

class TaglinkLocationFactory extends TestNodeFactory[TaglinkLocation] {
  val logger = LoggerFactory.getLogger(this.getClass.getName)
  override def randomItem(): TaglinkLocation = {
    val name = Symbol(Gen.alphaStr.sample.get.take(10))
    logger.info(s"Creating location $name")
    TaglinkLocation(name)
  }
}

case class TaglinkTagLocationPair(location: TaglinkLocation, tag: TaglinkTag)


class TestContentLinker(server: TagLinkServer with TagLinkConfig)
  extends NodeJoiner[TaglinkTagLocationPair, TaglinkContent, Boolean] {
  val logger = LoggerFactory.getLogger(this.getClass.getName)
  override def _join(from: TaglinkTagLocationPair, to: TaglinkContent): Boolean = {
    logger.info(s"Linking location ${from.location.name}/${from.tag.name.name} and ${to.content}")
    server.putItem(from.location.name.name, from.tag.name.name, to.content)
  }
}