package com.circusoc.simplesite.users

import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import org.scalatest.Matchers._
import org.dbunit.{PropertiesBasedJdbcDatabaseTester, DBTestCase}
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import com.circusoc.simplesite._
import java.sql.{Connection, DriverManager}
import org.dbunit.database.DatabaseConnection
import org.dbunit.operation.DatabaseOperation
import scalikejdbc.ConnectionPool
import com.circusoc.simplesite.users.permissions.Permission
import com.circusoc.simplesite.users.permissions.ChangePasswordPermission
import com.circusoc.simplesite.users.permissions.PermissionConstructionException
import scala.Some
import com.circusoc.simplesite.users.User.UserBuilder
import com.circusoc.simplesite.users.permissions.CanChangePermissionsPermission
import org.codemonkey.simplejavamail.Email

class UserSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter {
  System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, "org.h2.jdbcDriver" )
  System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, "jdbc:h2:mem:userspec;DB_CLOSE_DELAY=-1" )
  System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME, "sa" )
  System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD, "" )

  implicit val config = new WithConfig {
    override val db: DB = new DB {
      override val symbol = 'userspec
      override def setup() = {
        Class.forName("org.h2.Driver")
        val url = s"jdbc:h2:mem:${symbol.name};DB_CLOSE_DELAY=-1"
        ConnectionPool.add(symbol, url, "sa", "")
      }
    }
    override val hire: Hire = new Hire {}
    override val mailer: MailerLike = new MailerLike {
      override def sendMail(email: Email): Unit = ???
    }
  }
  def getJDBC(): Connection = {
    Class.forName("org.h2.Driver")
    val c = DriverManager.getConnection("jdbc:h2:mem:userspec;DB_CLOSE_DELAY=-1", "sa", "")
    c.setAutoCommit(true)
    c
  }
  config.db.setup()
  DBSetup.setup()

  val conn = new DatabaseConnection(getJDBC())
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
      build(classOf[UserSpec].
    getResourceAsStream("/com/circusoc/simplesite/users/UserDBSpec.xml"))

  it should "Not retrieve made up users" in {
      User.getUserByID(100) should be(None)
      User.getUserByName("JOEBVOOB") should be(None)
  }

  it should "Retrieve real users" in {
    val user1 = User.getUserByID(1)
    user1.map(_.id) should be(Some(1))
    user1.map(_.username) should be(Some("Admin"))
    val userAdmin = User.getUserByName("Admin")
    userAdmin.map(_.id) should be(Some(1))
    userAdmin.map(_.username) should be(Some("Admin"))
    assert(userAdmin.exists(_.hasPermission(CanChangePermissionsPermission())))
  }

  "the user class" should "deal with permissions" in {
    val user = new User(1,"asd", Set(Permission.apply("CanChangePermissionsPermission")))
    assert(user.hasPermission(Permission.apply("CanChangePermissionsPermission")))
    assert(!user.hasPermission(Permission.apply("ChangePasswordPermission")))
  }

  "the user builder" should "fill out right" in {
    val builder = UserBuilder()
    val user = builder.
      addId(1).
      addUsername("steve").
      addUsername("steve").
      addId(1).
      addPermission(Permission.apply("CanChangePermissionsPermission")).build()
    assert(user.nonEmpty)
    assert(user.exists(_.id == 1))
    assert(user.exists(_.username == "steve"))
    assert(user.exists(_.username == "steve"))
    assert(user.exists(_.hasPermission(Permission.apply("CanChangePermissionsPermission"))))
    assert(user.exists(!_.hasPermission(Permission.apply("ChangePasswordPermission"))))
  }

  it should "error when you double up on id" in {
    val builder = UserBuilder()
    val withID = builder.addId(1)
    intercept[AssertionError] {
      withID.addId(2)
    }
  }

  it should "error when you construct without ID" in {
    val builder = UserBuilder()
    val withID = builder.addId(1)
    intercept[AssertionError] {
      withID.build()
    }
  }

  it should "error when you construct with different names" in {
    val builder = UserBuilder()
    val withName = builder.addUsername("hello")
    intercept[AssertionError] {
      withName.addUsername("frwfde")
    }
  }
  it should "error when you construct without name" in {
    val builder = UserBuilder()
    val withName = builder.addUsername("hello")
    intercept[AssertionError] {
      withName.addUsername("steve")
    }
  }

  "the password stuff" should "change passwords and authenticate" in {
    val user = new UserBuilder().addId(1).addUsername("Admin").build().get
    User.changePassword(user, Password("joe"), User.MayChangePassProof.isTest)
    val founduser = User.authenticateByUsername("Admin", Password("joe"))
    assert(founduser.isDefined)
    val unfounduser = User.authenticateByUsername("Admin", Password("joeee"))
    assert(unfounduser.isEmpty)
  }

  it should "not allow test proofs in production" in {
    val prod_config = new WithConfig {
      override val isProduction = true
      override val db: DB = new DB {
        override val symbol = 'userspec
        override def setup() = {
          Class.forName("org.h2.Driver")
          val url = s"jdbc:h2:mem:${symbol.name};DB_CLOSE_DELAY=-1"
          ConnectionPool.add(symbol, url, "sa", "")
        }
      }
      override val hire: Hire = new Hire {}
      override val mailer: MailerLike = new MailerLike {
        override def sendMail(email: Email): Unit = ???
      }
    }
    intercept[AssertionError] {
      User.MayChangePassProof.isTest(prod_config)
    }
  }

  it should "be able to change authenticated user's passwords" in {
    val user = new UserBuilder().addId(1).addUsername("Admin").build().get
    val authenticatedUser = new AuthenticatedUser(user.id, user.username, user.userPermissions)
    authenticatedUser.changePassword(Password("boob"), authenticatedUser)
    val unfounduser = User.authenticateByUsername("Admin", Password("joe"))
    assert(unfounduser.isEmpty)
    val founduser = User.authenticateByUsername("Admin", Password("boob"))
    assert(founduser.isDefined)
  }

  it should "change other's passwords if the changing user has the change permission" in {
    val user = new UserBuilder().addId(1).addUsername("Admin").build().get
    val changingUser = new UserBuilder().addId(1).addUsername("Setve").addPermission(ChangePasswordPermission()).build().get
    val authedChanger = new AuthenticatedUser(changingUser.id, changingUser.username, changingUser.userPermissions)
    user.changePassword(Password("blerp"), authedChanger)
    val unfounduser = User.authenticateByUsername("Admin", Password("joe"))
    assert(unfounduser.isEmpty)
    val founduser = User.authenticateByUsername("Admin", Password("blerp"))
    assert(founduser.isDefined)
  }

  "the permissions stuff" should "add permissions" in {
    val user = User.getUserByID(2).get
    assert(!user.hasPermission(CanChangePermissionsPermission()))
    val changingUser = User.getUserByID(1).get
    val authedChanger = new AuthenticatedUser(changingUser.id, changingUser.username, changingUser.userPermissions)
    val changedUser = user.addPermission(CanChangePermissionsPermission(), authedChanger)
    assert(changedUser.hasPermission(CanChangePermissionsPermission()))
    val retrievedUser = User.getUserByID(2).get
    assert(retrievedUser.hasPermission(CanChangePermissionsPermission()))
  }

  it should "remove permissions" in {
    val user = User.getUserByID(2).get
    val changingUser = User.getUserByID(1).get
    val authedChanger = new AuthenticatedUser(changingUser.id, changingUser.username, changingUser.userPermissions)
    val changedUser = user.addPermission(CanChangePermissionsPermission(), authedChanger)
    assert(changedUser.hasPermission(CanChangePermissionsPermission()))
    val retrievedUser = User.getUserByID(2).get
    assert(retrievedUser.hasPermission(CanChangePermissionsPermission()))

    changedUser.removePermission(CanChangePermissionsPermission(), authedChanger)
    val nopermuser = User.getUserByID(2).get

    assert(!nopermuser.hasPermission(CanChangePermissionsPermission()))
  }
  it should "ignore non-existent permissions" in {
    val user = User.getUserByID(2).get
    val changingUser = User.getUserByID(1).get
    val authedChanger = new AuthenticatedUser(changingUser.id, changingUser.username, changingUser.userPermissions)
    val changedUser = user.addPermission(CanChangePermissionsPermission(), authedChanger)
    assert(changedUser.hasPermission(CanChangePermissionsPermission()))
    val retrievedUser = User.getUserByID(2).get
    assert(retrievedUser.hasPermission(CanChangePermissionsPermission()))

    changedUser.removePermission(ChangePasswordPermission(), authedChanger)
    changedUser.removePermission(ChangePasswordPermission(), authedChanger)

    val nopermuser = User.getUserByID(2).get
    assert(!nopermuser.hasPermission(ChangePasswordPermission()))
  }

  "the user serialization code" should "serialize" in {
    import spray.json._
    import User.UserJSONProtocol._
    val user = User.getUserByID(1).get
    val expected = JsObject(
      "id" -> JsNumber(1),
      "username" -> JsString("Admin"),
      "permissions" -> JsArray(JsString("CanChangePermissionsPermission"))
    )
    user.toJson should be(expected)
  }
  it should "unserialize" in {
    import spray.json._
    import User.UserJSONProtocol._
    val jsUser = "{\"id\":3,\"username\":\"madeup\",\"permissions\":[\"CanChangePermissionsPermission\"]}"
    jsUser.parseJson.convertTo[User] should be(new User(3, "madeup", Set(CanChangePermissionsPermission())))
  }
  it should "break with made up permissions" in {
    import spray.json._
    import User.UserJSONProtocol._
    val jsUser = "{\"id\":3,\"username\":\"madeup\",\"permissions\":[\"CanJigglePermission\"]}"
    intercept[PermissionConstructionException] {
      jsUser.parseJson.convertTo[User]
    }
  }
  it should "break with not users" in {
    import spray.json._
    import User.UserJSONProtocol._
    val jsUser = "{\"username\":\"madeup\",\"permissions\":[\"CanChangePermissionsPermission\"]}"
    intercept[spray.json.DeserializationException] {
      jsUser.parseJson.convertTo[User]
    }
    val jsUser2 = "[]"
    intercept[spray.json.DeserializationException] {
      jsUser2.parseJson.convertTo[User]
    }
  }
  "user equality" should "work" in {
    val user1 = new User(1, "steve", Set(Permission("CanChangePermissionsPermission")))
    val user2 = new User(1, "steve", Set(Permission("CanChangePermissionsPermission")))
    val user3 = new User(2, "steve", Set(Permission("CanChangePermissionsPermission")))
    val user4 = new User(1, "bvo", Set(Permission("CanChangePermissionsPermission")))
    val user5 = new User(1, "steve", Set(Permission("CanChangePermissionsPermission"), Permission("ChangePasswordPermission")))
    val autheduser = new AuthenticatedUser(1, "steve", Set(Permission("CanChangePermissionsPermission")))
    user1 should be(user2)
    user2 should be(user1)
    user1 should not be user3
    user1 should not be user4
    user1 should not be user5
    user1 should not be autheduser
    user1 should not be "wut"
    user1 should not be 3
    user1.hashCode() should be(user2.hashCode())
    user2.hashCode() should not be user3.hashCode()
    autheduser should not be user1
  }
  "authed user equality" should "work" in {
    val user1 = new AuthenticatedUser(1, "steve", Set(Permission("CanChangePermissionsPermission")))
    val user2 = new AuthenticatedUser(1, "steve", Set(Permission("CanChangePermissionsPermission")))
    val user3 = new AuthenticatedUser(2, "steve", Set(Permission("CanChangePermissionsPermission")))
    val user4 = new AuthenticatedUser(1, "bvo", Set(Permission("CanChangePermissionsPermission")))
    val user5 = new AuthenticatedUser(1, "steve", Set(Permission("CanChangePermissionsPermission"), Permission("ChangePasswordPermission")))
    val nonauthedUser = new User(1, "steve", Set(Permission("CanChangePermissionsPermission")))
    user1 should be(user2)
    user2 should be(user1)
    user1 should not be user3
    user1 should not be user4
    user1 should not be user5
    user1 should not be nonauthedUser
    user1 should not be "wut"
    user1 should not be 3
    user1.hashCode() should be(user2.hashCode())
    user2.hashCode() should not be user3.hashCode()
    nonauthedUser should not be user1
  }
}