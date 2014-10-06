package com.circusoc.simplesite

import scalikejdbc._
import com.circusoc.simplesite.users.User
import org.slf4j.LoggerFactory


object DBSetup {
  val logger = LoggerFactory.getLogger(DBSetup.getClass.getName)

  def setup()(implicit config: WithConfig ) {
    NamedDB(config.db.poolName) autoCommit {implicit ses =>
      logger.warn("Creating database schema.")
      sql"CREATE TABLE user (id INTEGER PRIMARY KEY, username VARCHAR(100) NOT NULL, password VARCHAR(100) NOT NULL);".execute()()
      sql"CREATE TABLE permission (user_id INTEGER NOT NULL, permission VARCHAR(100) NOT NULL);".execute()()
      sql"""CREATE TABLE hirerequest (id INTEGER PRIMARY KEY AUTO_INCREMENT,
                                      email VARCHAR(100) NOT  NULL,
                                      location VARCHAR(100))""".execute()()
      sql"""CREATE TABLE hirerequest_skill (hirerequest_id INTEGER NOT NULL, skill VARCHAR(100))""".execute()()
      sql"""CREATE TABLE performer (
        id INTEGER PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        profile_picture_id INTEGER NOT NULL,
        shown BOOLEAN
      )""".execute()()
      sql"""CREATE TABLE performer_skill (
        performer_id INTEGER NOT NULL,
        skill VARCHAR(100) NOT NULL
      )""".execute()()
      sql"""CREATE TABLE performer_picture (
        performer_id INTEGER NOT NULL,
        picture_id INTEGER NOT NULL
      )""".execute()()
      sql"""CREATE TABLE picture (id INTEGER PRIMARY KEY AUTO_INCREMENT,
                                  mediatype VARCHAR(100),
                                  picture BLOB)""".execute()()
      sql"""CREATE TABLE token (userid INTEGER,
                                token VARCHAR(100),
                                created TIMESTAMP DEFAULT current_timestamp)""".execute()()
    }
  }
}
