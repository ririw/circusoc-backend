package com.circusoc.testgraph

import java.io.{File, FileInputStream, FilenameFilter}

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.pictures.{PictureReference, PictureResult}
import com.circusoc.simplesite.users.AuthenticatedUser
import com.circusoc.simplesite.users.permissions.ModifyImagesPermission
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.util.Random

object PictureTestGraph {
  val conf = {
    val configFile = new File("./backend.conf")
    if (configFile.canRead)
      ConfigFactory.parseFile(configFile)
    else
      ConfigFactory.load()
  }
  val picFilter = new FilenameFilter {
    override def accept(dir: File, name: String): Boolean = {
      val namelower = name.toLowerCase
      name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
    }
  }
  def pictureFactory(implicit config: WithConfig): TestNodeFactory[PictureReference] = {
    new TestNodeFactory[PictureReference] {
      val pictureLocation = new File(conf.getString("com.circusoc.pictures.randomimages_dir"))
      assert(pictureLocation.exists() && pictureLocation.isDirectory, "Could not find images")
      val pictures = Random.shuffle(pictureLocation.listFiles(picFilter).toList)
      val pictureQueue = new mutable.Queue[File]()
      pictureQueue ++= pictures
      val fakeUser = new AuthenticatedUser(-1, "steve", Set(ModifyImagesPermission))
      override def randomItem(): PictureReference = {
        val selectedPic = pictureQueue.dequeue()
        val is = new FileInputStream(selectedPic)
        val pictureResult = PictureResult(is).get
        PictureReference.putPicture(pictureResult, fakeUser)
      }
    }
  }
}



