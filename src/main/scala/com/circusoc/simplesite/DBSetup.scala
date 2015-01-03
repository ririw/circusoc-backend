package com.circusoc.simplesite

import org.slf4j.LoggerFactory
import scalikejdbc._


object DBSetup {
  val logger = LoggerFactory.getLogger(DBSetup.getClass.getName)

  def setup()(implicit config: WithConfig ) {
    config.db.getDB autoCommit {implicit ses =>
      logger.warn("Creating database schema.")
      sql"""CREATE TABLE user (
        id INTEGER PRIMARY KEY AUTO_INCREMENT,
        username VARCHAR(1024) NOT NULL,
        password VARCHAR(1024) NOT NULL,
        UNIQUE (username)
        );
      """.execute()()
      sql"""CREATE TABLE permission (
        user_id INTEGER NOT NULL,
        permission VARCHAR(1024) NOT NULL,
        UNIQUE (user_id, permission),
        FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE cascade ON UPDATE cascade
        );
      """.execute()()
      sql"""CREATE TABLE hirerequest (
        id INTEGER PRIMARY KEY AUTO_INCREMENT,
        email VARCHAR(1024) NOT  NULL,
        location VARCHAR(1024));
      """.execute()()
      sql"""CREATE TABLE hirerequest_skill (
        hirerequest_id INTEGER NOT NULL,
        skill VARCHAR(1024),
        UNIQUE (hirerequest_id, skill),
        FOREIGN KEY (hirerequest_id) REFERENCES hirerequest (id) ON DELETE cascade ON UPDATE cascade
        )
      """.execute()()
      sql"""CREATE TABLE performer (
        id INTEGER PRIMARY KEY AUTO_INCREMENT,
        name VARCHAR(1024) NOT NULL,
        profile_picture_id INTEGER NOT NULL,
        shown BOOLEAN NOT NULL)
      """.execute()()
      sql"""CREATE TABLE performer_skill (
        performer_id INTEGER NOT NULL ,
        skill VARCHAR(1024) NOT NULL,
        UNIQUE (performer_id, skill),
        FOREIGN KEY (performer_id) REFERENCES performer (id) ON DELETE cascade ON UPDATE cascade
        )
      """.execute()()
      sql"""CREATE TABLE picture (
        id INTEGER PRIMARY KEY AUTO_INCREMENT,
        mediatype VARCHAR(1024),
        picture BLOB)
      """.execute()()
      sql"""CREATE TABLE default_performer_picture (
        picture_id INTEGER NOT NULL,
        singleton BOOLEAN NOT NULL DEFAULT TRUE,
        UNIQUE (singleton),
        CHECK(singleton=TRUE)
      )""".execute()()
      sql"""CREATE TABLE performer_picture (
        performer_id INTEGER NOT NULL,
        picture_id INTEGER NOT NULL,
        FOREIGN KEY (performer_id) REFERENCES performer (id) ON DELETE cascade ON UPDATE cascade,
        )
      """.execute()()
      sql"""CREATE TABLE token (
        user_id INTEGER,
        token VARCHAR(1024),
        created TIMESTAMP DEFAULT current_timestamp)
      """.execute()()
      sql"""CREATE TABLE member (
        id INTEGER PRIMARY KEY AUTO_INCREMENT,
        name VARCHAR(1024) NOT NULL,
        email VARCHAR(1024),
        student_number VARCHAR(1024),
        is_arc BOOLEAN NOT NULL,
        subscribed BOOLEAN NOT NULL,
        UNIQUE (email),
        UNIQUE (name),
        UNIQUE (student_number),
        -- ensure we exclude a situation where is_arc is True and student number is null.
        -- if student number is not null, this check succeeds
        -- if it is null, we go on to check is_arc is false
        CHECK(student_number IS NOT NULL or is_arc = FALSE)
      )""".execute()()
      sql"""CREATE TABLE member_waiver (
        member_id integer not null,
        waiver_time TIMESTAMP NOT NULL,
        FOREIGN KEY (member_id) REFERENCES member (id)
      )""".execute()()
      sql"""CREATE TABLE member_payment (
        member_id integer not null,
        payment_time TIMESTAMP NOT NULL,
        FOREIGN KEY (member_id) REFERENCES member (id)
      )""".execute()()
      sql"""CREATE SCHEMA tracking""".execute()()
      sql"""CREATE TABLE tracking.page_views(
        clientid VARCHAR(1024) NOT NULL,
        sessionid VARCHAR(1024) NOT NULL,
        pageid VARCHAR(1024) NOT NULL,
        viewtime TIMESTAMP NOT NULL,
        page VARCHAR(1024) NOT NULL,
        referrer VARCHAR(1024)
      )""".execute()()
      sql"""CREATE TABLE tracking.page_actions(
        clientid VARCHAR(1024) NOT NULL,
        sessionid VARCHAR(1024) NOT NULL,
        pageid VARCHAR(1024) NOT NULL,
        viewtime TIMESTAMP NOT NULL,
        page VARCHAR(1024) NOT NULL,
        label VARCHAR(1024) NOT NULL,
        section VARCHAR(1024)
      )""".execute()()
    }
  }
}
