package com.circusoc.simplesite.pictures

import com.circusoc.simplesite.GraphTestingConf
import com.circusoc.testgraph.PictureTestGraph
import org.scalatest.FlatSpec

class PictureTestGraphSpec extends FlatSpec {
  import GraphTestingConf._
  it should "create pictures" in {
    val factory = PictureTestGraph.pictureFactory
    val picture = factory.randomItem()
    assert(PictureReference.getPicture(picture).isDefined)
  }
}
