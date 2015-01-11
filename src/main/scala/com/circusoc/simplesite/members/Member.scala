package com.circusoc.simplesite.members

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.users.AuthenticatedUser
import com.circusoc.simplesite.util.ApiFormats
import org.joda.time.DateTime
import scalikejdbc._
import spray.json._

import scala.util.{Failure, Success, Try}

case class Member(
  id: Long,
  name: String,
  email: Option[String],
  studentRecord: Option[StudentRecord],
  subscribed: Boolean,
  lastWaiver: DateTime,
  lastPayment: DateTime)

case class StudentRecord(studentNumber: String, isArc: Boolean)

/*
// $COVERAGE-OFF$
*/
object StudentRecordJsonSupport extends DefaultJsonProtocol {
    implicit val studentRecordFormat = jsonFormat2(StudentRecord)
}

object MemberJsonProtocol {
  import DefaultJsonProtocol._
  implicit object MemberJsonSupport extends RootJsonFormat[Member] {
    import StudentRecordJsonSupport._
    import ApiFormats._

    override def read(json: JsValue): Member = ???
    override def write(obj: Member): JsValue = JsObject(
      "id" -> JsNumber(obj.id),
      "name" -> JsString(obj.name),
      "email" -> obj.email.map(JsString(_)).getOrElse(JsNull),
      "student_record" -> obj.studentRecord.map(_.toJson).getOrElse(JsNull),
      "subscribed" -> JsBoolean(obj.subscribed),
      "last_waiver" -> obj.lastWaiver.toJson,
      "last_payment" -> obj.lastPayment.toJson
    )
  }
}
// $COVERAGE-ON$

object Member {
  def getMember(id: Long)(implicit config: WithConfig, session: DBSession): Option[Member] =
    getMemberByEmailOrId(Right(id))
  def getMember(email: String)(implicit config: WithConfig, session: DBSession): Option[Member] =
    getMemberByEmailOrId(Left(email))

  def searchMembers(text: String)(implicit config: WithConfig, session: DBSession): List[Member] = {
    val liketext = s"%$text%"
    sql"""SELECT
            id,
            name,
            email,
            student_number,
            is_arc,
            subscribed,
            max(waiver_time) as last_waiver,
            max(payment_time) as last_payment
           FROM
            member
            left join member_waiver on member.id=member_waiver.member_id
            left join member_payment on member.id=member_payment.member_id
           WHERE lower(name)             LIKE lower($liketext)
                OR lower(email)          LIKE lower($liketext)
                OR lower(student_number) LIKE lower($liketext)
           GROUP BY
             id,
             name,
             email,
             student_number,
             is_arc,
             subscribed
            """.map { m =>
      val id = m.long("id")
      val name = m.string("name")
      val email = m.stringOpt("email")
      val student_number = m.stringOpt("student_number")
      val is_arc = m.boolean("is_arc")
      val subscribed = m.boolean("subscribed")
      val last_waiver = m.jodaDateTime("last_waiver")
      val last_payment = m.jodaDateTime("last_payment")
      val student_record = student_number.map { n =>
        StudentRecord(n, is_arc)
      }
      Member(id, name, email, student_record, subscribed, last_waiver, last_payment)
    }.list()()
  }

  def getAllMembers(implicit config: WithConfig, session: DBSession): List[Member] = searchMembers("")

  private def getMemberByEmailOrId(emailOrId: Either[String, Long])
                                  (implicit config: WithConfig, session: DBSession): Option[Member] = {
    val email = emailOrId.left.toOption
    val id = emailOrId.right.toOption
    sql"""SELECT
           id,
           name,
           email,
           student_number,
           is_arc,
           subscribed,
           max(waiver_time) as last_waiver,
           max(payment_time) as last_payment
      FROM
        member
        left join member_waiver on member.id=member_waiver.member_id
        left join member_payment on member.id=member_payment.member_id
      WHERE
        email=$email
        or id=$id
      GROUP BY
        id,
        name,
        email,
        student_number,
        is_arc,
        subscribed
      """.map{m =>
      val id = m.long("id")
      val name = m.string("name")
      val email = m.stringOpt("email")
      val student_number = m.stringOpt("student_number")
      val is_arc = m.boolean("is_arc")
      val subscribed = m.boolean("subscribed")
      val last_waiver = m.jodaDateTime("last_waiver")
      val last_payment = m.jodaDateTime("last_payment")
      val student_record = student_number.map{n =>
        StudentRecord(n, is_arc)
      }
      Member(id, name, email, student_record, subscribed, last_waiver, last_payment)
    }.headOption().apply()
  }
  def newMember(name: String,
                email: Option[String],
                studentRecord: Option[StudentRecord],
                subscribed: Boolean)(
       implicit config: WithConfig,
                session: DBSession): Either[Member, MemberCreationError] = {
    val student_number = studentRecord.map(_.studentNumber)
    val is_arc = studentRecord.exists(_.isArc)
    val now = new DateTime()
    val result = Try {
      sql"""
        INSERT INTO member (name, email, student_number, is_arc, subscribed) VALUES
          ($name, $email, $student_number, $is_arc, $subscribed)
      """.updateAndReturnGeneratedKey()()
    }.map{id =>
      sql"""INSERT INTO member_payment VALUES ($id, $now)""".execute()()
      sql"""INSERT INTO member_waiver VALUES ($id, $now)""".execute()()
      Left(Member(id, name, email, studentRecord, subscribed, now, now))
    }
    result match {
      case Success(l) => l
      case Failure(e: org.h2.jdbc.JdbcSQLException) => Right(mapMessage(e))
      // $COVERAGE-OFF$
      case Failure(e) => throw e
      // $COVERAGE-ON$
    }
  }

  def mapMessage(exception: org.h2.jdbc.JdbcSQLException): MemberCreationError = {
    if      (exception.getMessage.contains("PUBLIC.MEMBER(NAME)")) DuplicateNameError
    else if (exception.getMessage.contains("PUBLIC.MEMBER(EMAIL)")) DuplicateEmailError
    else if (exception.getMessage.contains("PUBLIC.MEMBER(STUDENT_NUMBER)")) DuplicateSNumError
    else throw exception
  }

  def getSubscribedEmails()(implicit config: WithConfig,
                            session: DBSession): List[String] = {
    sql"""SELECT email FROM member WHERE subscribed""".map(e => e.stringOpt(1)).list().apply().flatten
  }

  def recordPaymentAndWaiver(member: Member, operationPermittedProof: MayUpdateUserProof)(
    implicit config: WithConfig,
             session: DBSession): Member = {
    val id = member.id
    val now = new DateTime()
    sql"""INSERT INTO member_payment VALUES ($id, $now)""".execute()()
    sql"""INSERT INTO member_waiver  VALUES ($id, $now)""".execute()()
    member.copy(lastPayment = now, lastWaiver = now)
  }
}

sealed trait MayUpdateUserProof

  case class HasUpdatePermission(updatingUser: AuthenticatedUser) extends MayUpdateUserProof {
  assert(updatingUser.hasPermission(com.circusoc.simplesite.users.permissions.CanUpdateMembers()))
}

case class TestUpdatePermission()(implicit config: WithConfig) extends MayUpdateUserProof {
  assert(!config.isProduction)
}

sealed trait MemberCreationError
object DuplicateNameError extends MemberCreationError
object DuplicateEmailError extends MemberCreationError
object DuplicateSNumError extends MemberCreationError