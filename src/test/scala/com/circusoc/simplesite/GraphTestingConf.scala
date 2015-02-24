package com.circusoc.simplesite

import java.net.URL

import org.codemonkey.simplejavamail.Email
import scalikejdbc.ConnectionPool

object GraphTestingConf {
  implicit val config = new WithConfig {
    override val port: Int = 8080
    override val db: DB = new DB {
      override val poolName = 'usergraphtests
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
  }

  config.db.setup()
  DBSetup.setup()
}
