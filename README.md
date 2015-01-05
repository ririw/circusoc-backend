todo
====
* write the seo magic code
 * Write the conceptnet extractor
 * Write a small module/script for doing large scale SVD of the whole system
 * Run on AWS to produce the SVD factorization
 * Truncate the matrices so they're of manageable size for a single machine
 * Use the S, V and D matrices to infer the similarity with a particular set of concepts, which are related to the ones we care about.
 * Write an m-tree implementation so we can do all the clever quick searches
* Write mock SEO magic code
 * This actually encompasses far more than just SEO. It'll be used to select who is displayed on various pages
 * We can create a mock version where the display is done with tags or something
 * Good for testing.
* write the gallery code ✓
* write the tracking code
 * event reporting ✓
 * user source reporting ✓
 * user fraud detection (multiple hire requests etc)
 * site issue detection code
* Write site-setup code
 * We should be able to initialize the site with full sets of valid users
 * And be able to select out a set of users interest, eg, members who have signed up but haven't opted in to the
   newsletter
* write the member code
 * member email
 * member signup handler
 * member edit code
* Use typestates to avoid re-using stale references? 

user stories
------------
* as a member of the public, I should be able to hire circusoc
* as a potential circusoc member, I should be able to get information on the club
* as an admin, I should be able to email users
* as an admin, I should be able to make sure members are signed up
* as an admin, I should be able to audit our users

Testing
=======
