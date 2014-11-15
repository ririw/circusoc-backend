package com.circusoc.commonsense

import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import spray.json._

class EdgeSpec extends FlatSpec {
  import EdgeJsonProtocol.EdgeJsonSupport
  implicit val jsonReader = EdgeJsonSupport
  it should "Parse properly" in {
    val json = """{"start": "/c/en/la_flocellière", "end": "/c/en/settlement", "features": ["/c/en/la_flocellière /r/IsA -", "/c/en/la_flocellière - /c/en/settlement", "- /r/IsA /c/en/settlement"], "license": "/l/CC/By-SA", "context": "/ctx/all", "rel": "/r/IsA", "dataset": "/d/dbpedia/en", "sources": ["/s/dbpedia/3.7"], "surfaceText": null, "id": "/e/59dd5029393d836ce82aaf7acf554d92074b4378", "uri": "/a/[/r/IsA/,/c/en/la_flocellière/,/c/en/settlement/]", "weight": 0.5849625007211562, "source_uri": "/s/dbpedia/3.7"}"""
    val obj = json.parseJson.convertTo[Edge]
  }
  it should "Parse a sample" in {
    val sampleStream = classOf[EdgeSpec].getResourceAsStream("/com/circusoc/commonsense/sample.jsons")
    sampleStream should not be null
    val file = scala.io.Source.fromInputStream(sampleStream).getLines() map {
      _.parseJson.convertTo[Edge]
    }
    file.toStream.toList
  }
}
