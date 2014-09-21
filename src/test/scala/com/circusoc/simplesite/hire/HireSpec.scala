package com.circusoc.simplesite.hire

import org.dbunit.{PropertiesBasedJdbcDatabaseTester, DBTestCase}
import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import com.circusoc.simplesite.{DBSetup, Hire, DB, WithConfig}
import scalikejdbc.ConnectionPool
import java.sql.{DriverManager, Connection}
import org.dbunit.database.DatabaseConnection
import org.dbunit.operation.DatabaseOperation
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class HireSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter {
  val dbtype = "h2"
  dbtype match {
    case "h2" =>
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, "org.h2.jdbcDriver" )
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, "jdbc:h2:mem:hirespec;DB_CLOSE_DELAY=-1" )
    case "hsqldb" =>
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, "org.hsqldb.jdbc.JDBCDriver")
      System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, "jdbc:hsqldb:mem:hirespec;DB_CLOSE_DELAY=-1" )
  }
  System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME, "sa" )
  System.setProperty( PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD, "" )

  implicit val config = new WithConfig {
    override val db: DB = new DB {
      override val symbol = 'hirespec
      override def setup() = {
        dbtype match {
          case "h2" => Class.forName("org.h2.Driver")
          case "hsqldb" => Class.forName("org.hsqldb.jdbc.JDBCDriver")
        }
        val url = dbtype match {
          case "h2" =>     s"jdbc:h2:mem:${symbol.name};DB_CLOSE_DELAY=-1"
          case "hsqldb" => s"jdbc:hsqldb:mem:${symbol.name};DB_CLOSE_DELAY=-1"
        }
        ConnectionPool.add(symbol, url, "sa", "")
      }
    }
    override val hire: Hire = new Hire {}
  }
  def getJDBC(): Connection = {
    dbtype match {
      case "h2" => Class.forName("org.h2.Driver")
      case "hsqldb" => Class.forName("org.hsqldb.jdbc.JDBCDriver")
    }
    val c = dbtype match {
      case "h2" => DriverManager.getConnection("jdbc:h2:mem:hirespec;DB_CLOSE_DELAY=-1", "sa", "")
      case "hsqldb" => DriverManager.getConnection("jdbc:hsqldb:mem:hirespec;DB_CLOSE_DELAY=-1", "sa", "")
    }
    c.setAutoCommit(true)
    c
  }
  config.db.setup()
  DBSetup.setup()

  val conn = new DatabaseConnection(getJDBC())
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[HireSpec].
    getResourceAsStream("/com/circusoc/simplesite/users/UserDBSpec.xml"))

  it should "send emails" in {
    val hire = Hire.hire(EmailAddress("richard@example.com"), Location("sydney"), List("Fire", "Juggles"))
    val result = Await.result(hire, Duration.Inf)
  }
}
