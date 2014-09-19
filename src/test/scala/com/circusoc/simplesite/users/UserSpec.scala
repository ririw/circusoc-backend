package com.circusoc.simplesite.users.permissions

import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import org.scalatest.Matchers._
import org.dbunit.{PropertiesBasedJdbcDatabaseTester, DBTestCase}
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import com.circusoc.simplesite.{DBSetup, DB, WithConfig}
import java.sql.{Connection, DriverManager}
import com.circusoc.simplesite.users.{Password, User}
import org.dbunit.database.DatabaseConnection
import org.dbunit.operation.DatabaseOperation
import scalikejdbc.ConnectionPool
import com.circusoc.simplesite.users.User.UserBuilder

class UserSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter {
val dbtype = "h2"
  dbtype match {
    case "h2" =>
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, "org.h2.jdbcDriver" )
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, "jdbc:h2:mem:userspec;DB_CLOSE_DELAY=-1" )
    case "hsqldb" =>
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, "org.hsqldb.jdbc.JDBCDriver")
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, "jdbc:hsqldb:mem:userspec;DB_CLOSE_DELAY=-1" )
    case "sqlite" =>
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, "org.sqlite.JDBC" )
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, "jdbc:sqlite:/home/riri/tmp/test.db" )
  }
  System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME, "sa" )
  System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD, "" )

  implicit val config = new WithConfig {
    override val db: DB = new DB {
      override def setup() = {
        dbtype match {
          case "h2" => Class.forName("org.h2.Driver")
          case "hsqldb" => Class.forName("org.hsqldb.jdbc.JDBCDriver")
          case "sqlite" => Class.forName("org.sqlite.JDBC")
        }
        val url = dbtype match {
          case "h2" =>     "jdbc:h2:mem:userspec;DB_CLOSE_DELAY=-1"
          case "hsqldb" => "jdbc:hsqldb:mem:userspec;DB_CLOSE_DELAY=-1"
          case "sqlite" => "jdbc:sqlite:/home/riri/tmp/test.db"
        }
        ConnectionPool.singleton(url, "sa", "")
      }
    }
  }
  def getJDBC(): Connection = {
    dbtype match {
      case "h2" => Class.forName("org.h2.Driver")
      case "hsqldb" => Class.forName("org.hsqldb.jdbc.JDBCDriver")
      case "sqlite" => Class.forName("org.sqlite.JDBC")
    }
    val c = dbtype match {
      case "h2" => DriverManager.getConnection("jdbc:h2:mem:userspec;DB_CLOSE_DELAY=-1", "sa", "")
      case "hsqldb" => DriverManager.getConnection("jdbc:hsqldb:mem:userspec;DB_CLOSE_DELAY=-1", "sa", "")
      case "sqlite" => DriverManager.getConnection("jdbc:sqlite:/home/riri/tmp/test.db")
    }
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

  it should "change passwords and authenticate" in {
    val user = new UserBuilder().addId(1).addUsername("Admin").build().get
    User.changePassword(user, Password("joe"), User.MayChangePassProof.isTest())
    val founduser = User.authenticateByUsername("Admin", Password("joe"))
    assert(founduser.isDefined)
    val unfounduser = User.authenticateByUsername("Admin", Password("joeee"))
    assert(unfounduser.isEmpty)
  }
}