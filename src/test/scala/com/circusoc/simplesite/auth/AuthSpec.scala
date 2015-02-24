package com.circusoc.simplesite.auth

import org.dbunit.DBTestCase
import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import org.scalatest.prop.PropertyChecks
import com.circusoc.simplesite._
import scalikejdbc.ConnectionPool
import org.codemonkey.simplejavamail.Email
import java.net.URL
import java.sql.{DriverManager, Connection}
import org.dbunit.database.DatabaseConnection
import org.dbunit.operation.DatabaseOperation
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import com.circusoc.simplesite.users.{AuthenticatedUser, User}
import org.scalatest.Matchers._
import java.util.UUID


class AuthSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter with PropertyChecks {
  implicit val config = new WithConfig {
    override val db: DB = new DB {
      override val poolName = 'authspec
      override def setup() = {
        Class.forName("org.h2.Driver")
        val url = s"jdbc:h2:mem:${poolName.name};DB_CLOSE_DELAY=-1"
        ConnectionPool.add(poolName, url, "sa", "")
      }
    }
    override val hire: Hire = new Hire {}
    override val mailer: MailerLike = new MailerLike {
      override def sendMail(email: Email): Unit = throw new NotImplementedError()
    }
    override val paths: PathConfig = new PathConfig {
      override val cdnUrl = new URL("https://localhost:5051")
    }
    override val port: Int = 8080
  }

  def getJDBC: Connection = {
    Class.forName("org.h2.Driver")
    val c = DriverManager.getConnection("jdbc:h2:mem:authspec;DB_CLOSE_DELAY=-1", "sa", "")
    c.setAutoCommit(true)
    c
  }
  config.db.setup()
  DBSetup.setup()(config)


  val conn = new DatabaseConnection(getJDBC)
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[AuthSpec].
    getResourceAsStream("/com/circusoc/simplesite/auth/AuthDBSpec.xml"))


  it should "create tokens" in {
    val user = User.getUserByID(1).get
    val authedUser = new AuthenticatedUser(user.id, user.username, user.userPermissions)
    val token = Auth.getToken(authedUser)
    val foundUser = Auth.checkToken(token.token)
    foundUser should be(Some(authedUser))
  }
  it should "find existing tokens" in {
    val user = User.getUserByID(1).get
    val authedUser = new AuthenticatedUser(user.id, user.username, user.userPermissions)
    Auth.checkToken("helloworld") should be(Some(authedUser))
  }
  it should "not find random tokens" in {
    val uuid = UUID.randomUUID().toString
    Auth.checkToken(uuid) should be(None)
  }
  it should "revoke tokens" in {
    val user = User.getUserByID(1).get
    val authedUser = new AuthenticatedUser(user.id, user.username, user.userPermissions)
    val token = Auth.getToken(authedUser)
    val foundUser = Auth.checkToken(token.token)
    foundUser should be(Some(authedUser))

    Auth.revokeToken(token) should be(true)

    val unfoundUser = Auth.checkToken(token.token)
    unfoundUser should be(None)
  }
  it should "not revoke made up tokens" in {
    val uuid = UUID.randomUUID().toString
    Auth.revokeToken(AuthToken(uuid)) should be(false)
  }
}
