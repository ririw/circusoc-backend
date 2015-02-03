package com.circusoc

import java.io.File

import com.typesafe.config.ConfigFactory
import org.scalatest.FlatSpec

/**
  * I know config can be a pain
  * I hope this helps you figure out what's going on.
  */
class ConfigHelperSpec extends FlatSpec{
  val conf = ConfigFactory.load()

  it should "have a random dir" in {
    try {
      val s = conf.getString("com.circusoc.pictures.randomimages_dir")
      if (s.length < 0)
        fail("The string for the config item was too short. Consider un-ignoring some other tests to help debug")
      assert(s.length > 0)
    } catch {
      case _: com.typesafe.config.ConfigException =>
        fail("The config item was missing. Consider un-ignoring some other tests to help debug")
    }
  }

  it should "have the circus config" ignore {
    assert(conf.hasPath("com.circusoc"))
  }

  it should "print out all the config, just for you" ignore {
    val circusconf = conf.getConfig("com.circusoc")
    println("here is your config")
    println("###############################")
    println(circusconf)
    println("###############################")
  }

  it should "print out where it thinks the config is coming from" ignore {
    println(conf.root().render())
  }
}
