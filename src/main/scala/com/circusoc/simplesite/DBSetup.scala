package com.circusoc.simplesite

import scalikejdbc._
import com.circusoc.simplesite.users.User


object DBSetup {
  def setup()(implicit config: WithConfig ) {
    DB autoCommit {implicit ses =>
      sql"CREATE TABLE user (id INTEGER PRIMARY KEY, username VARCHAR(100) NOT NULL, password VARCHAR(100) NOT NULL);".execute().apply()
      sql"CREATE TABLE permissions (user_id INTEGER NOT NULL, permission VARCHAR(100) NOT NULL);".execute().apply()
    }
  }
}
