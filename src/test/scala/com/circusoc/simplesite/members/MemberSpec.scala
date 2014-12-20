package com.circusoc.simplesite.members

import java.sql.{Connection, DriverManager}

import com.circusoc.simplesite._
import com.circusoc.simplesite.users.AuthenticatedUser
import com.circusoc.simplesite.users.permissions.{CanEditTagsPermission, CanUpdateMembers}
import org.codemonkey.simplejavamail.Email
import org.dbunit.DBTestCase
import org.dbunit.database.DatabaseConnection
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import org.dbunit.operation.DatabaseOperation
import org.joda.time.DateTime
import org.scalatest.Matchers._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import scalikejdbc._

class MemberSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter with PropertyChecks {
  implicit val config = new WithConfig {
    override val db: com.circusoc.simplesite.DB = new com.circusoc.simplesite.DB {
      override val poolName = 'memberspec
      override def setup() = {
        Class.forName("org.h2.Driver")
        val url = s"jdbc:h2:mem:memberspec;DB_CLOSE_DELAY=-1"
        ConnectionPool.add(poolName, url, "sa", "")
      }
    }
    override val hire: Hire = new Hire {}
    override val mailer: MailerLike = new MailerLike {
      override def sendMail(email: Email): Unit = throw new NotImplementedError()
    }
    override val paths: PathConfig = new PathConfig {}
  }

  def getJDBC: Connection = {
    Class.forName("org.h2.Driver")
    val c = DriverManager.getConnection(s"jdbc:h2:mem:memberspec;DB_CLOSE_DELAY=-1", "sa", "")
    c.setAutoCommit(true)
    c
  }
  config.db.setup()
  DBSetup.setup()(config)

  val conn = new DatabaseConnection(getJDBC)
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[MemberSpec].
    getResourceAsStream("/com/circusoc/simplesite/members/MembersDBSpec.xml"))

  it should "get members by email" in {
    val member = config.db.getDB().readOnly(implicit session => Member.getMember("steve123@example.com"))
    assert(member.isDefined)
    member.get.name should be("steve")
    member.get.lastPayment should be (new DateTime(2014, 1, 1, 0, 0, 0))
    member.get.lastWaiver should be (new DateTime(2015, 1, 1, 0, 0, 0))
  }
  it should "get members by id" in {
    val member = config.db.getDB().readOnly(implicit session => Member.getMember(1))
    assert(member.isDefined)
    member.get.name should be("steve")
    member.get.lastPayment should be (new DateTime(2014, 1, 1, 0, 0, 0))
    member.get.lastWaiver should be (new DateTime(2015, 1, 1, 0, 0, 0))
  }

  it should "get members by name search" in {
    val members = config.db.getDB().readOnly(implicit session => Member.searchMembers("eve"))
    assert(members.length == 1)
    val member = members.head
    member.name should be("steve")
    member.lastPayment should be (new DateTime(2014, 1, 1, 0, 0, 0))
    member.lastWaiver should be (new DateTime(2015, 1, 1, 0, 0, 0))
  }

  it should "get members by email search" in {
    val members = config.db.getDB().readOnly(implicit session => Member.searchMembers("example.com"))
    assert(members.length == 2)
    val names = members.map(_.name).sorted
    val emails = members.map(_.email).sorted
    names should be(List("bob", "steve"))
    emails should be(List(Some("bob444@example.com"), Some("steve123@example.com")))
  }
  it should "get members by student number search" in {
    val members = config.db.getDB().readOnly(implicit session => Member.searchMembers("333222111"))
    assert(members.length == 1)
    members.head.id should be(3)
  }

  it should "update payment and waivers" in {
    val member = config.db.getDB().readOnly(implicit session => Member.getMember(3)).head
    val updatedMember = config.db.getDB().autoCommit { implicit session =>
      Member.recordPaymentAndWaiver(member, TestUpdatePermission())
    }
    val newMember = config.db.getDB().readOnly(implicit session => Member.getMember(3)).head
    newMember should be(updatedMember)
  }

  it should "get the list of subscribed users" in {
    val members = config.db.getDB().readOnly(implicit session => Member.getSubscribedEmails())
    members.sorted should be(List("bob444@example.com", "steve123@example.com"))
  }

  it should "get all the members" in {
    val members = config.db.getDB().readOnly(implicit session => Member.getAllMembers)
    members.map(_.id).sorted should be(List(1, 2, 3))
  }
  it should "add members" in {
    val anne = config.db.getDB().localTx { implicit s =>
      val anne1 = Member.newMember("Anne Alison", None, Some(StudentRecord("z325555", true)), false).left.get
      val anne2 = Member.searchMembers("anne").head
      anne1 should be(anne2)
      anne1
    }
    val anne3 = config.db.getDB().readOnly(implicit s => Member.searchMembers("anne").head)
    anne should be(anne3)
  }
  it should "be impossible to have two users with the same name" in {
    val broken = config.db.getDB().localTx { implicit s =>
      Member.newMember("steve", None, Some(StudentRecord("xxxxxx", true)), false)
    }
    broken should be(Right(DuplicateNameError))
  }
  it should "be impossible to have two users with the same email" in {
    val broken = config.db.getDB().localTx { implicit s =>
      Member.newMember("bobbert", Some("steve123@example.com"), Some(StudentRecord("xxxxxx", true)), false)
    }
    broken should be(Right(DuplicateEmailError))
  }
  it should "be impossible to have two users with the same student number" in {
    val broken = config.db.getDB().localTx { implicit s =>
      Member.newMember("bobbert", Some("cxedasda@derp.com"), Some(StudentRecord("333222111", true)), false)
    }
    broken should be(Right(DuplicateSNumError))
  }
  it should "create proofs from authenticated users" in {
    val autheduser = new AuthenticatedUser(1, "steve", Set(CanUpdateMembers()))
    HasUpdatePermission(autheduser)
  }
  it should "reject proofs when the user doens't have permission" in {
    val autheduser = new AuthenticatedUser(1, "steve", Set(CanEditTagsPermission()))
    intercept[AssertionError]{
      HasUpdatePermission(autheduser)
    }
  }
  "mapMessage" should "throw unknown exceptions" in {
  val e = new org.h2.jdbc.JdbcSQLException("", "", "", 0, null, "")
    intercept[org.h2.jdbc.JdbcSQLException] {
      Member.mapMessage(e)
    }
  }

}

