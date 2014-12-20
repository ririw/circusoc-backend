What's the aim here? We want to be able to fill out the tag link system, as well as the rest of our system, with test 
users. We'd like to be able to specify a setup in code or a configuration file, and have it be applied in the system.
The main issue is the expressivity of the system. It's very easy to create a DSL that doesn't really express anything 
other than very basic config. Then I'll be forced to write painfully large amounts of code to build up useful test 
cases.

I'll try to enumerate some of the cases I'd want to cover

 - Give me a set of objects, and ensure they appear in the database
 - We wish to be able to join objects, but that's quite easy
 - We wish to be able to join sets of objects to other sets of objects:
 
       •───•      •───• 
          ╱        ╲ ╱ 
         ╱          ╳  
        ╱          ╱ ╲ 
       •───•      •───•  etc.
          ╱        ╲ ╱ 
         ╱          ╳  
        ╱          ╱ ╲ 
       •───•      •───•
       
  and various other combinations. Sadly, I'm really asking for ways to express graphs without much thinking. But there
  aren't many ways to do that. I may try a "mark and connect" approach. I build up sets of objects, along with markers
  that represent sets of objects. Then, I can combine this easily with a few basic connection approaches. For example,
  I could have a "cartesian product" connection strategy, a zipper connection strategy, a random connection strategy,
  and so on. The final part of the system are pre-join and post-join calls that I can use to set up anything that needs 
  setup.
  
  With any luck, I'll be able to use this system in a lot of places.
  
# Design

The basic building block, and the interface between the tested system and the graph-builder, is the ObjectFactory class.
The test system uses this factory to generate the objects. The factory should also declare, as implicits (viz. 
typeclasses) a set of connectors, that we can use to join any two objects that may be joined. 
 
 - The Object Factory also includes a type level argument for the class that's created
 - The join class has two type-level arguments, for the types being joined.
 - When the object factory manufactures an object, we may include a "marking" as an optional argument. Where the marking
   is specified, it may be used to create joins later on
 - Joins may be specified manually, by executing the ".join" method with another instance, as long as there exists an 
   implementation for the join type.
 - Joins may also be executed in several ways on collections of objects. In this case, there are different join 
   strategies, such as "cartesian", "zipper", "fanout", "random" (I'm still not clear on how "random" will work, but 
   it'll probably end up the workhorse of the system).
 - You are not required to set up join names and object names, but it's strongly recommended. By adding (meaningful) 
   names to all the objects created in the object-graph, we can produce images of the object graph, for verification.
   
# Scalability

It would be good to be able to use this system with ScalaCheck, to generate random test cases. For this to work, we 
could perhaps create a description object, that builds the nodes and the joins, and then wrap that as a Gen instance 
which could generate as many instances of the tested object graph as needed. But for now, we'll keep it simple. 