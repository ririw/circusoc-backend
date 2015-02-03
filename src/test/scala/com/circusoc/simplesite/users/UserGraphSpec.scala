package com.circusoc.simplesite.users

import com.circusoc.simplesite.GraphTestingConf
import com.circusoc.simplesite.users.permissions.Permission
import com.circusoc.testgraph.{UserTestGraph, TestNodeFactory}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

/**
 * Created by riri on 25/01/15.
 */
class UserGraphSpec extends FlatSpec {
  import GraphTestingConf._
   it should "do things that I expect with reflection" in {
     val factoryA = UserTestGraph.permissionsFactory[permissions.CanAdministerUsersPermission.type]
     factoryA.randomItem() should be(permissions.CanAdministerUsersPermission)
     // val factoryB = UserTestGraph.permissionsFactory[Integer] // this doesn't compile, as expected.
   }
   it should "create a user" in {
     val userFactory = UserTestGraph.userFactory
     val user = userFactory.randomNode()
     val permsFactory: TestNodeFactory[Permission] = UserTestGraph.permissionsFactory[permissions.CanAdministerUsersPermission.type]
     val perm = permsFactory.randomNode()

     val join = UserTestGraph.addPermissionJoiner
     join.join(user, perm)

     val userGot = User.authenticateByUsername(user.node.username, userFactory.retrieveUserPassword(user.node))
     assert(userGot.isDefined)
     assert(userGot.get.hasPermission(permissions.CanAdministerUsersPermission))
   }
 }
