package com.circusoc.simplesite.pictures

import java.io.{FilenameFilter, File, FileFilter, FileInputStream}
import javax.swing.filechooser.FileNameExtensionFilter

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.users.AuthenticatedUser
import com.circusoc.simplesite.users.permissions.ModifyImagesPermission
import com.circusoc.testgraph.TestNodeFactory
import com.typesafe.config.ConfigFactory
import org.scalatest.FlatSpec

import scala.collection.mutable
import scala.util.Random

object PictureTestGraph {
  val conf = ConfigFactory.load()
  val picFilter = new FilenameFilter {
    override def accept(dir: File, name: String): Boolean = {
      val namelower = name.toLowerCase
      name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
    }
  }
  def pictureFactory(implicit config: WithConfig): TestNodeFactory[Picture] = {
    new TestNodeFactory[Picture] {
      val pictureLocation = new File(conf.getString("com.circusoc.test.pictures.randomimages_dir"))
      assert(pictureLocation.exists() && pictureLocation.isDirectory, "Could not find images")
      val pictures = Random.shuffle(pictureLocation.listFiles(picFilter).toList)
      val pictureQueue = new mutable.Queue[File]()
      pictureQueue ++= pictures
      val fakeUser = new AuthenticatedUser(-1, "steve", Set(ModifyImagesPermission()))
      override def randomItem(): Picture = {
        val selectedPic = pictureQueue.dequeue()
        val is = new FileInputStream(selectedPic)
        val pictureResult = PictureResult(is).get
        Picture.putPicture(pictureResult, fakeUser)
      }
    }
  }
}

class PictureTestGraphSpec extends FlatSpec {
  import com.circusoc.simplesite.GraphTestingConf._
  it should "create pictures" in {
    val factory = PictureTestGraph.pictureFactory
    val picture = factory.randomItem()
    assert(Picture.getPicture(picture).isDefined)
  }
}


