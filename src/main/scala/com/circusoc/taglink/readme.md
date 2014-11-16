Taglink service
===============

The taglink service is used to create and request lists of objects for various places on the site. For example, one
might want the list of images to display on the homepage jumbotron. To get it, we place a request to the taglink
service:
    GET taglink/<location>/<tag>
And then get the response:
    {
        "result": "success",
        "objects": ["/pictures/1.jpg", "/pictures/2.jpg"]
    }
    
Now, the taglink service isn't just for pictures. It's up to the programmer to ensure that the correct type of resource
is returned for any request.

Taglink works by tracking the page/tag pairs, and then returning the set of results.

You **may** put whatever you want for location and tag, but it is **suggested** that location correspond to the 
requesting page, and the tag be something meaningful. This ensures you don't muck up the global namespace

It is also **suggested** that you use the angular routing conventions to specify locations. That way we can create 
groups of content that work over several pages in a clear way.

Authentication
==============
I need to sort out how we'll authenticate users using their simplesite credentials.

REST Spec
=========
    GET    taglink/<location>/<tag>
    POST   taglink/<location>/<tag> - set the objects related to a particular spec
    DELETE taglink/<location>/<tag> - delete a spec
    
Why do all this
===============
Because soon, some of the specs will work automatically, where you'll be able to ask for the set of pictures related
to the word "fire" and you'll get them. With any luck, the GET location/tag approach will stay the same, although the 
administration parts will be much more complicated.