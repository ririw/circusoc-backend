package com.circusoc.taglink

import com.circusoc.testgraph._
import org.scalacheck.Gen

trait TaglinkTestTools {
  def server: TagLinkServer with TagLinkConfig
  implicit val testContentLinker = new TestContentLinker(server)

  implicit object TagLocationLinker extends NodeJoiner[TaglinkLocation, TaglinkTag, TaglinkTagLocationPair] {
    override def _join(from: TaglinkLocation, to: TaglinkTag): TaglinkTagLocationPair =
      TaglinkTagLocationPair(from, to)
  }


}

case class TaglinkContent(content: String)
case class TaglinkTag(name: Symbol)
case class TaglinkLocation(name: Symbol)

class TaglinkContentFactory extends TestNodeFactory[TaglinkContent] {
  override def randomItem(): TaglinkContent =
    TaglinkContent(Gen.alphaStr.sample.get)
}

class TaglinkTagFactory extends TestNodeFactory[TaglinkTag] {
  override def randomItem(): TaglinkTag =
    TaglinkTag(Symbol(Gen.alphaStr.sample.get))
}

class TaglinkLocationFactory extends TestNodeFactory[TaglinkLocation] {
  override def randomItem(): TaglinkLocation =
    TaglinkLocation(Symbol(Gen.alphaStr.sample.get))
}

case class TaglinkTagLocationPair(location: TaglinkLocation, tag: TaglinkTag)


class TestContentLinker(server: TagLinkServer with TagLinkConfig)
  extends NodeJoiner[TaglinkTagLocationPair, TaglinkContent, Boolean] {
  override def _join(from: TaglinkTagLocationPair, to: TaglinkContent): Boolean = {
    server.putItem(from.location.name.name, from.tag.name.name, to.content)
  }
}