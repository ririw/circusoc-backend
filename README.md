todo
====
* write the seo magic code
 * Write the conceptnet extractor
 * Write a small module/script for doing large scale SVD of the whole system
 * Run on AWS to produce the SVD factorization
 * Truncate the matrices so they're of manageable size for a single machine
 * Use the S, V and D matrices to infer the similarity with a particular set of concepts, which are related to the ones we care about.
* write the gallery code ✓
* write the tracking code
 * event reporting ✓
 * user source reporting ✓
 * user fraud detection (multiple hire requests etc)
 * site issue detection code
* write the member code
 * member email
 * member signup handler
 * member edit code

user stories
------------
* as a member of the public, I should be able to hire circusoc
* as a potential circusoc member, I should be able to get information on the club
* as an admin, I should be able to email users
* as an admin, I should be able to make sure members are signed up
* as an admin, I should be able to audit our users

bug log
=======
* 951fe1244710e5d31279b36593e159edb732aabc: T - I copy pasted some an SQL insert, and didn't change the table name. Caught by unit tests.

