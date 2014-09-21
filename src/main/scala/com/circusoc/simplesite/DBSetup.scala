package com.circusoc.simplesite

import scalikejdbc._
import com.circusoc.simplesite.users.User


object DBSetup {
  def setup()(implicit config: WithConfig ) {
    NamedDB(config.db .symbol) autoCommit {implicit ses =>
      sql"CREATE TABLE user (id INTEGER PRIMARY KEY, username VARCHAR(100) NOT NULL, password VARCHAR(100) NOT NULL);".execute()()
      sql"CREATE TABLE permission (user_id INTEGER NOT NULL, permission VARCHAR(100) NOT NULL);".execute()()
      sql"""CREATE TABLE hirerequest (id INTEGER PRIMARY KEY AUTO_INCREMENT,
                                      email VARCHAR(100) NOT NULL,
                                      location VARCHAR(100))""".execute()()
      sql"""CREATE TABLE hirerequest_skill (hirerequest_id INTEGER NOT NULL, skill VARCHAR(100))""".execute()()
    }
  }
}
