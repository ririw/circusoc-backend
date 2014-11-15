package com.circusoc.commonsense

import spray.json._

case class Edge(
                 id: EdgeId,
                 uri: IRI,
                 rel: Relation,
                 start: IRI,
                 end: IRI,
                 weight: Double,
                 sources: List[Source],
                 licence: Licence,
                 dataset: DataSet,
                 context: Context,
                 features: List[String],
                 surfaceText: Option[String]
                 )
object EdgeJsonProtocol extends DefaultJsonProtocol {
  object EdgeJsonSupport extends RootJsonReader[Edge]  {
    implicit val thisGetsTheJsonImportsSomehow = IRIJsonProtocol.IRIJsonSupport

    override def read(json: JsValue): Edge = json match {
      case o: JsObject =>
        val fields = o.fields
        val id = fields("id").convertTo[IRI].asInstanceOf[EdgeId]
        val uri = fields("uri").convertTo[IRI]
        val rel = fields("rel").convertTo[IRI].asInstanceOf[Relation]
        val start = fields("start").convertTo[IRI]
        val end = fields("end").convertTo[IRI]
        val weight = fields("weight").convertTo[Double]
        val sources = fields("sources").convertTo[List[IRI]].asInstanceOf[List[Source]]
        val licence = fields("license").convertTo[IRI].asInstanceOf[Licence]
        val dataset = fields("dataset").convertTo[IRI].asInstanceOf[DataSet]
        val context = fields("context").convertTo[IRI].asInstanceOf[Context]
        val features = fields("features").convertTo[List[String]]
        val surfaceTextOption = fields.get("surfaceText")
        val surfaceText = surfaceTextOption match {
          case None => None
          case Some(JsNull) => None
          case Some(JsString(s)) => Some(s)
          case _ => deserializationError(s"Unexpected value for sufaceText: $surfaceTextOption")
        }
        Edge(id, uri, rel, start, end, weight, sources, licence, dataset, context, features, surfaceText)
      case _ => deserializationError("Could not read object, Edge expected")
    }
  }
}
sealed trait IRI
case class Assertion(assertion: String) extends IRI
case class Concept(concept: String)     extends IRI
case class DataSet(dataset: String)     extends IRI
case class EdgeId(edge: String)         extends IRI
case class Licence(licence: String)     extends IRI
case class Relation(relation: String)   extends IRI
case class Context(context: String)     extends IRI
case class Source(source: String)       extends IRI
case class And(and: String)             extends IRI
case class Or(or: String)               extends IRI

object IRIJsonProtocol extends DefaultJsonProtocol {
  implicit object IRIJsonSupport extends JsonFormat[IRI] {
    override def write(obj: IRI): JsValue = obj match {
      case v: Assertion => JsString(v.assertion)
      case v: Concept => JsString(v.concept)
      case v: DataSet => JsString(v.dataset)
      case v: EdgeId => JsString(v.edge)
      case v: Licence => JsString(v.licence)
      case v: Relation => JsString(v.relation)
      case v: Context => JsString(v.context)
      case v: Source => JsString(v.source)
      case v: And => JsString(v.and)
      case v: Or => JsString(v.or)
    }

    override def read(json: JsValue): IRI = json match {
      case JsString(s) if s.length > 1 && s.charAt(0) == '/' =>
        s.substring(0, 3) match {
          case "/a/"   => Assertion(s)
          case "/c/"   => Concept(s)
          case "/d/"   => DataSet(s)
          case "/e/"   => EdgeId(s)
          case "/l/"   => Licence(s)
          case "/s/"   => Source(s)
          case "/r/"   => Relation(s)
          case _ =>
            if (s.startsWith("/and")) And(s)
            else if (s.startsWith("/or")) Or(s)
            else if (s.startsWith("/ctx/")) Context(s)
            else deserializationError(s"Could not match the string '$s' to an IRI")
        }
      case JsString(s) => deserializationError(s"The string '$s' was not valid")
      case _ => deserializationError("Value must be a string")
    }
  }
}