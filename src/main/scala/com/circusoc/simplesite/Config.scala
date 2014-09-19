package com.circusoc.simplesite

import java.sql.{Connection, DriverManager}
import scalikejdbc._
trait WithConfig {
  val db: DB
}

trait DB {
  def setup() = {
    Class.forName("org.h2.Driver")
    ConnectionPool.singleton("jdbc:h2:~/test", "sa", "")
    ConnectionPool.add('foo, "jdbc:h2:~/test", "sa", "")
  }
}
